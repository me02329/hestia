#!/usr/bin/env python3
"""Conservative, journaled HOME switching over an already paired ADB transport.

Never changes debugging, installs an APK, or starts an activity.
Snapshot -> preview the installed app manually -> activate -> restore.

On firmwares where Google TV's launcher outranks any chosen HOME, disable-stock-home turns off exactly the
STOCK packages (recorded next to the backup) and selects Hestia; enable-stock-home turns those back on and puts
the snapshot's HOME and screensaver back. No other package is ever touched.
"""
import argparse
from contextlib import contextmanager
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
import tempfile
import time

HOME = "io.hestia.tv/io.hestia.tv.HomeActivity"
# Google TV's launcher and its setup "recovery" HOME, both above any user-chosen HOME on the TCL G08.
STOCK = ("com.google.android.apps.tv.launcherx", "com.google.android.tungsten.setupwraith")
PACKAGE = re.compile(r"[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+")
DREAM = "io.hestia.tv/io.hestia.tv.Screensaver"
SETTING = "screensaver_components"
HOME_SETTLE_SECONDS = 10
MAIN = "android.intent.action.MAIN"
CATEGORY = "android.intent.category.HOME"
COMPONENT = re.compile(r"[A-Za-z_][A-Za-z0-9_.]*/[A-Za-z_.][A-Za-z0-9_.$]*")


class SafetyError(RuntimeError):
    pass


def component(value):
    if not isinstance(value, str) or not COMPONENT.fullmatch(value):
        raise SafetyError("Invalid Android component")
    package, activity = value.split("/")
    return package + "/" + (package + activity if activity.startswith(".") else activity)


def components(output):
    return {component(line.strip()) for line in output.splitlines()
            if COMPONENT.fullmatch(line.strip())}


class Adb:
    def __init__(self, executable, serial):
        self.executable, self.serial = executable, serial

    def shell(self, *args):
        # adb shell uses a remote shell even with subprocess(shell=False).
        # Quote every remote argument, including snapshot values.
        try:
            result = subprocess.run([self.executable, "-s", self.serial, "shell", shlex.join(args)],
                                    capture_output=True, text=True, timeout=25, check=False)
        except (OSError, subprocess.TimeoutExpired) as e:
            raise SafetyError("ADB unavailable or timed out") from e
        if result.returncode:
            raise SafetyError("ADB command failed: " + (result.stderr or result.stdout).strip()[:400])
        return result.stdout.rstrip("\r\n")

    def identity(self):
        if self.shell("am", "get-current-user") != "0":
            raise SafetyError("Only the primary Android user (0) is supported")
        serial = self.shell("getprop", "ro.serialno") or self.shell("getprop", "ro.boot.serialno")
        fingerprint = self.shell("getprop", "ro.build.fingerprint")
        sdk = self.shell("getprop", "ro.build.version.sdk")
        if not serial or serial.lower() == "unknown" or not fingerprint or not sdk.isdigit() or int(sdk) < 33:
            raise SafetyError("Device identity unavailable or Android older than 13")
        return {"serial": serial, "fingerprint": fingerprint, "sdk": int(sdk), "user": 0}

    def homes(self):
        return components(self.shell("cmd", "package", "query-activities", "--brief", "--user", "0",
                                     "-a", MAIN, "-c", CATEGORY))

    def home(self):
        found = components(self.shell("cmd", "package", "resolve-activity", "--brief", "--user", "0",
                                      "-a", MAIN, "-c", CATEGORY))
        if len(found) != 1 or not found.issubset(self.homes()):
            raise SafetyError("No unambiguous enabled default HOME; refusing to guess")
        return found.pop()

    def dream(self):
        # Unlike `settings get`, this distinguishes absent from the literal value "null".
        output = self.shell("settings", "--user", "0", "list", "secure")
        if not output or "=" not in output or output.startswith(("Error", "Exception", "Unknown")):
            raise SafetyError("Cannot read secure settings")
        matches = [line.partition("=")[2] for line in output.splitlines()
                   if line.startswith(SETTING + "=")]
        if len(matches) > 1:
            raise SafetyError("Ambiguous screensaver setting")
        return matches[0] if matches else None

    def set_home(self, value):
        value = component(value)
        if value not in self.homes():
            raise SafetyError("Requested home is not enabled/installed")
        output = self.shell("cmd", "package", "set-home-activity", "--user", "0", value)
        if output.startswith(("Error", "Exception", "Failure")):
            raise SafetyError("set-home-activity refused: " + output[:400])
        # Android 14 applies the HOME role holder asynchronously; give resolution time to follow.
        deadline = time.monotonic() + HOME_SETTLE_SECONDS
        while True:
            try:
                if self.home() == value:
                    return
            except SafetyError:
                pass  # Transiently ambiguous while the role propagates.
            if time.monotonic() >= deadline:
                raise SafetyError("Android did not retain the requested HOME")
            time.sleep(0.5)

    def disabled(self):
        output = self.shell("pm", "list", "packages", "-d", "--user", "0")
        return {line[len("package:"):].strip() for line in output.splitlines() if line.startswith("package:")}

    def set_enabled(self, package, enabled):
        if not PACKAGE.fullmatch(package):
            raise SafetyError("Invalid package name")
        self.shell("pm", "enable" if enabled else "disable-user", "--user", "0", package)
        if (package in self.disabled()) == enabled:
            raise SafetyError("Package state read-back failed: " + package)

    def set_dream(self, value):
        if value is None:
            self.shell("settings", "--user", "0", "delete", "secure", SETTING)
        else:
            self.shell("settings", "--user", "0", "put", "secure", SETTING, value)
        if self.dream() != value:
            raise SafetyError("Screensaver read-back failed")


def encoded(data):
    return (json.dumps(data, sort_keys=True, indent=2) + "\n").encode()


def durable_write(path, data, exclusive=False):
    """Same-filesystem atomic publication + fsync; never truncate a good backup."""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    fd, temporary = tempfile.mkstemp(prefix=".tv-safe-", dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as stream:
            stream.write(encoded(data))
            stream.flush()
            os.fsync(stream.fileno())
        if exclusive:
            os.link(temporary, path)  # Fails if a backup already exists, including a symlink.
        else:
            os.replace(temporary, path)
        directory = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


@contextmanager
def device_lock(identity):
    # One lock per physical TV, even when different backup files/ADB ports are used.
    directory = Path.home() / ".local/state/hestia/locks"
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    name = hashlib.sha256(identity["serial"].encode()).hexdigest() + ".lock"
    with (directory / name).open("a") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as e:
            raise SafetyError("Another operation is running for this TV") from e
        yield


def read_json(path):
    try:
        with Path(path).open() as stream:
            value = json.load(stream)
        if not isinstance(value, dict):
            raise ValueError()
        return value
    except (OSError, ValueError) as e:
        raise SafetyError("Missing or invalid JSON: " + str(path)) from e


def journal_path(path):
    return Path(str(path) + ".journal.json")


def digest(snapshot):
    return hashlib.sha256(encoded(snapshot)).hexdigest()


def validate_snapshot(adb, snapshot):
    if snapshot.get("schema") != 1 or snapshot.get("identity") != adb.identity():
        raise SafetyError("Snapshot belongs to another TV, user, or firmware; no changes made")
    original = snapshot.get("home")
    if component(original) == HOME or original not in adb.homes():
        raise SafetyError("Original HOME is not a valid enabled alternative")
    if "dream" not in snapshot or (snapshot["dream"] is not None and not isinstance(snapshot["dream"], str)):
        raise SafetyError("Invalid screensaver snapshot")


def snapshot(adb, path):
    identity = adb.identity()
    home = adb.home()
    if home == HOME:
        raise SafetyError("Already using Hestia; the original home cannot be inferred")
    data = {"schema": 1, "identity": identity, "home": home, "dream": adb.dream()}
    # No mutations on the TV; backup is immutable and private (0600).
    durable_write(path, data, exclusive=True)
    return data


def load_journal(path, saved):
    journal = read_json(journal_path(path))
    if (journal.get("schema") != 1 or journal.get("snapshot") != digest(saved)
            or type(journal.get("screensaver")) is not bool
            or journal.get("phase") not in {"applying", "active", "restoring", "restored"}):
        raise SafetyError("Invalid journal or snapshot/journal mismatch")
    return journal


def restore(adb, path):
    saved = read_json(path)
    validate_snapshot(adb, saved)
    journal = load_journal(path, saved)
    if journal["phase"] == "restored":
        # Idempotent, but don't report success if someone changed the state afterwards.
        if adb.home() != saved["home"] or (journal["screensaver"] and adb.dream() != saved["dream"]):
            raise SafetyError("State changed after restoration; refusing to overwrite it")
        return
    current = adb.home()
    current_dream = adb.dream() if journal["screensaver"] else None
    conflicts = []
    if current not in {HOME, saved["home"]}:
        conflicts.append("HOME changed outside this operation")
    if journal["screensaver"] and current_dream not in (DREAM, saved["dream"]):
        conflicts.append("screensaver changed outside this operation")
    unverified = journal["phase"] in {"applying", "restoring"}
    journal["phase"] = "restoring"
    durable_write(journal_path(path), journal)
    # Recover each non-conflicting resource independently, especially HOME.
    errors = []
    # An interrupted or unverified activation may have moved the HOME role without changing resolution
    # (e.g. a higher-priority system launcher still resolves first), so re-apply the saved HOME then too.
    if current == HOME or (current == saved["home"] and unverified):
        try:
            adb.set_home(saved["home"])
        except SafetyError as e:
            errors.append(str(e))
    if journal["screensaver"] and current_dream == DREAM and current_dream != saved["dream"]:
        try:
            adb.set_dream(saved["dream"])
        except SafetyError as e:
            errors.append(str(e))
    if conflicts or errors:
        raise SafetyError("Restoration incomplete: " + "; ".join(conflicts + errors))
    if adb.home() != saved["home"] or (journal["screensaver"] and adb.dream() != saved["dream"]):
        raise SafetyError("Restoration final verification failed")
    journal["phase"] = "restored"
    durable_write(journal_path(path), journal)


def activate(adb, path, screensaver=False):
    saved = read_json(path)
    validate_snapshot(adb, saved)
    if journal_path(path).exists():
        journal = load_journal(path, saved)
        if journal["phase"] != "restored":
            raise SafetyError("Existing operation: run restore before a new activation")
    if HOME not in adb.homes():
        raise SafetyError("Install and preview Hestia before activating")
    if adb.home() != saved["home"] or (screensaver and adb.dream() != saved["dream"]):
        raise SafetyError("TV state changed since snapshot; no changes made")
    journal = {"schema": 1, "snapshot": digest(saved), "screensaver": screensaver, "phase": "applying"}
    # Persist intent before the first command, including when ADB later times out after executing it.
    durable_write(journal_path(path), journal)
    try:
        adb.set_home(HOME)
        if screensaver:
            adb.set_dream(DREAM)
        if adb.home() != HOME or (screensaver and adb.dream() != DREAM):
            raise SafetyError("Activation final verification failed")
        journal["phase"] = "active"
        durable_write(journal_path(path), journal)
    except (Exception, KeyboardInterrupt) as original:
        try:
            restore(adb, path)
        except (Exception, KeyboardInterrupt) as recovery:
            raise SafetyError("Activation interrupted; recovery incomplete. Keep the backup and journal, "
                              "reconnect ADB and run restore. " + str(recovery)) from original
        raise SafetyError("Activation failed; original settings restored") from original


def stock_path(path):
    return Path(str(path) + ".stock.json")


def same_tv(adb, saved):
    if saved.get("schema") != 1 or saved.get("identity") != adb.identity():
        raise SafetyError("Snapshot belongs to another TV, user, or firmware; no changes made")


def disable_stock_home(adb, path):
    """Disables the STOCK packages still enabled, recording them first, then selects Hestia as HOME."""
    saved = read_json(path)
    same_tv(adb, saved)
    if HOME not in adb.homes():
        raise SafetyError("Install and preview Hestia before disabling the stock home")
    disabled = adb.disabled()
    targets = [p for p in STOCK if p not in disabled]
    record = read_json(stock_path(path)) if stock_path(path).exists() else {"packages": []}
    # Stock packages already disabled (by hand, or for an earlier app id) are recorded too: they're ours to undo.
    packages = sorted(set(record.get("packages", [])) | set(STOCK))
    # Persist which packages this tool owns before the first change, so enable-stock-home can always undo it.
    durable_write(stock_path(path), {"schema": 1, "snapshot": digest(saved), "packages": packages})
    done = []
    try:
        for package in targets:
            adb.set_enabled(package, False)
            done.append(package)
        adb.set_home(HOME)
    except (Exception, KeyboardInterrupt) as original:
        for package in reversed(done):
            try:
                adb.set_enabled(package, True)
            except SafetyError:
                pass
        raise SafetyError("Disabling the stock home failed; packages re-enabled where possible. "
                          "Run enable-stock-home after reconnecting if needed.") from original


def enable_stock_home(adb, path, assume_stock=False):
    """Re-enables the recorded packages, then puts the snapshot's HOME (and screensaver, if still Hestia's) back."""
    saved = read_json(path)
    same_tv(adb, saved)
    if stock_path(path).exists():
        record = read_json(stock_path(path))
        if record.get("snapshot") != digest(saved):
            raise SafetyError("Stock-home record does not match this snapshot")
        packages = record.get("packages", [])
    elif assume_stock:
        packages = list(STOCK)   # explicitly requested: the TV was switched by hand before this tool knew
    else:
        raise SafetyError("No stock-home record; pass --assume-stock to re-enable " + ", ".join(STOCK))
    if any(p not in STOCK for p in packages):
        raise SafetyError("Stock-home record names packages this tool never disables")
    disabled = adb.disabled()
    for package in packages:
        if package in disabled:
            adb.set_enabled(package, True)
    adb.set_home(saved["home"])
    if saved["dream"] != DREAM and adb.dream() == DREAM:
        adb.set_dream(saved["dream"])
    if stock_path(path).exists():
        os.unlink(stock_path(path))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("status", "snapshot", "activate", "restore",
                                           "disable-stock-home", "enable-stock-home"))
    parser.add_argument("--serial", required=True, help="Exact identifier from adb devices; never auto-selects")
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--backup", type=Path, help="Private JSON path; retain it and its .journal.json sibling")
    parser.add_argument("--screensaver", action="store_true", help="Also switch screensaver (activate only)")
    parser.add_argument("--assume-stock", action="store_true",
                        help="enable-stock-home without a record: re-enable the stock packages anyway")
    args = parser.parse_args()
    if args.action != "status" and args.backup is None:
        parser.error("--backup is required")
    if args.assume_stock and args.action != "enable-stock-home":
        parser.error("--assume-stock applies only to enable-stock-home")
    if args.screensaver and args.action != "activate":
        parser.error("--screensaver applies only to activate")
    adb = Adb(args.adb, args.serial)
    try:
        identity = adb.identity()
        with device_lock(identity):
            if args.action == "status":
                print(json.dumps({"sdk": identity["sdk"], "home": adb.home(), "homes": sorted(adb.homes()),
                                  "screensaver": adb.dream(),
                                  "stock_disabled": sorted(set(STOCK) & adb.disabled())}, indent=2))
            elif args.action == "snapshot":
                snapshot(adb, args.backup)
                print("Snapshot saved; TV unchanged.")
            elif args.action == "disable-stock-home":
                disable_stock_home(adb, args.backup)
                print("Stock home disabled and Hestia selected. Undo with enable-stock-home.")
            elif args.action == "enable-stock-home":
                enable_stock_home(adb, args.backup, args.assume_stock)
                print("Stock home re-enabled; original HOME and screensaver verified.")
            elif args.action == "activate":
                activate(adb, args.backup, args.screensaver)
                print("Default HOME verified. Test the Home button, inputs, shortcuts and restart on the TV.")
            else:
                restore(adb, args.backup)
                print("Original settings verified. Press Home on the remote.")
    except (SafetyError, OSError, KeyboardInterrupt) as e:
        print("Stopped: " + (str(e) or "interrupted"), file=sys.stderr)
        if e.__cause__ is not None:
            print("Cause: " + (str(e.__cause__) or type(e.__cause__).__name__), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

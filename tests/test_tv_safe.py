import importlib.util
import json
import os
from pathlib import Path
import shlex
import stat
import subprocess
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("tv_safe", ROOT / "tools/tv_safe.py")
tv = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tv)
ORIGINAL = "com.google.android.apps.tv.launcherx/com.google.android.apps.tv.launcherx.home.HomeActivity"
OTHER = "org.example.home/org.example.home.Main"


class FakeTV:
    def __init__(self):
        self.ident = dict(serial="test-device", fingerprint="test-firmware", sdk=34, user=0)
        self.current = ORIGINAL
        self.setting = "org.example.dream/.Dream"
        self.enabled = {ORIGINAL, tv.HOME, OTHER}
        self.mutations = []
        self.fail_dream = False
        self.disconnect = False
        self.interrupt_home = False
        self.disabled_set = set()
        self.fail_package = None

    def disabled(self):
        return set(self.disabled_set)

    def set_enabled(self, package, enabled):
        self.mutations.append(("package", package, enabled))
        if package == self.fail_package:
            raise tv.SafetyError("simulated package failure")
        if enabled:
            self.disabled_set.discard(package)
        else:
            self.disabled_set.add(package)

    def identity(self):
        if self.disconnect:
            raise tv.SafetyError("disconnected")
        return dict(self.ident)

    def homes(self):
        # Google TV's HOME disappears while its package is disabled.
        homes = set(self.enabled)
        if tv.STOCK[0] in self.disabled_set:
            homes.discard(ORIGINAL)
        return homes

    def home(self):
        if self.disconnect:
            raise tv.SafetyError("disconnected")
        return self.current

    def dream(self):
        return self.setting

    def set_home(self, value):
        self.mutations.append(("home", value))
        self.current = value
        if self.interrupt_home:
            self.interrupt_home = False
            raise KeyboardInterrupt()

    def set_dream(self, value):
        self.mutations.append(("dream", value))
        if self.fail_dream and value == tv.DREAM:
            self.fail_dream = False
            raise tv.SafetyError("simulated write failure")
        self.setting = value


class DeploymentTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / "backup.json"
        self.adb = FakeTV()
        tv.snapshot(self.adb, self.path)

    def journal(self):
        return tv.read_json(tv.journal_path(self.path))

    def test_snapshot_read_only_private_never_overwritten(self):
        self.assertEqual(self.adb.mutations, [])
        self.assertEqual(stat.S_IMODE(self.path.stat().st_mode), 0o600)
        before = self.path.read_bytes()
        with self.assertRaises(FileExistsError):
            tv.snapshot(self.adb, self.path)
        self.assertEqual(self.path.read_bytes(), before)

    def test_home_only_preserves_screensaver_and_packages(self):
        enabled = set(self.adb.enabled)
        tv.activate(self.adb, self.path)
        self.assertEqual(self.adb.current, tv.HOME)
        self.assertEqual(self.adb.setting, "org.example.dream/.Dream")
        tv.restore(self.adb, self.path)
        self.assertEqual(self.adb.current, ORIGINAL)
        self.assertEqual(self.adb.enabled, enabled)
        self.assertEqual(self.adb.mutations, [("home", tv.HOME), ("home", ORIGINAL)])

    def test_screensaver_exact_round_trip_including_absent_empty_and_literal_null(self):
        for value in [None, "", "null", "a/.Dream:b/.Other", "unusual ' value $()"]:
            with self.subTest(value=value):
                path = self.path.parent / (str(len(self.adb.mutations)) + ".json")
                self.adb.setting = value
                tv.snapshot(self.adb, path)
                tv.activate(self.adb, path, screensaver=True)
                tv.restore(self.adb, path)
                self.assertEqual(self.adb.setting, value)

    def test_journal_precedes_first_mutation(self):
        actual = self.adb.set_home
        def checked(value):
            self.assertEqual(self.journal()["phase"], "applying")
            actual(value)
        with patch.object(self.adb, "set_home", side_effect=checked):
            tv.activate(self.adb, self.path)

    def test_failed_second_write_rolls_back_home(self):
        self.adb.fail_dream = True
        with self.assertRaisesRegex(tv.SafetyError, "original settings restored"):
            tv.activate(self.adb, self.path, screensaver=True)
        self.assertEqual(self.adb.current, ORIGINAL)
        self.assertEqual(self.journal()["phase"], "restored")

    def test_ctrl_c_after_successful_write_rolls_back(self):
        self.adb.interrupt_home = True
        with self.assertRaisesRegex(tv.SafetyError, "original settings restored"):
            tv.activate(self.adb, self.path)
        self.assertEqual(self.adb.current, ORIGINAL)

    def test_unretained_home_still_reapplies_original(self):
        # Role moved but a higher-priority system launcher still resolves: undo the role change too.
        def unretained(value):
            self.adb.mutations.append(("home", value))
            if value == tv.HOME:
                raise tv.SafetyError("Android did not retain the requested HOME")
        self.adb.set_home = unretained
        with self.assertRaisesRegex(tv.SafetyError, "original settings restored"):
            tv.activate(self.adb, self.path)
        self.assertEqual(self.adb.mutations, [("home", tv.HOME), ("home", ORIGINAL)])
        self.assertEqual(self.journal()["phase"], "restored")

    def test_stock_home_round_trip_touches_only_stock_packages(self):
        self.adb.setting = tv.DREAM   # screensaver switched to Hestia's by hand meanwhile
        tv.disable_stock_home(self.adb, self.path)
        self.assertEqual(self.adb.disabled_set, set(tv.STOCK))
        self.assertEqual(self.adb.current, tv.HOME)
        self.assertEqual(tv.read_json(tv.stock_path(self.path))["packages"], sorted(tv.STOCK))
        tv.enable_stock_home(self.adb, self.path)
        self.assertEqual(self.adb.disabled_set, set())
        self.assertEqual(self.adb.current, ORIGINAL)
        self.assertEqual(self.adb.setting, "org.example.dream/.Dream")
        self.assertFalse(tv.stock_path(self.path).exists())
        touched = {m[1] for m in self.adb.mutations if m[0] == "package"}
        self.assertEqual(touched, set(tv.STOCK))

    def test_stock_home_failure_rolls_back_packages(self):
        self.adb.fail_package = tv.STOCK[1]
        with self.assertRaisesRegex(tv.SafetyError, "re-enabled"):
            tv.disable_stock_home(self.adb, self.path)
        self.assertEqual(self.adb.disabled_set, set())
        self.assertEqual(self.adb.current, ORIGINAL)

    def test_enable_stock_home_needs_record_or_explicit_assumption(self):
        self.adb.disabled_set = set(tv.STOCK)   # switched by hand, no record
        self.adb.current = tv.HOME
        with self.assertRaisesRegex(tv.SafetyError, "assume-stock"):
            tv.enable_stock_home(self.adb, self.path)
        self.assertEqual(self.adb.mutations, [])
        tv.enable_stock_home(self.adb, self.path, assume_stock=True)
        self.assertEqual(self.adb.disabled_set, set())
        self.assertEqual(self.adb.current, ORIGINAL)

    def test_stock_home_refuses_other_tv_and_foreign_packages(self):
        self.adb.ident["fingerprint"] = "other-firmware"
        with self.assertRaises(tv.SafetyError):
            tv.disable_stock_home(self.adb, self.path)
        self.adb.ident["fingerprint"] = "test-firmware"
        tv.durable_write(tv.stock_path(self.path), {"schema": 1, "snapshot": tv.digest(tv.read_json(self.path)),
                                                    "packages": ["com.example.important"]})
        with self.assertRaisesRegex(tv.SafetyError, "never disables"):
            tv.enable_stock_home(self.adb, self.path)
        self.assertEqual([m for m in self.adb.mutations if m[0] == "package"], [])

    def test_disconnect_keeps_journal_for_reconnection(self):
        def disconnect(value):
            self.adb.current = value
            self.adb.disconnect = True
            raise tv.SafetyError("timeout after write")
        with patch.object(self.adb, "set_home", side_effect=disconnect):
            with self.assertRaisesRegex(tv.SafetyError, "recovery incomplete"):
                tv.activate(self.adb, self.path)
        self.assertEqual(self.journal()["phase"], "applying")
        self.adb.disconnect = False
        tv.restore(self.adb, self.path)
        self.assertEqual(self.adb.current, ORIGINAL)

    def test_process_death_after_write_is_recoverable(self):
        saved = tv.read_json(self.path)
        tv.durable_write(tv.journal_path(self.path), dict(schema=1, snapshot=tv.digest(saved),
                                                        screensaver=True, phase="applying"))
        self.adb.current = tv.HOME
        tv.restore(self.adb, self.path)
        self.assertEqual(self.adb.current, ORIGINAL)
        self.assertEqual(self.adb.setting, saved["dream"])

    def test_wrong_device_or_firmware_blocks_all_mutations(self):
        for key in ("serial", "fingerprint", "user"):
            with self.subTest(key=key):
                old = self.adb.ident[key]
                self.adb.ident[key] = "changed"
                with self.assertRaises(tv.SafetyError):
                    tv.activate(self.adb, self.path)
                self.adb.ident[key] = old
        self.assertEqual(self.adb.mutations, [])

    def test_disabled_original_or_missing_new_home_blocks(self):
        for missing in (ORIGINAL, tv.HOME):
            self.adb.enabled.remove(missing)
            with self.assertRaises(tv.SafetyError):
                tv.activate(self.adb, self.path)
            self.adb.enabled.add(missing)
        self.assertEqual(self.adb.mutations, [])

    def test_changes_since_snapshot_block_activation(self):
        self.adb.current = OTHER
        with self.assertRaises(tv.SafetyError):
            tv.activate(self.adb, self.path)
        self.assertEqual(self.adb.mutations, [])

    def test_repeated_activation_refused_and_restore_idempotent(self):
        tv.activate(self.adb, self.path)
        with self.assertRaises(tv.SafetyError):
            tv.activate(self.adb, self.path)
        tv.restore(self.adb, self.path)
        before = list(self.adb.mutations)
        tv.restore(self.adb, self.path)
        self.assertEqual(self.adb.mutations, before)
        tv.activate(self.adb, self.path)  # permitted after verified restoration

    def test_restore_preserves_external_home_change(self):
        tv.activate(self.adb, self.path, screensaver=True)
        self.adb.current = OTHER
        with self.assertRaisesRegex(tv.SafetyError, "HOME changed"):
            tv.restore(self.adb, self.path)
        self.assertEqual(self.adb.current, OTHER)
        self.assertEqual(self.adb.setting, "org.example.dream/.Dream")

    def test_dream_conflict_does_not_prevent_home_recovery(self):
        tv.activate(self.adb, self.path, screensaver=True)
        self.adb.setting = "another/.Dream"
        with self.assertRaisesRegex(tv.SafetyError, "screensaver changed"):
            tv.restore(self.adb, self.path)
        self.assertEqual(self.adb.current, ORIGINAL)
        self.assertEqual(self.adb.setting, "another/.Dream")
        self.assertEqual(self.journal()["phase"], "restoring")

    def test_mismatched_journal_refused(self):
        tv.activate(self.adb, self.path)
        data = tv.read_json(self.path)
        data["dream"] = "tampered"
        tv.durable_write(self.path, data)
        before = list(self.adb.mutations)
        with self.assertRaisesRegex(tv.SafetyError, "mismatch"):
            tv.restore(self.adb, self.path)
        self.assertEqual(self.adb.mutations, before)

    def test_disk_failure_before_journal_prevents_mutation(self):
        with patch.object(tv, "durable_write", side_effect=OSError("disk full")):
            with self.assertRaises(OSError):
                tv.activate(self.adb, self.path)
        self.assertEqual(self.adb.mutations, [])

    def test_new_snapshot_when_already_active_refused(self):
        self.adb.current = tv.HOME
        with self.assertRaises(tv.SafetyError):
            tv.snapshot(self.adb, self.path.parent / "new.json")


class AdbTests(unittest.TestCase):
    def test_settings_preserve_trailing_spaces_and_distinguish_absence(self):
        adb = tv.Adb("adb", "paired-device")
        result = subprocess.CompletedProcess([], 0, "other=1\nscreensaver_components= value  \n", "")
        with patch.object(subprocess, "run", return_value=result):
            self.assertEqual(adb.dream(), " value  ")
        with patch.object(adb, "shell", return_value="other=1"):
            self.assertIsNone(adb.dream())

    def test_invalid_settings_output_is_not_an_absent_value(self):
        adb = tv.Adb("adb", "paired-device")
        for output in ("", "Error: denied", "Unknown command list"):
            with patch.object(adb, "shell", return_value=output):
                with self.assertRaises(tv.SafetyError):
                    adb.dream()

    def test_remote_values_are_shell_quoted(self):
        adb = tv.Adb("adb", "paired-device")
        value = "old'; touch /tmp/no; echo '$(whoami)"
        with patch.object(subprocess, "run", return_value=subprocess.CompletedProcess([], 0, "ok", "")) as run:
            adb.shell("settings", "put", "secure", tv.SETTING, value)
        argv = run.call_args.args[0]
        self.assertEqual(argv[:4], ["adb", "-s", "paired-device", "shell"])
        self.assertEqual(shlex.split(argv[4]), ["settings", "put", "secure", tv.SETTING, value])

    def test_timeout_and_nonzero_exit_are_errors(self):
        adb = tv.Adb("adb", "paired-device")
        with patch.object(subprocess, "run", side_effect=subprocess.TimeoutExpired("adb", 25)):
            with self.assertRaises(tv.SafetyError):
                adb.home()
        with patch.object(subprocess, "run", return_value=subprocess.CompletedProcess([], 1, "", "denied")):
            with self.assertRaises(tv.SafetyError):
                adb.home()

    def test_resolver_and_command_error_not_treated_as_original_home(self):
        adb = tv.Adb("adb", "paired-device")
        for output in ("Unknown command: resolve-activity", "android/com.android.internal.app.ResolverActivity", ""):
            with patch.object(adb, "shell", side_effect=[output, ORIGINAL]):
                with self.assertRaises(tv.SafetyError):
                    adb.home()

    def test_readback_catches_success_exit_without_effect(self):
        adb = tv.Adb("adb", "paired-device")
        with patch.object(adb, "homes", return_value={ORIGINAL, tv.HOME}), \
                patch.object(adb, "home", return_value=ORIGINAL), \
                patch.object(adb, "shell", return_value="Success"), \
                patch.object(tv, "HOME_SETTLE_SECONDS", 0), patch.object(tv.time, "sleep"):
            with self.assertRaisesRegex(tv.SafetyError, "did not retain"):
                adb.set_home(tv.HOME)

    def test_readback_waits_for_asynchronous_role_change(self):
        adb = tv.Adb("adb", "paired-device")
        resolved = [ORIGINAL, tv.SafetyError("ambiguous"), tv.HOME]
        with patch.object(adb, "homes", return_value={ORIGINAL, tv.HOME}), \
                patch.object(adb, "home", side_effect=resolved), \
                patch.object(adb, "shell", return_value="Success"), patch.object(tv.time, "sleep") as sleep:
            adb.set_home(tv.HOME)
        self.assertEqual(sleep.call_count, 2)

    def test_set_home_command_error_is_refused(self):
        adb = tv.Adb("adb", "paired-device")
        with patch.object(adb, "homes", return_value={ORIGINAL, tv.HOME}), \
                patch.object(adb, "shell", return_value="Error: Failed to set default home."):
            with self.assertRaisesRegex(tv.SafetyError, "refused"):
                adb.set_home(tv.HOME)

    def test_component_validation_rejects_injection(self):
        for value in (None, "", "a/.Home; reboot", "a/.Home\nreboot", "$(id)/.Home"):
            with self.assertRaises(tv.SafetyError):
                tv.component(value)


if __name__ == "__main__":
    unittest.main()

"""Keeps the bricks of docs/BRIQUES.md and the layers of docs/ARCHITECTURE.md true as the code grows."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/io/hestia/tv"
ANDROID = "{http://schemas.android.com/apk/res/android}"

# What each package may import from the app (root: the manifest's components may use anything).
LAYERS = {
    "core": set(),
    "data": {"core"},
    "apps": {"core"},
    "power": {"core"},
    "remote": {"core", "apps"},
    "ui": {"core", "data"},
    "panel": {"core", "ui"},
    "media": {"core", "ui", "panel"},
    "home": {"core", "data", "apps", "remote", "media", "ui"},
    "settings": {"core", "data", "apps", "ui"},
    "news": {"core", "data", "ui"},
    "dream": {"core", "data", "ui"},
}
# Each rule of the app lives in one file: pattern -> the only files allowed to use it.
ONE_DOOR = {
    r"getSharedPreferences\(": {"core/Prefs.java"},
    r"\.startActivity\(": {"apps/Launcher.java"},
    r"Choreographer\.": {"ui/CanvasView.java"},
    r"Executors\.": {"core/Background.java", "data/Feed.java"},
    r"\b4001\b": {"core/Keys.java"},
    r"new HttpURLConnection|openConnection\(": {"data/Net.java"},
    r"Thread\(": set(),
}
PLAIN_JAVA = ["core/I18n.java", "core/TimeText.java", "data/City.java", "data/FeedText.java", "data/Freshness.java", "data/Net.java", "data/Version.java",
              "data/WeatherText.java", "power/SleepPolicy.java", "remote/KeyAction.java", "ui/Colors.java",
              "ui/Spring.java"]
MAX_LINES = 500


def sources():
    return sorted(SRC.rglob("*.java"))


class ArchitectureTests(unittest.TestCase):
    def test_layers(self):
        for path in sources():
            package = path.relative_to(SRC).parts[0] if len(path.relative_to(SRC).parts) > 1 else None
            for name in re.findall(r"^import io\.hestia\.tv\.(.+);", path.read_text(), re.M):
                if name == "R":
                    continue
                self.assertIn(".", name, f"{path}: a layer imports the root package ({name})")
                if package is not None:
                    target = name.split(".")[0]
                    if target != package:
                        self.assertIn(target, LAYERS[package], f"{path}: {package} may not use {target}")

    def test_every_package_has_a_layer(self):
        packages = {p.name for p in SRC.iterdir() if p.is_dir()}
        self.assertEqual(packages, set(LAYERS))

    def test_one_door_for_each_rule(self):
        for pattern, allowed in ONE_DOOR.items():
            for path in sources():
                if str(path.relative_to(SRC)) not in allowed:
                    self.assertIsNone(re.search(pattern, path.read_text()), f"{path}: {pattern} outside {allowed}")

    def test_plain_java_stays_plain(self):
        for name in PLAIN_JAVA:
            source = (SRC / name).read_text()
            self.assertNotRegex(source, r"import android\.", name)
            self.assertNotRegex(source, r"import org\.json", name)

    def test_files_stay_small(self):
        for path in sources():
            self.assertLessEqual(len(path.read_text().splitlines()), MAX_LINES, str(path))

    def test_components_are_in_the_root_package(self):
        # TV settings, the home role and tools/tv_safe.py name these classes: moving one breaks the installed setup.
        app = ET.parse(ROOT / "app/AndroidManifest.xml").getroot().find("application")
        names = {e.get(ANDROID + "name") for tag in ("activity", "service") for e in app.findall(tag)}
        names.add(app.get(ANDROID + "name"))
        for name in names:
            self.assertRegex(name, r"^\.[A-Za-z]+$")
            self.assertTrue((SRC / (name[1:] + ".java")).exists(), name)
        screens = (SRC / "core/Screens.java").read_text()
        for screen in re.findall(r'screen\(c, "(\w+)"\)', screens):
            self.assertIn("." + screen, names)

    def test_every_translated_text_has_its_french(self):
        table = (SRC / "core/I18n.java").read_text()
        french = set(re.findall(r'\{"((?:[^"\\]|\\.)*)",\s*"(?:[^"\\]|\\.)*"\}', table))
        call = re.compile(r'tr\(\s*"(?:[^"\\]|\\.)*",\s*"((?:[^"\\]|\\.)*)"\s*\)', re.S)
        for path in sources():
            for english in call.findall(path.read_text()):
                self.assertIn(english, french, f"{path}: no French for {english!r}")


if __name__ == "__main__":
    unittest.main()

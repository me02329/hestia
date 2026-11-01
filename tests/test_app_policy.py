from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

import jvm

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
SECURE_WRITER = ROOT / "app/src/io/hestia/tv/power/SleepDelay.java"


class AppPolicyTests(unittest.TestCase):
    def test_manifest_has_no_boot_jobs_or_unexpected_permissions(self):
        root = ET.parse(ROOT / "app/AndroidManifest.xml").getroot()
        self.assertEqual({p.get(ANDROID + "name") for p in root.findall("uses-permission")},
                         {"android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
                          # User-confirmed uninstall from the app menu (Android shows its own dialog).
                          "android.permission.REQUEST_DELETE_PACKAGES",
                          # Quick panel's delays, once granted over ADB: docs/adr/001 and 002.
                          "android.permission.WRITE_SECURE_SETTINGS"})
        app = root.find("application")
        self.assertEqual(app.get(ANDROID + "allowBackup"), "false")
        self.assertEqual(app.get(ANDROID + "usesCleartextTraffic"), "false")
        self.assertNotEqual(app.get(ANDROID + "debuggable"), "true")
        self.assertEqual(app.findall("receiver"), [])
        # Screensaver, (empty) media listener and the remote keys service, each bound by the system alone.
        self.assertEqual({s.get(ANDROID + "permission") for s in app.findall("service")},
                         {"android.permission.BIND_DREAM_SERVICE",
                          "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
                          "android.permission.BIND_ACCESSIBILITY_SERVICE"})
        self.assertEqual(len(app.findall("service")), 3)

    def test_accessibility_service_sees_keys_only(self):
        config = ET.parse(ROOT / "app/res/xml/remote_keys_service.xml").getroot()
        self.assertEqual(config.get(ANDROID + "canRetrieveWindowContent"), "false")
        self.assertEqual(config.get(ANDROID + "accessibilityFlags"), "flagRequestFilterKeyEvents")
        self.assertEqual(config.get(ANDROID + "accessibilityEventTypes"), "typeWindowStateChanged")

    def test_no_app_side_shell_or_adb(self):
        for path in (ROOT / "app/src").rglob("*.java"):
            for forbidden in ("new Socket(", "new ProcessBuilder(", "Runtime.getRuntime()", "pm disable-user",
                              "127.0.0.1", "class Adb"):
                self.assertNotIn(forbidden, path.read_text(), str(path))

    def test_only_the_three_delays_can_be_written(self):
        writers = re.compile(r"Settings\.(Secure|Global|System)\.put")
        for path in (ROOT / "app/src").rglob("*.java"):
            if path != SECURE_WRITER:
                self.assertIsNone(writers.search(path.read_text()), str(path))
        source = SECURE_WRITER.read_text()
        self.assertEqual(set(re.findall(r"Settings\.(\w+)\.put\w+\(c\.getContentResolver\(\), (\w+),", source)),
                         {("Secure", "SLEEP"), ("Secure", "ATTENTIVE"), ("System", "SCREEN_OFF")})
        self.assertIn('SLEEP = "sleep_timeout", ATTENTIVE = "attentive_timeout"', source)
        self.assertIn("SCREEN_OFF = Settings.System.SCREEN_OFF_TIMEOUT", source)
        self.assertEqual(len(writers.findall(source)), 3)

    def test_network_boundaries_with_real_jvm(self):
        self.assertIn("checks passed", jvm.run("NetTest", "data/Net.java"))

    def test_feed_text_with_real_jvm(self):
        self.assertIn("checks passed", jvm.run("FeedTextTest", "data/FeedText.java"))

    def test_data_logic_with_real_jvm(self):
        self.assertIn("checks passed", jvm.run("DataLogicTest", "data/Freshness.java", "data/WeatherText.java",
                                               "data/City.java", "data/FeedText.java", "data/Version.java",
                                               "core/I18n.java"))

    def test_core_logic_with_real_jvm(self):
        self.assertIn("checks passed", jvm.run("CoreLogicTest", "core/I18n.java", "core/TimeText.java"))

    def test_ui_logic_with_real_jvm(self):
        self.assertIn("checks passed", jvm.run("UiLogicTest", "ui/Colors.java", "ui/Spring.java"))

    def test_sleep_policy_with_real_jvm(self):
        self.assertIn("checks passed", jvm.run("PowerLogicTest", "power/SleepPolicy.java"))

    def test_key_actions_with_real_jvm(self):
        self.assertIn("checks passed", jvm.run("RemoteLogicTest", "remote/KeyAction.java"))


if __name__ == "__main__":
    unittest.main()

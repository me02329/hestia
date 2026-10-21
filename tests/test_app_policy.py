import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"


class AppPolicyTests(unittest.TestCase):
    def test_manifest_has_no_boot_jobs_or_privileged_permissions(self):
        root = ET.parse(ROOT / "app/AndroidManifest.xml").getroot()
        self.assertEqual({p.get(ANDROID + "name") for p in root.findall("uses-permission")},
                         {"android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE"})
        app = root.find("application")
        self.assertEqual(app.get(ANDROID + "allowBackup"), "false")
        self.assertEqual(app.get(ANDROID + "usesCleartextTraffic"), "false")
        self.assertNotEqual(app.get(ANDROID + "debuggable"), "true")
        self.assertEqual(app.findall("receiver"), [])
        self.assertEqual(len(app.findall("service")), 1)
        self.assertEqual(app.find("service").get(ANDROID + "permission"), "android.permission.BIND_DREAM_SERVICE")

    def test_no_app_side_shell_or_adb(self):
        for path in (ROOT / "app/src").rglob("*.java"):
            for forbidden in ("new Socket(", "new ProcessBuilder(", "Runtime.getRuntime()", "pm disable-user",
                              "127.0.0.1", "class Adb", "WRITE_SECURE_SETTINGS"):
                self.assertNotIn(forbidden, path.read_text(), str(path))

    def test_network_boundaries_with_real_jvm(self):
        local = Path.home() / ".local/share/free-my-tv/jdk17"
        java_home = Path(os.environ.get("JAVA_HOME", str(local)))
        javac = str(java_home / "bin/javac") if (java_home / "bin/javac").exists() else shutil.which("javac")
        java = str(java_home / "bin/java") if (java_home / "bin/java").exists() else shutil.which("java")
        self.assertTrue(javac and java, "JDK required for network boundary tests")
        with tempfile.TemporaryDirectory() as directory:
            subprocess.run([javac, "-d", directory, str(ROOT / "app/src/io/freemytv/Net.java"),
                            str(ROOT / "tests/java/NetTest.java")], check=True, capture_output=True, text=True)
            result = subprocess.run([java, "-cp", directory, "io.freemytv.NetTest"],
                                    check=True, capture_output=True, text=True)
            self.assertIn("checks passed", result.stdout)


if __name__ == "__main__":
    unittest.main()

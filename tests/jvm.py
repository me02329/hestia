"""Runs the plain-Java parts of the app (no Android types) and their checks on a real JVM."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/io/hestia/tv"


def run(test, *sources):
    """Compiles tests/java/<test>.java with the given sources (relative to SRC); returns the class's output."""
    java_home = Path(os.environ.get("JAVA_HOME", str(Path.home() / ".local/share/hestia/jdk17")))
    javac = str(java_home / "bin/javac") if (java_home / "bin/javac").exists() else shutil.which("javac")
    java = str(java_home / "bin/java") if (java_home / "bin/java").exists() else shutil.which("java")
    if not (javac and java):
        raise AssertionError("JDK required for the JVM tests")
    test_file = ROOT / "tests/java" / (test + ".java")
    package = next(l for l in test_file.read_text().splitlines() if l.startswith("package ")).split()[1].rstrip(";")
    with tempfile.TemporaryDirectory() as directory:
        subprocess.run([javac, "-d", directory, str(test_file), *(str(SRC / s) for s in sources)],
                       check=True, capture_output=True, text=True)
        result = subprocess.run([java, "-cp", directory, package + "." + test],
                                check=True, capture_output=True, text=True)
    return result.stdout

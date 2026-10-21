#!/usr/bin/env python3
"""Validate local configuration and generate Java literals without shell interpolation."""
import json
import math
from pathlib import Path
import re
import sys

DEFAULTS = dict(latitude="48.8566", longitude="2.3522", city="", language="auto", temperature="C")
# Keys of earlier versions (TMDB picks, crypto prices): accepted and ignored so old config files still build.
RETIRED = {"tmdb_key", "tmdb_region", "currency"}


def generate(source):
    values = dict(DEFAULTS)
    seen = set()
    for line in source.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        key, sep, value = line.partition("=")
        key, value = key.strip(), value.strip()
        if sep and key in RETIRED:
            continue
        if not sep or key not in DEFAULTS or key in seen:
            raise ValueError("Unknown, duplicate, or malformed configuration key")
        if any(ord(c) < 32 for c in value):
            raise ValueError("Control character in configuration key: " + key)
        seen.add(key)
        values[key] = value
    for key, limit in (("latitude", 90), ("longitude", 180)):
        number = float(values[key])
        if not math.isfinite(number) or not -limit <= number <= limit:
            raise ValueError("Invalid coordinate: " + key)
    for key, pattern in (("language", "auto|en|fr|pt"), ("temperature", "auto|C|F")):
        if not re.fullmatch(pattern, values[key]):
            raise ValueError("Invalid configuration value for: " + key)
    return ("package io.freemytv;\n\n// Generated; edit config.properties.\nfinal class Config {\n"
            "    private Config() {}\n" + "".join(
                "    static final String " + key.upper() + " = " + json.dumps(value, ensure_ascii=True) + ";\n"
                for key, value in values.items()) + "}\n")


if __name__ == "__main__":
    try:
        output = generate(Path(sys.argv[1]).read_text())
        Path(sys.argv[2]).write_text(output)
    except (OSError, ValueError) as e:
        sys.exit("Configuration error: " + str(e))

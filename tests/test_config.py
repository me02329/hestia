import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("generate_config", ROOT / "tools/generate_config.py")
config = importlib.util.module_from_spec(spec)
spec.loader.exec_module(config)


class ConfigTests(unittest.TestCase):
    def test_quotes_backslashes_and_shell_text_are_data(self):
        generated = config.generate('city=A "quote" \\ path $(id) `id`')
        self.assertIn('A \\"quote\\" \\\\ path $(id) `id`', generated)

    def test_invalid_coordinate_language_and_duplicate_rejected(self):
        for source in ("latitude=NaN", "latitude=91", "longitude=181", "language=de",
                       "city=A\ncity=B", "disable_assistant=true", "tmdb_key"):
            with self.subTest(source=source), self.assertRaises(ValueError):
                config.generate(source)

    def test_example_is_valid(self):
        generated = config.generate((ROOT / "config.example.properties").read_text())
        self.assertIn('LANGUAGE = "auto";', generated)
        self.assertNotIn("TMDB", generated)

    def test_retired_keys_ignored(self):
        generated = config.generate("tmdb_key=secret\ntmdb_region=FR\ncurrency=EUR\nlanguage=fr")
        self.assertNotIn("secret", generated)
        self.assertIn('LANGUAGE = "fr";', generated)


if __name__ == "__main__":
    unittest.main()

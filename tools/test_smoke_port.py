"""Regression checks for validation isolation and cleanup; does not start Minecraft."""
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
import smoke_port

SCRATCH = smoke_port.ROOT / "run/1.21.11/validation/script-tests"
assert SCRATCH.resolve().is_relative_to(smoke_port.ROOT.resolve())
SCRATCH.mkdir(parents=True, exist_ok=True)


class IsolationChecks(unittest.TestCase):
    SAFE = "server-ip=127.0.0.1\nserver-port=0\nlevel-name=smoke-world\n"

    def test_canonical_generated_properties(self):
        smoke_port.check_server_properties("# generated\n" + self.SAFE + "motd=A Minecraft Server\n")

    def test_effective_overrides_and_ambiguous_syntax_are_rejected(self):
        for suffix in ("level-name=other-save\n", "server-ip=0.0.0.0\n", "server-port=25565\n",
                       "level-name:other-save\n", "level-name other-save\n", "\\u006cevel-name=other-save\n",
                       "generator-settings=value\\\nserver-ip=127.0.0.1\n"):
            with self.subTest(suffix=suffix), self.assertRaises(AssertionError):
                smoke_port.check_server_properties(self.SAFE + suffix)

    def test_launch_failure_restores_existing_and_new_config(self):
        for existing in (True, False):
            with self.subTest(existing=existing), tempfile.TemporaryDirectory(dir=SCRATCH) as directory:
                root = Path(directory)
                self.assertEqual(root.resolve().parent, SCRATCH.resolve())
                folder = root / "run/1.21.11/server"
                original = b"[items]\r\nenableLongStick = true\r\n"
                fallback = folder / "config/mfqm-server.toml"
                fallback.parent.mkdir(parents=True)
                fallback.write_bytes(original)
                config = folder / "smoke-world/serverconfig/mfqm-server.toml"
                if existing:
                    config.parent.mkdir(parents=True)
                    config.write_bytes(original)
                args = SimpleNamespace(disable_long_stick=True, reload=True, compat_fixture=True, persistence_phase=None)
                with patch("smoke_port.subprocess.Popen", side_effect=OSError("launch probe")), self.assertRaises(OSError):
                    smoke_port.run(args, root)
                if existing:
                    self.assertEqual(config.read_bytes(), original)
                else:
                    self.assertFalse(config.exists())
                self.assertEqual(list((folder / "smoke-world/datapacks").glob("mfqm-compat-fixture-*")), [])

    def test_partial_fixture_failure_also_rolls_back(self):
        with tempfile.TemporaryDirectory(dir=SCRATCH) as directory:
            root = Path(directory)
            self.assertEqual(root.resolve().parent, SCRATCH.resolve())
            fallback = root / "run/1.21.11/server/config/mfqm-server.toml"
            fallback.parent.mkdir(parents=True)
            fallback.write_text("enableLongStick = true\n", encoding="utf-8")

            def interrupted(fixture):
                fixture.mkdir(parents=True)
                (fixture / "partial.json").write_text("{}", encoding="utf-8")
                raise OSError("fixture probe")

            args = SimpleNamespace(disable_long_stick=True, reload=True, compat_fixture=True, persistence_phase=None)
            with patch("smoke_port.write_fixture", side_effect=interrupted), self.assertRaises(OSError):
                smoke_port.run(args, root)
            world = root / "run/1.21.11/server/smoke-world"
            self.assertFalse((world / "serverconfig/mfqm-server.toml").exists())
            self.assertEqual(list((world / "datapacks").glob("mfqm-compat-fixture-*")), [])


if __name__ == "__main__":
    unittest.main()

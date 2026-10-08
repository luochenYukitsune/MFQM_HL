"""The installed smoke runner must reject development class injection."""
from contextlib import contextmanager
import shutil
import unittest
from pathlib import Path
import uuid
import tomllib

from smoke_installed_client import instance_lock, java_argument, validate_runtime, prepare_fml_config, screenshot_saved


class InstalledRuntimeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary_root = Path(__file__).resolve().parents[1] / "build/installed-runtime-tests"
        cls.temporary_root.mkdir(parents=True, exist_ok=True)

    @contextmanager
    def temporary_directory(self):
        # Windows Python 3.12 mkdtemp applies a restrictive ACL that excludes the
        # sandbox account. Ordinary workspace directories inherit its write ACL.
        temporary = self.temporary_root / uuid.uuid4().hex
        temporary.mkdir()
        try:
            yield temporary
        finally:
            assert temporary.resolve().is_relative_to(self.temporary_root.resolve())
            shutil.rmtree(temporary)

    def runtime(self, base):
        dependency = base / "dependency.jar"
        dependency.touch()
        vm = base / "vm.txt"
        vm.write_text("-Dmfqm.clientChecks=true\n", encoding="utf-8")
        program = base / "program.txt"
        program.write_text("net.neoforged.fml.startup.Client\n", encoding="utf-8")
        return dict(classpath=[str(dependency)], vmArgsFile=str(vm), programArgsFile=str(program))

    def test_rejects_output_folder_on_classpath(self):
        with self.temporary_directory() as temporary:
            base = Path(temporary)
            runtime = self.runtime(base)
            output = base / "build/classes/java/main"
            output.mkdir(parents=True)
            runtime["classpath"].append(str(output))
            with self.assertRaisesRegex(ValueError, "development output"):
                validate_runtime(runtime, base)

    def test_rejects_vm_mod_folder_injection(self):
        with self.temporary_directory() as temporary:
            base = Path(temporary)
            runtime = self.runtime(base)
            Path(runtime["vmArgsFile"]).write_text("-Dfml.modFolders=mfqm%%build/classes\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "development mod injection"):
                validate_runtime(runtime, base)

    def test_rejects_devlaunch_wrapper(self):
        with self.temporary_directory() as temporary:
            base = Path(temporary)
            runtime = self.runtime(base)
            Path(runtime["programArgsFile"]).write_text("net.neoforged.devlaunch.Main\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "NeoForge Client"):
                validate_runtime(runtime, base)

    def test_quotes_windows_paths_without_escape_sequences(self):
        self.assertEqual(java_argument('C:\\Game Path\\mods\\mfqm.jar'), '"C:\\\\Game Path\\\\mods\\\\mfqm.jar"')

    def test_rejects_concurrent_instance_and_releases_lock(self):
        with self.temporary_directory() as temporary:
            with instance_lock(temporary):
                with self.assertRaises((RuntimeError,OSError)):
                    with instance_lock(temporary):
                        self.fail("Concurrent game instance lock was accepted")
            with instance_lock(temporary):
                pass

    def test_normalizes_bom_config_and_preserves_other_settings(self):
        with self.temporary_directory() as temporary:
            path = temporary / "fml.toml"
            path.write_text("# Disables window\nearlyWindowControl = true\nother = 42\n", encoding="utf-8-sig")
            prepare_fml_config(path)
            self.assertFalse(path.read_bytes().startswith(b"\xef\xbb\xbf"))
            self.assertEqual(tomllib.loads(path.read_text(encoding="utf-8")), dict(earlyWindowControl=False, other=42))

    def test_rejects_malformed_config_without_overwriting_it(self):
        with self.temporary_directory() as temporary:
            path = temporary / "fml.toml"
            original = b"invalid key = 1\n"
            path.write_bytes(original)
            with self.assertRaises(tomllib.TOMLDecodeError):
                prepare_fml_config(path)
            self.assertEqual(path.read_bytes(), original)

    def test_screenshot_completion_supports_game_language(self):
        name = "mfqm-adhesive-hand.png"
        self.assertTrue(screenshot_saved("MFQM_ADHESIVE_SCENE_SAVED 已将截图保存为" + name, name))
        self.assertTrue(screenshot_saved("MFQM_ADHESIVE_SCENE_SAVED Saved screenshot as " + name, name))
        self.assertFalse(screenshot_saved("MFQM_ADHESIVE_SCENE_SAVED Failed screenshot", name))


if __name__ == "__main__":
    unittest.main()

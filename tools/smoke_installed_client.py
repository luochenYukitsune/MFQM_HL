"""Launch the built mod JAR from an isolated game's mods directory.

Prepare with Gradle prepareInstalledClient -PmfqmClientChecks
-PmfqmTextureChecks after building the JAR and running smoke_port.py.
This runs NeoForge/Minecraft using cached runtime dependencies, without Gradle
development mod folders. It never writes to a launcher or a player's own save.
"""
import argparse
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import queue
import re
import shutil
import subprocess
import sys
import threading
import time
import tomllib

ROOT = Path(__file__).resolve().parents[1]
SCREENSHOTS = (
    "mfqm-texture-scene.png", "mfqm-texture-flow.png",
    "mfqm-adhesive-glue.png", "mfqm-adhesive-deep.png",
    "mfqm-adhesive-board.png", "mfqm-adhesive-hand.png",
)


def java_argument(value):
    """Quote one token using Java @argument-file escaping, not shell quoting."""
    if "\n" in value or "\r" in value:
        raise ValueError("Newline in Java argument")
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"'


def validate_runtime(runtime, root):
    output_paths = [root / "build/classes", root / "build/resources"]
    classpath = [Path(value).resolve() for value in runtime["classpath"]]
    if not classpath:
        raise ValueError("Empty installed runtime classpath")
    for path in classpath:
        if any(path.is_relative_to(output.resolve()) for output in output_paths):
            raise ValueError("Installed client classpath contains development output: " + str(path))
        if path.name.startswith("DevLaunch-"):
            raise ValueError("Installed client cannot use the DevLaunch wrapper")
        if not path.is_file():
            raise ValueError("Runtime dependency must be an existing JAR: " + str(path))
    vm = Path(runtime["vmArgsFile"]).read_text(encoding="utf-8")
    program = Path(runtime["programArgsFile"]).read_text(encoding="utf-8")
    forbidden = ("fml.modFolders", "FML_MOD_CLASSES", "MOD_CLASSES", "fml.modRoots")
    if any(value in vm + program for value in forbidden):
        raise ValueError("Installed runtime contains development mod injection")
    tokens = [line.strip() for line in program.splitlines() if line.strip() and not line.lstrip().startswith("#")]
    if not tokens or tokens[0] != "net.neoforged.fml.startup.Client":
        raise ValueError("Installed runtime must directly launch NeoForge Client")
    return classpath, vm, program


def workspace_path(path):
    resolved = path.resolve()
    if not resolved.is_relative_to(ROOT.resolve()):
        raise ValueError("Test path escapes the workspace: " + str(path))
    return resolved


def sha256(path):
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def prepare_fml_config(path):
    """Normalize this isolated instance and reject errors before launching."""
    path.parent.mkdir(parents=True, exist_ok=True)
    text = path.read_text(encoding="utf-8-sig") if path.exists() else "earlyWindowControl = false\n"
    text = re.sub(r"(?m)^earlyWindowControl\s*=\s*true\s*$", "earlyWindowControl = false", text)
    tomllib.loads(text)
    path.write_text(text, encoding="utf-8")


def screenshot_saved(text, name):
    # Minecraft's screenshot completion message uses the selected game language.
    return any(name in line and ("MFQM_TEXTURE_SCENE_SAVED" in line or "MFQM_ADHESIVE_SCENE_SAVED" in line)
               for line in text.splitlines())


@contextmanager
def instance_lock(game):
    """Prevent concurrent scripts from sharing a save, logs or screenshots."""
    game.mkdir(parents=True, exist_ok=True)
    with (game / ".validation.lock").open("a+b") as handle:
        handle.seek(0, 2)
        if handle.tell() == 0:
            handle.write(b"0"); handle.flush()
        handle.seek(0)
        if os.name == "nt":
            import msvcrt
            try:
                msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
            except OSError as failure:
                raise RuntimeError("Another validation script is using this isolated game") from failure
        else:
            import fcntl
            fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        try:
            yield
        finally:
            handle.seek(0)
            if os.name == "nt":
                msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.flock(handle, fcntl.LOCK_UN)


def run(timeout):
    with instance_lock(workspace_path(ROOT / "run/1.21.11/installed-client")):
        return run_locked(timeout)


def run_locked(timeout):
    runtime = json.loads((ROOT / "build/installed-client-runtime.json").read_text(encoding="utf-8"))
    classpath, vm, program = validate_runtime(runtime, ROOT)
    assert "-Dmfqm.clientChecks=true" in vm and "-Dmfqm.textureChecks=true" in vm, "Prepare runtime with both check properties"
    jar = workspace_path(Path(runtime["jar"]))
    assert jar.is_file() and jar.suffix == ".jar", "Build the mod JAR first"
    source = ROOT / "run/1.21.11/server/smoke-world"
    assert (source / "level.dat").is_file(), "Run tools/smoke_port.py first"
    game = workspace_path(ROOT / "run/1.21.11/installed-client")
    fml_config=game / "config/fml.toml"
    prepare_fml_config(fml_config)
    mods = workspace_path(game / "mods")
    mods.mkdir(parents=True, exist_ok=True)
    archives = workspace_path(game / "previous-runs")
    stamp = time.strftime("%Y%m%d-%H%M%S") + "-" + str(time.time_ns())
    for old in mods.iterdir():
        old = workspace_path(old)
        if not old.is_file() or not old.name.startswith("MFQM-neoforge-") or old.suffix != ".jar":
            raise ValueError("Unexpected file in isolated mods directory: " + str(old))
        archive = workspace_path(archives / stamp / "mods" / old.name)
        archive.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(old), str(archive))
    installed = workspace_path(mods / jar.name)
    shutil.copy2(jar, installed)
    digest = sha256(jar)
    assert sha256(installed) == digest, "Installed JAR differs from the built artifact"
    target = workspace_path(game / "saves/mfqm-port-checks")
    if target.exists():
        archive = workspace_path(archives / stamp / "mfqm-port-checks")
        archive.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(target), str(archive))
    shutil.copytree(source, target)
    screenshots = workspace_path(game / "screenshots")
    for name in SCREENSHOTS:
        old = workspace_path(screenshots / name)
        if old.exists():
            archive = workspace_path(archives / stamp / "screenshots" / name)
            archive.parent.mkdir(parents=True, exist_ok=True)
            shutil.move(str(old), str(archive))
    options = game / "options.txt"
    saved_options = options.read_text(encoding="utf-8") if options.exists() else None
    lines = [line for line in (saved_options or "").splitlines() if not line.startswith(("onboardAccessibility:", "pauseOnLostFocus:", "fullscreen:", "lang:"))]
    options.write_text("\n".join(lines + ["onboardAccessibility:false", "pauseOnLostFocus:false", "fullscreen:false", "lang:zh_cn"]) + "\n", encoding="utf-8")
    launch = game / "installed-client-java.args"
    launch.write_text(vm + "\n-Dmfqm.installedChecks=true\n-Dfile.encoding=UTF-8\n-Xmx3G\n-cp\n"
                      + java_argument(os.pathsep.join(str(path) for path in classpath)) + "\n" + program + "\n--width\n1280\n--height\n720\n", encoding="utf-8")
    environment = os.environ.copy()
    # A caller's development injection must never make this test pass on loose classes.
    for key in list(environment):
        if key.upper() in ("FML_MOD_CLASSES", "MOD_CLASSES", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH"):
            del environment[key]
    process = None
    logfile = ROOT / "run/1.21.11/validation/installed-client-smoke.log"
    logfile.parent.mkdir(parents=True, exist_ok=True)
    output = queue.Queue()
    captured = []
    try:
        process = subprocess.Popen([runtime["javaExecutable"], "@" + str(launch)], cwd=game, env=environment,
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace",
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        def read():
            for line in process.stdout:
                output.put(line)
            output.put(None)
        threading.Thread(target=read, daemon=True).start()
        deadline = time.monotonic() + timeout
        with logfile.open("w", encoding="utf-8") as log:
            log.write("INSTALLED_ARTIFACT=" + str(installed) + "\nSHA256=" + digest + "\n")
            while time.monotonic() < deadline:
                try:
                    line = output.get(timeout=1)
                except queue.Empty:
                    continue
                if line is None:
                    break
                captured.append(line)
                log.write(line)
                log.flush()
                if any(mark in line for mark in ("MFQM", "ERROR", "Failed", "Missing", "Unable", "Exception", "Saved screenshot")):
                    print(line.rstrip(), flush=True)
                if "Failed to start FML" in line or "MFQM_CLIENT_CHECKS_FAILED" in line or re.search(r"(?:type=ERROR|severity=HIGH|/ERROR\]|\bmain ERROR\b)", line):
                    # Let the same logged exception's stack arrive before stopping
                    # our process; otherwise the failure reason can be lost.
                    diagnostic_deadline = time.monotonic() + .5
                    while time.monotonic() < diagnostic_deadline:
                        try:
                            extra = output.get(timeout=.05)
                        except queue.Empty:
                            continue
                        if extra is None:
                            break
                        captured.append(extra); log.write(extra); log.flush()
                        print(extra.rstrip(), flush=True)
                    raise RuntimeError("Actual game error; this script will terminate its own client: " + line.strip())
            else:
                raise TimeoutError("Installed client checks exceeded " + str(timeout) + " seconds")
        code = process.wait(timeout=20)
        text = "".join(captured)
        assert code == 0, "Installed game exited unsuccessfully; inspect " + str(logfile)
        for marker in ("MFQM_INSTALLED_JAR_LOADED", "MFQM_CLIENT_CHECKS_COMPLETE", "MFQM_TEXTURE_SCENE_SAVED", "MFQM_ADHESIVE_SCENE_COMPLETE"):
            assert marker in text, "Missing " + marker + "; inspect " + str(logfile)
        assert not any(marker in text for marker in ("MFQM_CLIENT_CHECKS_FAILED", "MFQM_ADHESIVE_SCENE_FAILED")), "In-game checks failed"
        assert not any(re.search(r"(?:/ERROR\]|\[ERROR\]|type=ERROR|severity=HIGH)", line) for line in captured), "Game logged an ERROR; inspect " + str(logfile)
        for name in SCREENSHOTS:
            assert (screenshots / name).is_file() and (screenshots / name).stat().st_size > 1000, "Missing actual screenshot: " + name
            assert screenshot_saved(text, name), "No screenshot completion log: " + name
        assert sha256(installed) == digest, "Installed artifact changed during testing"
        print("PASS: actual mods JAR, NeoForge client, gameplay assertions, six rendered screenshots; SHA256=" + digest, flush=True)
        return 0
    finally:
        if process is not None and process.poll() is None:
            # Only terminate this script's Java process. No launcher or other game is touched.
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=10)
        if saved_options is not None:
            options.write_text(saved_options, encoding="utf-8")
        elif options.exists():
            options.unlink()


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--timeout", type=int, default=360)
    arguments = parser.parse_args()
    if arguments.timeout < 30:
        parser.error("--timeout must be at least 30 seconds")
    raise SystemExit(run(arguments.timeout))

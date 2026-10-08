"""Run actual gameplay checks in the dedicated isolated smoke-world; never a user save."""
import os
import argparse
import json
import re
import shutil
import uuid
from pathlib import Path
import queue
import subprocess
import threading
import time
import sys

ROOT = Path(__file__).resolve().parents[1]
JAVA = Path(os.environ.get("JAVA_HOME", "D:/download/jdk21/jdk-21.0.9+10"))
SAFE_SETTINGS = {"server-ip": "127.0.0.1", "server-port": "0", "level-name": "smoke-world"}


def check_server_properties(text):
    """Accept canonical generated properties only, without escaped keys or continuations."""
    settings = {}
    for line in text.splitlines():
        if not line.strip() or line.lstrip().startswith(("#", "!")):
            continue
        match = re.fullmatch(r"([A-Za-z0-9_.-]+)=(.*)", line)
        assert match is not None and not line.endswith("\\"), "Ambiguous server.properties syntax; refuse gameplay fixtures"
        key, value = match.groups()
        if key in SAFE_SETTINGS:
            assert key not in settings, "Duplicate protected server.properties key: " + key
            settings[key] = value
    assert settings == SAFE_SETTINGS, "Refuse gameplay fixtures outside the loopback-only isolated smoke-world"


def write_fixture(fixture):
    fixture.mkdir(parents=True)
    (fixture / "pack.mcmeta").write_text(json.dumps({"pack": {"description": "MFQM isolated compatibility validation", "min_format": [94, 1], "max_format": [94, 1]}}), encoding="utf-8")
    mappings = {"bop/hive": "bee_nest", "bop/empty_honeycomb": "honeycomb", "bop/filled_honeycomb": "honey_bottle",
                "bop/mud": "clay", "bop/mud_ball": "clay_ball", "aoa/face_mask": "leather_helmet",
                "aoa/doomstone": "obsidian", "aoa/toxic_lump": "slime_ball", "ic2/fertilizer": "bone_meal"}
    for tag, item in mappings.items():
        out = fixture / ("data/mfqm/tags/item/compat/" + tag + ".json")
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps({"replace": True, "values": ["minecraft:" + item]}), encoding="utf-8")


def run(args, root=ROOT):
    natural_glue = getattr(args, "natural_glue", False)
    folder = root / "run/1.21.11/server"
    config_path = folder / "smoke-world/serverconfig/mfqm-server.toml"
    fixture_parent = folder / "smoke-world/datapacks"
    saved_config = None
    config_existed = False
    config_changed = False
    fixture = None
    process = None
    try:
        folder.mkdir(parents=True, exist_ok=True)
        props = folder / "server.properties"
        if props.exists():
            check_server_properties(props.read_text(encoding="utf-8"))
        else:
            props.write_text("server-ip=127.0.0.1\nserver-port=0\nlevel-name=smoke-world\nview-distance=2\nsimulation-distance=2\n", encoding="utf-8")
        (folder / "eula.txt").write_text("eula=true\n", encoding="utf-8")
        if args.disable_long_stick:
            config_existed = config_path.exists()
            source_config = config_path if config_existed else folder / "config/mfqm-server.toml"
            assert source_config.exists(), "Run the default server checks first to create the isolated config"
            original_config = source_config.read_text(encoding="utf-8")
            if config_existed:
                saved_config = config_path.read_bytes()
            disabled, count = re.subn(r"(?m)^(\s*enableLongStick\s*=\s*)(?:true|false)", r"\g<1>false", original_config)
            assert count == 1, "Missing or ambiguous acquisition setting"
            config_path.parent.mkdir(parents=True, exist_ok=True)
            config_changed = True
            config_path.write_text(disabled, encoding="utf-8")
        if args.compat_fixture:
            fixture = fixture_parent / ("mfqm-compat-fixture-" + uuid.uuid4().hex[:8])
            assert fixture.resolve().parent == fixture_parent.resolve()
            write_fixture(fixture)
        log_name = "gameplay-smoke-compat" if args.compat_fixture else "gameplay-smoke"
        if args.disable_long_stick:
            log_name += "-disabled"
        if args.persistence_phase:
            log_name += "-persistence-" + args.persistence_phase
        if natural_glue:
            log_name += "-natural-glue"
        logfile = root / ("run/1.21.11/validation/" + log_name + ".log")
        logfile.parent.mkdir(parents=True, exist_ok=True)
        environment = os.environ.copy()
        environment["JAVA_HOME"] = str(JAVA)
        command = [str(root / "gradlew.bat"), "runServer", "-PmfqmPortChecks", "--console=plain", "--max-workers=2"]
        if args.persistence_phase:
            command.append("-PmfqmPersistencePhase=" + args.persistence_phase)
        if natural_glue:
            command.append("-PmfqmNaturalGlueChecks")
        process = subprocess.Popen(command, cwd=root, env=environment, shell=True, stdin=subprocess.PIPE,
                                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace",
                                   creationflags=subprocess.CREATE_NO_WINDOW)
        received = queue.Queue()

        def output():
            for line in process.stdout:
                received.put(line)
            received.put(None)
        threading.Thread(target=output, daemon=True).start()
        complete = reload_complete = reload_requested = False
        fixture_disabled = fixture_removed_verified = False
        failed = stopped = False
        persistence_complete = not args.persistence_phase
        natural_complete = not natural_glue
        all_lines = []
        timeout = 900 if natural_glue else 300
        deadline = time.monotonic() + timeout
        with logfile.open("w", encoding="utf-8") as log:
            while time.monotonic() < deadline:
                try:
                    line = received.get(timeout=1)
                except queue.Empty:
                    continue
                if line is None:
                    break
                log.write(line); log.flush(); all_lines.append(line)
                if any(marker in line for marker in ("MFQM", "ERROR", "Exception", "Done (", "BUILD", "FAILED")):
                    print(line.rstrip(), flush=True)
                complete |= "MFQM_PORT_CHECKS_COMPLETE" in line
                persistence_complete |= "MFQM_PERSISTENCE_CHECKS_COMPLETE phase=" + str(args.persistence_phase) in line
                failed |= "MFQM_PERSISTENCE_CHECKS_FAILED" in line
                natural_complete |= "MFQM_NATURAL_GLUE_CHECKS_COMPLETE" in line
                failed |= "MFQM_NATURAL_GLUE_CHECKS_FAILED" in line
                if "MFQM_RELOAD_CHECKS_COMPLETE" in line:
                    if args.compat_fixture and fixture_disabled:
                        fixture_removed_verified = "compat=0" in line
                        failed |= not fixture_removed_verified
                    else:
                        reload_complete = True
                        if args.compat_fixture:
                            failed |= "compat=9" not in line
                            if not failed:
                                process.stdin.write('datapack disable "file/' + fixture.name + '"\n')
                                process.stdin.flush(); fixture_disabled = True
                failed |= "MFQM_PORT_CHECKS_FAILED" in line or "MFQM_RELOAD_CHECKS_FAILED" in line
                if complete and (args.reload or args.compat_fixture) and not reload_requested and not failed:
                    process.stdin.write("reload\n"); process.stdin.flush(); reload_requested = True
                reload_done = not (args.reload or args.compat_fixture) or reload_complete
                fixture_done = not args.compat_fixture or fixture_removed_verified
                if (failed or complete and reload_done and fixture_done and persistence_complete and natural_complete) and not stopped:
                    process.stdin.write("stop\n"); process.stdin.flush(); stopped = True
            else:
                raise TimeoutError(f"Gameplay validation exceeded {timeout} seconds")
        code = process.wait(timeout=20)
        assert complete and not failed, "Gameplay assertions did not all pass; inspect " + str(logfile)
        assert persistence_complete, "Persistent world restart check did not complete"
        assert natural_complete, "Natural glue sample did not complete; inspect " + str(logfile)
        assert not (args.reload or args.compat_fixture) or reload_complete, "Datapack reload assertions did not pass"
        assert not args.compat_fixture or fixture_removed_verified, "Disabling compatibility mapping left recipes active"
        assert code == 0, "Dedicated server did not exit normally"
        resource_errors = [line for line in all_lines if "/ERROR]" in line]
        assert not resource_errors, "Runtime errors: " + "".join(resource_errors)
        print("PASS: dedicated-server gameplay assertions and resource loading", flush=True)
    finally:
        try:
            if process is not None and process.poll() is None:
                subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"], capture_output=True)
                process.wait(timeout=20)
        finally:
            try:
                if config_changed:
                    if config_existed:
                        config_path.write_bytes(saved_config)
                    else:
                        config_path.unlink(missing_ok=True)
            finally:
                if fixture is not None and fixture.exists():
                    assert fixture.resolve().parent == fixture_parent.resolve()
                    shutil.rmtree(fixture)


def main():
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--disable-long-stick", action="store_true", help="Cold-start with the isolated world's acquisition setting disabled")
    parser.add_argument("--reload", action="store_true", help="Also verify recipe availability after the real reload command")
    parser.add_argument("--compat-fixture", action="store_true", help="Map all nine optional recipes in an isolated temporary pack, then disable it")
    parser.add_argument("--persistence-phase", choices=["prepare", "recover"], help="Save footwear, then verify it in a separate server process")
    parser.add_argument("--natural-glue", action="store_true", help="Observe glue in 256 untouched forest-area chunks; do not alter terrain or generation probability")
    run(parser.parse_args())


if __name__ == "__main__":
    main()

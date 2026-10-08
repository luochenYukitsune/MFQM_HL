"""Measure 60 ticks of genuine LocalPlayer W input from an isolated installed JAR.

Build and prepareInstalledClient -PmfqmClientChecks first, and run smoke_port.py
to supply the copied smoke-world. Screenshots default to disabled; --board-visual
enables one targeted board-model image. The launcher's actual game directory and
desktop source export are never modified.
"""
import argparse
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

from smoke_installed_client import (ROOT, instance_lock, java_argument,
    prepare_fml_config, sha256, validate_runtime, workspace_path)


def upstream_skin_pack_metadata_error(line, skin_layers, first_person=False, animations=False):
    """Record a known upstream metadata defect, never suppress other game/render errors."""
    names=["trender","transition"] if skin_layers or first_person or animations else []
    if skin_layers:names.append("skinlayers3d")
    if first_person:names.append("firstperson")
    if animations:names.append("notenoughanimations")
    return bool(names) and bool(re.search(
        r"\[minecraft/AbstractPackResources\]: Couldn't load mod/(?:"+"|".join(names)+r") pack metadata: "
        r'Pack declares support for format 75, but game versions supporting formats 17 to 81 require a supported_formats field\.', line))


def viscosity_vm(vm, creative_ground_traps, fail_fast=True, flight_controls=False, bounded_only=False, board_visual=False, actions_only=False, coating_only=False, skin_layers=False, coating_visual=False, first_person=False, dynamic_only=False, dynamic_visual=False, animations=False):
    """Disable broad texture previews; board imagery requires an explicit opt-in."""
    own_flags = re.compile(r"^-Dmfqm\.(?:textureChecks|viscosityChecks|viscosityCreativeGroundTraps|viscosityFailFast|viscosityFlightChecks|clientChecks|installedChecks|boundedOnly|boardVisual|actionsOnly|coatingChecks|coatingSkinLayersExpected|coatingVisual|firstPersonExpected|dynamicAdhesionChecks|dynamicVisual|coatingAnimationsExpected)(?:=|$)")
    vm = "\n".join(line for line in vm.splitlines()
                   if not own_flags.match(line.strip()))
    return vm + ("\n-Dmfqm.clientChecks=true\n-Dmfqm.textureChecks=false\n-Dmfqm.installedChecks=true"
                 "\n-Dmfqm.viscosityChecks=true\n-Dmfqm.viscosityCreativeGroundTraps="
                 + str(creative_ground_traps).lower() + "\n-Dmfqm.viscosityFailFast="
                 + str(fail_fast).lower() + "\n-Dmfqm.viscosityFlightChecks="
                 + str(flight_controls).lower() + "\n-Dmfqm.boundedOnly=" + str(bounded_only).lower()
                 + "\n-Dmfqm.boardVisual=" + str(board_visual).lower() + "\n-Dmfqm.actionsOnly=" + str(actions_only).lower()
                 + "\n-Dmfqm.coatingChecks=" + str(coating_only).lower() + "\n-Dmfqm.coatingSkinLayersExpected=" + str(skin_layers).lower()
                 + "\n-Dmfqm.coatingVisual=" + str(coating_visual).lower() + "\n-Dmfqm.firstPersonExpected=" + str(first_person).lower()
                 + "\n-Dmfqm.dynamicAdhesionChecks=" + str(dynamic_only).lower() + "\n-Dmfqm.dynamicVisual=" + str(dynamic_visual).lower()
                 + "\n-Dmfqm.coatingAnimationsExpected="+str(animations).lower()+"\n")


def run(timeout, creative_ground_traps, fail_fast, flight_controls=False, bounded_only=False, board_visual=False, actions_only=False, coating_only=False, skin_layers=False, coating_visual=False, first_person=False, dynamic_only=False, dynamic_visual=False, animations=False):
    game = workspace_path(ROOT / ("run/1.21.11/coating-client" if coating_only else "run/1.21.11/viscosity-client"))
    with instance_lock(game):
        return run_locked(game, timeout, creative_ground_traps, fail_fast, flight_controls, bounded_only, board_visual, actions_only, coating_only, skin_layers, coating_visual, first_person, dynamic_only, dynamic_visual, animations)


def run_locked(game, timeout, creative_ground_traps, fail_fast, flight_controls, bounded_only, board_visual, actions_only, coating_only=False, skin_layers=False, coating_visual=False, first_person=False, dynamic_only=False, dynamic_visual=False, animations=False):
    runtime = json.loads((ROOT / "build/installed-client-runtime.json").read_text(encoding="utf-8"))
    classpath, vm, program = validate_runtime(runtime, ROOT)
    jar = workspace_path(Path(runtime["jar"]))
    if not jar.is_file():
        raise ValueError("Build the installed JAR first")
    source = ROOT / "run/1.21.11/server/smoke-world"
    if not (source / "level.dat").is_file():
        raise ValueError("Run smoke_port.py first")
    prepare_fml_config(game / "config/fml.toml")
    archives = workspace_path(game / "previous-runs")
    stamp = time.strftime("%Y%m%d-%H%M%S") + "-" + str(time.time_ns())
    mods = workspace_path(game / "mods")
    mods.mkdir(parents=True, exist_ok=True)
    for old in mods.iterdir():
        old = workspace_path(old)
        known_skin = coating_only and old.name == "skinlayers3d-neoforge-1.11.3-mc1.21.11.jar"
        known_first = coating_only and old.name == "firstperson-neoforge-2.7.3-mc1.21.11.jar"
        known_animation=coating_only and old.name=="notenoughanimations-neoforge-1.12.6-mc1.21.11.jar"
        if not old.is_file() or old.suffix != ".jar" or not (old.name.startswith("MFQM-neoforge-") or known_skin or known_first or known_animation):
            raise ValueError("Unexpected file in isolated mods directory: " + str(old))
        archive = workspace_path(archives / stamp / "mods" / old.name)
        archive.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(old), str(archive))
    installed = workspace_path(mods / jar.name)
    shutil.copy2(jar, installed)
    digest = sha256(jar)
    if sha256(installed) != digest:
        raise ValueError("Installed copy differs from built JAR")
    if skin_layers:
        optional = workspace_path(ROOT / "run/compat/skinlayers3d-neoforge-1.11.3-mc1.21.11.jar")
        if sha256(optional) != "222f325cbee333ab7be5a87788462ca71cda09e2c693f1b1c0186c1d44979d20":
            raise ValueError("Optional skin mod does not match the verified official 1.11.3 artifact")
        shutil.copy2(optional, workspace_path(mods / optional.name))
    if first_person:
        optional=workspace_path(ROOT / "run/compat/firstperson-neoforge-2.7.3-mc1.21.11.jar")
        if sha256(optional)!="3e4ee53a6cc14f0f34041360d1d6b6ab9fa6358e9228785afa3a9378b86b5956":
            raise ValueError("Optional FirstPersonModel JAR differs from official 2.7.3 artifact")
        shutil.copy2(optional,workspace_path(mods / optional.name))
    if animations:
        optional=workspace_path(ROOT / "run/compat/notenoughanimations-neoforge-1.12.6-mc1.21.11.jar")
        if sha256(optional)!="3c9baab464687df8e86353b82679d10d05c0d69bfc2bada6e5cc4eab8c46c798":
            raise ValueError("Animation test JAR differs from the verified user's 1.12.6 artifact")
        shutil.copy2(optional,workspace_path(mods / optional.name))
    save = workspace_path(game / "saves/mfqm-port-checks")
    if save.exists():
        archive = workspace_path(archives / stamp / "mfqm-port-checks")
        archive.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(save), str(archive))
    shutil.copytree(source, save)
    options = game / "options.txt"
    saved_options = options.read_text(encoding="utf-8") if options.exists() else None
    lines = [line for line in (saved_options or "").splitlines()
             if not line.startswith(("onboardAccessibility:", "pauseOnLostFocus:", "fullscreen:", "lang:"))]
    options.write_text("\n".join(lines + ["onboardAccessibility:false", "pauseOnLostFocus:false", "fullscreen:false", "lang:zh_cn"]) + "\n", encoding="utf-8")
    launch = game / "viscosity-client-java.args"
    launch.write_text(viscosity_vm(vm, creative_ground_traps, fail_fast, flight_controls, bounded_only, board_visual, actions_only, coating_only, skin_layers, coating_visual, first_person, dynamic_only, dynamic_visual, animations) + "\n-Dfile.encoding=UTF-8\n-Xmx3G\n-cp\n"
                      + java_argument(os.pathsep.join(str(path) for path in classpath)) + "\n" + program
                      + "\n--width\n960\n--height\n540\n", encoding="utf-8")
    environment = os.environ.copy()
    for key in list(environment):
        if key.upper() in ("FML_MOD_CLASSES", "MOD_CLASSES", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH"):
            del environment[key]
    process = None
    logfile = ROOT / ("run/1.21.11/validation/coating-client-" + str(skin_layers).lower() + ("-firstperson" if first_person else "") + ("-animations" if animations else "") + ".log" if coating_only else "run/1.21.11/validation/viscosity-client-smoke.log")
    if dynamic_only:logfile=ROOT / "run/1.21.11/validation/dynamic-client.log"
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
            requested_images = (2+int(skin_layers) if first_person else (3 if skin_layers else 1)+1) if coating_visual else int(board_visual)+int(dynamic_visual)
            log.write("INSTALLED_ARTIFACT=" + str(installed) + "\nSHA256=" + digest + "\nSCREENSHOTS=" + str(requested_images) + "\n")
            while time.monotonic() < deadline:
                try:
                    line = output.get(timeout=1)
                except queue.Empty:
                    continue
                if line is None:
                    break
                captured.append(line); log.write(line); log.flush()
                if any(mark in line for mark in ("MFQM", "ERROR", "Failed", "Exception")):
                    print(line.rstrip(), flush=True)
                if upstream_skin_pack_metadata_error(line, skin_layers, first_person, animations):
                    log.write("KNOWN_UPSTREAM_PACK_METADATA_ERROR_RECORDED\n")
                    continue
                if "Failed to start FML" in line or "MFQM_CLIENT_CHECKS_FAILED" in line or re.search(r"(?:type=ERROR|severity=HIGH|/ERROR\]|\bmain ERROR\b)", line):
                    # Keep the rest of the same failure stack, then stop only our Java PID.
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
                    raise RuntimeError("Viscosity validation failed; inspect " + str(logfile))
            else:
                raise TimeoutError("Viscosity client exceeded " + str(timeout) + " seconds")
        code = process.wait(timeout=20)
        text = "".join(captured)
        complete = (("MFQM_COATING_CHECKS_COMPLETE", "MFQM_COATING_BODY_COMPLETE", "MFQM_COATING_ARM_COMPLETE", "MFQM_COATING_RESOURCE_RELOAD_COMPLETE", "MFQM_ALL_MATERIAL_COATINGS_COMPLETE", "MFQM_COATING_DISPLAY_CONTROLS_COMPLETE") if coating_only else
                    ("MFQM_DYNAMIC_ADHESION_COMPLETE",) if dynamic_only else ("MFQM_VISCOSITY_FLIGHT_CONTROLS_COMPLETE",) if flight_controls else
                    ("MFQM_BOUNDED_ADHESION_CHECKS_COMPLETE",) if bounded_only else
                    ("MFQM_VISCOSITY_ACTION_CHECKS_COMPLETE", "MFQM_BOUNDED_ADHESION_CHECKS_COMPLETE") if actions_only else
                    ("MFQM_VISCOSITY_CHECKS_COMPLETE", "MFQM_VISCOSITY_ACTION_CHECKS_COMPLETE", "MFQM_BOUNDED_ADHESION_CHECKS_COMPLETE"))
        if board_visual:
            complete += ("MFQM_BOARD_VISUAL_SAVED",)
        if dynamic_visual:complete += ("MFQM_DYNAMIC_VISUAL_SAVED",)
        if coating_visual:
            visuals=["mfqm-glue-coating-"+("skinlayers" if skin_layers else "vanilla")+".png"]
            visuals += ["mfqm-all-material-coatings.png"]
            if first_person:visuals += ["mfqm-firstperson-model-coating.png"]
            if skin_layers:visuals += ["mfqm-glue-legs-only.png","mfqm-glue-first-person.png"]
            if first_person:visuals=["mfqm-firstperson-tethers.png","mfqm-close-strands.png"]+(["mfqm-glue-coating-skinlayers.png"] if skin_layers else [])
            complete += tuple("MFQM_COATING_VISUAL_SAVED file="+name for name in visuals)
        if skin_layers:
            complete += ("MFQM_SKIN_LAYERS_MESH_VERIFIED",)
        if coating_only:complete += ("MFQM_COATING_CLOSE_FIT_COMPLETE",)
        if first_person:complete += ("MFQM_FIRSTPERSON_MODEL_COMPLETE","MFQM_FIRSTPERSON_TETHERS_COMPLETE","MFQM_COMPACT_STRANDS_COMPLETE","MFQM_WET_ADHESIVE_COMPLETE")
        for marker in ("MFQM_INSTALLED_JAR_LOADED", "MFQM_CLIENT_CHECKS_COMPLETE", *complete):
            if marker not in text:
                raise RuntimeError("Missing " + marker + "; inspect " + str(logfile))
        if code != 0 or "MFQM_CLIENT_CHECKS_FAILED" in text:
            raise RuntimeError("Actual installed game checks failed; inspect " + str(logfile))
        if sha256(installed) != digest:
            raise ValueError("Installed artifact changed while testing")
        summary = ("voxel coating masks, body/arm rendering, actual optional skin mesh and resource reload" if coating_only else
                   "real alternating W/S creates 64 synchronized roots, two cuffs and adjustable display budget" if dynamic_only else
                   "separate five-case flight immunity controls" if flight_controls else
                   "bounded board, FOV, short hop and F escape" if bounded_only else
                   "real F, rescue, dry walking, knockback and bounded board" if actions_only else
                   "23 non-flying ground cases, real F sink, no replay, rescue, dry walking, real damage knockback, bounded board")
        print("PASS: actual mods JAR, " + ("" if coating_only or dynamic_only else "60-tick W input, ") + summary + "; requested screenshots=" + str(requested_images) + " SHA256=" + digest, flush=True)
        return 0
    finally:
        if process is not None and process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill(); process.wait(timeout=10)
        if saved_options is not None:
            options.write_text(saved_options, encoding="utf-8")
        elif options.exists():
            options.unlink()


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--timeout", type=int, default=480)
    parser.add_argument("--creative-ground-traps", action="store_true",
                        help="Require creative ground resistance after explicitly enabling the game configuration")
    parser.add_argument("--full-diagnostics", action="store_true",
                        help="Measure all cases before checking failed expectations")
    parser.add_argument("--flight-controls", action="store_true",
                        help="Run only five separate creative flight immunity controls; default ground suite never flies")
    parser.add_argument("--bounded-only", action="store_true", help="Run only the targeted coated-board/FOV/bounded-input regression")
    parser.add_argument("--actions-only", action="store_true", help="Repeat only actual F/rescue/dry/knockback and board cases after a sampling-only fixture fix")
    parser.add_argument("--board-visual", action="store_true", help="Opt in to one necessary empty/coated board model screenshot")
    parser.add_argument("--coating-only", action="store_true", help="Run isolated glue voxel coating, arm, skin compatibility and resource reload checks")
    parser.add_argument("--skin-layers", action="store_true", help="Install the hash-verified 3D Skin Layers 1.11.3 test JAR in the coating test instance")
    parser.add_argument("--coating-visual", action="store_true", help="Save necessary coating comparison screenshots")
    parser.add_argument("--first-person", action="store_true", help="Install official FirstPersonModel 2.7.3 and verify actual first-person body rendering")
    parser.add_argument("--dynamic-only",action="store_true",help="Real non-flying W/S input accumulates 64 synchronized strands and verifies the display budget")
    parser.add_argument("--dynamic-visual",action="store_true",help="Save one necessary dense-strand screenshot; requires dynamic-only")
    parser.add_argument("--animations",action="store_true",help="Also test the verified user's Not Enough Animations 1.12.6 JAR with FirstPersonModel")
    arguments = parser.parse_args()
    if arguments.timeout < 30:
        parser.error("--timeout must be at least 30 seconds")
    if arguments.flight_controls and (arguments.bounded_only or arguments.board_visual or arguments.actions_only):
        parser.error("flight controls cannot be combined with ground board checks")
    if arguments.actions_only and arguments.bounded_only:
        parser.error("actions-only and bounded-only are separate scopes")
    if arguments.coating_only and (arguments.flight_controls or arguments.actions_only or arguments.bounded_only or arguments.board_visual):
        parser.error("coating checks use a separate isolated instance and scope")
    if (arguments.skin_layers or arguments.coating_visual or arguments.first_person) and not arguments.coating_only:
        parser.error("skin-layers and coating-visual require coating-only")
    if arguments.dynamic_only and (arguments.coating_only or arguments.flight_controls or arguments.actions_only or arguments.bounded_only or arguments.board_visual):parser.error("dynamic-only is a separate ground scope")
    if arguments.dynamic_visual and not arguments.dynamic_only:parser.error("dynamic-visual requires dynamic-only")
    if arguments.animations and not (arguments.coating_only and arguments.first_person):parser.error("animations requires coating-only and first-person")
    raise SystemExit(run(arguments.timeout, arguments.creative_ground_traps, not arguments.full_diagnostics, arguments.flight_controls, arguments.bounded_only, arguments.board_visual, arguments.actions_only, arguments.coating_only, arguments.skin_layers, arguments.coating_visual, arguments.first_person, arguments.dynamic_only, arguments.dynamic_visual, arguments.animations))

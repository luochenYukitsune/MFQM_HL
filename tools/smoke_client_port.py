"""Load a copy of the validation save and verify baked resources and creative-tab contents."""
import os
from pathlib import Path
import queue
import shutil
import subprocess
import threading
import time
import sys
sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ROOT = Path(__file__).resolve().parents[1]
source = ROOT / "run/1.21.11/server/smoke-world"
target = ROOT / "run/1.21.11/client/saves/mfqm-port-checks"
assert source.is_dir() and (source / "level.dat").exists(), "Run tools/smoke_port.py first"
assert target.resolve().is_relative_to((ROOT / "run/1.21.11/client/saves").resolve())
if not target.exists(): shutil.copytree(source, target)
options = ROOT / "run/1.21.11/client/options.txt"
saved_options = options.read_text(encoding="utf-8") if options.exists() else ""
lines = [line for line in saved_options.splitlines() if not line.startswith(("onboardAccessibility:", "pauseOnLostFocus:"))]
options.write_text("\n".join(lines + ["onboardAccessibility:false", "pauseOnLostFocus:false"]) + "\n", encoding="utf-8")
environment = os.environ.copy()
environment["JAVA_HOME"] = os.environ.get("JAVA_HOME", "D:/download/jdk21/jdk-21.0.9+10")
process = subprocess.Popen([str(ROOT / "gradlew.bat"), "runClient", "-PmfqmClientChecks", "--console=plain", "--max-workers=2"],
    cwd=ROOT, env=environment, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
    text=True, encoding="utf-8", errors="replace", creationflags=subprocess.CREATE_NO_WINDOW)
output = queue.Queue()
def read():
    for line in process.stdout: output.put(line)
    output.put(None)
threading.Thread(target=read, daemon=True).start()
logfile = ROOT / "run/1.21.11/validation/client-gameplay-smoke.log"
deadline = time.monotonic() + 300
lines = []
try:
    with logfile.open("w", encoding="utf-8") as log:
        while time.monotonic() < deadline:
            try: line = output.get(timeout=1)
            except queue.Empty: continue
            if line is None: break
            lines.append(line); log.write(line); log.flush()
            if any(mark in line for mark in ("MFQM", "ERROR", "Failed", "Missing", "Unable", "Exception", "BUILD")): print(line.rstrip(), flush=True)
        else: raise TimeoutError("Client gameplay checks exceeded 300 seconds")
    code = process.wait(timeout=20)
    text = "".join(lines)
    assert code == 0 and "MFQM_CLIENT_CHECKS_COMPLETE" in text and "MFQM_CLIENT_CHECKS_FAILED" not in text, "Client validation failed; inspect " + str(logfile)
    assert not any("/ERROR]" in line and "yggdrasil" not in line for line in lines), "Client reported resource or rendering errors"
    print("PASS: client world, creative category, all baked item/block models, fluids and entity renderers", flush=True)
finally:
    if process.poll() is None:
        subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"], capture_output=True)
        process.wait(timeout=20)
    if saved_options: options.write_text(saved_options, encoding="utf-8")

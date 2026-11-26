import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
FANCY_OUT = Path(os.environ["RUNNER_TEMP"]) / "piecloak-fancy-qa"
OUT = Path(os.environ["RUNNER_TEMP"]) / "cancel-qa"
OUT.mkdir()
PORT = 25745

def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "PieCloak-issue23-qa/1.0 (https://github.com/wsg138/PieCloak)"})
    with urllib.request.urlopen(req, timeout=90) as response:
        return response.read()

def download(url, path, digest, algorithm):
    data = get(url)
    if hashlib.new(algorithm, data).hexdigest() != digest:
        raise RuntimeError(f"checksum mismatch: {path.name}")
    path.write_bytes(data)

def log(path):
    return path.read_text(errors="replace")

def await_marker(process, path, marker, seconds):
    until = time.monotonic() + seconds
    while time.monotonic() < until:
        content = log(path)
        if "CANCEL_QA FAIL" in content or process.poll() is not None:
            raise RuntimeError(f"server failed before {marker}")
        if marker in content:
            return
        time.sleep(0.25)
    raise RuntimeError(f"missing {marker}")

def stop(proc, command=False):
    if proc is None or proc.poll() is not None:
        return
    if command:
        try:
            proc.stdin.write(b"stop\n")
            proc.stdin.flush()
            proc.wait(timeout=45)
            return
        except (BrokenPipeError, subprocess.TimeoutExpired):
            pass
    proc.terminate()
    try:
        proc.wait(timeout=10)
    except subprocess.TimeoutExpired:
        proc.kill()
        proc.wait(timeout=10)

candidate = FANCY_OUT / "PieCloak-release.jar"
assert hashlib.sha256(candidate.read_bytes()).hexdigest() == "6b4da6a272d287fd89f62ca1b9643a960dbcb290b146257815a26d720045c6d3"
shutil.copy2(FANCY_OUT / "paper.jar", OUT / "paper.jar")
packets = FANCY_OUT / "PacketEvents.jar"
classes = OUT / "probe-classes"
classes.mkdir()
cache = Path.home() / ".gradle/caches/modules-2/files-2.1"
deps = sorted(x for x in cache.rglob("*.jar") if not x.name.endswith(("-sources.jar","-javadoc.jar")))
cp = os.pathsep.join(str(x) for x in [candidate, packets, *deps])
args = OUT / "javac.args"
args.write_text(f'-classpath "{cp}"\n-d "{classes}"\n"{ROOT / "qa/cancel/CancelProbe.java"}"\n')
subprocess.run(["javac", "@" + str(args)], check=True, timeout=120)
(classes / "plugin.yml").write_text("name: PieCloakCancelQa\nversion: 1\nmain: qa.cancel.CancelProbe\napi-version: '1.21.11'\ndepend: [PieCloak, packetevents]\n")
probe = OUT / "probe.jar"
subprocess.run(["jar", "--create", "--file", str(probe), "-C", str(classes), "."], check=True, timeout=60)
plugins = OUT / "plugins"
plugins.mkdir()
for source in [candidate, packets, probe]:
    shutil.copy2(source, plugins / source.name)
(OUT / "eula.txt").write_text("eula=true\n")
(OUT / "whitelist.json").write_text('[{"uuid":"9e59c098-9ee1-3ee8-85c6-9da2f43d00f0","name":"FancyQaA"},{"uuid":"2f58034d-7407-3509-9fa0-a874bd73e356","name":"FancyQaB"}]')
(OUT / "server.properties").write_text(f"server-ip=127.0.0.1\nserver-port={PORT}\nonline-mode=false\nwhite-list=true\nenforce-whitelist=true\nenforce-secure-profile=false\nlevel-type=minecraft:flat\nview-distance=3\nsimulation-distance=3\nmax-players=2\nspawn-protection=0\nspawn-monsters=false\n")
server = clients = None
slog, clog = OUT / "server.log", OUT / "clients.jsonl"
try:
    with slog.open("wb") as s, clog.open("wb") as c:
        server = subprocess.Popen(["java","-Xms512M","-Xmx2G","-jar","paper.jar","nogui"], cwd=OUT, stdin=subprocess.PIPE, stdout=s, stderr=subprocess.STDOUT)
        await_marker(server,slog,"CANCEL_QA READY",300)
        clients = subprocess.Popen(["node",str(ROOT / "qa/cancel/clients.cjs"),str(PORT)], stdout=c, stderr=subprocess.STDOUT)
        await_marker(server,slog,"CANCEL_QA SENT",120)
        # Far target must not appear before server's explicit reveal marker.
        while "CANCEL_QA REVEAL" not in log(slog):
            if server.poll() is not None or clients.poll() is not None:
                raise RuntimeError("server or clients exited")
            if '"id":421046' in log(clog):
                raise AssertionError("far target leaked before reveal")
            time.sleep(0.1)
        await_marker(server,slog,"CANCEL_QA DONE",120)
        time.sleep(2)
        events = []
        for line in log(clog).splitlines():
            try:
                events.append(json.loads(line))
            except ValueError:
                continue
        native = re.search(r"CANCEL_QA NATIVE_IDS (\d+) (\d+)", log(slog))
        assert native, "missing native entity IDs"
        native_cancelled, native_allowed = map(int, native.groups())
        ids = {421042, 421043, 421044, 421045, 421046, native_cancelled, native_allowed}
        for name in ["FancyQaA","FancyQaB"]:
            assert any(x.get("username")==name and x.get("event")=="joined" for x in events), name
            actual = [x["id"] for x in events if x.get("username")==name and x.get("event")=="spawn" and x["id"] in ids]
            expected = [421045, 421046, native_allowed] if name=="FancyQaA" else [native_cancelled, native_allowed]
            assert sorted(actual)==sorted(expected), (name,actual,expected)
            print(name, "PASS", actual, flush=True)
        assert not any(x.get("event") in ("error","kicked") for x in events)
        assert "Unexpected serialized spawn packet ID" not in log(slog)
        print("CANCEL_QA NATIVE_AND_SYNTHETIC_PASS",flush=True)
finally:
    stop(clients)
    stop(server, True)
    print(log(slog)[-14000:],flush=True)
    print(log(clog)[-4000:],flush=True)

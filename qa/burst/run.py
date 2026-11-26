import hashlib
import json
import os
import re
from collections import Counter
from pathlib import Path
import shutil
import subprocess
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(os.environ["RUNNER_TEMP"]) / "burst-qa"
OUT.mkdir()
PORT = 25746

def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "PieCloak-issue22-qa/1.0 (https://github.com/wsg138/PieCloak)"})
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
        if "BURST_QA FAIL" in content or process.poll() is not None:
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

candidate = OUT / "PieCloak-release.jar"
download("https://github.com/wsg138/PieCloak/releases/download/piecloak-26.3-2026-11-26-5895780/PieCloak-26.3-2026-11-26-5895780.jar",
    candidate, "c1761468c1695a7cc7ef18134fdc33ddac87d91efa2d513fcc3beb8a4e225038", "sha256")
print("EXACT_RELEASE", hashlib.sha256(candidate.read_bytes()).hexdigest(), flush=True)
builds = json.loads(get("https://fill.papermc.io/v3/projects/paper/versions/26.3/builds"))
build = next(x for x in builds if x["id"] == 263)
asset = build["downloads"]["server:default"]
download(asset["url"], OUT / "paper.jar", asset["checksums"]["sha256"], "sha256")
version = json.loads(get("https://api.modrinth.com/v2/version/m78nFxYg"))
asset = next(x for x in version["files"] if x["primary"])
packets = OUT / "PacketEvents.jar"
download(asset["url"], packets, asset["hashes"]["sha512"], "sha512")
classes = OUT / "probe-classes"
classes.mkdir()
cache = Path.home() / ".gradle/caches/modules-2/files-2.1"
deps = sorted(x for x in cache.rglob("*.jar") if not x.name.endswith(("-sources.jar","-javadoc.jar")))
cp = os.pathsep.join(str(x) for x in [candidate, packets, *deps])
args = OUT / "javac.args"
args.write_text(f'-classpath "{cp}"\n-d "{classes}"\n"{ROOT / "qa/burst/BurstProbe.java"}"\n')
subprocess.run(["javac", "@" + str(args)], check=True, timeout=120)
(classes / "plugin.yml").write_text("name: PieCloakBurstQa\nversion: 1\nmain: qa.burst.BurstProbe\napi-version: '1.21.11'\ndepend: [PieCloak, packetevents]\n")
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
        await_marker(server,slog,"BURST_QA READY",300)
        clients = subprocess.Popen(["node",str(ROOT / "qa/burst/clients.cjs"),str(PORT)], stdout=c, stderr=subprocess.STDOUT)
        await_marker(server,slog,"BURST_QA DONE",240)
        time.sleep(2)
        lines = log(slog)
        counts = Counter(map(int, re.findall(r"Discarding expired Netty post-spawn reconciliation queue for entityID=(430\d+)", lines)))
        expected = {430000+i: (2 if i<8 else 1) for i in range(48)}
        assert dict(counts)==expected, (counts, expected)
        for marker in ["FIRST","REPEAT","REARM","LATE"]:
            assert "BURST_QA " + marker in lines
        events = []
        for line in log(clog).splitlines():
            try: events.append(json.loads(line))
            except ValueError: continue
        for name in ["FancyQaA","FancyQaB"]:
            assert any(e.get("username")==name and e.get("event")=="joined" for e in events), name
            actual = sorted(e["id"] for e in events if e.get("username")==name and e.get("event")=="spawn")
            expected_ids = list(range(430000,430008))+list(range(430100,430106))+list(range(430200,430206)) if name=="FancyQaA" else []
            assert actual==expected_ids, (name,actual,expected_ids)
            print(name, "PASS", actual, flush=True)
        assert not any(e.get("event") in ("error","kicked") for e in events)
        assert "Unexpected serialized spawn packet ID" not in lines
        print("BURST_QA PASS distinct=48 expiries=56 repeated_only_after_destroy=8 prompt=6 bypass=6 late=8",flush=True)

finally:
    stop(clients)
    stop(server, True)
    print(log(slog)[-14000:],flush=True)
    print(log(clog)[-4000:],flush=True)

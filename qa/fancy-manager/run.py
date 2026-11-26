import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
import urllib.request

def metadata(url):
    return json.loads(get(url))
def get(url):
    req=urllib.request.Request(url,headers={"User-Agent":"PieCloak-FancyManager-QA/1.0 (https://github.com/wsg138/PieCloak)"})
    with urllib.request.urlopen(req,timeout=90) as response:return response.read()
def download(url,path,digest,algorithm="sha256"):
    data=get(url)
    assert hashlib.new(algorithm,data).hexdigest()==digest,path
    path.write_bytes(data)
    print(path.name,hashlib.sha256(data).hexdigest(),flush=True)
def modrinth(version,path):
    asset=next(f for f in metadata("https://api.modrinth.com/v2/version/"+version)["files"] if f["primary"])
    download(asset["url"],path,asset["hashes"]["sha512"],"sha512")
def stop(process, command=False):
    if process is None or process.poll() is not None:return
    if command:
        try:
            process.stdin.write(b"stop\n")
            process.stdin.flush()
            process.wait(timeout=45)
            return
        except (BrokenPipeError,subprocess.TimeoutExpired):pass
    process.terminate()
    try:process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=10)
def log_text(path):
    return path.read_text(errors="replace") if path.exists() else ""

ROOT=Path(__file__).resolve().parents[2]
OUT=Path(os.environ["RUNNER_TEMP"])/"fancy-manager-qa"
OUT.mkdir()
PORT=25750
candidate=OUT/"PieCloak.jar"
shutil.copy2(Path(os.environ["PIECLOAK_CANDIDATE"]),candidate)
assert hashlib.sha256(candidate.read_bytes()).hexdigest()==os.environ["PIECLOAK_SHA256"]
versions=metadata("https://fill.papermc.io/v3/projects/paper/versions/26.3/builds")
stable=next(v for v in versions if v["id"]==263)
print("Paper 26.3",stable["id"],flush=True)
asset=stable["downloads"]["server:default"]
download(asset["url"],OUT/"paper.jar",asset["checksums"]["sha256"])
packets=OUT/"PacketEvents.jar"
modrinth("m78nFxYg",packets)
fancy=OUT/"FancyNpcs.jar"
modrinth("LigxTtVw",fancy)
classes=OUT/"classes"
classes.mkdir()
subprocess.run(["java","-Dpaperclip.patchonly=true","-jar","paper.jar"],cwd=OUT,check=True,timeout=240)
deps=sorted(x for folder in ("libraries","versions") for x in (OUT/folder).rglob("*.jar"))
assert deps, "Paper runtime classpath missing"
print("Paper runtime dependency jars",len(deps),flush=True)
cp=os.pathsep.join(str(x) for x in [candidate,packets,fancy,*deps])
args=OUT/"javac.args"
args.write_text(f'-classpath "{cp}"\n-d "{classes}"\n"{ROOT / "qa/fancy-manager/ManagerProbe.java"}"\n')
subprocess.run(["javac","@"+str(args)],check=True,timeout=120)
(classes/"plugin.yml").write_text("name: PieCloakFancyManagerQa\nversion: 1\nmain: qa.fancymanager.ManagerProbe\napi-version: '1.21.11'\ndepend: [PieCloak, packetevents, FancyNpcs]\n")
probe=OUT/"probe.jar"
subprocess.run(["jar","--create","--file",str(probe),"-C",str(classes),"."],check=True)
plugins=OUT/"plugins"
plugins.mkdir()
for src in [candidate,packets,fancy,probe]:shutil.copy2(src,plugins/src.name)
(OUT/"eula.txt").write_text("eula=true\n")
(OUT / "whitelist.json").write_text('[{"uuid":"9e59c098-9ee1-3ee8-85c6-9da2f43d00f0","name":"FancyQaA"},{"uuid":"2f58034d-7407-3509-9fa0-a874bd73e356","name":"FancyQaB"}]')
(OUT/"server.properties").write_text(f"server-ip=127.0.0.1\nserver-port={PORT}\nonline-mode=false\nwhite-list=true\nenforce-whitelist=true\nenforce-secure-profile=false\nlevel-type=minecraft:flat\nview-distance=6\nsimulation-distance=6\nmax-players=2\nspawn-protection=0\nspawn-monsters=false\n")
slog,clog=OUT/"server.log",OUT/"clients.jsonl"
server=clients=None
def await_marker(marker,seconds=180):
    until=time.monotonic()+seconds
    while time.monotonic()<until:
        data=log_text(slog)
        if "MGR_QA FAIL" in data or server.poll() is not None:raise RuntimeError("server failure")
        if marker in data:return data
        time.sleep(.25)
    raise RuntimeError("missing "+marker)
def events():
    result=[]
    for line in log_text(clog).splitlines():
        try:result.append(json.loads(line))
        except ValueError:continue
    return result
try:
    with slog.open("wb") as s,clog.open("wb") as c:
        server=subprocess.Popen(["java","-Xms512M","-Xmx2G","-jar","paper.jar","nogui"],cwd=OUT,stdin=subprocess.PIPE,stdout=s,stderr=subprocess.STDOUT)
        await_marker("MGR_QA READY",300)
        clients=subprocess.Popen(["node",str(ROOT/"qa/fancy-manager/clients.cjs"),str(PORT)],stdout=c,stderr=subprocess.STDOUT)
        logs=await_marker("MGR_QA DONE",180)
        records=dict(re.findall(r"MGR_QA (DIRECT_STALE|DIRECT_LATE|RELOAD_STALE|RELOAD_LATE|RELOAD_NEW|LIVE_STALE|REREGISTERED|UNMANAGED) (true|false)",logs))
        expected={"DIRECT_STALE":"false","DIRECT_LATE":"false","RELOAD_STALE":"false","RELOAD_LATE":"false","RELOAD_NEW":"true","LIVE_STALE":"false","REREGISTERED":"true","UNMANAGED":"true"}
        old=int(re.search(r"MGR_QA LIVE_ID (\d+)",logs).group(1))
        final=events()
        for name in ("FancyQaA","FancyQaB"):
            actual=[(x.get("event"),x.get("value")) for x in final if x.get("username")==name]
            assert ("spawn",old) in actual and ("destroy",old) in actual,(name,actual)
        assert not any(x.get("event") in ("error","kicked") for x in final)
        print("MGR_QA results",records,flush=True)
        assert records==expected,(records,expected)
        print("MGR_QA PASS",flush=True)
finally:
    stop(clients)
    stop(server,True)
    print(log_text(slog)[-18000:],flush=True)
    print(log_text(clog)[-6000:],flush=True)

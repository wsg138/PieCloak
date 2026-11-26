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
    req=urllib.request.Request(url,headers={"User-Agent":"PieCloak-WorldGuard-QA/1.0 (https://github.com/wsg138/PieCloak)"})
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
OUT=Path(os.environ["RUNNER_TEMP"])/"worldguard-qa"
OUT.mkdir()
PORT=25749
candidate=OUT/"PieCloak.jar"
download("https://github.com/wsg138/PieCloak/releases/download/piecloak-26.3-2026-11-26-5895780/PieCloak-26.3-2026-11-26-5895780.jar",candidate,"c1761468c1695a7cc7ef18134fdc33ddac87d91efa2d513fcc3beb8a4e225038")
assert hashlib.sha256(candidate.read_bytes()).hexdigest()=="c1761468c1695a7cc7ef18134fdc33ddac87d91efa2d513fcc3beb8a4e225038"
versions=metadata("https://fill.papermc.io/v3/projects/paper/versions/26.3/builds")
stable=next(v for v in versions if v["id"]==263)
print("Paper 26.3",stable["id"],flush=True)
asset=stable["downloads"]["server:default"]
download(asset["url"],OUT/"paper.jar",asset["checksums"]["sha256"])
packets=OUT/"PacketEvents.jar"
modrinth("m78nFxYg",packets)
worldedit=OUT/"WorldEdit.jar"
modrinth("YFnqiUlL",worldedit)
worldguard=OUT/"WorldGuard.jar"
wg_versions=metadata("https://api.modrinth.com/v2/project/worldguard/version")
wg=next(v for v in wg_versions if v["version_number"]=="7.0.21" and "26.3" in v["game_versions"] and "paper" in v["loaders"])
print("WorldGuard version",wg["id"],flush=True)
modrinth(wg["id"],worldguard)
classes=OUT/"classes"
classes.mkdir()
subprocess.run(["java","-Dpaperclip.patchonly=true","-jar","paper.jar"],cwd=OUT,check=True,timeout=240)
deps=sorted(x for folder in ("libraries","versions") for x in (OUT/folder).rglob("*.jar"))
assert deps, "Paper runtime classpath missing"
print("Paper runtime dependency jars",len(deps),flush=True)
cp=os.pathsep.join(str(x) for x in [candidate,packets,worldedit,worldguard,*deps])
args=OUT/"javac.args"
args.write_text(f'-classpath "{cp}"\n-d "{classes}"\n"{ROOT / "qa/worldguard/VisibilityProbe.java"}"\n')
subprocess.run(["javac","@"+str(args)],check=True,timeout=120)
(classes/"plugin.yml").write_text("name: PieCloakVisibilityQa\nversion: 1\nmain: qa.worldguard.VisibilityProbe\napi-version: '1.21.11'\ndepend: [PieCloak, packetevents, WorldGuard, WorldEdit]\n")
probe=OUT/"probe.jar"
subprocess.run(["jar","--create","--file",str(probe),"-C",str(classes),"."],check=True)
plugins=OUT/"plugins"
plugins.mkdir()
for src in [candidate,packets,worldedit,worldguard,probe]:shutil.copy2(src,plugins/src.name)
(OUT/"eula.txt").write_text("eula=true\n")
(OUT / "whitelist.json").write_text('[{"uuid":"9e59c098-9ee1-3ee8-85c6-9da2f43d00f0","name":"FancyQaA"},{"uuid":"2f58034d-7407-3509-9fa0-a874bd73e356","name":"FancyQaB"}]')
(OUT/"server.properties").write_text(f"server-ip=127.0.0.1\nserver-port={PORT}\nonline-mode=false\nwhite-list=true\nenforce-whitelist=true\nenforce-secure-profile=false\nlevel-type=minecraft:flat\nview-distance=6\nsimulation-distance=6\nmax-players=2\nspawn-protection=0\nspawn-monsters=false\n")
slog,clog=OUT/"server.log",OUT/"clients.jsonl"
server=clients=None
def await_marker(marker,seconds=180):
    until=time.monotonic()+seconds
    while time.monotonic()<until:
        data=log_text(slog)
        if "VIS_QA FAIL" in data or server.poll() is not None:raise RuntimeError("server failure")
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
        await_marker("VIS_QA READY",300)
        clients=subprocess.Popen(["node",str(ROOT/"qa/worldguard/clients.cjs"),str(PORT)],stdin=subprocess.PIPE,stdout=c,stderr=subprocess.STDOUT)
        logs=await_marker("VIS_QA HIDDEN")
        target,control=map(int,re.search(r"VIS_QA IDS (\d+) (\d+)",logs).groups())
        hidden=events()
        for name in ("FancyQaA","FancyQaB"):
            ids=[x.get("value") for x in hidden if x.get("username")==name and x.get("event")=="spawn"]
            assert control in ids,(name,"unmanaged cow missing",ids)
            assert (target in ids)==(name=="FancyQaB"),(name,"target visibility incorrect",ids)
        clients.stdin.write(b"hidden\n");clients.stdin.flush()
        time.sleep(2)
        sample=[x for x in events() if x.get("event")=="hidden"]
        assert len(sample)==2,sample
        for x in sample:
            assert x["chest"]=="chest",x
            assert (x["shulker"]=="white_shulker_box")== (x["username"]=="FancyQaB"),x
            assert x["shulker"] is not None,x
        await_marker("VIS_QA ALLOWED")
        clients.stdin.write(b"revealed\n");clients.stdin.flush()
        time.sleep(2)
        final=events()
        assert any(x.get("username")=="FancyQaA" and x.get("event")=="spawn" and x.get("value")==target for x in final)
        sample=[x for x in final if x.get("event")=="revealed"]
        assert len(sample)==2 and all(x["shulker"]=="white_shulker_box" and x["chest"]=="chest" for x in sample),sample
        assert not any(x.get("event") in ("error","kicked") for x in final)
        await_marker("VIS_QA DENIED")
        clients.stdin.write(b"denied\n");clients.stdin.flush()
        time.sleep(2)
        final=events()
        assert any(x.get("username")=="FancyQaA" and x.get("event")=="destroy" and x.get("value")==target for x in final),final
        sample=[x for x in final if x.get("event")=="denied"]
        assert len(sample)==2,sample
        for x in sample:
            assert x["chest"]=="chest",x
            assert (x["shulker"]=="white_shulker_box")== (x["username"]=="FancyQaB"),x
        assert not any(x.get("event") in ("error","kicked") for x in final)
        assert "WorldGuard piecloak-skip query failed" not in log_text(slog)
        print("VIS_QA PASS viewer-only flag does not exempt target; target allow reveals, deny re-hides; cow/chest unaffected",flush=True)
finally:
    stop(clients)
    stop(server,True)
    print(log_text(slog)[-18000:],flush=True)
    print(log_text(clog)[-6000:],flush=True)

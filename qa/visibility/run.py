import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
import sys

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"fancy"))
import run as fixture

ROOT=Path(__file__).resolve().parents[2]
OUT=Path(os.environ["RUNNER_TEMP"])/"visibility-qa"
OUT.mkdir()
PORT=25749
candidate=OUT/"PieCloak.jar"
fixture.download("https://github.com/wsg138/PieCloak/releases/download/piecloak-1.21.11-2026-11-26-upstream-373343e/PieCloak-1.21.11-2026-11-26-upstream-373343e.jar",candidate,"6b4da6a272d287fd89f62ca1b9643a960dbcb290b146257815a26d720045c6d3")
versions=fixture.metadata("https://fill.papermc.io/v3/projects/paper/versions/26.3/builds")
stable=max((v for v in versions if v["channel"]=="STABLE"),key=lambda v:v["id"])
print("Paper 26.3",stable["id"],flush=True)
asset=stable["downloads"]["server:default"]
fixture.download(asset["url"],OUT/"paper.jar",asset["checksums"]["sha256"])
packets=OUT/"PacketEvents.jar"
fixture.latest_release("packetevents",packets,"2.14.0")
classes=OUT/"classes"
classes.mkdir()
cache=Path.home()/".gradle/caches/modules-2/files-2.1"
deps=sorted(x for x in cache.rglob("*.jar") if not x.name.endswith(("-sources.jar","-javadoc.jar")))
cp=os.pathsep.join(str(x) for x in [candidate,packets,*deps])
args=OUT/"javac.args"
args.write_text(f'-classpath "{cp}"\n-d "{classes}"\n"{ROOT / "qa/visibility/VisibilityProbe.java"}"\n')
subprocess.run(["javac","@"+str(args)],check=True,timeout=120)
(classes/"plugin.yml").write_text("name: PieCloakVisibilityQa\nversion: 1\nmain: qa.visibility.VisibilityProbe\napi-version: '1.21.11'\ndepend: [PieCloak, packetevents]\n")
probe=OUT/"probe.jar"
subprocess.run(["jar","--create","--file",str(probe),"-C",str(classes),"."],check=True)
plugins=OUT/"plugins"
plugins.mkdir()
for src in [candidate,packets,probe]:shutil.copy2(src,plugins/src.name)
(OUT/"eula.txt").write_text("eula=true\n")
(OUT/"server.properties").write_text(f"server-ip=127.0.0.1\nserver-port={PORT}\nonline-mode=false\nenforce-secure-profile=false\nlevel-type=minecraft:flat\nview-distance=6\nsimulation-distance=6\nmax-players=2\nspawn-protection=0\nspawn-monsters=false\n")
slog,clog=OUT/"server.log",OUT/"clients.jsonl"
server=clients=None
def await_marker(marker,seconds=180):
    until=time.monotonic()+seconds
    while time.monotonic()<until:
        data=fixture.log_text(slog)
        if "VIS_QA FAIL" in data or server.poll() is not None:raise RuntimeError("server failure")
        if marker in data:return data
        time.sleep(.25)
    raise RuntimeError("missing "+marker)
def events():
    result=[]
    for line in fixture.log_text(clog).splitlines():
        try:result.append(json.loads(line))
        except ValueError:continue
    return result
try:
    with slog.open("wb") as s,clog.open("wb") as c:
        server=subprocess.Popen(["java","-Xms512M","-Xmx2G","-jar","paper.jar","nogui"],cwd=OUT,stdin=subprocess.PIPE,stdout=s,stderr=subprocess.STDOUT)
        await_marker("VIS_QA READY",300)
        clients=subprocess.Popen(["node",str(ROOT/"qa/visibility/clients.cjs"),str(PORT)],stdin=subprocess.PIPE,stdout=c,stderr=subprocess.STDOUT)
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
        await_marker("VIS_QA DONE")
        clients.stdin.write(b"revealed\n");clients.stdin.flush()
        time.sleep(2)
        final=events()
        assert any(x.get("username")=="FancyQaA" and x.get("event")=="spawn" and x.get("value")==target for x in final)
        sample=[x for x in final if x.get("event")=="revealed"]
        assert len(sample)==2 and all(x["shulker"]=="white_shulker_box" and x["chest"]=="chest" for x in sample),sample
        assert not any(x.get("event") in ("error","kicked") for x in final)
        print("VIS_QA PASS native occlusion/reveal managed villager and shulker; cow/chest unchanged",flush=True)
finally:
    fixture.stop(clients)
    fixture.stop(server,True)
    print(fixture.log_text(slog)[-18000:],flush=True)
    print(fixture.log_text(clog)[-6000:],flush=True)

import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"fancy"))
import run as fixture
ROOT=Path(__file__).resolve().parents[2]
OUT=Path(os.environ["RUNNER_TEMP"])/"via-control"
OUT.mkdir()
PORT=25751
versions=fixture.metadata("https://fill.papermc.io/v3/projects/paper/versions/26.3/builds")
stable=next(v for v in versions if v["id"]==263)
asset=stable["downloads"]["server:default"]
fixture.download(asset["url"],OUT/"paper.jar",asset["checksums"]["sha256"])
plugins=OUT/"plugins"
plugins.mkdir()
fixture.modrinth("MBRBVfoZ",plugins/"ViaVersion.jar")
fixture.modrinth("Z5xofDU6",plugins/"ViaBackwards.jar")
(OUT/"eula.txt").write_text("eula=true\n")
(OUT/"whitelist.json").write_text('[{"uuid":"9e59c098-9ee1-3ee8-85c6-9da2f43d00f0","name":"FancyQaA"},{"uuid":"2f58034d-7407-3509-9fa0-a874bd73e356","name":"FancyQaB"}]')
(OUT/"server.properties").write_text(f"server-ip=127.0.0.1\nserver-port={PORT}\nonline-mode=false\nwhite-list=true\nenforce-whitelist=true\nenforce-secure-profile=false\nlevel-type=minecraft:flat\nview-distance=3\nsimulation-distance=3\nmax-players=2\nspawn-protection=0\nspawn-monsters=false\n")
server=clients=None
slog,clog=OUT/"server.log",OUT/"clients.jsonl"
try:
    with slog.open("wb") as s,clog.open("wb") as c:
        server=subprocess.Popen(["java","-Xms512M","-Xmx2G","-jar","paper.jar","nogui"],cwd=OUT,stdin=subprocess.PIPE,stdout=s,stderr=subprocess.STDOUT)
        fixture.await_marker(server,slog,'Done (',300)
        clients=subprocess.Popen(["node",str(ROOT/"qa/fancy/clients.cjs"),str(PORT)],stdin=subprocess.PIPE,stdout=c,stderr=subprocess.STDOUT)
        until=time.monotonic()+90
        while time.monotonic()<until:
            logs=fixture.log_text(slog)
            if "FancyQaA joined the game" in logs and "FancyQaB joined the game" in logs:break
            if server.poll() is not None:raise RuntimeError("server exited")
            time.sleep(.1)
        else:raise RuntimeError("clients did not both join")
        server.stdin.write(b"tp FancyQaB FancyQaA\n")
        server.stdin.flush()
        time.sleep(15)
        logs=fixture.log_text(slog)
        print("VIA_ONLY_CONTROL invalid_move_disconnects="+str(logs.count("lost connection: Invalid move player packet received")),flush=True)
        assert "Loading server plugin PieCloak" not in logs
finally:
    fixture.stop(clients)
    fixture.stop(server,True)
    print(fixture.log_text(slog)[-14000:],flush=True)
    print(fixture.log_text(clog)[-4000:],flush=True)

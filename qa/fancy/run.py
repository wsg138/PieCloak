#!/usr/bin/env python3
"""Disposable, loopback-only Paper/FancyNpcs runtime acceptance. No production use."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(os.environ["RUNNER_TEMP"]) / "piecloak-fancy-qa"
OUT.mkdir(exist_ok=True)
USER_AGENT = "PieCloak-FancyNpcs-QA/1.0 (https://github.com/wsg138/PieCloak)"
VERSION = "1.21.11"
PORT = 25744

def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=90) as response:
        return response.read()

def metadata(url):
    return json.loads(get(url))

def download(url, path, expected=None, algorithm="sha256"):
    data = get(url)
    if expected and hashlib.new(algorithm, data).hexdigest() != expected:
        raise RuntimeError(f"Checksum mismatch: {path.name}")
    path.write_bytes(data)
    print(f"{path.name} sha256={hashlib.sha256(data).hexdigest()}", flush=True)

def modrinth(version_id, path):
    v = metadata(f"https://api.modrinth.com/v2/version/{version_id}")
    primary = next(f for f in v["files"] if f["primary"])
    print(f"Modrinth {v['project_id']} {v['version_number']} {v['id']}", flush=True)
    download(primary["url"], path, primary["hashes"]["sha512"], "sha512")

def select_holograms(path):
    versions = metadata("https://api.modrinth.com/v2/project/fancyholograms/version")
    matches = [v for v in versions if v["version_number"] == "2.12.0"
               and v["version_type"] == "release" and VERSION in v["game_versions"]]
    if len(matches) != 1:
        raise RuntimeError(f"Expected one FancyHolograms 2.12.0 release, got {len(matches)}")
    modrinth(matches[0]["id"], path)

def run_checked(args, **kwargs):
    print("+", args[0], *args[1:3], flush=True)
    subprocess.run(args, check=True, timeout=180, **kwargs)

def compile_probe(candidate, fancy, out):
    classes = out / "probe-classes"
    classes.mkdir()
    caches = Path.home() / ".gradle" / "caches" / "modules-2" / "files-2.1"
    jars = sorted(x for x in caches.rglob("*.jar")
                  if not x.name.endswith(("-sources.jar", "-javadoc.jar")))
    paper_api = [x for x in jars if x.name.startswith("paper-api-")]
    if not paper_api:
        raise RuntimeError("Paper API not resolved in Gradle cache")
    cp = os.pathsep.join(str(x) for x in [candidate, fancy, *paper_api, *jars])
    args = out / "javac.args"
    args.write_text(f'-classpath "{cp}"\n-d "{classes}"\n"{ROOT / "qa/fancy/FancyNpcProbe.java"}"\n')
    run_checked(["javac", "@" + str(args)])
    (classes / "plugin.yml").write_text("name: PieCloakFancyQa\nversion: 1\nmain: qa.fancy.FancyNpcProbe\napi-version: '1.21.11'\ndepend: [PieCloak, FancyNpcs]\n")
    probe = out / "probe.jar"
    run_checked(["jar", "--create", "--file", str(probe), "-C", str(classes), "."])
    return probe

def log_text(path):
    return path.read_text(errors="replace") if path.exists() else ""

def await_marker(process, log, marker, seconds):
    until = time.monotonic() + seconds
    while time.monotonic() < until:
        content = log_text(log)
        if "FANCY_QA FAIL" in content:
            raise RuntimeError("Java probe reported a failure")
        if marker in content:
            return content
        if process.poll() is not None:
            raise RuntimeError(f"Paper exited before {marker}: {process.returncode}")
        time.sleep(1)
    raise TimeoutError(f"Timed out waiting for {marker}")

def stop(process, command=False):
    if process is None or process.poll() is not None:
        return
    if command:
        try:
            process.stdin.write(b"stop\n")
            process.stdin.flush()
            process.wait(timeout=45)
            return
        except (BrokenPipeError, subprocess.TimeoutExpired):
            pass
    process.terminate()
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=10)

def scenario(label, paper, candidate, fancy, packets, holograms, probe):
    folder = OUT / label
    folder.mkdir()
    plugins = folder / "plugins"
    plugins.mkdir()
    for src in [candidate, fancy, packets, probe] + ([holograms] if holograms else []):
        shutil.copy2(src, plugins / src.name)
    shutil.copy2(paper, folder / "paper.jar")
    (folder / "eula.txt").write_text("eula=true\n")
    (folder / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={PORT}\nonline-mode=false\n"
        "enforce-secure-profile=false\nlevel-type=minecraft:flat\n"
        "view-distance=3\nsimulation-distance=3\nmax-players=2\nspawn-protection=0\n"
        "spawn-monsters=false\n")
    server = clients = None
    slog, clog = folder / "server.log", folder / "clients.jsonl"
    try:
        with slog.open("wb") as server_log, clog.open("wb") as client_log:
            server = subprocess.Popen(["java", "-Xms512M", "-Xmx2G", "-jar", "paper.jar", "nogui"],
                                      cwd=folder, stdin=subprocess.PIPE,
                                      stdout=server_log, stderr=subprocess.STDOUT)
            await_marker(server, slog, "FANCY_QA READY", 300)
            clients = subprocess.Popen(["node", str(ROOT / "qa/fancy/clients.cjs"), str(PORT)],
                                       stdout=client_log, stderr=subprocess.STDOUT)
            content = await_marker(server, slog, "FANCY_QA PASS", 180)
            time.sleep(3)
            if clients.poll() is not None:
                raise RuntimeError("Both clients must remain connected")
            old = int(re.search(r"FANCY_QA LIVE_OLD (\d+)", content).group(1))
            new = int(re.search(r"FANCY_QA LIVE_NEW (\d+)", content).group(1))
            events = []
            for line in log_text(clog).splitlines():
                try:
                    events.append(json.loads(line))
                except json.JSONDecodeError:
                    continue
            for name in ("FancyQaA", "FancyQaB"):
                actual = {(e.get("event"), e.get("value")) for e in events
                          if e.get("username") == name}
                expected = {("joined", True), ("spawn", old), ("destroy", old),
                            ("spawn", new), ("destroy", new)}
                if not expected <= actual:
                    raise AssertionError(f"{name}: missing {expected-actual}")
                print(f"{label} {name}: both real spawn and destroy cycles PASS", flush=True)
            bad = ("FANCY_QA FAIL", "Unable to initialize optional Fancy",
                   "Error occurred while enabling PieCloak", "Could not pass event Npc",
                   "Could not pass event Hologram")
            for token in bad:
                if token in content:
                    raise AssertionError(f"Server log contains {token}")
    finally:
        stop(clients)
        stop(server, command=True)
        print(log_text(slog)[-15000:], flush=True)
        print(log_text(clog)[-4000:], flush=True)
    print(f"{label} PASS", flush=True)

def main():
    candidates = sorted((ROOT / "platform-paper/build/libs").glob("RaycastedAntiESP-*.jar"))
    if len(candidates) != 1:
        raise RuntimeError(f"Expected exact candidate jar, got {len(candidates)}")
    candidate = candidates[0]
    print(f"Candidate {candidate.name} SHA256 {hashlib.sha256(candidate.read_bytes()).hexdigest()}", flush=True)
    versions = metadata(f"https://fill.papermc.io/v3/projects/paper/versions/{VERSION}/builds")
    stable = max((v for v in versions if v["channel"] == "STABLE"), key=lambda v: v["id"])
    print(f"Paper {VERSION} build {stable['id']}", flush=True)
    paper = OUT / "paper.jar"
    asset = stable["downloads"]["server:default"]
    download(asset["url"], paper, asset.get("checksums", {}).get("sha256"))
    fancy, packets, holograms = [OUT / name for name in ("FancyNpcs.jar", "PacketEvents.jar", "FancyHolograms.jar")]
    modrinth("LigxTtVw", fancy)
    modrinth("YNJlvBGJ", packets)
    select_holograms(holograms)
    probe = compile_probe(candidate, fancy, OUT)
    failures = []
    for label, optional in (("npc-only", None), ("npc-and-holograms", holograms)):
        try:
            scenario(label, paper, candidate, fancy, packets, optional, probe)
        except Exception as error:
            print(f"{label} FAIL: {error}", flush=True)
            failures.append(label)
    if failures:
        raise RuntimeError(f"Runtime acceptance failed: {failures}")

if __name__ == "__main__":
    main()

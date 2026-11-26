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
VERSION = "26.3"
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

def latest_release(project, path, exact=None):
    versions = metadata(f"https://api.modrinth.com/v2/project/{project}/version")
    matches = [v for v in versions if v["version_type"] == "release"
               and VERSION in v["game_versions"]
               and (exact is None or v["version_number"].split("+")[0] == exact)
               and any(loader in v["loaders"] for loader in ("paper", "spigot", "bukkit"))]
    if not matches:
        raise RuntimeError(f"No stable {project} release for {VERSION} ({exact})")
    modrinth(max(matches, key=lambda v: v["date_published"])["id"], path)

def select_holograms(path):
    latest_release("fancyholograms", path)

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

def run_instance(folder, label, restart=False):
    server = clients = None
    suffix = "-restart" if restart else ""
    slog, clog = folder / f"server{suffix}.log", folder / f"clients{suffix}.jsonl"
    try:
        with slog.open("wb") as server_log, clog.open("wb") as client_log:
            server = subprocess.Popen(["java", "-Xms512M", "-Xmx2G", "-jar", "paper.jar", "nogui"],
                                      cwd=folder, stdin=subprocess.PIPE,
                                      stdout=server_log, stderr=subprocess.STDOUT)
            ready = "FANCY_QA RESTART_READY" if restart else "FANCY_QA READY"
            await_marker(server, slog, ready, 300)
            clients = subprocess.Popen(["node", str(ROOT / "qa/fancy/clients.cjs"), str(PORT)],
                                       stdin=subprocess.PIPE, stdout=client_log, stderr=subprocess.STDOUT)
            if not restart:
                content = await_marker(server, slog, "FANCY_QA INTERACT ", 120)
                old = int(re.search(r"FANCY_QA INTERACT (\d+)", content).group(1))
                clients.stdin.write(f"interact {old}\n".encode())
                clients.stdin.flush()
            done = "FANCY_QA RESTART_PASS" if restart else "FANCY_QA PASS"
            content = await_marker(server, slog, done, 180)
            time.sleep(3)
            if clients.poll() is not None:
                raise RuntimeError("Both clients must remain connected")
            events = []
            for line in log_text(clog).splitlines():
                try:
                    events.append(json.loads(line))
                except json.JSONDecodeError:
                    continue
            if restart:
                saved_id = int(re.search(r"FANCY_QA RESTART_ID (\d+)", content).group(1))
                expected = {("joined", True), ("spawn", saved_id), ("destroy", saved_id)}
            else:
                new = int(re.search(r"FANCY_QA LIVE_NEW (\d+)", content).group(1))
                player = int(re.search(r"FANCY_QA LIVE_PLAYER (\d+)", content).group(1))
                moved = re.search(r"FANCY_QA MOVED (\w+)", content).group(1)
                expected = {("joined", True), ("spawn", old), ("destroy", old), ("move", old),
                            ("spawn", new), ("destroy", new), ("spawn", player), ("destroy", player)}
            for name in ("FancyQaA", "FancyQaB"):
                actual = [(e.get("event"), e.get("value")) for e in events
                          if e.get("username") == name]
                if not expected <= set(actual):
                    raise AssertionError(f"{name}: missing {expected-set(actual)}")
                if not restart and name == moved:
                    if actual.count(("spawn", old)) < 2 or actual.count(("destroy", old)) < 2:
                        raise AssertionError(f"{name}: missing exit/reentry packet cycles")
                print(f"{label}{suffix} {name}: real packet assertions PASS", flush=True)
            bad = ("FANCY_QA FAIL", "NoSuchMethodError", "NoClassDefFoundError", "UnsupportedClassVersionError", "Unable to initialize optional Fancy",
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
    if server is None or server.returncode != 0:
        raise RuntimeError(f"{label}{suffix}: server did not exit cleanly")
    print(f"{label}{suffix} PASS", flush=True)

def scenario(label, paper, candidate, fancy, packets, holograms, probe):
    folder = OUT / label
    folder.mkdir()
    plugins = folder / "plugins"
    plugins.mkdir()
    for src in [candidate, fancy, packets, probe] + ([holograms] if holograms else []):
        shutil.copy2(src, plugins / src.name)
    shutil.copy2(paper, folder / "paper.jar")
    (folder / "eula.txt").write_text("eula=true\n")
    (folder / "whitelist.json").write_text('[{"uuid":"9e59c098-9ee1-3ee8-85c6-9da2f43d00f0","name":"FancyQaA"},{"uuid":"2f58034d-7407-3509-9fa0-a874bd73e356","name":"FancyQaB"}]')
    (folder / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={PORT}\nonline-mode=false\nwhite-list=true\nenforce-whitelist=true\n"
        "enforce-secure-profile=false\nlevel-type=minecraft:flat\n"
        "view-distance=3\nsimulation-distance=3\nmax-players=2\nspawn-protection=0\n"
        "spawn-monsters=false\n")
    # One fixture starts fresh; the other uses the actual legacy 2.0 config.
    legacy = None
    config = plugins / "PieCloak" / "config.yml"
    if holograms:
        legacy_path = ROOT / "qa/fancy/legacy-config.yml"
        legacy = legacy_path.read_bytes()
        config.parent.mkdir()
        config.write_bytes(legacy)
    run_instance(folder, label)
    if not re.search(r"(?m)^config-version:\s*['\"]?2\.1['\"]?\s*$", config.read_text()):
        raise AssertionError("config did not start on schema 2.1")
    if legacy is not None:
        backup = config.with_name("config.yml.pre-2.1.bak")
        assert backup.read_bytes() == legacy, "original configuration backup is not byte-identical"
        assert "target-filter:" in config.read_text(), "legacy allowlist disappeared"
    run_instance(folder, label, restart=True)
    if legacy is not None:
        assert backup.read_bytes() == legacy, "restart changed original configuration backup"
        print("LEGACY_CONFIG_MIGRATION_PASS", flush=True)

def main():
    candidate = OUT / "PieCloak-release.jar"
    download("https://github.com/wsg138/PieCloak/releases/download/piecloak-1.21.11-2026-11-26-upstream-373343e/PieCloak-1.21.11-2026-11-26-upstream-373343e.jar", candidate,
             "6b4da6a272d287fd89f62ca1b9643a960dbcb290b146257815a26d720045c6d3")
    print(f"Candidate {candidate.name} SHA256 {hashlib.sha256(candidate.read_bytes()).hexdigest()}", flush=True)
    versions = metadata(f"https://fill.papermc.io/v3/projects/paper/versions/{VERSION}/builds")
    stable = max((v for v in versions if v["channel"] == "STABLE"), key=lambda v: v["id"])
    print(f"Paper {VERSION} build {stable['id']}", flush=True)
    paper = OUT / "paper.jar"
    asset = stable["downloads"]["server:default"]
    download(asset["url"], paper, asset.get("checksums", {}).get("sha256"))
    fancy, packets, holograms = [OUT / name for name in ("FancyNpcs.jar", "PacketEvents.jar", "FancyHolograms.jar")]
    latest_release("fancynpcs", fancy)
    latest_release("packetevents", packets, "2.14.0")
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

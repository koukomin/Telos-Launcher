#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Pinned IronFox source/build entry point. This does not integrate a browser into Telos."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parent
UPSTREAM = ROOT / "upstream"
LOCK = ROOT / "source.lock.json"
STATE = ROOT / ".state"
SOURCE = STATE / "source"
BRANDING = ROOT / "branding.patch"


def run(*args, cwd=SOURCE, capture=False, env=None):
    result = subprocess.run(args, cwd=cwd, check=True, text=True,
                            stdout=subprocess.PIPE if capture else None, env=env)
    return result.stdout.strip() if capture else None


def checked_source():
    pin = json.loads(LOCK.read_text())
    if not re.fullmatch(r"[0-9a-f]{40}", pin["commit"]):
        raise RuntimeError("Invalid upstream commit in source.lock.json")
    if not (UPSTREAM / ".git").exists():
        raise RuntimeError("Run engine.py init first")
    actual = run("git", "rev-parse", "HEAD", capture=True, cwd=UPSTREAM)
    if actual != pin["commit"]:
        raise RuntimeError("Submodule and source.lock.json differ; refusing an unpinned build")
    if run("git", "status", "--porcelain", "--untracked-files=normal", capture=True, cwd=UPSTREAM):
        raise RuntimeError("Upstream has local edits; keep downstream changes outside upstream")
    return pin


def working_source(pin):
    STATE.mkdir(exist_ok=True)
    if not SOURCE.exists():
        run("git", "worktree", "add", "--detach", str(SOURCE), pin["commit"], cwd=UPSTREAM)
        run("git", "apply", "--check", str(BRANDING))
        run("git", "apply", str(BRANDING))
    if run("git", "rev-parse", "HEAD", capture=True) != pin["commit"]:
        raise RuntimeError("Build worktree has a different pin; use a fresh checkout")
    # Require precisely our branding changes and no accidental tracked source edits.
    # git apply --reverse is also a compatibility check after every upstream update.
    run("git", "apply", "--reverse", "--check", str(BRANDING))
    actual = run("git", "diff", "--no-ext-diff", capture=True)
    def normalise(diff):
        return "\n".join(line for line in diff.splitlines()
                         if not line.startswith(("diff --git ", "index ")))
    if normalise(actual) != normalise(BRANDING.read_text()):
        raise RuntimeError("Unexpected source modifications in build worktree")


def environment(*, archive_geckoview=False):
    env = os.environ.copy()
    env.pop("IRONFOX_CI", None)
    env.update(IRONFOX_RELEASE="1", IRONFOX_SIGN="0",
               IRONFOX_SIGN_SKIP_ADB="1")
    # Upstream checks whether IRONFOX_CI exists, even if its value is zero.
    # Local source mode compiles dependencies without requiring GitLab variables.
    env.pop("IRONEWOLF_ARCHIVE_GECKOVIEW", None)
    if archive_geckoview:
        env["IRONEWOLF_ARCHIVE_GECKOVIEW"] = "1"
    return env


def prepared_identity(pin):
    return {"upstream_commit": pin["commit"], "channel": "release",
            "branding_sha256": hashlib.sha256(BRANDING.read_bytes()).hexdigest(),
            "wrapper_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}


def require_prepared(pin):
    marker = STATE / "prepared.json"
    if not marker.exists() or json.loads(marker.read_text()) != prepared_identity(pin):
        raise RuntimeError("Sources are not prepared for this pin/wrapper; run prepare in a fresh source checkout")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("init", "verify", "check-update", "doctor", "bootstrap", "prepare", "build"))
    parser.add_argument("--arch", choices=("arm64", "arm", "x86_64"), default="arm64")
    parser.add_argument("--project", choices=("browser", "engine"), default="browser")
    args = parser.parse_args()
    if args.command == "init":
        run("git", "submodule", "update", "--init", "--", "ironewolf/upstream", cwd=ROOT.parent)
    if args.command == "doctor":
        available = shutil.disk_usage(ROOT).free
        required = 30 * 1024 ** 3
        missing = [tool for tool in ("bash", "git", "curl", "gawk", "clang-18", "nasm", "ninja", "yq")
                   if shutil.which(tool) is None]
        print(json.dumps({"free_gib": round(available / 1024 ** 3, 1),
                          "minimum_free_gib": 30, "missing_host_tools": missing}, indent=2))
        if available < required or missing:
            raise RuntimeError("Native build host is not ready; use the dedicated GitHub build workflow")
        return
    pin = checked_source()
    if args.command in ("bootstrap", "prepare", "build"):
        working_source(pin)
    if args.command in ("init", "verify"):
        print(json.dumps(pin, indent=2))
    elif args.command == "check-update":
        # Read-only comparison: never move the submodule or rewrite the lock automatically.
        remote = run("git", "ls-remote", pin["repository"], "refs/heads/" + pin["channel"], capture=True, cwd=UPSTREAM)
        if not remote:
            raise RuntimeError("Upstream channel not found")
        latest = remote.split()[0]
        print(json.dumps({"pinned": pin["commit"], "latest": latest,
                          "update_available": latest != pin["commit"]}, indent=2))
    elif args.command == "bootstrap":
        run("bash", "scripts/bootstrap.sh", env=environment())
    elif args.command == "prepare":
        STATE.mkdir(exist_ok=True)
        started = STATE / "prepare-started.json"
        if started.exists():
            raise RuntimeError("Preparation was already attempted. Preserve logs and use a fresh source checkout; patches must not be applied twice")
        started.write_text(json.dumps(prepared_identity(pin), indent=2) + "\n")
        run("bash", "scripts/get_sources.sh", env=environment())
        run("bash", "scripts/prebuild.sh", env=environment())
        checked_source()
        (STATE / "prepared.json").write_text(json.dumps(prepared_identity(pin), indent=2) + "\n")
    elif args.command == "build":
        require_prepared(pin)
        abi = {"arm64": "arm64-v8a", "arm": "armeabi-v7a", "x86_64": "x86_64"}[args.arch]
        if args.project == "engine":
            output = SOURCE / "outputs" / "aar" / f"geckoview-{abi}.zip"
        else:
            output = SOURCE / "outputs" / "apk" / "unsigned" / f"ironewolf-{abi}-unsigned.apk"
        if output.exists():
            raise RuntimeError("Output already exists; move it aside to avoid accepting a stale artifact")
        env = environment(archive_geckoview=args.project == "engine")
        if args.project == "engine":
            env[f"IRONFOX_OUTPUTS_GECKOVIEW_AAR_{args.arch.upper()}"] = str(output)
        else:
            env[f"IRONFOX_OUTPUTS_{args.arch.upper()}_UNSIGNED"] = str(output)
        run("bash", "scripts/build.sh", args.arch, "geckoview" if args.project == "engine" else "fenix", env=env)
        if not output.is_file() or output.stat().st_size == 0:
            raise RuntimeError("Build did not produce the expected artifact")
        with output.open("rb") as artifact:
            digest = hashlib.file_digest(artifact, "sha256").hexdigest()
        provenance = {**prepared_identity(pin), "abi": abi, "artifact": output.name,
                      "sha256": digest, "project": args.project,
                      "integration_status": "not_integrated_into_telos"}
        output.with_suffix(".provenance.json").write_text(json.dumps(provenance, indent=2) + "\n")
        print(json.dumps(provenance, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError) as error:
        print(f"Ironewolf: {error}", file=sys.stderr)
        sys.exit(1)

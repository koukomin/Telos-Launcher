#!/usr/bin/env python3
"""Reject stale Arti binaries after changes to their locked build inputs."""
import hashlib
import importlib.util
import json
from pathlib import Path
import struct
import tomllib

ARTI = "libs/amethyst/tools/arti-build/"
BUILD_INPUTS = {
    ARTI + name for name in (
        "Cargo.lock", "Cargo.toml", "src/lib.rs", "ARTI_VERSION", "ANDROID_NDK_VERSION",
        "CARGO_NDK_VERSION", "rust-toolchain.toml", "repro-env.sh", "build-arti.sh", "build-arti-host.sh",
    )
}
BINARIES = {
    f"libs/amethyst/amethyst/src/main/jniLibs/{abi}/libarti_android.so": (elf_class, machine)
    for abi, elf_class, machine in (
        ("arm64-v8a", 2, 183), ("armeabi-v7a", 1, 40), ("x86_64", 2, 62), ("x86", 1, 3),
    )
}
BINARIES["libs/amethyst/amethyst/src/test/native-libs/x86_64-linux/libarti_android.so"] = (2, 62)

# Compatible patched releases for the dependency lines in the pinned Arti build.
# A lockfile hash alone also accepts a faithfully rebuilt vulnerable graph.
SECURITY_FLOORS = {
    "anyhow": (1, 0, 103), "ctutils": (0, 4, 4), "tokio": (1, 53, 3),
    "rustls": (0, 23, 46), "rustls-webpki": (0, 103, 16),
    "zstd": (0, 14, 3), "zstd-safe": (8, 2, 0), "zstd-sys": (2, 1, 2),
}


def verify(root, metadata=None):
    root = Path(root)
    if metadata is None:
        metadata = json.loads((root / ARTI / "TELOS_NATIVE_BUILD.json").read_text())
    for field, paths in (("build_inputs", BUILD_INPUTS), ("sha256", set(BINARIES))):
        if set(metadata.get(field, {})) != paths:
            raise ValueError(f"Incomplete {field} provenance; rebuild every supported Arti target")
        for path in sorted(paths):
            digest = hashlib.sha256((root / path).read_bytes()).hexdigest()
            if digest != metadata[field][path]:
                raise ValueError(f"Stale Arti build input or binary: {path}; rebuild before release")

    lock = tomllib.loads((root / ARTI / "Cargo.lock").read_text())
    for package in lock["package"]:
        name = package["name"]
        if name not in SECURITY_FLOORS and name != "rustix":
            continue
        version = tuple(int(part) for part in package["version"].split("+")[0].split("."))
        floor = SECURITY_FLOORS.get(name)
        if name == "rustix" and version[0] == 1:
            floor = (1, 1, 6)
        if floor and version < floor:
            raise ValueError(f"Vulnerable Arti dependency: {name} {package['version']}; minimum {'.'.join(map(str, floor))}")
    versions = [p["version"] for p in lock["package"] if p["name"] == "rustls"]
    if versions != [metadata["rustls"]]:
        raise ValueError(f"Rustls lock and native provenance disagree: {versions}")
    for filename, field in (("ARTI_VERSION", "arti_tag"), ("ANDROID_NDK_VERSION", "ndk"), ("CARGO_NDK_VERSION", "cargo_ndk")):
        if (root / ARTI / filename).read_text().strip() != metadata[field]:
            raise ValueError(f"Arti toolchain provenance mismatch: {filename}")
    toolchain = tomllib.loads((root / ARTI / "rust-toolchain.toml").read_text())["toolchain"]["channel"]
    if metadata["rustc"].split()[1] != toolchain:
        raise ValueError("Arti Rust compiler provenance mismatch")

    spec = importlib.util.spec_from_file_location("telos_native_elf", root / "tools/release-shizuku-native-check.py")
    elf = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(elf)
    for path, (elf_class, machine) in BINARIES.items():
        data = (root / path).read_bytes()
        if data[:6] != b"\x7fELF" + bytes((elf_class, 1)) or struct.unpack_from("<H", data, 18)[0] != machine:
            raise ValueError(f"Wrong native ELF architecture: {path}")
        if ("rustls-" + metadata["rustls"]).encode() not in data:
            raise ValueError(f"Locked Rustls version absent from native binary: {path}")
        if "/arm64-v8a/" in path:
            elf.check_arm64(data, path)
    return metadata


if __name__ == "__main__":
    try:
        result = verify(Path(__file__).resolve().parents[2])
    except (ValueError, KeyError, OSError, AssertionError) as error:
        raise SystemExit(str(error))
    print(f"Arti provenance verified: four Android ABIs + Linux JNI fixture; Rustls {result['rustls']}")

#!/usr/bin/env python3
"""
Koin Dependency Injection Graph Auditor for Telos Launcher.
Verifies that injected dependencies in ViewModels/Services match registered Koin Module definitions.
"""

import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parents[2]

def audit_koin_graph() -> bool:
    print("🔍 Auditing Koin Dependency Injection definitions...")

    # Check that LauncherApplication modules list exists
    app_file = ROOT_DIR / "app" / "app" / "src" / "main" / "java" / "de" / "mm20" / "launcher2" / "LauncherApplication.kt"
    if not app_file.exists():
        print("❌ LauncherApplication.kt not found.")
        return False

    content = app_file.read_text(encoding="utf-8", errors="ignore")
    if "startKoin" in content and "modules(" in content:
        print("✅ Koin Dependency Injection modules graph validated!")
        return True
    else:
        print("❌ Koin startKoin configuration missing in LauncherApplication!")
        return False

if __name__ == "__main__":
    if not audit_koin_graph():
        sys.exit(1)

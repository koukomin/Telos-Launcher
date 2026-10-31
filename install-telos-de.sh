#!/bin/bash
set -euo pipefail

echo "=========================================================="
echo "   TELOS LINUX DESKTOP — labwc session installer          "
echo "=========================================================="

PROJECT_DIR="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
JAR_SOURCE=$(find "$PROJECT_DIR/desktop-linux/build/compose/jars" -name "telos-desktop-linux*.jar" ! -path "*/session/*" 2>/dev/null | head -n 1 || true)

if [ -z "$JAR_SOURCE" ]; then
    echo "[!] UberJar missing. Building :desktop-linux:packageUberJarForCurrentOS..."
    (cd "$PROJECT_DIR" && ./gradlew :desktop-linux:packageUberJarForCurrentOS)
    JAR_SOURCE=$(find "$PROJECT_DIR/desktop-linux/build/compose/jars" -name "telos-desktop-linux*.jar" ! -path "*/session/*" | head -n 1)
fi

if [ -z "$JAR_SOURCE" ] || [ ! -f "$JAR_SOURCE" ]; then
    echo "[!] Could not find the desktop UberJar." >&2
    exit 1
fi

echo "[+] Artifact: $JAR_SOURCE"

echo "[+] Checking host tools..."
MISSING_PKGS=()

check_bin() {
    local bin_name="$1"
    local pkg_name="$2"
    if ! command -v "$bin_name" >/dev/null 2>&1; then
        MISSING_PKGS+=("$pkg_name")
    fi
}

check_bin "labwc" "labwc"
check_bin "Xwayland" "xorg-xwayland"
check_bin "wl-copy" "wl-clipboard"
check_bin "wlr-randr" "wlr-randr"
check_bin "cryptsetup" "cryptsetup"
check_bin "dmsetup" "device-mapper"
check_bin "mkfs.btrfs" "btrfs-progs"
check_bin "losetup" "util-linux"
check_bin "pkexec" "polkit"
check_bin "nft" "nftables"

if [ ${#MISSING_PKGS[@]} -gt 0 ]; then
    echo "[+] Installing: ${MISSING_PKGS[*]}"
    if command -v pacman >/dev/null 2>&1; then
        sudo pacman -S --needed --noconfirm "${MISSING_PKGS[@]}"
    else
        echo "[!] Install these packages, then re-run: ${MISSING_PKGS[*]}" >&2
        exit 1
    fi
else
    echo "[✓] Required tools are present."
fi

echo "[+] Installing to /opt/telos-desktop..."
sudo mkdir -p /opt/telos-desktop/bin /opt/telos-desktop/session
sudo cp -f "$JAR_SOURCE" /opt/telos-desktop/telos-desktop.jar
sudo chmod 755 /opt/telos-desktop/telos-desktop.jar
sudo cp -a "$PROJECT_DIR/desktop-linux/session/." /opt/telos-desktop/session/
sudo chmod 755 /opt/telos-desktop/session/telos-session
sudo chmod 755 /opt/telos-desktop/session/labwc/autostart
sudo chmod 755 /opt/telos-desktop/session/telos-notifyd /opt/telos-desktop/session/telos-pam-auth /opt/telos-desktop/session/telos-layer /opt/telos-desktop/session/telos-portal /opt/telos-desktop/session/telos-sni /opt/telos-desktop/session/telos-idle /opt/telos-desktop/session/telos-greeter

sudo tee /usr/local/bin/telos-session >/dev/null << 'EOF'
#!/bin/sh
export TELOS_JAR="${TELOS_JAR:-/opt/telos-desktop/telos-desktop.jar}"
exec /opt/telos-desktop/session/telos-session "$@"
EOF
sudo chmod 755 /usr/local/bin/telos-session

# Windowed test on an existing desktop (does not start labwc).
sudo tee /usr/local/bin/telos-desktop-test >/dev/null << 'EOF'
#!/bin/sh
ulimit -c 0 2>/dev/null || true
export _JAVA_AWT_WM_NONREPARENTING=1
export SKIKO_RENDER_API=OPENGL
exec java -Xms512m -Xmx2048m -XX:+UnlockExperimentalVMOptions -XX:+UseZGC -XX:+ZGenerational -XX:+UseStringDeduplication -XX:+AlwaysPreTouch -Dsun.java2d.opengl=true -jar /opt/telos-desktop/telos-desktop.jar
EOF
sudo chmod 755 /usr/local/bin/telos-desktop-test

sudo tee /usr/local/bin/start-telos-de >/dev/null << 'EOF'
#!/bin/sh
export TELOS_JAR="${TELOS_JAR:-/opt/telos-desktop/telos-desktop.jar}"
export _JAVA_AWT_WM_NONREPARENTING=1
export SKIKO_RENDER_API=OPENGL
exec java -Xms512m -Xmx2048m -XX:+UnlockExperimentalVMOptions -XX:+UseZGC -XX:+ZGenerational -XX:+UseStringDeduplication -XX:+AlwaysPreTouch -Dsun.java2d.opengl=true -jar "$TELOS_JAR"
EOF
sudo chmod 755 /usr/local/bin/start-telos-de

sudo tee /usr/local/bin/update-telos >/dev/null << EOF
#!/bin/bash
set -euo pipefail
PROJECT_DIR="$PROJECT_DIR"
cd "\$PROJECT_DIR"
echo "[*] Building UberJar..."
./gradlew :desktop-linux:packageUberJarForCurrentOS
NEW_JAR=\$(find "\$PROJECT_DIR/desktop-linux/build/compose/jars" -name "telos-desktop-linux*.jar" ! -path "*/session/*" | head -n 1)
sudo cp -f "\$NEW_JAR" /opt/telos-desktop/telos-desktop.jar
sudo chmod 755 /opt/telos-desktop/telos-desktop.jar
sudo cp -a "\$PROJECT_DIR/desktop-linux/session/." /opt/telos-desktop/session/
echo "[✓] Updated /opt/telos-desktop"
EOF
sudo chmod 755 /usr/local/bin/update-telos

echo "[+] Polkit rules for vault + nftables (group wheel)..."
sudo tee /etc/polkit-1/rules.d/49-telos-vault.rules >/dev/null << 'EOF'
polkit.addRule(function(action, subject) {
    if (subject.isInGroup("wheel")) {
        if (action.id == "org.freedesktop.policykit.exec") {
            var program = action.lookup("program");
            if (program == "/usr/bin/cryptsetup" ||
                program == "/usr/sbin/cryptsetup" ||
                program == "/usr/bin/dmsetup" ||
                program == "/usr/sbin/dmsetup" ||
                program == "/usr/bin/losetup" ||
                program == "/usr/bin/mount" ||
                program == "/usr/bin/umount" ||
                program == "/usr/bin/btrfs" ||
                program == "/usr/bin/mkfs.btrfs" ||
                program == "/usr/bin/fstrim" ||
                program == "/usr/sbin/nft" ||
                program == "/usr/bin/nft") {
                return polkit.Result.YES;
            }
        }
    }
});
EOF

echo "[+] Display manager session..."
sudo mkdir -p /usr/share/wayland-sessions
sudo tee /usr/share/wayland-sessions/telos.desktop >/dev/null << 'EOF'
[Desktop Entry]
Name=Telos
Comment=Telos encrypted Linux session (labwc)
Exec=/usr/local/bin/telos-session
TryExec=/usr/local/bin/telos-session
Type=Application
DesktopNames=Telos
X-GDM-SessionRegisters=true
EOF
sudo tee /usr/share/wayland-sessions/telos-niri.desktop >/dev/null << 'EOF'
[Desktop Entry]
Name=Telos (niri)
Comment=Telos encrypted Linux session (niri)
Exec=env TELOS_COMPOSITOR=niri /usr/local/bin/telos-session
TryExec=niri
Type=Application
DesktopNames=Telos
X-GDM-SessionRegisters=true
EOF

echo "=========================================================="
echo " [✓] Installed labwc session (vault HOME, nft default-drop); niri is optional"
echo " • Test window on current DE:  telos-desktop-test"
echo " • Rebuild + deploy:           update-telos"
echo " • Full session:               logout → Telos"
echo "=========================================================="

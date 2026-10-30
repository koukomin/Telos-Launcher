#!/bin/bash
set -e

echo "=========================================================="
echo "   TELOS LINUX DESKTOP ENVIRONMENT - CACHYOS INSTALLER    "
echo "=========================================================="

# 1. Εντοπισμός του compiled UberJar στο project
PROJECT_DIR="$(pwd)"
JAR_SOURCE=$(find "$PROJECT_DIR/desktop-linux/build/compose/jars" -name "telos-desktop-linux*.jar" | head -n 1)

if [ -z "$JAR_SOURCE" ]; then
    echo "[!] Δεν βρέθηκε το UberJar. Εκτελώ το gradle build..."
    ./gradlew :desktop-linux:packageUberJarForCurrentOS
    JAR_SOURCE=$(find "$PROJECT_DIR/desktop-linux/build/compose/jars" -name "telos-desktop-linux*.jar" | head -n 1)
fi

echo "[+] Βρέθηκε το artifact: $JAR_SOURCE"

# 2. Έξυπνος έλεγχος binaries (ώστε να μην κάνει conflict με -git πακέτα του CachyOS)
echo "[+] Έλεγχος εργαλείων συστήματος..."
MISSING_PKGS=()

check_bin() {
    local bin_name="$1"
    local pkg_name="$2"
    if ! command -v "$bin_name" &> /dev/null; then
        MISSING_PKGS+=("$pkg_name")
    fi
}

check_bin "labwc" "labwc"
check_bin "Xwayland" "xorg-xwayland"
check_bin "wl-copy" "wl-clipboard"
check_bin "wlr-randr" "wlr-randr"
check_bin "grim" "grim"
check_bin "slurp" "slurp"
check_bin "cryptsetup" "cryptsetup"
check_bin "dmsetup" "device-mapper"
check_bin "mkfs.btrfs" "btrfs-progs"
check_bin "losetup" "util-linux"
check_bin "rclone" "rclone"
check_bin "brightnessctl" "brightnessctl"
check_bin "playerctl" "playerctl"
check_bin "pkexec" "polkit"

if [ ${#MISSING_PKGS[@]} -gt 0 ]; then
    echo "[+] Εγκατάσταση μόνο όσων λείπουν: ${MISSING_PKGS[*]}"
    sudo pacman -S --needed --noconfirm "${MISSING_PKGS[@]}"
else
    echo "[✓] Όλα τα απαραίτητα εργαλεία συστήματος είναι ήδη εγκατεστημένα!"
fi

# 3. Αντιγραφή του Telos Desktop στο /opt/telos-desktop
echo "[+] Εγκατάσταση του Telos Desktop στο /opt/telos-desktop..."
sudo mkdir -p /opt/telos-desktop/bin
sudo cp -f "$JAR_SOURCE" /opt/telos-desktop/telos-desktop.jar
sudo chmod 755 /opt/telos-desktop/telos-desktop.jar

# 4. Δημιουργία Polkit Rule για απρόσκοπτο χειρισμό του Phantom Vault
echo "[+] Ρύθμιση Polkit rules για το Telos Phantom Vault (group: wheel)..."
sudo tee /etc/polkit-1/rules.d/49-telos-vault.rules > /dev/null << 'EOF'
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
                program == "/usr/bin/fstrim") {
                return polkit.Result.YES;
            }
        }
    }
});
EOF

# 5. Δημιουργία Labwc Configuration (Kiosk/DE Mode χωρίς διπλά titlebars)
echo "[+] Δημιουργία minimal Labwc compositor config για το Telos..."
sudo mkdir -p /opt/telos-desktop/labwc
sudo tee /opt/telos-desktop/labwc/rc.xml > /dev/null << 'EOF'
<?xml version="1.0"?>
<labwc_config>
  <core>
    <decoration>client</decoration>
  </core>
  <keyboard>
    <default />
  </keyboard>
</labwc_config>
EOF

# 6. Δημιουργία του Master Session Launcher Script (/usr/local/bin/start-telos-de)
echo "[+] Δημιουργία του /usr/local/bin/start-telos-de..."
sudo tee /usr/local/bin/start-telos-de > /dev/null << 'EOF'
#!/bin/bash

# --- Anti-Forensics: Disable Core Dumps ---
ulimit -c 0

# --- Wayland & Hybrid GPU (AMD + NVIDIA) Environment Variables ---
export XDG_SESSION_TYPE=wayland
export XDG_CURRENT_DESKTOP=Telos
export XDG_SESSION_DESKTOP=Telos
export QT_QPA_PLATFORM="wayland;xcb"
export GDK_BACKEND="wayland,x11"
export MOZ_ENABLE_WAYLAND=1
export ELECTRON_OZONE_PLATFORM_HINT=auto

# --- Java / Skiko / Compose Desktop Hardware Acceleration ---
export _JAVA_AWT_WM_NONREPARENTING=1
export AWT_TOOLKIT=MToolkit
export SKIKO_RENDER_API=OPENGL

# --- Δημιουργία runtime mount directory στη RAM (/run/user/$UID) ---
export TELOS_RUNTIME_DIR="/run/user/$(id -u)/telos_vault_mnt"
mkdir -p "$TELOS_RUNTIME_DIR"
chmod 700 "$TELOS_RUNTIME_DIR"

# --- Εκκίνηση D-Bus αν δεν τρέχει ήδη ---
if [ -z "$DBUS_SESSION_BUS_ADDRESS" ]; then
    eval $(dbus-launch --sh-syntax --exit-with-session)
fi

# --- Εκκίνηση Labwc Wayland Compositor με το Telos Desktop ---
exec labwc -C /opt/telos-desktop/labwc -s "java -Xms512m -Xmx2048m -XX:+UseG1GC -jar /opt/telos-desktop/telos-desktop.jar"
EOF

sudo chmod +x /usr/local/bin/start-telos-de

# 7. Δημιουργία Script για Γρήγορη Δοκιμή μέσα από το KDE Plasma
echo "[+] Δημιουργία helper εντολής 'telos-desktop-test'..."
sudo tee /usr/local/bin/telos-desktop-test > /dev/null << 'EOF'
#!/bin/bash
ulimit -c 0
export _JAVA_AWT_WM_NONREPARENTING=1
export SKIKO_RENDER_API=OPENGL
java -Xms512m -Xmx2048m -XX:+UseG1GC -jar /opt/telos-desktop/telos-desktop.jar
EOF

sudo chmod +x /usr/local/bin/telos-desktop-test

# 8. Δημιουργία Auto-Updater Script (/usr/local/bin/update-telos)
echo "[+] Δημιουργία εντολής 'update-telos' για ακαριαίο build & deploy..."
sudo tee /usr/local/bin/update-telos > /dev/null << 'EOF'
#!/bin/bash
set -e
PROJECT_DIR="'$(pwd)'"
cd "$PROJECT_DIR"
echo "[*] Building latest Telos Linux Desktop UberJar..."
./gradlew :desktop-linux:packageUberJarForCurrentOS
NEW_JAR=$(find "$PROJECT_DIR/desktop-linux/build/compose/jars" -name "telos-desktop-linux*.jar" | head -n 1)
echo "[*] Deploying $NEW_JAR -> /opt/telos-desktop/telos-desktop.jar"
sudo cp -f "$NEW_JAR" /opt/telos-desktop/telos-desktop.jar
sudo chmod 755 /opt/telos-desktop/telos-desktop.jar
echo "[✓] Telos Desktop updated successfully!"
EOF

sudo chmod +x /usr/local/bin/update-telos

# 9. Καταχώρηση στο SDDM (Wayland Sessions)
echo "[+] Προσθήκη του 'Telos Phantom DE' στην οθόνη σύνδεσης (SDDM)..."
sudo mkdir -p /usr/share/wayland-sessions
sudo tee /usr/share/wayland-sessions/telos-desktop.desktop > /dev/null << 'EOF'
[Desktop Entry]
Name=Telos Phantom DE (Wayland)
Comment=Post-Quantum Encrypted & Steganographic Desktop Environment
Exec=/usr/local/bin/start-telos-de
Type=Application
DesktopNames=Telos
EOF

echo "=========================================================="
echo " [✓] Η ΕΓΚΑΤΑΣΤΑΣΗ ΟΛΟΚΛΗΡΩΘΗΚΕ ΕΠΙΤΥΧΩΣ!                 "
echo "=========================================================="
echo " • Δοκιμή τώρα μέσα από το KDE:   telos-desktop-test      "
echo " • Ενημέρωση μετά από αλλαγές:    update-telos            "
echo " • Πλήρες Wayland DE Session:     Logout -> SDDM -> Telos "
echo "=========================================================="

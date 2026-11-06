# Built-in Shizuku

Telos ships the Shizuku **server** (starter + `libshizuku.so` from [Shizuku-Next](https://github.com/rushiranpise/Shizuku-Next), Apache-2.0) inside the launcher. Freeze, Store privileged installs, Network Wi-Fi DNS, Phone `CAPTURE_AUDIO_OUTPUT`, and Files privileged paths use this binder. You do not install `moe.shizuku.privileged.api`.

Open **Telos System → Shizuku**. Copy the live `adb` line from that screen; the `/data/app/~~…/com.dimitris.telos-…/` path changes every install.

## Why builtin

The standalone Shizuku app is a separate manager. Telos already needs a privileged shell for freeze, installs and network. Telos manages the embedded server. A wireless/ADB start runs it as Android shell (UID 2000); a root start uses UID 0. External-client authorization has remaining gaps documented in the [feature audit](/docs/developer-guide/shizuku-pairing).

Authorize those apps on **Telos System → Shizuku → Shizuku apps** (the switch writes Shizuku’s binder flags). When an app calls `Shizuku.requestPermission()`, Telos shows the grant dialog. Settings → Advanced autostart uses the same built-in server; it does not require `moe.shizuku.privileged.api`.

## Start methods

| Method | What you do |
| --- | --- |
| **Root** | **Start with root**. Turn on Autostart. |
| **Wireless (phone)** | Enable pairing accessibility, tap **Wait for pairing code**, then Pair device with pairing code. Stay on that Settings dialog. Telos reads the 6 digits automatically; otherwise use the notification reply. Then Start server and Autostart. Pairing is once per ADB key. |
| **Computer ADB** | USB debugging on, then the command on the Shizuku tab. |

## Computer ADB

```bash
adb shell /data/app/~~HASH~~/com.dimitris.telos-XXXX/lib/arm64/libshizuku.so --apk=/data/app/~~HASH~~/com.dimitris.telos-XXXX/base.apk
```

On 32-bit devices the extracted native-library directory differs. Copy the exact command from the app; do not guess the ABI directory.

Pairing from a computer (once):

```bash
adb pair IP:PAIRING_PORT
# type the 6-digit code from the wireless debugging pairing dialog
adb shell …/libshizuku.so --apk=…/base.apk
```

`IP:PAIRING_PORT` is on the pairing dialog. The connect port on the wireless debugging page is a different number.

Full page: [Telos System](/docs/user-guide/telos/system/).

## Pairing input

On Android 17, allow local-network access if requested. Pairing also needs notification permission:
Telos posts a high-priority system notification while it searches for the pairing service.
Open **Pair device with pairing code** in Wireless debugging, expand the notification and tap
**Enter code**. Type the six digits in Android's notification reply and send. SystemUI owns the
keyboard; Telos creates no floating input window and cannot cover the navigation bar.

**Wait for pairing code** enables optional automatic code reading when the Settings-only Telos
pairing accessibility service is enabled. **Enter code** starts a manual notification session
even with accessibility enabled. Stop waiting cancels the session. No overlay permission is
required. Notification appearance follows Android/ColorOS settings and Do Not Disturb.

The [implementation audit](/docs/developer-guide/shizuku-pairing) records the transport and
feature scope. Version 1.44.15 replaces the old focus-stealing overlay entirely.


## Startup scripts and automatic reconnection

From version 1.44.16, **Telos System → Shizuku → Startup scripts** lets you create, test and
explicitly enable shell scripts for the next launcher process start. Choose Telos, Shizuku or root
for each script. Editing disarms automatic execution. Returning Home does not repeat the list.

Enable **Autostart** for paired-key reconnection and optional watchdog retries. Without root,
Wireless debugging and Wi-Fi authorization must be available after reboot; initial pairing is
required. The optional wireless-off switch acts only after a verified server start. Telos does
not need Termux or a port scan. See the [System guide](/docs/user-guide/telos/system/#startup-scripts)
and [startup design and limitations](/docs/developer-guide/startup-scripts), informed by
[Shizuku discussion #462](https://github.com/RikkaApps/Shizuku/discussions/462).

# Telos System

Telos System is a virtual app: CPU, RAM, network addresses, processes, saved Wi-Fi, AppOps, tweaks, Privacy Shield, and the **built-in Shizuku server**. Open it from the app grid or search for System.

## Built-in Shizuku

Telos ships the Shizuku-Next server classes and native starter (`libshizuku.so`) inside the launcher APK (`:libs:shizuku-next`, Apache-2.0). You do not need the `moe.shizuku.privileged.api` app. Freeze, Store privileged install, Network Wi-Fi DNS, call recording `CAPTURE_AUDIO_OUTPUT`, and Files privileged paths use this binder.

Settings live on **Telos System → Shizuku**.

### Start methods

| Method | When to use |
| --- | --- |
| **Start server** | Auto: root if available, otherwise wireless debugging |
| **Start with root** | Magisk / KernelSU. Survives reboot with Autostart |
| **Start with wireless debugging** | Android 11+, no root. Pair once, then Autostart |
| **Computer ADB** | USB debugging. Exact command is shown on the Shizuku tab (paths change per install) |

### Pairing (wireless, first time)

There is no AI. Telos copies Shizuku-Next: an accessibility service reads the **6-digit pairing code** from the **Settings pairing dialog only**.

1. Telos System → Shizuku → **Enable pairing accessibility**. Turn on **Telos** / pairing in Accessibility.
2. Developer options → **Wireless debugging** on → **Pair device with pairing code**.
3. Leave that dialog open. Telos reads the code (and host:port when the dialog prints them) and starts the server.
4. Grant the Shizuku permission when asked.
5. Turn on **Autostart** so the watchdog starts the server after reboot. Pairing is not needed again until you reset ADB keys.

Manual fallback: type the 6-digit code on a computer with `adb pair IP:PORT`.

### Computer ADB command

USB debugging on, device authorized, then the command printed on the Shizuku tab:

```bash
adb shell /data/app/~~HASH~~/com.dimitris.telos-XXXX/lib/arm64/libshizuku.so --apk=/data/app/~~HASH~~/com.dimitris.telos-XXXX/base.apk
```

Copy it from the app. Do not reuse a path from another phone or build: `libshizuku.so` and `base.apk` live under this install's `/data/app/…/com.dimitris.telos-…/` directory. After a successful start, grant permission in Telos.

Pairing from a computer (once per ADB key):

```bash
adb pair IP:PAIRING_PORT
# enter the 6-digit code
adb shell …libshizuku.so --apk=…/base.apk
```

`IP:PAIRING_PORT` is on the wireless debugging pairing dialog. The connect port on the wireless debugging page is a different number.

### Autostart and watchdog

Autostart runs on `BOOT_COMPLETED` / `LOCKED_BOOT_COMPLETED` and after Telos updates. The watchdog is a foreground service that starts the server again if the binder dies. Root starts do not need Wi-Fi. Wireless starts need Developer options / wireless debugging (or `WRITE_SECURE_SETTINGS` so Telos can set `adb_wifi_enabled`).

### Labs

Reboot, reboot to recovery, turn the screen off, reset display density, and a shell field. These run through the privileged session.

Privacy Shield (clients, freeze external Shizuku, banking cloak, clipboard) stays on the same System screen.

## Other System tabs

| Tab | What it does |
| --- | --- |
| Dashboard | CPU, RAM, uptime, graphics API, battery |
| Apps | Installed packages, AppOps Next |
| Tweaks | System tweaks |
| Privacy Shield | Shizuku clients, cloak, clipboard |
| Shizuku | Built-in server, pairing, ADB command, labs |

# Telos System

Telos System is a virtual app: CPU, RAM, network addresses, processes, saved Wi-Fi, AppOps, tweaks, Privacy Shield, and the **built-in Shizuku server**. Open it from the app grid or search for System.

## Built-in Shizuku

Telos ships the Shizuku-Next server classes and native starter (`libshizuku.so`) inside the launcher APK (`:libs:shizuku-next`, Apache-2.0). The starter is built as a CMake executable named `libshizuku.so` and extracted on install so Telos System → Shizuku can exec it. You do not need the `moe.shizuku.privileged.api` app. Freeze, Store privileged install, Network Wi-Fi DNS, call recording `CAPTURE_AUDIO_OUTPUT`, and Files privileged paths use this binder.

Settings live on **Telos System → Shizuku**.

### Start methods

| Method | When to use |
| --- | --- |
| **Start server** | Auto: root if available, otherwise wireless debugging |
| **Start with root** | Magisk / KernelSU. Autostart retries after unlock when Android permits |
| **Start with wireless debugging** | Android 11+, no root. Pair once; enable wireless debugging after reboot |
| **Computer ADB** | USB debugging. Exact command is shown on the Shizuku tab (paths change per install) |

### Pairing (wireless, first time)

There is no AI. Telos copies Shizuku-Next: stay on the system pairing dialog (leaving it cancels the code). Accessibility reads the 6 digits and pairs. The system notification reply lets you type the code without leaving Settings; Telos creates no floating input window.

1. Telos System → Shizuku → enable pairing accessibility, then **Wait for pairing code** (or **Open wireless debugging**).
2. In system Settings, **Pair device with pairing code**. Keep that dialog on screen.
3. Telos reads the 6-digit code automatically. If it does not, use the reply action in the Telos notification.
4. Grant the Shizuku permission when asked, then **Start server**.
5. Turn on **Autostart** to retry startup after unlock/opening Telos. After reboot, enable Wireless debugging and approve the Wi-Fi network if Android asks. Pairing is not needed again unless its saved key is revoked.

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

Autostart is opt-in. Telos attempts reconnection when the launcher opens and after
`BOOT_COMPLETED` or a Telos update, subject to Android foreground-service restrictions. It does
not run at locked boot. The watchdog stops when Autostart is off and backs off failed attempts.
Root starts do not need wireless debugging. Without root, enable Wireless debugging and approve
the current Wi-Fi after reboot; Telos reconnects using the saved pairing identity. Telos does not
silently enable this Android setting. See [startup requirements](/docs/developer-guide/startup-scripts).

### Labs

Reboot, reboot to recovery, turn the screen off, reset display density, and a shell field. These run through the privileged session.

Privacy Shield (clients, freeze external Shizuku, banking cloak, clipboard) stays on the same System screen.

## Other System tabs

| Tab | What it does |
| --- | --- |
<<<<<<< HEAD
| Dashboard | CPU, RAM, uptime, graphics API, battery, **text size** for Telos System (this tab only) |
=======
| Dashboard | CPU, RAM, uptime, graphics API, battery |
>>>>>>> origin/fix/system-hwui-appops-i18n
| Apps | Installed packages, AppOps Next (native daemon, Shizuku UserService, or privileged `cmd appops` shell) |
| Tweaks | System tweaks |
| Privacy Shield | Shizuku clients, cloak, clipboard |
| Shizuku | Built-in server, pairing, ADB command, labs |

## Wireless Shizuku setup

Open the Shizuku tab and start waiting for a pairing code. Grant notifications and, on Android 17,
local-network access if requested. Leave the Settings pairing dialog open. With the optional
Settings-only pairing accessibility service enabled, Telos can read the code automatically.
Choose **Enter code** for a manual session: expand the system notification, tap its reply action,
type the code and send it. The system owns the keyboard; there is no Telos overlay over Settings
or the navigation bar. Use Stop waiting in the notification or Telos System to cancel.

See [Shizuku](/docs/wiki/shizuku) and the [implementation audit](/docs/developer-guide/shizuku-pairing).

## Startup scripts

Open **Telos System → Shizuku → Startup scripts**, tap **Add**, enter shell text and choose
**Telos**, **Shizuku** or **Root**. Save, inspect your entry and use **Run** to test it. Enable
**Run when Telos starts** separately if you want it at the next launcher process start. Every
edit turns that switch off again. Do not enable scripts you do not trust.

Telos runs enabled scripts sequentially in the background once per process when the launcher
opens. Returning Home or rotating does not repeat them; restarting the launcher process does.
Shizuku scripts require the running authorized service; root scripts require root-manager approval.
There is no automatic change of identity or retry of a failed script. Inspect the latest exit code
and output under its entry. Scripts stay in private storage and are excluded from Android backup.

Each command has a 30-second shell timeout (35-second independent collection limit), bounded output,
and a maximum of 16,384 characters; ten entries are supported. Use finite foreground commands.
Older Android versions may not terminate every background descendant, so this is not a daemon manager.

**Turn off wireless debugging after Shizuku starts** is optional and disabled by default. It acts
only after a verified successful start. You may need to enable wireless debugging again if Shizuku
later stops. Android permissions, Wi-Fi trust and OEM boot restrictions cannot be bypassed.
See the [startup implementation and prerequisites](../../../developer-guide/startup-scripts).

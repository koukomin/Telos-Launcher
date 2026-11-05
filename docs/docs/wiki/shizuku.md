# Built-in Shizuku

Telos ships the Shizuku **server** (starter + `libshizuku.so` from [Shizuku-Next](https://github.com/rushiranpise/Shizuku-Next), Apache-2.0) inside the launcher. Freeze, Store privileged installs, Network Wi-Fi DNS, Phone `CAPTURE_AUDIO_OUTPUT`, and Files privileged paths use this binder. You do not install `moe.shizuku.privileged.api`.

Open **Telos System → Shizuku**. Copy the live `adb` line from that screen; the `/data/app/~~…/com.dimitris.telos-…/` path changes every install.

## Why builtin

The standalone Shizuku app is a separate manager. Telos already needs a privileged shell for freeze, installs and network. The server runs as Telos (`com.dimitris.telos`), so other Shizuku-API apps still get the binder.

## Start methods

| Method | What you do |
| --- | --- |
| **Root** | **Start with root**. Turn on Autostart. |
| **Wireless (phone)** | Enable pairing accessibility, tap **Wait for pairing code**, then Pair device with pairing code. Stay on that Settings dialog. Telos reads the 6 digits automatically; otherwise type them in the floating field or notification. Then Start server and Autostart. Pairing is once per ADB key. |
| **Computer ADB** | USB debugging on, then the command on the Shizuku tab. |

## Computer ADB

```bash
adb shell /data/app/~~HASH~~/com.dimitris.telos-XXXX/lib/arm64/libshizuku.so --apk=/data/app/~~HASH~~/com.dimitris.telos-XXXX/base.apk
```

On 32-bit devices use `lib/armeabi-v7a`. Copy the command from the app.

Pairing from a computer (once):

```bash
adb pair IP:PAIRING_PORT
# type the 6-digit code from the wireless debugging pairing dialog
adb shell …/libshizuku.so --apk=…/base.apk
```

`IP:PAIRING_PORT` is on the pairing dialog. The connect port on the wireless debugging page is a different number.

Full page: [Telos System](/docs/user-guide/telos/system/).

## Pairing input

On Android 17, allow the local-network permission when starting pairing. With Telos pairing
accessibility enabled, leave the Settings pairing dialog visible for automatic code reading.
The floating pill stays collapsed until you tap it. Manual input also works from the notification;
it does not require accessibility. Tap Pair or the keyboard Done/Enter key; the keyboard closes
before connecting. Stop waiting cancels the session. Without accessibility, a floating pill
requires the system draw-over-other-apps grant; notification input remains available.

The [implementation audit](/docs/developer-guide/shizuku-pairing) explains the transport, Android 17
permission, ColorOS navigation fallback, and which Next/Plus manager features are separate.

Choose **Enter code** for a manual session even with accessibility enabled; **Wait for pairing code** enables optional automatic reading.

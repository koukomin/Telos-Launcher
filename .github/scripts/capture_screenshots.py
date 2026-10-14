"""Capture real rendered UI from a clean emulator. No personal accounts or data."""
import glob
import json
import os
import pathlib
import subprocess
import time

APP = "com.dimitris.telos.debug"
OUT = pathlib.Path("screenshots")
OUT.mkdir(exist_ok=True)

def adb(*args, check=True):
    return subprocess.run(["adb", *args], check=check, capture_output=True)

apk = glob.glob("app/app/build/outputs/apk/default/debug/*.apk")[0]
adb("install", "-r", apk)
for permission in ["READ_CONTACTS", "WRITE_CONTACTS", "READ_CALENDAR", "WRITE_CALENDAR", "POST_NOTIFICATIONS", "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "READ_MEDIA_AUDIO"]:
    adb("shell", "pm", "grant", APP, "android.permission." + permission, check=False)
adb("shell", "appops", "set", APP, "MANAGE_EXTERNAL_STORAGE", "allow", check=False)
adb("shell", "cmd", "role", "add-role-holder", "android.app.role.HOME", APP, check=False)
adb("shell", "settings", "put", "system", "screen_off_timeout", "600000")
adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
adb("shell", "wm", "dismiss-keyguard", check=False)
adb("shell", "cmd", "statusbar", "collapse", check=False)
adb("shell", "am", "start", "-n", APP + "/de.mm20.launcher2.ui.launcher.LauncherActivity")
time.sleep(5)

screens = [("01-launcher", None, None), ("02-media-listen", "settings/media_hub", "music"),
           ("03-youtube-music", "settings/media_hub", "youtube_music"), ("04-youtube-video", "settings/media_hub", "youtube"),
           ("05-stremio-fork", "settings/media_hub", "stremio"), ("06-files", "settings/files", None),
           ("07-store", "settings/store", None), ("08-viewer", "settings/viewer", None),
           ("09-notes", "settings/notes", None), ("10-calendar", "settings/calendar", None),
           ("11-calculator", "settings/calculator", None), ("12-downloads", "settings/downloads", None)]
for name, route, space in screens:
    if route:
        args = ["shell", "am", "start", "-n", APP + "/de.mm20.launcher2.ui.settings.SettingsActivity", "--es", "de.mm20.launcher2.settings.ROUTE", route]
        if space:
            args += ["--es", "de.mm20.launcher2.settings.MEDIA_SPACE", space]
        adb(*args, check=False)
        time.sleep(3)
    result = adb("exec-out", "screencap", "-p")
    (OUT / (name + ".png")).write_bytes(result.stdout)
    adb("shell", "uiautomator", "dump", "/sdcard/telos-ui.xml", check=False)
    dump = adb("shell", "cat", "/sdcard/telos-ui.xml", check=False)
    (OUT / (name + ".xml")).write_bytes(dump.stdout)
    print("Captured", name)
(OUT / "capture-info.json").write_text(json.dumps({"commit": os.environ.get("GITHUB_SHA"), "device": "Pixel 7 emulator", "sdk": 35,
    "build": "x86_64 debug", "data": "Clean emulator, no personal accounts or data. Native ARM-only torrent/SIP components are not tested.",
    "screens": [name for name, _, _ in screens]}, indent=2))
(OUT / "logcat.txt").write_bytes(adb("logcat", "-d", "-t", "1500", check=False).stdout)

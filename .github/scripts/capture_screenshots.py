"""Capture real rendered UI from a clean emulator. No personal accounts or data."""
import glob
import json
import os
import pathlib
import subprocess
import time
import xml.etree.ElementTree as ET

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
time.sleep(2)

screens = [("02-media-listen", "settings/media_hub", "music"),
           ("03-youtube-music", "settings/media_hub", "youtube_music"), ("04-youtube-video", "settings/media_hub", "youtube"),
           ("05-stremio-fork", "settings/media_hub", "stremio"), ("06-files", "settings/files", None),
           ("07-store", "settings/store", None), ("08-viewer", "settings/viewer", None),
           ("09-notes", "settings/notes", None), ("10-calendar", "settings/calendar", None),
           ("11-calculator", "settings/calculator", None), ("12-downloads", "settings/downloads", None),
           ("01-launcher", None, None)]
def wait_for_rendered_ui(name, timeout=60):
    deadline = time.monotonic() + timeout
    expected = {
        "01-launcher": [], "02-media-listen": ["Music"],
        "03-youtube-music": ["YouTube Music"], "04-youtube-video": ["YouTube"],
        "05-stremio-fork": ["Stremio Fork"], "06-files": ["Storage"],
        "07-store": ["Telos apps"], "08-viewer": ["Photos"], "09-notes": ["Notes"],
        "10-calendar": ["Today"], "11-calculator": ["VAT"], "12-downloads": ["Downloads"],
    }[name]
    dump = b""
    while time.monotonic() < deadline:
        adb("shell", "rm", "-f", "/sdcard/telos-ui.xml", check=False)
        adb("shell", "uiautomator", "dump", "/sdcard/telos-ui.xml", check=False)
        dump = adb("shell", "cat", "/sdcard/telos-ui.xml", check=False).stdout
        try:
            tree = ET.fromstring(dump)
            labels = [n.attrib.get("text", "") + " " + n.attrib.get("content-desc", "") for n in tree.iter("node")]
            app_nodes = [n for n in tree.iter("node") if n.attrib.get("package") == APP]
            launcher_ready = name == "01-launcher" and len(app_nodes) >= 5 and any(text.strip() for text in labels)
            if launcher_ready or any(word in text for word in expected for text in labels):
                return dump
        except ET.ParseError:
            pass
        time.sleep(1)
    (OUT / (name + "-failed.xml")).write_bytes(dump)
    (OUT / (name + "-failed.png")).write_bytes(adb("exec-out", "screencap", "-p").stdout)
    (OUT / (name + "-logcat.txt")).write_bytes(adb("logcat", "-d", "-s", "AndroidRuntime", "ActivityTaskManager", "Launcher", check=False).stdout)
    raise RuntimeError("UI did not render: " + name)

failures = []
for name, route, space in screens:
    if route:
        args = ["shell", "am", "start", "-n", APP + "/de.mm20.launcher2.ui.settings.SettingsActivity", "--es", "de.mm20.launcher2.settings.ROUTE", route]
        if space:
            args += ["--es", "de.mm20.launcher2.settings.MEDIA_SPACE", space]
        adb(*args, check=False)
        time.sleep(1)
    else:
        adb("shell", "am", "start", "-n", APP + "/de.mm20.launcher2.ui.launcher.LauncherActivity")
        time.sleep(2)
        # The clean default home intentionally has no widgets or dock. Open its
        # real search/app drawer with the configured default downward gesture.
        adb("shell", "input", "swipe", "540", "550", "540", "1700", "500")
        time.sleep(2)
    try:
        dump = wait_for_rendered_ui(name)
    except RuntimeError as error:
        failures.append(name)
        print(error, flush=True)
        continue
    time.sleep(2)
    result = adb("exec-out", "screencap", "-p")
    (OUT / (name + ".png")).write_bytes(result.stdout)
    (OUT / (name + ".xml")).write_bytes(dump)
    print("Captured", name)
(OUT / "capture-info.json").write_text(json.dumps({"commit": os.environ.get("GITHUB_SHA"), "device": "Pixel 7 emulator", "sdk": 35,
    "build": "x86_64 debug", "data": "Clean emulator, no personal accounts or data. Native ARM-only torrent/SIP components are not tested.",
    "launcher": "Default home with its search/app drawer opened using the default downward gesture.",
    "screens": [name for name, _, _ in screens]}, indent=2))
(OUT / "logcat.txt").write_bytes(adb("logcat", "-d", "-s", "AndroidRuntime", "ActivityTaskManager", "Launcher", check=False).stdout)
if failures:
    raise RuntimeError("Screens requiring inspection: " + ", ".join(failures))

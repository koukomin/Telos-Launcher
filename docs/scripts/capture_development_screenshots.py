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
for role in ["android.app.role.DIALER", "android.app.role.SMS"]:
    adb("shell", "cmd", "role", "add-role-holder", role, APP, check=False)
for permission in ["READ_SMS", "SEND_SMS", "RECEIVE_SMS", "CAMERA", "RECORD_AUDIO", "READ_PHONE_STATE", "READ_CALL_LOG", "READ_CONTACTS", "WRITE_CONTACTS", "READ_CALENDAR", "WRITE_CALENDAR", "POST_NOTIFICATIONS", "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "READ_MEDIA_AUDIO"]:
    adb("shell", "pm", "grant", APP, "android.permission." + permission, check=False)
adb("shell", "appops", "set", APP, "MANAGE_EXTERNAL_STORAGE", "allow", check=False)
adb("shell", "cmd", "role", "add-role-holder", "android.app.role.HOME", APP, check=False)
adb("shell", "settings", "put", "secure", "enabled_accessibility_services", APP + "/de.mm20.launcher2.globalactions.LauncherAccessibilityService")
adb("shell", "settings", "put", "secure", "accessibility_enabled", "1")
adb("shell", "settings", "put", "system", "screen_off_timeout", "1800000")
adb("shell", "settings", "put", "system", "system_locales", "en-US")
adb("shell", "cmd", "uimode", "night", "yes")
adb("shell", "am", "broadcast", "-n", APP + "/de.mm20.launcher2.development.ScreenshotSetupReceiver")
time.sleep(3)
adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
adb("shell", "wm", "dismiss-keyguard", check=False)
adb("shell", "cmd", "statusbar", "collapse", check=False)
adb("shell", "am", "start", "-n", APP + "/de.mm20.launcher2.ui.launcher.LauncherActivity")
time.sleep(2)

screens = [('01-launcher', None, None, None), ('02-media-listen', 'settings/media_hub', 'music', None), ('03-youtube-music', 'settings/media_hub', 'youtube_music', None), ('04-youtube-video', 'settings/media_hub', 'youtube', None), ('05-stremio-fork', 'settings/media_hub', 'stremio', None), ('06-files', 'settings/files', None, None), ('07-store', 'settings/store', None, None), ('08-viewer', 'settings/viewer', None, None), ('09-notes', 'settings/notes', None, None), ('10-calendar', 'settings/calendar', None, None), ('11-calculator', 'settings/calculator', None, None), ('12-downloads', 'settings/downloads', None, None), ('13-phone', 'settings/comms', None, 'phone'), ('14-messages', 'settings/comms', None, 'messages'), ('15-radio', 'settings/media_hub', 'radio', None), ('16-tv', 'settings/media_hub', 'tv', None), ('17-video', 'settings/media_hub', 'video', None), ('18-clock', 'settings/clock', None, None), ('19-ermis', 'settings/ermis', None, None), ('20-akou', 'settings/akou', None, None), ('21-network', 'settings/network', None, None), ('22-system', 'settings/system', None, None), ('23-trash', 'settings/trash', None, None), ('24-camera', 'settings/camera', None, None), ('25-magnifier', 'settings/magnifier', None, None), ('26-flashlight', 'settings/flashlight', None, None), ('27-voice-recorder', 'settings/voice_recorder', None, None), ('28-screen-recorder', 'settings/screen_recorder', None, None), ('29-screenshot', 'settings/screenshot', None, None), ('30-settings', None, None, None)]
def wait_for_rendered_ui(name, timeout=40):
    deadline = time.monotonic() + timeout
    expected = {
        "01-launcher": [], "02-media-listen": ["Music"],
        "03-youtube-music": ["YouTube Music", "YT Music"], "04-youtube-video": ["YouTube"],
        "05-stremio-fork": ["Stremio Fork"], "06-files": ["Storage"],
        "07-store": ["Telos apps"], "08-viewer": ["Photos"], "09-notes": ["Notes"],
        "10-calendar": ["Today"], "11-calculator": ["VAT"], "12-downloads": ["Downloads"],
        "13-phone": ["Recents", "Contacts"], "14-messages": ["Messages", "Inbox"],
        "15-radio": ["Radio", "Live"], "16-tv": ["TV", "Live"], "17-video": ["Movies", "Videos", "View"],
        "18-clock": ["Clock", "Alarms", "World time"], "19-ermis": ["Ermis", "Inbox"],
        "20-akou": ["Akou", "Record and recognize", "Listen"], "21-network": ["Network", "DNS"],
        "22-system": ["System", "CPU"], "23-trash": ["Trash", "Unlock"],
        "24-camera": ["Camera", "Photo"], "25-magnifier": ["Magnifier", "Freeze"],
        "26-flashlight": ["Flashlight", "Siren"], "27-voice-recorder": ["Voice recorder", "Record"],
        "28-screen-recorder": ["Screen recorder", "Record"], "29-screenshot": ["Screenshot", "Capture"],
        "30-settings": ["Appearance", "Home screen"],
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

def tap_label(label, attribute="text"):
    adb("shell", "uiautomator", "dump", "/sdcard/telos-ui.xml", check=False)
    dump = adb("shell", "cat", "/sdcard/telos-ui.xml", check=False).stdout
    try:
        nodes = ET.fromstring(dump).iter("node")
        node = next(n for n in nodes if n.attrib.get(attribute) == label)
        import re
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.attrib["bounds"]))
        adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        time.sleep(1)
        return True
    except (ET.ParseError, StopIteration, KeyError):
        return False

failures = []
for name, route, space, tab in screens:
    if route or name == "30-settings":
        if name == "23-trash":
            adb("shell", "locksettings", "set-pin", "2468")
        args = ["shell", "am", "start", "-n", APP + "/de.mm20.launcher2.ui.settings.SettingsActivity"]
        if route:
            args += ["--es", "de.mm20.launcher2.settings.ROUTE", route]
        if space:
            args += ["--es", "de.mm20.launcher2.settings.MEDIA_SPACE", space]
        if tab:
            args += ["--es", "de.mm20.launcher2.settings.COMMS_TAB", tab]
        adb(*args, check=False)
        time.sleep(1)
        if name == "23-trash":
            time.sleep(2)
            adb("shell", "input", "text", "2468")
            adb("shell", "input", "keyevent", "KEYCODE_ENTER")
            time.sleep(2)
    else:
        adb("shell", "am", "start", "-n", APP + "/de.mm20.launcher2.ui.launcher.LauncherActivity")
        time.sleep(2)
        # Dismiss the initial widget guide with the default downward gesture,
        # revealing the real home clock and search bar.
        adb("shell", "input", "swipe", "540", "550", "540", "1700", "500")
        time.sleep(2)
    try:
        if name == "01-launcher":
            tap_label("Skip")
        dump = wait_for_rendered_ui(name)
        if name == "16-tv" and tap_label("Accept"):
            time.sleep(12)
            dump = wait_for_rendered_ui(name)
        if name == "15-radio" and tap_label("Search"):
            if tap_label("Search", attribute="content-desc"):
                adb("shell", "input", "text", "BBC")
                adb("shell", "input", "keyevent", "KEYCODE_BACK")
                time.sleep(10)
            dump = wait_for_rendered_ui(name)
            if b"BBC" not in dump:
                tap_label("Collection")
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
    "launcher": "Home clock and search bar after the initial widget guide is dismissed.",
    "locale": "en-US", "theme": "System dark mode; default wallpaper blur; dark blue and burgundy wallpaper",
    "failures": failures, "screens": [name for name, _, _, _ in screens]}, indent=2))
(OUT / "logcat.txt").write_bytes(adb("logcat", "-d", "-s", "AndroidRuntime", "ActivityTaskManager", "Launcher", check=False).stdout)
if failures:
    raise RuntimeError("Screens requiring inspection: " + ", ".join(failures))

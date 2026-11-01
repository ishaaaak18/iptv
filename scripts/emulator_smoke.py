#!/usr/bin/env python3
"""Emulator smoke test: drives the debug APK through its main flow with adb.

Add playlist -> channel list -> play -> switch channel -> broken stream -> back.
Saves a screenshot and a UI dump per step in out/, plus logcat, and exits non-zero
on a crash or when an expected screen does not appear.
Expects testdata/ to be served on the host at port 8000 (seen as 10.0.2.2 by the emulator).
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "io.github.ishaaaak18.iptv.debug"
MAIN = f"{PKG}/io.github.ishaaaak18.iptv.ui.MainActivity"
PLAYLIST_URL = "http://10.0.2.2:8000/sample.m3u"
OUT = "out"
os.makedirs(OUT, exist_ok=True)
failures = []
step_no = 0


def adb(*args, check=True):
    r = subprocess.run(["adb", *args], capture_output=True, text=True)
    if check and r.returncode != 0:
        print("adb failed:", args, r.stderr)
    return r.stdout


def dump():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", check=False)
    xml = adb("shell", "cat", "/sdcard/ui.xml", check=False)
    start = xml.find("<?xml")
    if start < 0:
        start = xml.find("<hierarchy")
    try:
        # bytes, not str: ElementTree rejects str input that carries an encoding declaration
        return ET.fromstring(xml[max(start, 0):].encode("utf-8"))
    except Exception as e:
        print("ui dump parse error:", e, xml[:200].replace("\n", " "))
        return None


def nodes(root):
    return [] if root is None else list(root.iter("node"))


def label(n):
    return " | ".join(v for v in (n.get("text"), n.get("content-desc"), n.get("hint"), n.get("resource-id")) if v)


def find(text, timeout=15):
    deadline = time.time() + timeout
    while time.time() < deadline:
        for n in nodes(dump()):
            if text.lower() in label(n).lower():
                return n
        time.sleep(1)
    return None


def center(n):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def tap(text, timeout=15):
    n = find(text, timeout)
    if n is None:
        fail(f"element not found: {text!r}")
        return False
    x, y = center(n)
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(1)
    return True


def type_text(s):
    adb("shell", "input", "text", s.replace(" ", "%s").replace("&", "\\&"))
    time.sleep(1)


def shot(name):
    global step_no
    step_no += 1
    base = f"{OUT}/{step_no:02d}_{name}"
    adb("shell", "screencap", "-p", "/sdcard/s.png")
    adb("pull", "/sdcard/s.png", base + ".png")
    root = dump()
    lines = [f"{n.get('class')} [{label(n)}] {n.get('bounds')}" for n in nodes(root) if label(n)]
    with open(base + ".txt", "w") as f:
        f.write("\n".join(lines))
    print(f"-- screen {base}  (top: {resumed().strip()})")
    for l in lines[:25]:
        print("     ", l)


def resumed():
    out = adb("shell", "dumpsys", "activity", "activities")
    m = re.search(r"(?:mResumedActivity|topResumedActivity)[^\n]*", out)
    return m.group(0) if m else ""


def fail(msg):
    print("FAIL:", msg)
    failures.append(msg)


def check_alive(where):
    if PKG not in resumed():
        fail(f"app not in foreground after {where}: {resumed()}")


# ---------------------------------------------------------------- run
adb("logcat", "-c")
adb("shell", "am", "start", "-W", "-n", MAIN)
time.sleep(4)
shot("home_empty")
if find("No playlists yet", 10) is None:
    fail("empty home message missing")

tap("add_playlist")
shot("add_screen")
if tap("field_name"):
    type_text("Test streams")
if tap("field_link"):
    type_text(PLAYLIST_URL)
adb("shell", "input", "keyevent", "111")  # ESC: hide keyboard
time.sleep(1)
shot("add_filled")
tap("save")

if find("Big Buck Bunny", 30):
    shot("channels")
    if not find("3 channels", 5):
        fail("expected '3 channels' label")
else:
    shot("channels_missing")
    m = find("message", 2)
    fail("channel list did not appear" + (f" (message: {m.get('text')})" if m is not None else ""))

# search filter
if tap("search", 5):
    type_text("bip")
    adb("shell", "input", "keyevent", "111")
    time.sleep(2)
    shot("search_bip")
    if find("Big Buck Bunny", 2):
        fail("search did not filter out Big Buck Bunny")
    # clear search
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    for _ in range(5):
        adb("shell", "input", "keyevent", "67")
    adb("shell", "input", "keyevent", "111")
    time.sleep(2)

tap("Big Buck Bunny")
time.sleep(15)
shot("player_bbb")
check_alive("opening player")
if "PlayerActivity" not in resumed():
    fail("player activity not on top")

adb("shell", "input", "keyevent", "KEYCODE_CHANNEL_DOWN")
time.sleep(12)
shot("player_bipbop")

adb("shell", "input", "keyevent", "KEYCODE_CHANNEL_DOWN")
time.sleep(20)
shot("player_dead_stream")
check_alive("broken stream")

adb("shell", "input", "keyevent", "4")  # BACK
time.sleep(3)
shot("back_to_list")
check_alive("closing player")

# favorites
if tap("group_Favorites", 5):
    pass
adb("shell", "input", "keyevent", "4")
time.sleep(2)
adb("shell", "input", "keyevent", "4")
time.sleep(2)
shot("home_with_playlist")
if not find("Test streams", 5):
    fail("playlist not shown on home")

# relaunch: data must persist
adb("shell", "am", "force-stop", PKG)
adb("shell", "am", "start", "-W", "-n", MAIN)
time.sleep(4)
shot("after_restart")
if not find("Test streams", 10):
    fail("playlist not persisted across restart")

# Arabic UI (per-app locale, API 33+)
adb("shell", "cmd", "locale", "set-app-locales", PKG, "--locales", "ar", check=False)
adb("shell", "am", "force-stop", PKG)
adb("shell", "am", "start", "-W", "-n", MAIN)
time.sleep(4)
shot("arabic_home")

# ---------------------------------------------------------------- results
log = adb("logcat", "-d")
with open(f"{OUT}/logcat.txt", "w") as f:
    f.write(log)
player_lines = [l for l in log.splitlines() if "IptvPlayer" in l]
print("\n".join(player_lines[-30:]))
if "FATAL EXCEPTION" in log:
    i = log.index("FATAL EXCEPTION")
    print(log[i:i + 3000])
    fail("app crashed (FATAL EXCEPTION in logcat)")
if not any("state=3" in l for l in player_lines):
    fail("player never reached STATE_READY (3)")

print("\nRESULT:", "PASS" if not failures else "FAIL")
for f in failures:
    print(" -", f)
sys.exit(1 if failures else 0)

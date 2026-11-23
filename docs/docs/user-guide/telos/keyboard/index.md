# Keyboard

Telos Keyboard is an Android input method inside the Telos APK. Opening its settings does not itself make it the active keyboard.

1. Open Keyboard in Telos and tap **Open input settings**.
2. Enable **Telos Keyboard** in Android’s on-screen keyboard list.
3. Return to Telos and tap **Choose keyboard**, then select Telos Keyboard in Android’s chooser.
4. Focus a normal text field to open it. The language key switches Greek and English; numeric editors open the number layout.

**Look:** Keyboard settings include accent color, overall keyboard size, key height and key text size. Changes apply the next time the IME window opens.

Keys sit **above** the navigation/gesture bar; the panel color fills that bar. Suggestions use the system user dictionary when Android allows it, then Telos’ Greek and English lists. Swipe follows the keys the finger actually crosses.

Telos does **not** read Gboard’s private dictionary files. It can use the device spell checker / user dictionary APIs when the user has enabled them, and later Hunspell wordlists (LibreOffice/`el`/`en`) downloaded into Telos’ own storage.

Password/PIN and no-learning editors enforce private input independently of the manual incognito preference. Transient word and clipboard state clears when editors change. Such private input does not populate the suggestion buffer.

The IME keeps its own opaque input surface while using the launcher’s colors and typography. It displays an immediate Material fallback while theme preferences load and does not load wallpaper blur for the input window.

Validation on the affected OnePlus/Android 17 device remains outstanding.

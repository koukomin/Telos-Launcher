# Changelog

## 1.41.86

### Telos Media & Flow-Tube UI
- **Flow-Tube UI Redesign:** Fully adapted YouTube & YT Music interface to Flow-Tube specifications with rounded cards (`20.dp`), thumbnail duration tags, channel badges, and glass floating mini-player.
- **1-Click YouTube Playlist Import:** Paste any public or unlisted YouTube playlist link in the Account Panel to import all tracks into Telos Media in seconds.
- **Synced / Timed LRC Lyrics:** Real-time line-by-line scrolling lyrics (`SyncedLyricsSheet`) with active line highlighting and 1-tap seek to timestamp.
- **10-Band Equalizer & AudioFx Suite:** Integrated 10-Band `Equalizer`, `BassBoost`, 3D Spatial Audio `Virtualizer` (headset 3D surround effect), and `LoudnessEnhancer` audio normalization.
- **Mood Radio Builder & Quick Mix:** Generates custom radio queues based on track + selected mood (*Chill*, *Workout*, *Party*, *Focus*).
- **Sleep Timer with Volume Fade-Out:** Configurable sleep timer (`MediaSleepTimer`) with smooth 30s linear volume fade-out before pausing.
- **Ambient Artwork Palette Glow:** Dynamic background lighting glow derived from album artwork colors.

### Shizuku Auto-Start & System Automation
- **Bypass `FLAG_STOPPED` (Force-Stop Bypass):** Multi-stage background wake-up engine (ContentProvider ping, Foreground Broadcast, Service Poke, and Fallback Silent Activity launch) to automatically wake Shizuku on boot without PC/ADB.
- **Conditional Installation Handling:** Checks if Shizuku is installed. If missing, greys out the setting row and redirects to Telos Store pre-filtered to `"Shizuku"` with 1 tap.
- **Shizuku Diagnostic Dialog:** Live OS compatibility checklist, auto-grant button, and step-by-step background battery optimization guide.

### Telos Store & App Control Hub
- **App Control Hub for All Installed Apps:** Tapping any installed app in the "Εφαρμογές" tab opens its **App Details** page (Inure Analytics, Telos Firewall for Wi-Fi/Mobile Data, Root Component Blocker, Audio Focus Mixer, and Freeze policy).
- **Allow Version Downgrades (`-d` flag):** Toggle in Store Settings to allow installing updates even if package version code is lower than installed.

### Dialer, Calculator & UI Fixes
- **Responsive Dialer Layout:** Adaptive key sizing and font scale (`digitSp`, `sublabelSp`) in landscape and portrait for accessibility fonts without key clipping.
- **Contact Deletion Fix:** Uses `ContactsContract.Contacts.CONTENT_URI` to delete contacts permanently regardless of default dialer status.
- **YouTube Login Browser Fix:** Excludes `com.android.vending` (Play Store) from Custom Tabs to open your actual web browser (`https://music.youtube.com/`) directly.

### Themes, Documentation & Badges
- **Default Theme:** Set **"Colorful Grid"** as default launcher theme with transparencies disabled by default. Renamed "Telos" theme to **"Telos theme beta"**.
- **Documentation Badges & Banner:** Added 1-click **Obtainium Import Badge** (`badge_obtainium.svg`), **Get it on GitHub Badge** (`badge_github.svg`), and top **Keep Android Open** banner (`keepandroidopen.org`).
- **Complete Translations:** 100% complete localized Greek translations across all 1,762 string keys (`values-el/strings.xml`).

## 1.41.13

### Files
- Free and total space on internal storage, SD/USB and remote volumes that report a quota (Dropbox, Drive, OneDrive, WebDAV, SFTP, SMB when available).
- ISO 9660 / Joliet disc images open as folders. Unencrypted zip can be edited and saved inside the archive.
- Camera RAW (embedded JPEG) and HEIC/HEIF stills, HEVC/H.265 video, share one preview catalog with Telos Viewer.
- SMB by computer name, `.local`, IP or `smb://host/share`; nearby mDNS servers; share listing when the share name is empty.
- Long-press peek, picture filmstrip, disk-cached thumbnails. Tabs, two panes, transfer queue and batch rename.

### Viewer
- Immersive photo viewer with glass chrome, PDF text highlight, comics (CBZ/CBR/CBT/CB7), SVG/PSD/AI/CDR previews.

### Phone
- Full-bleed incoming contact photo, swipe-up Remind / Reject+SMS.
- Call recording: VOICE_CALL first, silence retry, Shizuku/root CAPTURE_AUDIO_OUTPUT.
- Call blocker (prefix, regex, outgoing).

### Media
- Listen / Watch / Live destinations, brightness and volume swipes, TV and radio live, blur theme.

### Store and appearance
- Glass Store cards, wrapping names, Play Store tab, AMOLED accent palettes.
- Magnifier and flashlight/morse/siren virtual apps (1.41.12).

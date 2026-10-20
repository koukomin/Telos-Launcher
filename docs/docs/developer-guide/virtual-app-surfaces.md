# Virtual-app surfaces

VirtualAppTheme in app/ui/component/VirtualAppBlur.kt supplies wallpaper-aware backgrounds through the launcher theme for TelosAppActivity and embedded virtual routes. New routes should use this wrapper rather than add an opaque root surface. Preserve contrast on media/night-mode surfaces. Use scrolling content with fixed critical actions, wrap labels, and adapt to available width rather than reduce the user font scale.

Camera owns its CameraX/Camera2 capture pipeline. Ermis owns native IMAP/SMTP transport, encrypted storage and mail UI. Terminal currently has an experimental bootstrap downloader/command runner; prefix relocation, persistent PTY and Android execution compatibility remain unimplemented. TerminalRuntimeArchive bounds expansion and rejects canonical-path escapes. Keep capability and provider limits explicit.

Ermis scopes a readable tonal palette inside the transparent virtual-app theme: cleared parent surface colors cannot be reused as mail-card or input backgrounds. Its root retains a small amount of wallpaper translucency; composer/reader pages use opaque surfaces. Android Email contact picking grants access only to the chosen data URI, read on Dispatchers.IO; it does not enumerate the full address book.

NextDNS is a route under the existing Network app, with its repository registered in networkModule. It uses the already pinned OkHttp dependency, cancellable bounded responses, fixed-origin requests and a separate Android Keystore vault in noBackupFilesDir. API credentials never appear in editor state or launcher preferences. The legacy Copilot standalone module is superseded rather than registered as a second virtual app. DNS selection remains an explicit operation on DnsController.

Ermis mailbox identity is (account, folder, UID), with UIDVALIDITY persisted per mailbox and legacy Inbox keys retained. MOVE fallback never expunges unrelated deleted messages. Forward references include source UIDVALIDITY and produce a bounded original-message attachment before SMTP. Passive HTML is sanitized with the already present Jsoup, stripped of active content/remote images, rendered with scripts/storage/file/content/network access disabled, and never persisted. Only a single message retains formatted HTML in memory. This local document component is not a webmail login or an external mail application.

ErmisCachePolicy also bounds all resident bodies together to 2,000,000 characters, retaining the selected body and at most 32,000 characters per other message. This avoids cumulative heap growth when many large messages are opened across folders; original messages remain on the server.

Ermis custom folder mutations validate full IMAP names, reject Inbox/control characters/wildcards and never overwrite existing targets. Rename rechecks server SPECIAL-USE metadata and refuses mailbox hierarchies; cached source rows and UIDVALIDITY are invalidated. Forward draft references follow the renamed leaf, retaining their independent UIDVALIDITY guard. Recent IMAP search uses server sender/subject/body terms within the existing 5,000-message/100-result bounds; the UI does not filter server matches using truncated local previews. Notification baselines include only Inbox messages, avoiding UID collisions with other folders.

SMTP completion preserves the original send result even if QUIT/disconnect fails; the send operation is never repeated automatically. Files FTP now uses a bounded passive-only session server in services:comms, rooted in SecureDirectoryStream handles with NOFOLLOW opens. Passwords are generated per session; upload opt-in uses CREATE_NEW to prohibit replacement. Foreground dialog lifecycle closes control/passive/data sockets. Integration tests use the already pinned Apache Commons Net FTP client as a test-only dependency.

Installed-app export uses a CreateDocument grant and performs package enumeration and bounded streaming copies on IO. InstalledApkExport preserves single APK bytes and writes every split to a ZIP using a 64 KiB buffer. Metadata is rechecked after export; failed or cancelled documents are deleted where supported. No bitmap list or whole APK buffer is retained. Communication JVM tests verify single APK identity and split bundle completeness.

Ermis messages can be marked unread from the message actions. The change is applied to the selected account, mailbox and UID on the IMAP server before updating the cache; unavailable messages cannot falsely appear updated.

## Shared cached frosted backdrop

FrostedGlassProvider in LauncherTheme is inherited by inline apps and shared across standalone Telos app surfaces. GlassWallpaperStore only processes explicitly selected home images, binds a <=512-pixel snapshot to WallpaperManager wallpaper ID, and rejects live/mismatched sources. Decode, three separable box passes and file IO run on IO; the service retains at most two ARGB frames (glass + optional secondary-page wallpaper radius), without recycling frames still used by Compose. Input is capped at 32 MiB / 100 MP. The thumbnail is atomically persisted and no source photo copy survives selection. API 26/27 EXIF orientation is handled explicitly.

FrostedGeometry defines one center-crop in window coordinates. Each surface subtracts its position in the window, clips to its own shape and draws the cached image plus exactly one requested tint, subtle highlight and border. Scrolling affects coordinates only, not decoding. Source capture and native window-blur calls were removed, along with the Haze runtime dependency. VirtualAppTheme prevents nested wrappers from adding a second full-window tint; explicit media/document content surfaces remain readable. Radius/color notifications are coalesced, and lifecycle resume/wallpaper colors revalidate the wallpaper ID. Pure JVM tests cover subsampling limits, blur invariants and portrait/landscape/split-window crop geometry. Real-device screenshot/performance validation remains outstanding.

The provider supplies pixels only; it never paints a full-screen layer over the home wallpaper. The existing wallpaper-blur preference is drawn only on secondary pages that do not own their glass backing, so enabling it does not blur the home wallpaper or add another layer under widget cards and inline apps.

Radius is converted from dp using the actual window-to-source center-crop scale, including portrait/landscape changes. Settings and Calendar avoid adding a second full-window backing; full-screen Ermis dialogs own exactly one shared backing in their own window. Photos Home/Both actions use the same snapshot writer as the wallpaper settings.

Material container-low/high roles retain their surface/elevated theme opacity for inner controls and cards; only full-window background/surface roles are cleared. Existing wallpaper target/action messages are completed in all supported locales when reused from Photos.


Telos video wallpaper glass samples only the service’s own rendered surface with asynchronous PixelCopy, at most 192 pixels per edge and eight updates per second (slower than video playback). One readback can be in flight across engines; CPU blur runs on Default and shares up to four radius/viewport profiles across active Activity providers. No player rebuild, screen capture, permission prompt or extra decoder is needed. Sampling stops when the wallpaper is hidden, playback pauses for battery/thermal policy, or no started glass provider requests frames; failures clear to translucent tint. Preview engines never publish. Compose reads the shared frame state during drawing, avoiding full-page recomposition on each frame. Static wallpaper caching remains unchanged. Physical-device visual/performance verification remains outstanding.


The home clock, transparent resting search bar and dock page dots have no automatic rectangular contrast backing. Wallpaper-aware foreground selection still runs, while the search bar keeps its explicitly selected style and active surface. Other widget/app glass panels and their contrast policy are unchanged. This also removes the extra backing that appeared when wallpaper sampling was unavailable on Android 13 and newer.


## Readable overlays, controls and search

Keep `surface`/`background` clear for root scaffolds, but preserve opaque
`surfaceContainer` for Material popup menus. Files and Phone drawers explicitly
use that role: wallpaper blur remains behind the app, while overlay labels do
not mix with underlying content. Playback buttons use paired
primaryContainer/onPrimaryContainer roles, never the transparent root surface as
foreground. Test both paused and playing TV mini player states.

Files workspace chrome uses separate rows for scrollable tabs and fixed actions,
so large fonts and narrow windows cannot squeeze a selected tab into a sliver.
Phone Recents search appends ContactSearch results after filtered recent calls in
one lazy list. It reuses ContactsViewModel privacy filtering, preferred numbers,
contact detail rows and existing call confirmation, without changing tabs.

Agent visual checks must include Greek labels, large system fonts, narrow and
wide windows, light/dark and animated wallpapers, and menus/drawers opened over
text. Preserve the no-plate home clock/search/dock-dot rule and single shared
blur backing documented above. Physical-device verification is still required.


## Process and package replacement continuity

PersistentUiState stores small tab selections independently of Android's optional
saved-instance bundle. Clock, Store, Phone, Radio, Music, Video, Photos, Stremio,
NextDNS and Network logs use it. PersistentNavigation checkpoints bounded (32
routes / 64 KiB) virtual-app navigation stacks in standalone and embedded hosts;
serialization and disk reads run on IO. Routes restore only under the same root,
without replacing navigation that changed during loading. Transient confirmation
routes are excluded; failed/incompatible snapshots fall back to the root. R8
retains NavKey class names for this lookup. This is navigation continuity, not a
snapshot of arbitrary ViewModels, playback sessions, dialogs or unsaved forms.

Files already persists open paths, selected tabs, split panes and active pane.
ClockAlarms persists alarm definitions before scheduling, and its receiver
reschedules on boot, package replacement, timezone/time changes and exact-alarm
authorization. Process death does not itself remove AlarmManager registrations.
Network persists wanted/off state, configuration and pause deadline; sticky VPN
restart, package replacement and activity resume restore it, subject to existing
VPN consent and the user's boot policy. Android force-stop/revoked permissions
are not equivalent to a recoverable crash. End-to-end update/crash checks on a
physical device remain outstanding.


## Recents task cleanup

SettingsActivity is a compatibility entry point for virtual apps. After forwarding to TelosAppActivity it removes a root redirect task, including when the redirect arrives through onNewIntent. A non-root redirect finishes only itself. Virtual-app document launches do not request RETAIN_IN_RECENTS, and explicitly finishing a root TelosAppActivity removes its document task. This prevents a blank Settings card from replaying the original virtual-app launch. Unfinished tasks still retain normal Android process-recreation and navigation recovery. Device validation of swipe-away, Back and re-opening from Recents remains required.


## Media control visibility and background playback

TV toggles controls over its full-window gesture surface and synchronizes system bars with controls. Local/stream video owns the single-tap gesture sequence across the full PlayerView, including portrait letterbox regions, without double toggling through native ACTION_UP. Existing double-tap, seek, volume and brightness gestures remain. Video controller visibility also drives system bars.

TV upgrades legacy pause-on-leave settings once to background playback; subsequent explicit opt-outs are saved with backgroundPolicyVersion=1. Video uses notification IDs 7405/7406 (normal/isolated process); Music, Radio, TV and YouTube already use distinct 7401–7404 IDs and MediaSession services. Actual Android media-card display still depends on playback/session state and system notification settings.

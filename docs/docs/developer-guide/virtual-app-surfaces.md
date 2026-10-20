# Virtual-app surfaces

VirtualAppTheme in app/ui/component/VirtualAppBlur.kt supplies wallpaper-aware backgrounds through the launcher theme for TelosAppActivity and embedded virtual routes. New routes should use this wrapper rather than add an opaque root surface. Preserve contrast on media/night-mode surfaces. Use scrolling content with fixed critical actions, wrap labels, and adapt to available width rather than reduce the user font scale.

Camera owns its CameraX/Camera2 capture pipeline. Ermis owns native IMAP/SMTP transport, encrypted storage and mail UI. Terminal remains an installer/opener for the external official Termux package. Keep capability and provider limits explicit.

Ermis scopes a readable tonal palette inside the transparent virtual-app theme: cleared parent surface colors cannot be reused as mail-card or input backgrounds. Its root retains a small amount of wallpaper translucency; composer/reader pages use opaque surfaces. Android Email contact picking grants access only to the chosen data URI, read on Dispatchers.IO; it does not enumerate the full address book.

NextDNS is a route under the existing Network app, with its repository registered in networkModule. It uses the already pinned OkHttp dependency, cancellable bounded responses, fixed-origin requests and a separate Android Keystore vault in noBackupFilesDir. API credentials never appear in editor state or launcher preferences. The legacy Copilot standalone module is superseded rather than registered as a second virtual app. DNS selection remains an explicit operation on DnsController.

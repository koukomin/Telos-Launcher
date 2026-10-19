# Virtual-app surfaces

VirtualAppTheme in app/ui/component/VirtualAppBlur.kt supplies wallpaper-aware backgrounds through the launcher theme for TelosAppActivity and embedded virtual routes. New routes should use this wrapper rather than add an opaque root surface. Preserve contrast on media/night-mode surfaces. Use scrolling content with fixed critical actions, wrap labels, and adapt to available width rather than reduce the user font scale.

Camera owns its CameraX/Camera2 capture pipeline. Ermis owns native IMAP/SMTP transport, encrypted storage and mail UI. Terminal remains an installer/opener for the external official Termux package. Keep capability and provider limits explicit.

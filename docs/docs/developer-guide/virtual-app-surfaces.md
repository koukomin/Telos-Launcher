# Virtual-app surfaces

VirtualAppTheme in app/ui/component/VirtualAppBlur.kt supplies wallpaper-aware backgrounds through the launcher theme for TelosAppActivity and embedded virtual routes. New routes should use this wrapper rather than add an opaque root surface. Preserve contrast on media/night-mode surfaces. Use scrolling content with fixed critical actions, wrap labels, and adapt to available width rather than reduce the user font scale.

Camera currently delegates explicit capture to Android, Ermis delegates mailbox UI to webmail, and Terminal installs/opens the official external Termux package. Do not document these as native engines.

# Virtual-app surfaces

VirtualAppTheme in app/ui/component/VirtualAppBlur.kt supplies wallpaper-aware backgrounds through the launcher theme for TelosAppActivity and embedded virtual routes. New routes should use this wrapper rather than add an opaque root surface. Preserve contrast on media/night-mode surfaces. Use scrolling content with fixed critical actions, wrap labels, and adapt to available width rather than reduce the user font scale.

Camera owns its CameraX/Camera2 capture pipeline. Ermis owns native IMAP/SMTP transport, encrypted storage and mail UI. Current main supplies Terminal with an on-demand bootstrap downloader and bash command UI; this pass does not certify runtime/package compatibility. Keep capability and provider limits explicit.

Use the [adaptive UI contract](./adaptive-ui) for shared tokens, preference/navigation components,
font scaling, insets and required validation. See [UI validation](./ui-validation) for execution
evidence; shared host changes are not proof that every hardware/editor state was visually tested.

# Telos Flat Adaptive icon design — mandatory specification

All AI agents must follow this guide for new and updated Telos virtual-app icons.

- Flat, contemporary, simple geometric vector shapes; match the quality and visual weight of the default launcher icon.
- Use a shared palette of solid colors (generally 2–3 per icon), consistent optical weight, corner radius, alignment and generous negative space.
- No skeuomorphism, 3D, photographic textures, bevels, glossy effects, complex gradients or cast shadows.
- Author Android adaptive foreground/background and a true monochrome themed-icon layer; never bake a circle or rounded square into the foreground.
- Use a 108×108 dp adaptive viewport and keep all essential shapes inside the central 66×66 dp safe region.
- Test all icons at 24, 32, 48, 72 and 108 dp under circle, square, rounded-square, squircle and OEM masks, with light/dark/colorful wallpapers.
- Each app needs a distinct, original silhouette; avoid third-party brand logos or lookalikes. Provide accessible contrast and avoid small text and fragile strokes.
- Use vector source of truth (SVG or VectorDrawable). Export raster only as needed. Provide a contact sheet of all icons under multiple masks before acceptance.
- Apply user-selected icon pack or custom icon consistently in dock, app drawer, search, widgets and Dialer sidebar where launcher controls the icon; preserve fallback to Telos icons.
- Suggested motifs: Dialer handset; Contacts paired silhouettes; Messages overlapping speech bubbles; Files folder; Media play/wave; Camera lens; Store download module; Ironewolf browser original compass; Tor/WireGuard differentiated routing/shield symbols.
- Include before/after previews and test evidence in PR. Do not claim icon redesign implemented by documentation alone.

## Dialer acceptance criteria

Show a large, prominent contact photo on incoming, outgoing and in-call screens, with a graceful initials fallback. Preserve readable name, number, call state and accessible buttons. Adapt to tablets, landscape, compact screens, insets and large fonts. Do not break Telecom behavior or permissions. Test with/without photos and all call states.

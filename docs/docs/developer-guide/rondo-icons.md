# Rondo icons for new Telos apps

Every new virtual app ships a **Rondo** icon in the same change as the app
code. The visual spec, XML templates and registration list live in
[ICON_DESIGN.md](/ICON_DESIGN.md).

Canonical references in the tree:

- `core/base/src/main/res/drawable/ic_app_phone_full.xml`
- `core/base/src/main/res/drawable/ic_app_files_full.xml`
- `core/base/src/main/res/drawable/ic_app_messages_full.xml`

Copy those files. Do not copy Store, Weather, Network, YouTube or Clock for new
work.

## Agents must produce

1. `ic_app_<slug>_full.xml` — 48 dp candy disc + 45° `#20000000` shadow + white glyph
2. `ic_app_<slug>_fg.xml` — 108 dp white glyph only (notifications / adaptive)
3. `ic_glyph_<slug>.xml` — 24 dp white glyph
4. Wiring in `TelosAppIcons`, `VirtualAppAppearance.glyph`, labels, deep link,
   manifest `android:icon`, Store catalog

A new app without this set is incomplete. Use `VirtualAppAppearance` for
notification small icons; never pass the coloured `_full` tile as
`setSmallIcon`.

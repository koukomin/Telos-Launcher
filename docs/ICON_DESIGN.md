# Telos Rondo Flat Adaptive icon specification

This document is binding for every new or changed Telos virtual-app icon.
Copy the geometry from `ic_app_phone_full.xml`, `ic_app_files_full.xml` and
`ic_app_messages_full.xml`. Those three are the canonical Rondo pack.

## What Rondo looks like

Each launcher tile is a **flat candy disc**:

1. A perfect **filled circle** in one saturated Material colour.
2. A **single 45° long-shadow plane** (`#20000000`) from the glyph toward the
   bottom-right rim. It is a flat silhouette, not a blur or drop shadow.
3. One **white 24 dp Material glyph**, scaled and centered on the disc.

At a glance the drawer should look like a set of round, high-chroma stickers
with the same shadow direction and the same glyph weight. Icons that fail this
test do not ship.

Do **not** copy Store, Weather, Network, YouTube or Clock as a template for new
apps. Those tiles use a rounded square, extra colours, or no long shadow.
Leave them as they are; new work follows Phone / Files / Messages.

## Forbidden

- Gloss, skeuomorphism, faux-3D, inner bevels, Gaussian / stacked drop shadows
- Gradients on the disc or the glyph
- Official third-party brand marks (YouTube play-button red, etc.)
- Baking squircle / rounded-rect / OEM mask shapes into `_full` or `_fg`
- Tiny detail that disappears at 48 dp
- A different shadow angle per app
- Using `Icons.AutoMirrored` / Material Icons Extended instead of these vectors
- A coloured `_full` tile as a notification **small** icon (Android masks it)

## Three files per app

Slug is the virtual-app id without the `telos_` prefix and `_app` suffix,
underscored (`phone`, `screen_recorder`, `youtube_music`).

All three live in `core/base/src/main/res/drawable/`.

| File | Size | Role |
| --- | --- | --- |
| `ic_app_<slug>_full.xml` | 48×48 viewport 48 | Drawer, search, Recents large icon, Store catalog |
| `ic_app_<slug>_fg.xml` | 108×108 viewport 108 | Adaptive / notification foreground (white glyph only) |
| `ic_glyph_<slug>.xml` | 24×24 viewport 24 | Status-bar small icon, in-app chrome |

The **same** 24 dp `pathData` is reused in all three. Only the transform
changes.

### Canonical `_full` (copy this)

Reference: `ic_app_files_full.xml`.

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="48"
    android:viewportHeight="48">
    <!-- 1. Flat circular disc. Change only fillColor. -->
    <path
        android:fillColor="#FF9800"
        android:pathData="M24,2A22,22 0 1,0 24,46A22,22 0 1,0 24,2Z" />
    <!-- 2. 45° long shadow. Keep this path and alpha. -->
    <path
        android:fillColor="#20000000"
        android:pathData="M14,14 L32,32 L26,44 L10,28 Z" />
    <!-- 3. White Material glyph, 24dp art scaled 1.25 and offset 9,9. -->
    <group
        android:translateX="9"
        android:translateY="9"
        android:scaleX="1.25"
        android:scaleY="1.25">
        <path
            android:fillColor="#FFFFFFFF"
            android:pathData="M10,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z" />
    </group>
</vector>
```

- Disc path is always `M24,2A22,22 0 1,0 24,46A22,22 0 1,0 24,2Z` (circle
  inset 2 units so the rim survives OEM masks).
- Shadow path is always `M14,14 L32,32 L26,44 L10,28 Z` at `#20000000`.
- Glyph group is always `translate 9,9` + `scale 1.25`. That keeps the 24 dp
  icon inside the adaptive safe zone (~66 % of the canvas).

### Canonical `_fg` (white glyph only)

Reference: `ic_app_files_fg.xml`. No disc. No shadow. Android / Telos supplies
the background colour for adaptive icons.

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group
        android:translateX="30"
        android:translateY="30"
        android:scaleX="2"
        android:scaleY="2">
        <path
            android:fillColor="#FFFFFFFF"
            android:pathData="M10,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z" />
    </group>
</vector>
```

### Canonical `_glyph`

Reference: `ic_glyph_files.xml` / `ic_glyph_phone.xml`.

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M10,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z" />
</vector>
```

## Glyph rules

- One filled silhouette from the Material Icons 24 dp set (filled, not outlined).
- White `#FFFFFFFF` on the disc. Dark-on-amber (Flashlight) is an existing
  exception; do not introduce more dark glyphs.
- Prefer a metaphor that reads at 24 dp: phone handset, chat bubble, folder,
  camera body, note, calculator grid.
- At most two path elements. No text, no letters of the app name, no photos.

## Colour

Pick **one** unused saturated Material colour for the disc. Neighbours in the
drawer must stay distinguishable.

| App | Disc |
| --- | --- |
| Phone | `#4CAF50` |
| Radio | `#2E9E3F` |
| Messages | `#00897B` |
| Voice recorder | `#00838F` |
| YouTube | `#00796B` |
| Sync | `#00A896` |
| Network | `#0B6B4D` |
| Calendar | `#1E88E5` |
| Screenshot | `#1A6DFF` |
| Viewer | `#3F6BE8` |
| Store | `#1E69B8` |
| Weather | `#0288D1` |
| Backup / Magnifier | `#3F51B5` |
| Downloads / Media | `#5E35B1` |
| Music / YouTube Music | `#7B2FC9` |
| Akou | `#8E24AA` |
| Camera / Photos | `#E91E63` |
| Ermis | `#D93025` |
| Trash / Video | `#E53935` |
| Screen recorder | `#E64A19` |
| Files | `#FF9800` |
| Flashlight | `#FF9F00` |
| Notes | `#F9A825` |
| Calculator | `#37474F` |
| System | `#102A43` |
| Clock | `#1F1A3A` |
| TV | `#3949AB` |

For a **new** app, choose a colour that is not already in this table, or a
clearly darker/lighter sibling of a related app (example: a new mail tool next
to Ermis uses a distinct red, not `#D93025`).

## Cyberpunk / Neo-Candy (settings toggle only)

When that icon theme is on:

- Disc `#121318`
- Glyph stroke neon (`#00F2FE`, `#818CF8` or `#FF007F`), `strokeLineCap` and
  `strokeLineJoin` round
- Default Rondo `_full` files stay the light candy discs. Do not replace them.

## Registration (same change as the XML)

A drawable that is not wired is invisible after R8 resource shrinking.

1. `TelosAppIcons.forDomain` → `R.drawable.ic_app_<slug>_full`
2. `VirtualAppAppearance.glyph` → `R.drawable.ic_app_<slug>_fg` (small
   notification icon). Never point this at `_full`.
3. `TelosAppLabels` + `TelosAppKeywords`
4. `SettingsDeepLinkContract.virtualAppTaskId` and SettingsActivity task map
5. Manifest `android:icon="@drawable/ic_app_<slug>_full"` on the activity-alias
6. Store catalog `TelosApps.kt` `iconRes`
7. User-guide page + this catalog table + README feature list if the app is new

## Agent workflow for a new virtual app

1. Read this file and open `ic_app_phone_full.xml` / `ic_app_files_full.xml`.
2. Invent slug + disc colour + one 24 dp filled glyph.
3. Write the three XML files by copying the templates; only `fillColor` on the
   disc and `pathData` on the glyph change.
4. Register the resources in the list above in the **same** change as the app.
5. Confirm drawer, search, Recents, notification shade (small + large), Store
   row and the activity-alias icon all resolve to these drawables.
6. Honor a user-selected icon pack on launcher-controlled surfaces.
7. Update this catalog, the user-guide app page, and translations for the label.

Do not mark the app complete without the three drawables and the registration
list. Compilation without icons is not done.

## Visual QA

Check the new tile next to Phone, Files and Messages on light and dark
wallpaper, at dock size and at search-result size. The disc, shadow direction
and glyph weight must match. Circle / squircle / rounded-square masks are
applied by the system on `_fg`; `_full` is already a circle and must stay one.

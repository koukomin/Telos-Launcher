# Color Schemes

Color schemes can be managed at settings > appearance > color schemes.

## Default

The default color scheme is different depending on the Android version:

- On **Android 12+**: the default color scheme is the color scheme provided by the system, usually based on the wallpaper (“Material You”).
- On **Android 8.1 - Android 11**: the default color scheme is based on the current wallpaper (“Material You Compat”)
- On **Android 8.0**: the default color scheme is a blueish fallback theme because the APIs to extract wallpaper colors do not exist on this version. This is also used on Android 8.1 - Android 11 if the wallpaper color extraction fails for some reason.

## Black and White

A high contrast theme using only black and white.

## Custom color schemes

Customize the launchers color scheme to your likings. You can have as many color schemes as you want.
Create a new color scheme by clicking the <span class="material-symbols-rounded">add</span> button in the bottom right corner, or by
selecting <span class="material-symbols-rounded">more_vert</span> > <span class="material-symbols-rounded">content_copy</span> **Duplicate** on an existing color scheme.

You can then edit a color scheme by selecting <span class="material-symbols-rounded">more_vert</span> > <span class="material-symbols-rounded">edit</span> **Edit**.

Telos uses the Material Design 3 system for its user interface. The color schemes are therefore
based on the Material Design 3 color system.

> [!INFO]
> For a detailed explanation of the color system, see https://m3.material.io/styles/color/the-color-system

### Key colors

A color scheme is defined by a set of key colors. These key colors are not used on their own, but
they are used to generate the rest of the color scheme. The key colors are:

- **Primary**: mainly used for interactive key components, like buttons, switches and input
  fields. Also used for [themed icons](/docs/user-guide/customization/themed-icons) and as elevation overlay color (cards, dialogs and other elevated
  surfaces will have a slight tint of this color)
- **Secondary**: used for less prominent components, such as chips, badges and some (non interactive) headlines
- **Tertiary**: not used anywhere at the moment, reserved for future use
- **Neutral**: used as background for cards, dialogs, settings, and other surfaces. Also used for text and some icons
- **Neutral variant**: used for banners, switch and slider tracks and for outlines
- **Error**: used to indicate errors, like invalid inputs in text fields

Key colors can be edited in the "color palette" section in the first row of the color scheme editor.
For each key color, you can select a custom color, or you can use the system default, which is
usually based on the wallpaper.

Material Design 3 comes with an algorithm to generate an entire key color palette based on a single
seed color (this is what the system uses to generate an entire color palette based on a single
wallpaper key color). To make use of this algorithm, each key color (except primary) has
a <span class="material-symbols-rounded">auto_fix_high</span> **From primary** button, which generates a new color based on the currently
selected primary color.

### Scheme colors

Scheme colors are colors that are derived from the key colors and that are used for actual UI
components.

> [!INFO]
> To derive these colors, Material 3 uses a color model called HCT (Hue, Chroma, Tone). In its core,
> it is similar to the probably more commonly known HSL (Hue, Saturation, Lightness) color model, but it
> better reflects how humans perceive colors. For more information, read https://material.io/blog/science-of-color-design
>
> Each scheme color is generated from a specific key color by changing the key color's tone.

To learn how the different scheme colors are used in components, refer to the
[Material 3 Design docs](https://m3.material.io/styles/color/the-color-system/color-roles).
Examples are shown in the color scheme editor. Keep in mind, that not all scheme colors are currently
used by Telos.

> [!TIP]
> Long press a color to display its name.

For each scheme color, there is one variant for light mode, and one for dark mode. To toggle between
light and dark mode variants, use the <span class="material-symbols-rounded">light_mode</span> and <span class="material-symbols-rounded">dark_mode</span> buttons in the top right corner
of each section.

For each scheme color, you can either select a custom color, or you can derive a color from a key color.
To derive a color from a key color, select <span class="material-symbols-rounded">palette</span> **Palette**, and then select the key color
to derive from. You can then adjust the tone of the derived color by dragging the **T** slider.


## Wallpaper readability

In **Home screen > Wallpaper > Wallpaper text**, choose **Automatic**, **White**, or **Black**.
Automatic compares the worst sampled light and dark pixels with black and white text using
relative luminance and a 4.5:1 normal-text contrast target. It does not use the average wallpaper
color as proof that a particular label will be readable.

The clock and dynamic zone, home favorite labels, transparent resting search bar, page indicators
and transparent external-widget backings get a local protective scrim when the background is
mixed or cannot be read. White text uses a 65% black scrim; black text uses an 80% white
scrim when needed. Explicit clock colors and custom grid label colors retain priority. Custom
label colors and the clock's **Use theme color** option can reduce contrast; these remain explicit
user choices. Third-party widgets can draw their own text and backgrounds, so their internal
content cannot be recolored reliably by the launcher.

The default transparency scheme now uses **80% opacity** for backgrounds, surfaces and elevated
surfaces. The alternative semi-transparent scheme and saved custom schemes keep their values.
The sliders show the same 80% fallback as the renderer; an explicit value, including zero, is
not increased according to wallpaper hints. Turning off widget glass uses solid widget cards.
Shared launcher cards select text and icons from the final alpha-composited surface, including
nested shared cards, rather than using the wallpaper text choice. Very low custom opacity can
still reduce contrast on complex wallpapers.

### Android and live wallpaper limits

Android 13 and later restrict access to wallpaper image pixels and can return a default image
instead of the active wallpaper. Telos deliberately does not analyze that default image, does
not request broad file access or screen capture, and uses a stable scrim instead. The global
palette supplied by Android still supplies theme colors, but is not treated as local pixels.

Android 8.0 also uses the fallback because it has no wallpaper-colors change callback.

On Android 8.1–12, local sampling is attempted only if the existing external-storage read
permission is already granted, a static wallpaper file is available and its aspect ratio matches
the window. Cropped/wide scrolling images are rejected rather than guessing their viewport.
Blur and wallpaper dimming also use the fallback because the raw image no longer represents
the final screen pixels. All live/video/GIF wallpapers use the fallback; there is no continuous
frame capture. OEM cropping/zoom and tiny image details remain reasons to check readability on
a device. Samples are approximate, not a pixel-perfect accessibility certification.

Sampling and luminance analysis happen off the UI thread. Only one small grid of luminance
ranges is cached, keyed by wallpaper ID, viewport and permission. Repeated resume/color events
reuse it; new wallpapers retain the previous text polarity with a matching protective backing
while sampling is pending. Position changes retain the previous text polarity with a protective scrim until geometry has
settled for 150 ms, then select cached cells without decoding images. Text colors are not
animated through low-contrast gray.


## Window and text size

The launcher uses the active window dimensions, including split-screen changes. Search and filter
bars reserve their measured content height. Shared settings rows move controls below long text
when necessary; settings content stays centered on wide windows. Telos Comms and Media navigation
labels can wrap. Files shows the active tab when two readable panes do not fit. Calculator keys,
clock values and the calendar week can scroll rather than reduce important text to fit.

These adaptations retain Android text/display scaling and the launcher font setting. Saved grid
counts remain preferences; fewer columns may be displayed in a narrow window. Device validation
and remaining limits are tracked in the [UI validation record](../../developer-guide/ui-validation).

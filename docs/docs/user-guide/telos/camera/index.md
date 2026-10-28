# Telos Camera

![Telos Camera](https://raw.githubusercontent.com/koukomin/Telos-Launcher/main/docs/public/img/telos-development-24-camera.webp)


Camera opens a native Telos viewfinder. Photos and videos are captured inside Telos using CameraX; there is no external camera handoff. The dark, responsive control layout places the mode strip, zoom and capture action next to the preview in landscape and beneath it in portrait.

## Capture

- Photo: JPEG, PNG or WebP. JPEG can retain camera EXIF metadata; re-encoded PNG/WebP remove metadata. Captures are saved in Pictures/Telos. Still capture targets approximately 5 MP to keep conversion memory bounded; the hardware chooses its nearest supported resolution. PNG/WebP conversion also samples oversized OEM images before decoding to remain within 5 MP.
- Video: native FHD recording with a supported-quality fallback, optional microphone audio and torch where available. Videos are saved in Movies/Telos. Leaving the foreground stops recording.
- Focus: tap the preview. Zoom: pinch or use the zoom slider. Switch between front and rear cameras with the camera selector.
- Settings: flash, a 3/5/10-second timer, composition grid, live luminance histogram and exposure compensation. The histogram is available in the standard photo mode when the camera supports the additional analysis stream.
- Manual sensor exposure: ISO and shutter duration controls appear only when the selected camera advertises manual-sensor support. The camera's own supported ranges constrain both controls.
- Portrait and night: shown only when the device supplies the corresponding CameraX OEM extension. Telos does not emulate a proprietary computational photography engine.

## RAW and panorama

On cameras advertising RAW capability, the RAW option captures DNG and JPEG through a separate one-shot Camera2 session, then restores the viewfinder. RAW uses that session's automatic exposure, rather than the CameraX manual settings. Hardware-dependent RAW behavior requires device testing.

The panorama option assembles 2–8 manually aligned left-to-right captures with 20% overlap. Keep the horizon level and overlap consistently. The bounded local stitcher streams source files, downsamples each to at most 1024 pixels on its longest edge, limits the combined image to 8 MP, and blends seams; it does not perform computer-vision alignment or spherical correction. Original slices remain saved.

## Discreet preview and LAN webcam

Discreet preview blacks out the viewfinder while retaining explicit capture controls. Android's privacy indicators remain visible. Camera use stops with the screen's lifecycle; Telos does not silently record in the background.

Start IP webcam explicitly in settings to obtain a token-bearing local-network URL. Anyone on that local network with the URL can view its MJPEG feed. Stop it with the matching control. Sharing also stops when Camera leaves the foreground. This is a bounded-resolution LAN feed, not an Internet relay or USB gadget implementation.

Optional geotagging adds an available recent location fix to JPEG EXIF only (up to 10 minutes old, accuracy within 500 m). It requests location permission when selected. PNG/WebP and EXIF stripping remove location metadata.

Hidden album saves still images under Telos’s app-specific external Pictures/TelosHidden directory with a .nomedia marker. The gallery does not index it; app uninstallation removes it. RAW and video continue to use their normal destinations. Panorama originals remain in the normal gallery.

Foreground automatic capture can be enabled explicitly for camera-reported face detection (only if supported) or preview luminance changes. Both have a five-second cooldown and stop outside the foreground. Motion detection can react to lighting changes. Android device policy can also impose shutter behavior. No option claims to bypass those restrictions.

## QR, barcodes and document scans

Standard Photo mode recognizes QR, Aztec, Data Matrix, PDF417 and common retail/linear barcodes when the camera supports an analysis stream. Recognition pauses outside the foreground and while a result is open. Copy a result or explicitly open an HTTP(S) address; scanning never executes a payload automatically.

Choose **Scan to PDF** to capture pages privately, or use the document button to import images. The review screen supports up to 30 pages, reordering, removal, quarter-turn rotation, grayscale and symmetrical horizontal/vertical edge trimming. The crop sliders do not perform perspective correction. Export uses Android’s Save as picker and processes one image at a time at a bounded resolution.

Select **Searchable text (OCR)** for a searchable PDF, or choose an image-only PDF. Up to three recognition languages can be combined, including Greek and English. Pinned Tesseract fast language models download on first use after selection; documents stay on the device. Network access is required for the initial model download. Models are checked against pinned upstream Git blob hashes before use. OCR quality depends on lighting, focus and document layout. Temporary scan sessions are private cache data and may be removed by Android; export important scans promptly. Sessions older than seven days or exceeding the retained-session budget are pruned.

Compilation and emulator tests for image-only multipage PDF and Greek/English searchable PDF passed. Physical camera/OEM behavior has not been verified. At large font sizes or short window heights, capture, import and save use compact labeled icon controls.

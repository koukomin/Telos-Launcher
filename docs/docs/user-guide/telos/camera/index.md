# Telos Camera

A virtual camera app with a OnePlus 9 / Hasselblad-style viewfinder: photo, video, portrait, night, pro,
discreet capture, optional DNG, and an IP webcam for OBS or a browser.

::: tip At a glance
Gold Hasselblad wordmark, a mode rail (Night / Video / Photo / Portrait / Pro / Pano / Discreet),
zoom pills, a large shutter, tap to focus, pinch zoom, histogram and grid. Photos land in `DCIM/TelosCamera`.
:::

## What it is

Telos Camera is a [virtual app](../#how-the-built-in-apps-work). It uses Camera2 (the same stack as
[CamX](https://github.com/uncannyRishabh/camx) and [Open Camera](https://opencamera.org.uk/)).
Switching it off in [Telos Store](../store/) hides its icon.

## Modes

| Mode | What it does |
| --- | --- |
| Photo | JPEG / PNG / WebP stills, optional DNG RAW |
| Video | H.264 1080p30 with microphone |
| Portrait | Camera2 portrait scene mode when the hardware has it |
| Night | Camera2 night scene mode |
| Pro | EV compensation, live ISO and shutter readout, histogram |
| Pano | Capture several slices with the shutter, then Stitch to a wide JPEG |
| Discreet | Black viewfinder, tap to shoot, optional face and motion auto-shot, silent shutter, hidden `.nomedia` album |

## Webcam

**IP webcam** serves MJPEG on the LAN (default port 8088). Open the URL in a browser or as an OBS
browser source. This covers the same job as [Open Camera OMT](https://github.com/ysdede/opencamera-omt)
without bundling the OMT native libraries.

**USB webcam:** Android has no public API to become a USB UVC gadget. That path exists only when the
OEM kernel exposes it (ColorOS USB webcam on devices such as the OnePlus 15). Telos cannot invent
that gadget. Use IP MJPEG on every device.

## Privacy

EXIF is kept by default. Turn **Keep EXIF** off in the camera sheet to strip it. GPS tagging stays off until you turn it on.
There is no auto-email, no cloud, and no hidden app icon for spying on other people. Discreet mode
is for the phone owner (candid shots, a black screen while you still hear the shutter off).

## Honest limits

- No Photon Camera LUT engine, Depth Anything bokeh, multi-frame stack or vendor motion photos.
- No Open Camera auto-level crop or focus bracketing. Panorama is a simple side-by-side stitch, not OpenCV blending.
- Face auto-shot uses Camera2 face statistics, not a separate ML model.
- Background capture with the screen off needs the camera foreground service notification.

## Based on

Feature ideas from Open Camera, Libre Camera, CamX, Photon Camera, Open Camera OMT and Spy Camera OS.
No source was copied from those projects.

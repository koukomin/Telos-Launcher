# Ermis and Camera: recovered requirements

Source: the repository's main-branch user guides as of 0c9b792012d35f4e504aacc266ba3f44c7785328. These are documentation claims, not proof of implementation or a recovered Gemini transcript. The original Gemini prompt was not found in available personal context.

User clarification, 19 October 2026: both apps must be native Telos implementations. Webmail and external camera handoff are rejected. Camera uses the OnePlus 9 control layout only as a visual reference, with Telos branding and no vendor wordmark.

## Ermis

- Functional reference: FairEmail. Layout reference: Gmail. Per-message note reference: Simple Gmail Notes.
- Multiple IMAP/SMTP accounts with verified TLS, Gmail app passwords and Google/microG OAuth where available; Android Keystore protection for credentials.
- Inbox, conversation grouping, starred messages, search, read and compose/send.
- Background new-mail checks and notifications.
- Attachments from local documents and Telos Files/cloud connections; explicit large-file link workflow.
- Local message notes; Gmail sidecar notes in Ermis-Notes keyed by Message-ID.
- Responsive Telos glass theme; implementation must not claim features only described in this checklist.

## Camera

- Feature references: Open Camera, Libre Camera, CamX, Photon Camera, Open Camera OMT and Spy Camera OS. No source-copy authorization is inferred from a feature-reference list; incompatible licenses must not be incorporated.
- Native preview/capture, front/rear selection, tap focus, pinch zoom, zoom shortcuts, flash, timer, grid and histogram.
- Photo formats JPEG/PNG/WebP, DNG when the camera supports RAW, EXIF controls and optional geotagging.
- Native video with microphone, portrait/night only where available, manual exposure/ISO controls and capability-based availability.
- Panorama capture/stitching; discreet black preview, owner-triggered/face/motion capture, optional hidden album and shutter control subject to device restrictions.
- Explicit LAN IP webcam; USB gadget support depends on the operating system and cannot be promised by an ordinary app.
- Foreground-service/lifecycle handling and truthful hardware limitations. No proprietary computational photography engine is implied.

## Release gate

PR #41 remains unmerged while the native implementation scope is completed. Existing Network/call/media/Clock fixes remain in the same PR. Device-dependent camera behavior requires physical-device testing beyond a successful build.

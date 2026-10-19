# Virtual-app implementation audit — 19 October 2026

This is a source audit, not a physical-device certification. Git records commit authors; they do not prove which AI tool produced a change. Findings below must not be attributed to Gemini without the original development transcript.

| Area | Verified finding | Current action |
| --- | --- | --- |
| Akou | The old listening path only delayed 3.5 seconds. It never started AudioRecord. A GET to a sample endpoint fell back to a fabricated “Telos Acoustic Melody / Telos Artists” success result. | Removed the sample network call, invented result, pretend listening and microphone prompt. The app now explicitly states that recognition is not implemented. Store no longer advertises Shazam/Audile support. |
| Clock alarms on main | Alarms were a remembered Compose list with no AlarmManager scheduling or durable storage. Audio selection did not provide an actual file/radio picker. | PR #41 adds persisted one-shot scheduling, boot restoration, real sound selection and a playback service. Hardware/permission behavior still needs device validation. |
| Ermis | The repository documented an IMAP/SMTP client but contained no native mailbox engine or route. | Native implementation required; temporary webmail hub rejected by the user. Merge held. |
| Camera | Camera dependencies and mode UI existed, but capture launched another camera app. Documentation described engines that were absent. | Native implementation required; external capture rejected. Vendor branding removed. Merge held. |
| Terminal | No executable embedded Termux runtime. The current implementation downloads/installs/opens official Termux. | Treat as an external companion, never claim embedded compatibility. |
| System processes | Running process names/PIDs come from ActivityManager, with Android visibility restrictions, but per-process memory had been hardcoded to zero. | Changed missing memory to an explicit unknown dash. System-wide RAM is read from ActivityManager and is real. CPU values need availability handling when /proc/stat is inaccessible. |
| Messages legacy repository | MessageRepositoryImpl emits an empty list, but the current Messages UI does not consume it. | Do not classify SMS as fake: the active screen uses SmsThreads/SmsStore and Android Telephony providers. Remove obsolete repository separately if no callers remain. |
| Magnifier / Flashlight | Magnifier opens Camera2, changes real zoom/torch and stops repeating for freeze. Flashlight calls setTorchMode and generates actual audio patterns. | Real implementations with capability/lifecycle issues to review; not evidence of fabricated results. |
| Radio / TV / Video / YouTube / Stremio | Source and service paths connect actual providers, catalogs, parsers and media playback. | Not certified end-to-end by this source inspection; failures and incomplete features are not proof of fake data. |

Scope: virtual routes/catalog, suspicious constants and simulated delays, active repository wiring, prior Clock implementation, Akou recognition path, system telemetry and camera/tool entry points. Further review must follow UI actions into their underlying service rather than classify by comments or a grep match alone.

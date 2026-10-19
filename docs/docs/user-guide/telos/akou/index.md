# Akou — music recognition

Tap **Record and recognize** and grant microphone permission. Akou listens for ten seconds only while this screen is open, computes a Shazam-compatible audio fingerprint on the phone, then asks Shazam to match that fingerprint. No account or API key is needed. The original microphone audio is never uploaded and is cleared from memory after the attempt. Leaving the screen cancels listening or the pending request.

A successful match displays the returned title, artist and album when supplied. History is stored locally and can be cleared. **Find in YouTube Music** opens a search for that title and artist; a returned match does not guarantee a particular YouTube recording.

Quiet recordings, unsupported microphones, missing connectivity, rate limits and unknown songs produce an error or no match, never a sample song. Recognition needs Internet access; the unofficial third-party endpoint can change or become unavailable. There is no continuous background listening.

Fingerprinting is based on Audile and SongRec through InterTune's GPL-3.0 Kotlin port. See `THIRD_PARTY_NOTICES.md` for source revisions and test-audio attribution.

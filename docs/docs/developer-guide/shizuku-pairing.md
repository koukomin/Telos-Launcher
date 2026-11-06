# Built-in Shizuku pairing audit

## Current notification-based flow (1.44.15)

The floating overlay and prompt Activity have been removed. Both manual entry points use Android
notification `RemoteInput`; only SystemUI owns the reply field and IME. Notification permission
is requested before starting, and repeated mDNS results are deduplicated. Automatic pairing is
optional and reads only Settings during an explicit session. Reads are coalesced instead of
walking the window tree on every accessibility event; navigation attempts are throttled.

On the installed 1.44.14 OnePlus build, ADB confirmed a `telos-adb-pairing` window holding focus
above the browser and covering the system navigation bar. Disabling only pairing accessibility
removed that window. CPU was not saturated at the time; GPU/input timing from mixed app windows
is not a controlled performance comparison. The previous isolated overlay tests below did not
certify the installed production version or every IME/navigation interaction. The notification
implementation supersedes that approach.

The following sections retain the original transport audit and historical validation record.

This audit compares the actual Telos manager and server, not just the vendored source tree,
with Shizuku-Next `8508e1d50a293cec82eb60465bcc17022d7a5926` and Shizuku+
`80330704be4ebc46e282334b9c542d4a7d071b1b`. Both references were cloned with their API
submodules. The managers are Apache-2.0; API/Rish is MIT; Telos changes remain GPL-3.0-or-later.

## Faults found

- The CMake build selected only the starter executable. The pairing implementation called
  `System.loadLibrary("adb")` but did not build that library. The server also unconditionally initializes Rish, but
  `librish.so` was not built, causing a fatal `UnsatisfiedLinkError` during server construction.
- JNI registered the upstream `moe.shizuku.manager.adb.PairingContext`, while the compiled Telos
  class has a different package. A successful Kotlin compile cannot detect this mismatch.
- Wireless start called the privileged shell engine, which requires Shizuku or root to be running
  already. It did not establish an authenticated ADB connection to bootstrap the server.
- On the tested OnePlus, TLS signing with the original non-exportable hardware RSA key failed.
  Next/Plus instead use a software RSA identity encrypted at rest by an Android Keystore AES key.
- Pairing and connect ports were conflated. They are separate `_adb-tls-pairing._tcp` and
  `_adb-tls-connect._tcp` services and have different lifetimes.
- Both the foreground service and accessibility service could create an editable overlay.
  The field immediately took window focus; submit did not hide the IME or handle hardware Enter.
- Accessibility was not restricted to Settings or to an explicit pairing session. The fallback
  concatenated digits across labels, so an IP/port could become an apparent six-digit code.
- Starting Settings from a background service is unreliable. ColorOS also rejects the usual
  direct wireless intent and the quick-settings tile preferences route.
- Android 17 requires the local-network runtime permission for unrestricted NSD. Without it,
  a system picker can cover the pairing dialog. Request the permission before starting discovery.

## Transport and lifecycle

1. The visible Activity requests the Android 17 local-network permission when necessary, starts
   the bounded foreground pairing service, and opens Settings.
2. A direct wireless-settings activity is tried first; Developer options highlights
   `toggle_adb_wireless` as a fallback. During the explicitly requested navigation window,
   accessibility uses Settings' own localized `enable_adb_wireless` label to open the row.
3. **Wait for pairing code** selects automatic reading when accessibility is enabled;
   **Enter code** starts a manual session even if accessibility is enabled.
   A single collapsed, non-focusable pill waits. With accessibility enabled it is an accessibility
   overlay; otherwise the application overlay requires the user's draw-over-other-apps grant.
   The notification reply is the fallback when overlays are unavailable.
4. The optional reader examines only Settings windows during the session. It accepts a standalone
   six-digit code (including a space between two groups of three), never digits combined from
   unrelated labels. Android 17 dialogs without a printed IP use mDNS to find the pairing service.
5. Pairing uses SPAKE2/TLS and the persisted software RSA identity, encrypted at rest with Android Keystore AES-GCM. The native binding has an
   explicit keep rule. Sockets have connect/read timeouts, and discovery stops on completion or
   cancellation. Discovered endpoints are restricted to addresses belonging to this device.
6. A separate connection-service discovery authenticates an ADB TLS stream with the same identity,
   runs the starter using `shell:`, then waits for a responding Shizuku binder. Command completion
   alone is not server-start success.
7. Manual submit and IME Done/Enter use the same serialized transaction. Submission hides the IME,
   clears focus and collapses the pill before network work. Stop removes the overlay; the pairing
   service expires after three minutes. Failed/replayed intents do not silently restart pairing.

The one-time code is not written to the host diagnostic log and is cleared after an attempt.
Launcher data and old keys are not deleted. The corrected TLS identity uses a versioned encrypted file in no-backup app storage; the old
hardware-RSA alias is not reused because the OnePlus TLS provider rejected its signing operation.
An older installation must pair once with the corrected identity. Decryption errors are reported
rather than silently replacing a trusted key.

## Feature visibility and parity

Vendoring the Shizuku server does **not** embed the standalone manager UI or all of Next/Plus.
The Telos Shizuku tab has start/stop/restart, root, wireless pairing, computer-ADB instructions,
autostart/watchdog, and the existing Security Hub client list. AppOps is separately exposed in
System Apps; per-package firewall is in Network. These are not missing merely because they are
not repeated in Shizuku settings.

Next's separate TCP/USB transport selector, detailed retry/boot-port policy, service diagnostics,
per-feature lab toggles, modules/catalog, and Plus' Dhizuku/SU bridge and exclusive API suite are
not provided by copying server sources. Telos's `Usb`/`Tcp` enum entries were not implemented
as distinct transports. Do not advertise full Next/Plus parity or add switches that call the
same wireless implementation. These need separate implementation and acceptance tests if adopted.
The legacy external `starter.sh` helper is unused by the built-in UI.

A further **client-authorisation gap** is visible in the current manager code: the server launches
`<manager package>.intent.action.REQUEST_PERMISSION`, but Telos does not declare a matching
permission-confirmation Activity. Security Hub's existing `setClientAccess` implementation uses
`pm grant`/permission-name app-ops commands rather than Next's manager-side
`Shizuku.getFlagsForUid`/`updateFlagsForUid` flow. On this ColorOS build, shell runtime-permission
grants are also restricted. These are functional manager gaps, not settings hidden elsewhere.
The pairing fix does not certify external-client authorisation; a separate implementation needs
a validated allow/deny prompt and server-flag controls before full manager parity can be claimed.


## Validation

Automated regression: `:services:app-management:testDebugUnitTest` includes rejection of IP/port
and mixed-label false positives, spaced codes, and leading zeros. ARM64 and armeabi-v7a CMake
builds include `libadb.so`, `librish.so`, and `libshizuku.so`.

Device check: the isolated `:services:app-management:assembleDebugAndroidTest` APK can run
`PairingDeviceTest#nativePairingBindingsLoad`. The opt-in interactive test requires
`-e interactivePairing true`; it must reach a live binder with UID 2000, not merely a pairing
success message. The test Activity is also launchable directly for OEMs that prevent binding an
accessibility service in a process controlled by instrumentation. Its result file belongs only
to the test package. Never replace/uninstall the user's release launcher to run this test.

Physical-device and visual results are recorded after execution; compilation is not evidence of
pairing, keyboard behavior, or OEM compatibility.

The JNI libraries privately link the NDK C++ runtime statically (no C++ objects cross JNI
boundaries). This avoids resolving a different runtime when loaded in `app_process`. The physical
device exposed the original dynamic-runtime failure as a missing `__libcpp_verbose_abort` symbol.

## Executed physical-device results (2026-11-05)

OnePlus 15 (CPH2747), ColorOS Android 17, portrait, dark mode, system font scale 1.15:

| Check | Observed result |
| --- | --- |
| Explicit start → wireless Settings | Correct wireless-debugging fragment, using the localized row |
| Automatic reader | Full pairing + TLS shell bootstrap; `Done`, `running=true`, UID 2000 |
| Manual pill + hardware Enter | Full pairing + fresh server bootstrap; UID 2000; IME hidden |
| Manual pill + software Done | User entered the code and pressed Done; `Done`, fresh server UID 2000, IME hidden |
| Device regression tests | JNI registration and encrypted identity reopening: 2 tests passed |
| Server verification | Starter reports a live process after 3 seconds; manager receives the binder |
| Existing launcher | Release package retained; no app-data clear or replacement |

These are tests of the isolated harness using the production service/overlay/transport classes,
not a claim that a new production APK was installed. Landscape, the full font/display-size matrix,
light mode, other OEMs, notification-only input, root and distinct TCP/USB transports are not
certified by these results. No device screenshots, addresses, pairing codes or identifiers are
committed.

Pairing transactions have their own bounded coroutine lifetime. Rebinding/closing an overlay
does not cancel the network transaction or strand the UI in Working; explicit Stop cancels it.
The pairing operation has a one-minute deadline, and idle discovery expires after three minutes.

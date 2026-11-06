# Launcher startup scripts and Shizuku reconnection

Telos System → Shizuku contains **Startup scripts**. This is a foreground-launcher capability,
not an embedded Termux installation, an unrestricted boot daemon or an Android permission bypass.

## User-managed scripts

Scripts are created and edited explicitly in Telos. Each entry has a name, shell text, an identity
(**Telos**, **Shizuku**, or **Root**) and a separate **Run when Telos starts** switch. New entries
and edited entries are disabled for automatic execution. The **Run** action is explicit and does
not require the automatic switch. Scripts are not downloaded, imported or automatically enabled.

The actual launcher Activity starts one background script coordinator per process. Resizing, returning
Home or recreating the Activity does not run the list again. If Shizuku autostart is enabled, its
bounded start attempt happens before the script list. Only the enabled snapshot is considered;
a queued entry edited, disabled or removed before its turn is skipped. Scripts execute sequentially.
A manual run shares the same execution lock. Provider, notification, media and boot-triggered
process creation alone do not execute these scripts. A process restart followed by opening the
launcher runs enabled entries again, so scripts should be idempotent.

* **Telos** uses the application's UID and filesystem permissions. It does not gain ADB privileges.
* **Shizuku** requires an available, authorized binder. An unavailable binder fails that run.
* **Root** invokes `su` explicitly. The root manager may require authorization.

There is no fallback to another identity and no automatic replay after failure. Shizuku spawn
uses the non-retrying Binder path: an ambiguous IPC failure must not replay a command that may
already have started. This avoids
running a partially successful script twice under different privileges. Shell text is passed as
one argument, without rewriting, to Android's shell. This is not a full terminal distribution.

The private script store uses `AtomicFile` under `noBackupFilesDir`: at most ten entries, 80
characters per name, 16,384 characters per body and a checked 1 MiB serialized UTF-8 limit. It
never executes shared-storage or restored backup files. A corrupt or oversized store is ineligible
for execution. Script text and output are not sent to logcat. Latest results stay in process memory;
the UI displays the exit code and a short output preview. Do not put credentials in scripts.

Each command uses Android `toybox timeout -k 2 30`; the launcher independently stops collecting
after 35 seconds. Standard output and error are polled and drained together, capped at 8 KiB each.
A 250 ms settling window after exit accommodates asynchronous Shizuku stream forwarding without
waiting indefinitely for EOF on a pipe inherited by another process. A missing timeout applet
fails closed. Modern Android timeout sends signals to the command process group; older Android
implementations may terminate only the direct child. Use finite foreground scripts, not background
jobs or daemons: Telos does not guarantee cleanup of detached descendants on every Android/OEM.

## Automatic Shizuku startup

The design was informed by [Shizuku discussion #462](https://github.com/RikkaApps/Shizuku/discussions/462),
which pairs a Termux ADB identity once, enables wireless debugging after reboot, discovers a port,
starts the server and optionally turns wireless debugging off. No script from that discussion is
copied into Telos. Telos already contains its own paired TLS identity, local-device mDNS discovery,
ADB transport and native starter, so it does not install Termux, nmap, Tasker or MacroDroid and does
not scan a port range or connect to other devices.

1. Grant notification/local-network access and pair once through Android's Wireless debugging.
2. Enable **Autostart** in Telos System → Shizuku.
3. After reboot, Android must expose an authorized wireless-debugging endpoint. Enable Wireless
   debugging and approve the current Wi-Fi if asked. A root device can start without wireless ADB.
4. Opening Telos starts reconnection using the saved key. The enabled watchdog also attempts
   startup after `BOOT_COMPLETED`/package replacement, when Android permits foreground services.
   Locked boot is deliberately excluded: credentials and script preferences are unavailable then.
5. Optional **Turn off wireless debugging after Shizuku starts** runs only after a verified live,
   authorized Shizuku binder. It is off by default. It never disables wireless debugging after a
   failed start. If enabled, a later server restart may require enabling Wireless debugging again.

The watchdog reads the opt-in before starting from a receiver and stops if the setting is disabled.
Failed retries back off from one minute to five minutes; a running server is checked every 30
seconds. Start attempts are serialized with manual/pairing starts. Background service restrictions
are caught; each return to the foreground ensures the enabled watchdog is requested again,
without repeating scripts. Autostart changes are persisted before starting the watchdog.
The watchdog is requested before awaiting the server, while the launcher is foreground. Receiver work is bounded to
eight seconds. There is no silent `Settings.Global` permission bypass to enable wireless debugging.
Revoked pairing, Wi-Fi trust, local-network permissions, locked devices and OEM background limits
remain Android prerequisites. This cannot promise unattended non-root startup on every reboot.

## Validation

See [UI validation](./ui-validation). Local JVM coverage verifies opt-in defaults, input bounds,
queued snapshot eligibility, non-ASCII storage limits, exact shell text, exit codes, output caps,
timeout destruction, inherited background pipes and delayed Binder-style output. Production module
and UI compilation are required before delivery. No physical device is connected for this feature;
boot, root-manager prompts, all OEMs and the UI/font/IME matrix remain device validation work.

# Clock

![Telos Clock](https://raw.githubusercontent.com/koukomin/Telos-Launcher/main/docs/public/img/telos-development-18-clock.webp)


Clock provides persisted one-shot alarms and local world-clock search. The world tab starts with a search field instead of a preset city list. Search city or country names in the device language, English or Greek; Greeklish aliases are normalized locally. Results show the corresponding local time zone.

Create an alarm, choose the time, and select a system tone, a document-provider audio file, a saved radio station or a stream URL. Grant Android exact-alarm access when prompted. Audio-file access is retained where the document provider permits it. A stream that cannot start falls back to the default alarm tone after 15 seconds. Playback stops after ten minutes or when dismissed from the notification. Alarms are restored after device boot and time-zone changes; disabled alarms remain disabled when edited.

These are one-shot alarms, not a recurring schedule. Android force-stop and revoked permissions can prevent delivery.


Alarm definitions are stored independently of the launcher process and
rescheduled after package updates and device reboot. Exact-alarm permission must
remain granted. The last Clock tab is also saved. End-to-end crash/update
recovery has not yet been verified on a physical device.

The World time search uses the same pill as the other Telos apps. Focus it without
typing to see a short list of capitals. Athens is first, except when Greece is
already selected as the home country or app locale.

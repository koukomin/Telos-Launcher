# SIP / VoIP and the FRITZ!Box phonebook

Two network features of Telos Phone that are aimed at home routers: a **SIP account** that rings and calls
through your internet telephony (for example a FRITZ!Box IP telephone), and a **remote phonebook** that puts names
on callers from the FRITZ!Box telephone book. They are independent of each other.

## SIP / VoIP <Badge type="warning" text="experimental" /> <Badge type="info" text="untested" />

Telos uses [baresip](https://github.com/baresip/baresip) as the SIP engine. SIP is a call path **next to** your SIM
calls, not a replacement for the phone's call framework.

::: warning Experimental
SIP is new and <Badge type="info" text="untested" /> on real devices and servers. It needs Android 9 or newer.
SIP calls use their own call screen, notification and audio handling, not Android's telecom service. Do not
depend on it for emergency calls.
:::

### What you need

- A SIP account: server, user name and password. For a FRITZ!Box this is an **IP telephone** (LAN/WLAN telephone)
  set up in the FRITZ!Box under Telephony > Telephony Devices, with the user name you gave it.
- The phone must reach the server, for example over your home Wi-Fi or a VPN to the router.
- The microphone permission, which is requested when you switch SIP on.

### Set it up

1. **Phone settings > SIP / VoIP account > Account**.
2. Enter **Server** (for a FRITZ!Box `fritz.box`, which is the default), **User name** (the IP phone user),
   **Password** and an optional **Display name**, then **Save**.
3. Turn on **Use SIP account**. Telos asks for the microphone permission if needed.
4. The summary line shows the state: *Switched off*, *Starting*, *Connecting*, *Registered, ready for calls* or
   *Registration failed* with a reason.

The display name accepts letters, digits, spaces, dots, dashes and underscores. The server must be a valid host
name or address, otherwise registration fails with "The SIP server address is not valid".

### Settings

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Use SIP account | SIP / VoIP account | off | Keeps the account registered in the background so calls can come in. Shows a small low-priority notification ("SIP account") |
| Account | SIP / VoIP account | not configured | Server (default `fritz.box`), user, password, display name |
| Outgoing calls | SIP / VoIP account | Offer a SIP button when calling | **Never, receive calls only**, **Offer a SIP button when calling**, or **Use SIP by default** |

### How it behaves

| Topic | Behavior |
| --- | --- |
| Registration | Registers on start and refreshes every 300 seconds. It re-registers when the network address changes |
| Background | A foreground service of type *phone call* runs only while **Use SIP account** is on |
| Incoming calls | A high-priority notification with Answer and Decline, a ringtone, and the SIP call screen. The number is resolved to a contact name, or to the FRITZ!Box phonebook name |
| Missed calls | A "missed" notification and an entry in the call log |
| Outgoing calls | A **SIP** button next to the call button on the keypad (mode "choose"), a **Call over SIP** card on the contact page, or SIP for every call without an explicit SIM (mode "default") |
| Plain numbers | A number without `@` is called on your server, so `0301234` becomes `sip:0301234@fritz.box`. `*` and `#` are encoded for the address |
| Call screen | Mute, Speaker, Keypad (DTMF tones), Answer, Hang up. No hold, merge or recording |
| Call log | Calls are written to the system call log with the account id `telos-sip`, so they appear in [Recents](./recents-recording) |
| Limits | One SIP call at a time. A second call is refused with "A SIP call is already in progress" |
| Biometric check | Honors **Biometric before placing a call** for calls placed from Telos |

### Media and security details

- Audio codecs: Opus (16 kHz, in-band FEC, 28 kbps) and G.711.
- Encryption of media: SRTP and DTLS-SRTP modules are loaded. Whether a call is encrypted depends on what your
  server offers.
- NAT traversal modules (STUN, TURN, ICE) are loaded.
- Server transports accepted: UDP, TCP, TLS, WebSocket.
- The password is stored encrypted with a Keystore key.

::: warning Verify the server certificate
The account form has a switch **Verify the server certificate**. It is **on** for new accounts: with TLS, the
server's certificate is checked against the Android system certificates. Accounts saved before this switch existed
keep it **off**, as before, until you edit the account. A FRITZ!Box usually has its own self-signed certificate, so
it normally needs the switch **off**. With it off, the certificate is **not checked** and an attacker on your
network could impersonate your server, so use that only on networks you trust, for example your home network.

If registration fails in a way that looks like a TLS or certificate problem while the switch is on, Telos shows a
translated message that names the switch.
:::

### Limitations

- Text messages, video and Bluetooth routing for SIP calls are not implemented (they are listed as planned in the
  [readme](https://github.com/koukomin/Telos-Launcher#planned)).
- No call recording, gestures or auto redial for SIP calls.
- Registration needs the network to be up; the foreground notification cannot be hidden while SIP is on.

### Troubleshooting

| Problem | Try this |
| --- | --- |
| "SIP is not available in this build or on this device" | The engine needs Android 9 or newer |
| "Registration failed" | Check server, user and password. For a FRITZ!Box, the IP phone must exist and have a password |
| Registered but no incoming calls | Allow the app to run in the background and ignore battery optimization for Telos |
| "SIP calls need the microphone permission" | Grant the microphone permission in Android settings |
| The SIP button is missing on the keypad | The account is not registered, or **Outgoing calls** is set to "Never, receive calls only" |
| No audio in one direction | NAT or firewall between phone and server. Test on the same Wi-Fi as the router |

## FRITZ!Box remote phonebook

Shows names from an AVM FRITZ!Box telephone book for numbers that are not in your Android contacts. The lookup
works offline from a local copy.

### Set it up

1. **Phone settings > Remote phonebook (FRITZ!Box) > Connection**.
2. Enter the **Address** (`fritz.box` or an IP such as `192.168.178.1`), the **User** and the **Password**, then
   **Save**.
3. Turn on **Identify callers from FRITZ!Box**.
4. Tap **Sync now**. The line below shows "N contacts synced" or "Failed" with a reason.

The FRITZ!Box user needs the right to access *FRITZ!Box settings*. Create a dedicated user for it.

### How it works

| Topic | Behavior |
| --- | --- |
| Protocol | AVM TR-064, service `X_AVM-DE_OnTel`, on port 49000 of the box over plain HTTP, with HTTP digest authentication |
| Books | Every telephone book of the box is read |
| Local copy | A JSON file in Telos' private storage, loaded into memory. Lookups never use the network |
| Matching | Numbers are compared by their last 9 digits, so `+49 30 123456`, `030 123456` and `0049 30 123456` match the same entry |
| Where names appear | Incoming call screen, in-call screen, SIP call screen and Recents |
| Auto refresh | When the Dialer screen opens, the book is synced again if the last sync is older than 6 hours and the feature is on |
| Switching off | Turning the switch off deletes the local copy |
| Password | Stored encrypted with a Keystore key |

### Settings

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Identify callers from FRITZ!Box | Remote phonebook | off | Uses the local copy for names |
| Connection | Remote phonebook | `fritz.box`, no user | Address, user and password |
| Sync now | Remote phonebook | | Reads the books now |

### Limitations and privacy

- The traffic to the FRITZ!Box is **not encrypted** (HTTP). Use it only on your home network.
- The cached names and numbers are stored unencrypted in the app's private files.
- The phone must reach the box, so names are not synced when you are away from home. The cached copy still works.
- Android contacts always win over the FRITZ!Box name.
- It is read only: Telos never writes to the FRITZ!Box.

### Troubleshooting

| Problem | Try this |
| --- | --- |
| "Failed" with an authentication message | Check the user and password and the *FRITZ!Box settings* right |
| "Failed" with a connection message | The phone is not on the home network, or the address is wrong |
| Names do not show | Sync again, check the switch, and remember that your own contacts take precedence |

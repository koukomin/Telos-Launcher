# Firewall

The firewall decides about every new connection. The first rule that matches wins.

1. Telos itself and connections without an owner app: always allowed.
2. An app with **Bypass firewall**: allowed.
3. IP rules and domain rules (the app's before the ones for all apps; *Trust* before *Block*).
4. The app's own rules.
5. Universal rules, unless the app ignores them.
6. Blocklist hits reported by the DNS layer.
7. Allowed.

If the firewall itself fails to decide, the connection is let through.

## Rules per app

For each app: **Block everything**, and block when using **Wi-Fi**, **mobile data**, **roaming**, **the local network**, **another VPN**,
when the app is in the **background** (not effective yet), or with the **screen off**. Also **Ignore universal rules**, **Bypass
firewall** (skips everything, including blocklists and IP and domain rules; DNS is still answered by Telos) and **Exclude from the VPN**
(the app does not use the VPN at all; applies after the VPN restarted).

## Universal rules

Apply to all apps: block Wi-Fi, mobile data, roaming, metered networks, the local network, background, screen off, device locked,
newly installed apps, apps that cannot be identified, UDP, plain HTTP (port 80), apps that use their own DNS, and **default deny**
(nothing is allowed unless a rule allows it). Use default deny with care: it can cut off apps until you allow them.

## Custom rules

- **IP rules:** an address or range such as `10.0.0.0/8`, an optional port and protocol, for all apps or one app, **Block** or **Trust**.
- **Domain rules:** `example.com` or `*.example.com` (the name and all subdomains), **Block** or **Trust**.

*Trust* skips the universal rules and the blocklists for the match. Changes apply to new connections; open connections can be closed so that
apps reconnect.

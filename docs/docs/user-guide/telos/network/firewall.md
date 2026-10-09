# Firewall

The firewall decides about every new connection. The first rule that matches wins.

1. Telos itself: always allowed.
2. An app with **Bypass firewall**: allowed.
3. IP rules and domain rules (the app's before the ones for all apps; *Trust* before *Block*).
4. The app's own rules (skipped while the app is allowed temporarily).
5. Universal rules, unless the app ignores them (also skipped while allowed temporarily).
6. Blocklist hits reported by the DNS layer, unless the domain is an exception.
7. *Default deny*, if switched on (unless the app ignores universal rules).
8. Allowed.

After the firewall allowed a connection, the app's [WireGuard](wireguard.md) choice decides where it goes.

If the firewall itself fails to decide, the connection is let through.

## Rules per app

For each app: **Block everything**, and block when using **Wi-Fi**, **mobile data**, **roaming**, **the local network**, **another VPN**,
when the app is in the **background** (needs usage access, see below), or with the **screen off**. Also **Ignore universal rules**, **Bypass
firewall** (skips everything, including blocklists and IP and domain rules; DNS is still answered by Telos) and **Exclude from the VPN**
(the app does not use the VPN at all; applies after the VPN restarted).

The app list can be searched and filtered (all, user apps, system apps, with rules). *Actions for the apps shown* blocks or allows all connections, blocks Wi-Fi or mobile data, or removes the rules of all apps shown (resets them to the defaults); the last one asks for confirmation first. **Allow temporarily** lets an app through the app and universal rules for 15 minutes, 1 hour or 8 hours (**End now** cancels it).

::: info Background rules and usage access
Android does not tell a VPN which app is in the foreground. Telos reads it from the usage events, which needs the *usage access* permission (the firewall screen has a **Grant access** button). Without it, rules for the background never block. An app counts as background shortly after it left the screen.
:::

## Universal rules

Apply to all apps: block Wi-Fi, mobile data, roaming, metered networks, the local network, background, screen off, device locked,
newly installed apps, apps that cannot be identified, UDP, plain HTTP (port 80), apps that use their own DNS (connections to port 53 or 853 outside the local network), and **default deny**
(*Block everything by default*: only apps that ignore the universal rules, apps with Bypass firewall, apps allowed temporarily and addresses or domains you trust can connect). Use default deny with care: it can cut off apps until you allow them.

*Block newly installed apps* only affects user apps installed after you switched it on; they stay blocked until you allow them in the list *Blocked new apps*.

## Custom rules

- **IP rules:** an address or range such as `10.0.0.0/8`, an optional port and protocol, for all apps or one app, **Block** or **Trust**.
- **Domain rules:** `example.com` (this name only) or `*.example.com` (the name and all subdomains), for all apps or one app, **Block** or **Trust**. They also apply to DNS questions.

*Trust* allows the connection and skips the app rules, the universal rules and the blocklists for the match. Changes apply to new connections; open connections can be closed so that
apps reconnect.

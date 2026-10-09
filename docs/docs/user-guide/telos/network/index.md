# Telos Network

A firewall and DNS filter for the whole phone, built on the engine of [RethinkDNS](https://github.com/celzero/rethink-app).

::: warning Early version
Telos Network was written without a compiler and has **not been run on a device yet**. It is new and may fail. If it does,
the internet simply works as before (see [Safety](#safety)). You can turn it off at any time.
:::

::: tip At a glance
Telos Network uses the Android VPN service as a local filter. Nothing leaves your phone through a Telos server: there is no
account, no subscription and no paid proxy. You choose the [DNS server](dns.md), set [firewall rules](firewall.md) per app and
per connection type, switch on [blocklists](blocklists-bypass.md), send chosen apps through [WireGuard](wireguard.md) and read
the [logs](logs-settings.md).
:::

## What it is

Telos Network is a [virtual app](../#how-the-built-in-apps-work), part of the launcher. Switching it off in
[Telos Store](../store/) hides its icon. It is based on RethinkDNS (Apache-2.0); its network engine, firestack (MPL-2.0), is used
unchanged. See [THIRD_PARTY_NOTICES.md](https://github.com/koukomin/Telos-Launcher/blob/main/THIRD_PARTY_NOTICES.md).

## The home screen

| Part | What it shows |
| --- | --- |
| Big card | The state: **Off**, **Starting**, **On**, **Stopped** (with the reason) or **Permission needed**, and the **Turn on** / **Turn off** button |
| Status | The DNS server in use, the WireGuard tunnels that are up, blocked DNS queries and blocked connections (counted since the logs were last cleared) |
| DNS and tunnels | [DNS](dns.md), [WireGuard](wireguard.md), WireGuard per app |
| Firewall | Rules per app, universal rules, custom rules ([Firewall](firewall.md)) |
| Filters and logs | [Blocklists](blocklists-bypass.md), [Logs](logs-settings.md), [Settings](logs-settings.md#settings) |

The first time you turn it on, Android asks for your consent to set up a VPN. Without it Telos Network cannot start.

## Safety

- It **never starts by itself**. The only automatic start is after a reboot, and only if you switched on *Start on boot* and gave the VPN
  permission before.
- If the engine fails to start or stops working, the VPN is shut down and your normal connection is back (the state shows *Stopped*
  and a notification says so). It does not block the internet when it breaks.
- **Turn off** always works, also while it is starting.
- Telos itself is kept out of the tunnel by default (*Exclude Telos*).
- Android's own *Always-on VPN* and *Block connections without VPN* are your choice in the Android settings; Telos never switches
  them on. With lockdown on, the internet stops when Telos Network stops, so leave it off if you want the fallback.

## What it does not do

- No subscription and no RPN (no paid proxy service), no account, no server of ours.
- None of the experimental features of RethinkDNS.
- It cannot look inside encrypted connections. It filters by app, address and domain.
- It does not make you anonymous. Websites still see your IP address, unless you use a WireGuard tunnel you trust.
- Android allows one VPN at a time. Telos Network replaces another VPN while it runs; use [WireGuard](wireguard.md) inside Telos Network instead.
- The foreground or background state of other apps is not known to the engine, so rules for *background* do not block yet.

## Pages

- [DNS](dns.md)
- [WireGuard](wireguard.md)
- [Firewall](firewall.md)
- [Blocklists and exceptions](blocklists-bypass.md)
- [Logs and settings](logs-settings.md)

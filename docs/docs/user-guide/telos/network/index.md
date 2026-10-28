# Telos Network

![Telos Network](https://raw.githubusercontent.com/koukomin/Telos-Launcher/main/docs/public/img/telos-development-21-network.webp)


A firewall and DNS filter for the whole phone, built on the engine of [RethinkDNS](https://github.com/celzero/rethink-app).

::: warning Early version
Network changes are covered by automated tests and build checks. Android 16 device recovery and Tor still need target-device validation. See [Safety](#safety) for failure behavior and Android lockdown. You can turn it off at any time.
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
| START / STOP | Large primary action with readable connection state. Tap to start or stop. |
| Live counts | Blocked DNS queries and blocked connections since logs were cleared |
| Tiles | [Speed test](#privacy-minded-speed-test), [DNS](dns.md), Android [system Private DNS and Wi-Fi rules](dns.md#android-system-private-dns), firewall, [WireGuard](wireguard.md), [blocklists](blocklists-bypass.md), [logs](logs-settings.md), [settings](logs-settings.md#settings) |

The first time you turn it on, Android asks for your consent to set up a VPN. Without it Telos Network cannot start.

## Privacy-minded speed test

The Network home screen includes a LibreSpeed test for ping, jitter, download and upload. Choose a community server from the public directory or enter the HTTPS URL of a self-hosted LibreSpeed backend. The test runs only after you start it; results stay on screen and are not uploaded by Telos. Requests go directly from your device to the chosen server, which can see your IP address and test traffic. Loading the public directory contacts `librespeed.org`; using your own server avoids that directory request. Download data is bounded per sample, and you can cancel a running test.

The System widget has a matching shortcut to the test. Its continuously sampled traffic rates remain device-wide counters and are not speed-test results.

## Safety

- Previously active Network is restored after a process restart or app update when Android permits. The saved DNS, firewall, WireGuard and Tor configuration is reapplied. Manual **Turn off** and VPN revocation cancel restoration. The boot receiver requires *Start on boot* and previously granted VPN permission; opening the launcher also restores saved active intent.
- If the engine fails to start or stops working, the VPN is shut down and your normal connection is back (the state shows *Stopped*
  and a notification says so). Up to three delayed retries are attempted while active intent remains saved. It does not block the internet between retries unless Android lockdown is enabled.
- **Turn off** always works, also while it is starting.
- Telos itself is kept out of the tunnel by default (*Exclude Telos*).
- Android's own *Always-on VPN* and *Block connections without VPN* are your choice in the Android settings; Telos never switches
  them on. With lockdown on, the internet stops when Telos Network stops, so leave it off if you want the fallback.

## What it does not do

- No subscription and no RPN (no paid proxy service), no account, no server of ours.
- None of the experimental features of RethinkDNS.
- It cannot look inside encrypted connections. It filters by app, address and domain.
- NetGuard extras (notify on access, new apps, pause on call, speed notification, hosts import) run on this engine. Port forwarding and live PCAP capture from NetGuard are not included.
- NetGuard's JNI packet filter is GPL-3 and small (~a few hundred KB per ABI, under ~1.5 MB for four ABIs). It is **not** bundled: Android allows one VPN, and Telos Network already uses firestack for DNS, WireGuard and Tor. A second packet engine would replace those, not sit beside them.
- It does not make you anonymous. Websites still see your IP address, unless you use a WireGuard tunnel you trust or explicitly route the connection through Tor.
- Android allows one VPN at a time. Telos Network replaces another VPN while it runs; use [WireGuard](wireguard.md) inside Telos Network instead.
- The engine does not know which app is in the foreground. Rules for *background* work only if you grant *usage access* (the firewall screen offers a button for it); without it they never block.

## Pages

- [Tor and Web3 names](tor-web3.md)
- [DNS](dns.md)
- [WireGuard](wireguard.md)
- [Firewall](firewall.md)
- [Blocklists and exceptions](blocklists-bypass.md)
- [Logs and settings](logs-settings.md)

## Home widget

Add **Telos Network** from the built-in widget picker. It shows VPN state, the selected DNS server, blocked query/connection counts since logs were cleared, local interface addresses and sampled download/upload rates. Rates use device counters, including possible VPN interface overhead; they are not a tunnel speed test. The **System** widget also has a shortcut to the on-demand [privacy-minded speed test](#privacy-minded-speed-test).

WAN lookup runs only when tapped and contacts the displayed `api.ipify.org` endpoint. It shows the route used by Telos itself, which is normally excluded from its own VPN; it must not be interpreted as the exit IP of a selected WireGuard or Tor route.

## Routing and notification controls

Tor is available from the Network home and settings. The shared system route chooser, also available in the System widget, selects Direct, an imported WireGuard profile, or Tor. Direct disables the selected proxy route while the local Network firewall may remain active. Tor starts internally and TCP/DNS traffic fails closed while it connects; unsupported UDP is blocked. The System widget uses the same process reader as the System app, subject to Android process visibility limits.

The VPN notification exposes status and controls. Pause offers 5 minutes, 30 minutes, one hour or two hours, plus Stop. Resume restores the wanted configuration. Android exact-alarm/background-service restrictions may delay automatic resume; Resume remains available manually. System always-on VPN lockdown can block connectivity while paused. Stopping explicitly cancels pending resume.


Network restores its saved on/off intent, configuration and any pause deadline
after a recoverable service restart or package update. Resume requires the
existing Android VPN consent; reboot follows Start on boot. Force-stop and
revoking VPN consent can prevent automatic recovery.

## Network navigation and statistics (development draft)

Home, Statistics, Logs, Apps and Settings share one bottom navigation bar. Switching destinations reuses the Network root. App icons inside logs open the same package firewall editor as the Apps list. The app editor has a large identity section and adaptive rule tiles for unmetered/metered access, full blocking, firewall bypass, universal-rule bypass and VPN exclusion. Additional controls retain Wi-Fi/mobile/LAN/VPN, background/screen/roaming rules, proxy assignment, IP/domain rules, notes and temporary allowance. The 15-minute allowance remains available.

Apps can be searched by name, package or app ID, and filtered by Android-declared category in the menu. A category selection also constrains confirmed bulk actions. Shared UIDs with mixed categories remain uncategorized.

Statistics offers 1-hour, 24-hour and 7-day windows, metered/unmetered totals and per-app upload/download totals. These use retained connection logs grouped by connection opening time; byte counts become available when flows close. Retention and logging switches affect the results. Older records have unknown metering, shown separately. These are not device-wide or per-second traffic-meter totals.

### User-configured proxies (development draft)

Settings → Proxies adds SOCKS5 and HTTP CONNECT endpoints with optional authentication. Endpoint addresses and credentials are encrypted in Android Keystore-backed private storage; UI summaries never include credentials. Select a default endpoint or assign one in an app’s firewall editor. Explicit per-app proxy/direct selections override other routing; otherwise a selected WireGuard tunnel takes priority over the default proxy. HTTP CONNECT supports TCP only; other protocols are blocked for apps assigned to it. DNS continues to use the selected Telos resolver.

Disabled, missing and unregistered assigned endpoints block affected apps. Removing an endpoint preserves its assignments so removal cannot silently switch those apps to direct access; choose another route explicitly. Editing requires entering authentication again, with empty credentials removing authentication. A registered indicator means the native VPN registry contains the endpoint, not that a remote server has passed a reachability test. The endpoints require your own server; no paid RPN account is created. Native routing and device interoperability are pending validation.

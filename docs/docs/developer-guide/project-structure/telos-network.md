# Telos Network internals

Telos Network is the module `:services:network` (package `de.mm20.launcher2.network`) plus the screens in `:app:ui`
(`de.mm20.launcher2.ui.network`, with the sub packages `dns`, `wireguard`, `firewall`, `blocklists`, `logs`).
It is a virtual app (`VirtualNetworkApp` in `:data:comms`, key `telos_network_app://network`, deep link route `settings/network`).

## Licenses

Telos Network is based on [RethinkDNS](https://github.com/celzero/rethink-app) (Apache-2.0, Copyright (c) Celzero / The Rethink DNS Open Source Project),
which is compatible with GPL-3.0 when adapted with the notices kept. Its Go engine,
[firestack](https://github.com/celzero/firestack) (MPL-2.0), is used **unchanged** as a Maven dependency (`com.celzero:firestack`, an AAR). Do not copy firestack
source files into Telos: any change to MPL files must be published under MPL. Notices are in `THIRD_PARTY_NOTICES.md`; the table in `readme.md`
must be updated with any new source.

## Module layout

| Package | Content |
| --- | --- |
| `network` | `NetworkEngine` (start, stop, restart, state), `NetState`, `Module.kt` (Koin `networkModule`) |
| `network.api` | The public interfaces and types the UI uses: `NetworkSettings`, `DnsController`, `WireguardController`, `FirewallController`, `BlocklistController`, `LogController`, `AppDirectory`, `EngineComponent`, shared types |
| `network.impl` | `Default*` implementations of those interfaces |
| `network.vpn` | `TelosVpnService`, `FlowBridge` (firestack callbacks to firewall, DNS and logs), `NetworkMonitor`, notifications, boot receiver |
| `network.util` | Small helpers (IP parsing, persisted state) |

The UI only talks to the `api` package (injected with Koin) and to `NetworkEngine`. Replacing an implementation means changing one line in `Module.kt`.

The NextDNS account editor is separate from the VPN DNS controller. `NextDnsRepository` owns the encrypted credential vault and bounded API operations; the UI never stores the API key in saved state. Analytics requests use a fixed metric registry, finite period enum, optional encoded device ID, validated cursor and bounded JSON parsing. Rewrite mutations validate both host and answer before using the fixed-origin API; profile settings are limited to boolean fields returned by the documented settings pages and are patched one nested field at a time. `PrivateDnsController` is a distinct, explicit Shizuku-backed integration for Android's global Private DNS settings; it validates provider hostnames and verifies writes rather than changing settings when a NextDNS profile is connected.

`WifiDnsAutomationService` is an explicitly enabled, special-use foreground service. It watches Wi-Fi transport callbacks, reads the SSID only with location permission, and uses exact SSID matching against `NetworkSettingsValues.wifiDnsRules`. It persists the pre-rule and last-applied settings so it can safely restore on network change or explicit stop; restoration occurs only if the current value still matches the one Telos wrote. Unknown SSIDs do not trigger guessed rules. The opt-in service is restored after boot only when location and notification permissions are available; OEM behavior and Shizuku availability still need device testing.

`PrivateDnsTileService` exposes Android's system Private DNS Automatic/Off toggle in Quick Settings. It reads the current system value before toggling, uses the same Shizuku-backed controller as the settings screen, and opens the settings screen when permission or command access is unavailable. It does not start or reconfigure Telos Network's VPN DNS.

## Engine

`NetworkEngine.state` is a `StateFlow<NetState>`: `Off`, `Starting`, `On`, `Error(message)`, `NeedsPermission(intent)`. `start()` calls `VpnService.prepare()`; when Android needs
consent the state becomes `NeedsPermission` and the UI launches the intent and calls `start()` again after `RESULT_OK` (or `acknowledgeError()` when declined).
A watchdog fails a start after 30 s. Components implementing `EngineComponent` (DNS, WireGuard, blocklists) receive the Go `Tunnel` in `onTunnelConnected` and must apply their whole state there.

## Safety rules

1. The VPN never starts by itself. Allowed starts: a user action, the boot receiver (only with *Start on boot* and a previously granted permission), and the system for Android's always-on VPN.
2. Fail-open: when the engine cannot start or stops, the service is torn down; the state becomes `Error` and traffic uses the normal network.
3. `stop()` works in every state.
4. Controllers never throw into the engine; an exception in `FirewallController.decide` counts as *allow* (`DecisionReason.EngineError`).
5. `decide` and `decideDns` work on in-memory snapshots, with no disk access and no blocking.
6. Telos' own uid is excluded from the tunnel (setting *Exclude Telos*, on by default) and its connections are never filtered.
7. No private key or password is written to the logs.

## Adding a screen

Create a `@Serializable data object ...Route : NavKey` and a parameterless `@Composable` screen, then register both in `SettingsActivity` and in
`TelosPages` (`TelosAppPageComponent.kt`), which host the same screens. Put all texts in `core/i18n/src/main/res/values/` and translate them into every language (see `tools/i18n/`).

## Status

On 2026-10-22, the Network unit tests and default debug UI Kotlin compilation passed for the Private DNS tile and Network changes. The tile has not been exercised from Android Quick Settings on a device; Shizuku permission flow and OEM tile behavior remain unverified.

## Tor and Web3

`network.tor.TorController` binds the Guardian Project Tor Android service while the foreground VPN runs. It registers a loopback SOCKS5 proxy and local Tor DNS transport only after bootstrap. `TorPolicy` recognizes onion names and reserved synthetic IPv4/IPv6 ranges without DNS calls. `FlowBridge` applies firewall blocks first, then routes Tor-required flows only through the Tor proxy; no UDP or direct fallback is allowed. Unknown owners use Tor conservatively while any Tor-only app rule exists. Proxy IDs are exclusive, not a fallback list. Tor mode must change while the VPN is off so the interface routes and exclusions match the policy. The native service shares the excluded Telos UID; this integration does not intercept Telos traffic.

`network.web3.Web3Resolver` performs bounded, read-only JSON requests through Telos' own connection, using public ENS Ideas and eth.limo DNSLink endpoints for ASCII ENS names. UD names require a user-supplied HTTPS backend proxy returning the official Resolution API schema; no API key is embedded. It produces wallet records and explicit website gateway URLs independently. It does not implement a DNS A/AAAA adapter, ENSIP-15 normalization, chain proof verification or arbitrary namespace support. Screens are registered in both SettingsActivity and TelosPages. See the [user guide](../../user-guide/telos/network/tor-web3.md) for trust and failure boundaries.

## Built-in status widget

The Network home-screen widget was removed. VPN state, DNS/block counts and device traffic rates live on the System widget (off in the picker until enabled in Telos System). TrafficRate still derives bytes/second from monotonic elapsed time and real device counters. Sampling runs only while the widget is resumed.

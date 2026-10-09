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

The module and the screens were written without a compiler and have not been run on a device.

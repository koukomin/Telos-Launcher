# Logs and settings

## Logs

Three tabs: **Connections**, **DNS** and **Apps** (totals per app). The first two list entries newest first: **connections** (time, app, protocol, address and port, the domain if known, allowed or blocked and why, the network, the tunnel, bytes
and duration) and **DNS queries** (time, app, name, record type, answer, server, delay, cached, blocked and by which list). You can search, filter by app and
by blocked or allowed, clear them (all, or the entries of one app; both ask for confirmation first) and **Export as CSV**. Entries offer shortcuts to allow or block the domain or address for one app or all apps, or to allow the app for 15 minutes.

Logs stay on the phone and are limited by the number of entries (1000, 5000, 20000 or 100000; default 5000) and the number of days (1, 3, 7, 30, 90 or until the entry limit; default 7). You can switch the logging of connections and of
DNS queries off.

## Settings

| Setting | Meaning |
| --- | --- |
| Start on boot | Off by default. Turns Telos Network on after a restart, only if the VPN permission was given before and you left it on |
| IP version | IPv4 only, IPv6 only, or both (default). The other family bypasses the tunnel |
| Filter the local network | On by default. Sends LAN traffic through Telos Network so that LAN rules apply |
| Exclude Telos | Keeps Telos' own traffic out of the tunnel. On by default, recommended |
| Allow apps to bypass the VPN | On by default. Apps may use other networks directly |
| Mark the VPN as metered | Off by default. Tells Android the connection has limited data |
| Hold traffic without a network | Off by default. Blocks traffic while no network is available, instead of resuming by itself |
| MTU | Automatic (default), 1280, 1380, 1420 or 1500 |
| Notification | Minimal (default) or with the number of blocked connections; hide the content on the lock screen (on by default); alert when it stopped by itself (on by default) |
| Logs | Switches for connections and DNS queries (both on), maximum entries, days to keep |
| Always-on VPN | Opens the Android VPN settings. Telos never switches this on by itself |
| Battery optimisation | Opens the Android list, in case Android stops Telos Network in the background |

Network settings apply the next time Telos Network starts; **Restart now** applies them at once.

# Logs and settings

## Logs

Two logs, newest first: **connections** (time, app, protocol, address and port, the domain if known, allowed or blocked and why, the network, the tunnel, bytes
and duration) and **DNS queries** (time, app, name, record type, answer, server, delay, cached, blocked and by which list). You can search, filter by app and
by blocked or allowed, and clear them.

Logs stay on the phone and are limited by the number of entries (default 5000) and the number of days (default 7). You can switch the logging of connections and of
DNS queries off.

## Settings

| Setting | Meaning |
| --- | --- |
| Start on boot | Off by default. Turns Telos Network on after a restart, only if the VPN permission was given before and you left it on |
| IP version | IPv4 only, IPv6 only, or both (default). The other family bypasses the tunnel |
| Filter the local network | Sends LAN traffic through Telos Network so that LAN rules apply |
| Exclude Telos | Keeps Telos' own traffic out of the tunnel. Recommended |
| Allow apps to bypass the VPN | Apps may use other networks directly |
| Mark the VPN as metered | Tells Android the connection has limited data |
| Hold traffic without a network | Block while no network is available, instead of resuming by itself |
| MTU | Automatic (default) or a fixed value |
| Notification | Minimal, or with the number of blocked connections; hide on the lock screen; alert when it stopped by itself |
| Logs | Switches, maximum entries, days to keep |
| Always-on VPN | Opens the Android VPN settings. Telos never switches this on by itself |
| Battery optimisation | Opens the Android list, in case Android stops Telos Network in the background |

Network settings apply the next time Telos Network starts; **Restart now** applies them at once.

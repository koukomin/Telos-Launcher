# WireGuard

WireGuard runs inside the Telos Network engine. You do not need a second VPN app, and no kernel module: Telos Network *is* the VPN
and sends the apps you choose into a tunnel.

## Tunnels

- **Add tunnel** offers: **Create new** (by hand: name, private key with a **Generate** button, addresses, DNS servers, MTU, and one or more peers with public key, optional preshared key, endpoint, allowed IPs and keepalive), **Import .conf file**, **Import from QR code image** and **Paste config text**. Imports read the wg-quick format; lines Telos does not need (such as `PostUp` or `Table`) are ignored. Texts above 256 KB are refused.
- Per tunnel: switch it **on**, **Only on mobile data** (on other networks the apps go out directly) and **Block the apps while the tunnel is down** (lockdown, for a switched-on tunnel that is not up; with the tunnel switched off the apps go out directly).
- The screen shows the status (off, connecting, connected, not responding, error), received and sent bytes and the last handshake.
- **Share config** gives the `.conf` text back; it contains the private key. Private and preshared keys are stored encrypted with a key from the Android Keystore.
- A tunnel needs Telos Network to be on. The switch is remembered and applied when it starts.

## Which app uses which tunnel

| Choice | Meaning |
| --- | --- |
| System default | The app uses the tunnel set as system default (chosen on the WireGuard screen); with no default it goes out directly |
| Direct | The app never uses WireGuard, even when a default is set |
| A tunnel | The app always uses this tunnel |

The screen *Per-app assignment* (hub entry *WireGuard per app*) lists your apps with their choice; you can select several apps and assign them at once, and show system apps. Removing a tunnel sends its apps back to *System default*.

## Limits

The tunnel is used only after the firewall allowed a connection. DNS questions of the apps are not sent through the tunnel; they go out directly to the DNS server you chose.


## Torrents through a WireGuard config

In Settings > Downloads > Network > Proxy, **Torrents through Telos Network WireGuard** lets Telos Downloads and Telos Video send torrent traffic through one of your configs. Telos opens a local HTTP proxy bridged to that config; it exists only while Telos Network is on and the config is connected, otherwise torrents wait and nothing connects directly. It carries TCP only (no DHT or uTP), and other apps on the device could use the local proxy while it runs.

A VPN does not make you anonymous: the provider of the tunnel sees your traffic. Only use configurations from providers you trust.

# WireGuard

WireGuard runs inside the Telos Network engine. You do not need a second VPN app, and no kernel module: Telos Network *is* the VPN
and sends the apps you choose into a tunnel.

## Tunnels

- **Import** a `.conf` file (wg-quick format) or paste its text, or add a tunnel by hand (interface key and addresses, peers with
  allowed IPs, endpoint, preshared key, keepalive).
- Per tunnel: switch it **on**, use it **only on mobile data**, use it **only on these Wi-Fi names (SSIDs)**, and **lockdown**: if
  the tunnel is down, the apps that use it are blocked instead of going out directly.
- **Export** gives the `.conf` text back. Private keys are never written to the logs.
- A tunnel needs Telos Network to be on. The switch is remembered and applied when it starts.

## Which app uses which tunnel

| Choice | Meaning |
| --- | --- |
| System default | The app uses the tunnel set as system default; with no default it goes out directly |
| Direct | The app never uses WireGuard, even when a default is set |
| A tunnel | The app always uses this tunnel |

The screen *WireGuard per app* lists your apps with their choice. Removing a tunnel sends its apps back to *System default*.

## Limits

A VPN does not make you anonymous: the provider of the tunnel sees your traffic. Only use configurations from providers you trust.

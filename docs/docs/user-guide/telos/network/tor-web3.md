# Tor and Web3 names

Open **Telos Network → Tor / Web3**. These are separate tools: Tor routes network connections; Web3 lookup reads website and wallet records.

## Tor

Stop Telos Network before changing Tor mode, then turn the VPN on again. The bundled Tor client starts inside the same VPN; Orbot and a second VPN are not needed. The connection status is shown on this screen.

- `.onion` DNS queries seen by this VPN go only to the local Tor resolver. They never fall back to public DNS, including when Tor mode is off. Tor maps onion names to reserved virtual IPv4/IPv6 addresses, which the VPN sends only through Tor SOCKS.
- **Start Tor automatically** waits for a visible `.onion` DNS query instead of connecting immediately. Retry the website after Tor connects. This needs the VPN already running; private/encrypted DNS, cached requests and literal addresses cannot trigger it. Choosing any Tor-only apps starts Tor immediately so those apps can connect.
- **Apps using only Tor** applies to every package sharing the selected Android app ID, including profile instances. Their TCP connections and DNS use Tor. UDP/QUIC and incoming connections are blocked. While Tor starts or is unavailable, their connections stay blocked within the running VPN. If Android cannot identify a connection owner while any Tor-only rule exists, it is conservatively sent through Tor too.
- Tor mode captures both IP families and LAN routes, disables VPN bypass and other app exclusions. Telos itself remains excluded so the embedded Tor client can reach relays without looping through its own VPN. Thus this is device-wide for the other apps routed into this VPN, **not for Telos itself**. Web3 lookups made by Telos are also outside the VPN.
- A Tor-only rule does not override a firewall block or a blocking WireGuard assignment. Regular allowed apps continue to use their normal route for ordinary domains; turning on Tor mode does not send all internet traffic through Tor.

The Tor component does not stop the VPN on a bootstrap failure. However, a stopped, revoked or crashed VPN cannot enforce these rules. For protection across VPN failure use Android **Always-on VPN** and **Block connections without VPN**. Telos never changes those Android settings automatically. Android lockdown can also prevent the excluded Telos/Tor UID from reaching relays on some devices; test this combination before relying on it. Embedded Tor, network changes, lockdown, IPv6 and large-font layouts still need physical-device validation.

Tor does not make an ordinary browser equivalent to Tor Browser: browser fingerprinting and browser storage are outside this network feature. No bridges or pluggable transports are configured by this integration.

## Web3 websites and wallets

Enter an ASCII name and press **Search**. Unicode ENS normalization is not implemented; Unicode names are rejected rather than transformed with an incompatible DNS normalizer. Websites and wallet records appear separately; copy an address or explicitly open the displayed HTTPS destination. No wallet is connected and no transaction is signed.

| Namespace | Wallet lookup | Website lookup |
| --- | --- | --- |
| `.eth` | Public ENS Ideas API, Ethereum address record | eth.limo DNSLink TXT lookup; open `<name>.limo` only when a content record exists |
| Supported Unstoppable Domains suffixes shown in the screen, including `.crypto` | Your configured HTTPS backend proxy, `crypto.*.address` records | `dweb.ipfs.hash` via the public `ipfs.io` gateway, or an HTTPS `browser.redirect_url` |

ENS Ideas and eth.limo receive the queried name and Telos' connection IP. The configured proxy receives UD names and your IP. These are provider-mediated lookups, not independently verified blockchain proofs. An ENS wallet-provider outage does not hide a successful website record, or vice versa; the screen marks a partial provider error. No records and provider errors are shown separately.

For Unstoppable Domains, enter the base URL of **your own HTTPS proxy**, for example `https://resolver.example/resolve/domains`. Telos requests `<base>/<name>` and expects the official Resolution API JSON format with a `records` object. The backend holds the Unstoppable API key and forwards to `https://api.unstoppabledomains.com/resolve/domains/<name>`. Telos does not ship an API key, accept an API key in the app, or deploy a proxy for you. Redirects from resolver API requests are not followed. Website destinations are displayed before opening; HTTP, script URLs and URLs containing credentials are rejected.

There is no universal resolver for every blockchain namespace. Handshake, `.bit`, arbitrary emerging suffixes and non-ASCII ENS names are not supported. Web3 lookup does not transparently rewrite HTTPS traffic or DNS across other apps; such rewriting would break certificate validation for the original name. It opens an explicit gateway URL instead.

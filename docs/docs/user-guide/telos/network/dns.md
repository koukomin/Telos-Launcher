# DNS

Every app asks a DNS server for the address of a name. Telos Network answers those questions with the server you choose.

## Servers

| Kind | Meaning |
| --- | --- |
| System | The DNS of the current network (not encrypted). Cannot be removed. This is the default |
| Plain | DNS over UDP/TCP to an IP address, for example `9.9.9.9` |
| DNS over HTTPS | An `https://` address |
| DNS over TLS | `host` or `tls://host:port` |
| DNSCrypt | An `sdns://` stamp, optionally with a relay stamp |
| Oblivious DoH | A resolver address and a proxy address |
| DNS proxy | A DNS server on this phone or in your network, as `ip:port` |

Telos ships a list of well known providers; **Mullvad is not offered**. You can add your own servers; the address is checked for its
kind before it is saved. The list shows whether the engine could use a server (working or failing).

Choosing a server applies at once while Telos Network is on. If a chosen server cannot be registered in the engine, the system DNS is used instead, so
names still resolve.

## Blocklists and rules

DNS questions are also where [blocklists](blocklists-bypass.md) and domain rules ([Firewall](firewall.md#custom-rules)) apply.
A blocked name gets an answer that tells the app it does not exist, without asking the server.

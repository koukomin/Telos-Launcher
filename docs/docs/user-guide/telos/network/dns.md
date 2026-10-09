# DNS

Every app asks a DNS server for the address of a name. Telos Network answers those questions with the server you choose.

## Servers

| Kind | Meaning |
| --- | --- |
| System | The DNS of the current network (not encrypted). Cannot be removed. This is the default |
| Plain | DNS to one or more IP addresses (comma separated), for example `9.9.9.9`. Not encrypted |
| DNS over HTTPS | An `https://` address. `http://` is accepted only for local hosts (see below) |
| DNS over TLS | `host` or `tls://host:port` |
| DNSCrypt | An `sdns://` stamp, optionally with a relay (a built-in relay or a relay stamp) |
| Oblivious DoH | An `https://` target address and an optional `https://` relay. Without a relay the target sees your IP address |
| DNS proxy | A DNS server on this phone or in your network, as IP address and port (`ip:port`) |

::: warning DoH needs https://
A custom DNS-over-HTTPS server must use `https://`. A plain `http://` address is accepted only when the host is on
your own device or network: loopback, link-local, private addresses (RFC 1918 and ULA), names ending in `.local`,
`.lan`, `.home.arpa` or `.internal`, and single-label hosts. Any other `http://` address is refused with an error.
Servers you saved earlier that break this rule keep working until you edit them.
:::

Telos ships a list of well known providers, grouped as ad blocking, malware protection, family filter, privacy-focused and no filtering, with filter chips on top; **Mullvad is not offered**. You can add your own servers (**Add custom server**); the address is checked for its
kind before it is saved. Custom servers can be edited and removed; built-in servers can be copied as a custom server. **Test** sends a request to a server and shows the answer time. The list shows whether the engine could use a server (working or failing). **Set up NextDNS** builds the address from your configuration ID (4 to 12 letters and digits) and an optional device name.

Choosing a server applies at once while Telos Network is on. If a chosen server cannot be registered in the engine, the system DNS is used instead, so
names still resolve.

## Blocklists and rules

DNS questions are also where [blocklists](blocklists-bypass.md) and domain rules ([Firewall](firewall.md#custom-rules)) apply.
A name blocked by a blocklist gets an empty answer (an unspecified address, `0.0.0.0` or `::`). A name blocked by a domain rule or by *Block everything* for the app is not sent to the server at all. DNS-over-TLS (port 853) questions sent to the tunnel's own DNS address are blocked, so apps use the plain question that Telos can answer.

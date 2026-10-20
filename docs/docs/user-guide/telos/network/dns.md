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

## NextDNS account editor

Open **Telos Network → NextDNS** or **DNS → NextDNS editor**. Enter a six-character profile ID and the API key from [your NextDNS account](https://my.nextdns.io/account). The key is validated against the selected profile before being saved in Android Keystore-encrypted app-private storage excluded from backup. It is masked on screen, not saved in UI instance state, not logged, and sent only to the official HTTPS API without redirects.

The editor shows profile DNS history, domain search and a blocked-only filter. Load more retrieves the next page, with at most 1,000 history entries retained in memory. History availability depends on NextDNS logging settings and retention. It shows traffic from the entire profile, including other devices, rather than claiming all queries came from this phone. No background history polling or disk history cache is added.

Open **Allowlist** or **Denylist** to inspect entries, add a domain, toggle an entry or remove it with confirmation. Domains are normalized to ASCII IDN form and invalid URLs, wildcards and header/path injection are refused. Changes affect every device using this profile. Opposing-list entries are not silently removed. Each list is fetched completely up to a 2,000-entry limit; oversized or malformed responses are reported rather than silently presenting an incomplete list. Mutations are not automatically retried. NextDNS application errors are handled even when returned with HTTP 200.

**Use this profile for DNS** explicitly adds/reuses its `https://dns.nextdns.io/<profile>` server and selects it in Telos Network. It does not start the VPN or change Android Private DNS. Connecting the editor alone does not select a resolver. **Remove saved key** clears the encrypted credential and its dedicated encryption key; it does not remove server-side rules or change the currently selected DNS server. This action also permits recovery from an invalid local credential vault.

This completes the Copilot editor idea inside the existing Network service and Compose navigation. The incomplete standalone `app:network` module is superseded; it is not packaged alongside a second Network provider. The API contract follows [official NextDNS documentation](https://nextdns.github.io/api/). Contract tests use fixtures and fake transports; a live user API key was not available for end-to-end verification.

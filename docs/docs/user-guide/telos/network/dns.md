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

## Android system Private DNS

The separate **Network DNS (system)** screen changes Android's system-wide Private DNS mode: Automatic, Off or a provider hostname. You can add its **Private DNS** Quick Settings tile for a direct Automatic/Off toggle. Reading and applying this setting requires an explicitly granted Shizuku permission; when permission is missing, the tile opens the settings screen instead of silently failing. Provider names are validated and changes are read back for verification. This setting is distinct from the DNS server selected for Telos Network's VPN; changing one does not select or start the other. Because Android and OEM VPN behavior varies, system Private DNS may conflict with DNS handling in a running VPN. Use this control only when you intend to change the device-wide Android setting.

### Wi-Fi rules

On the same screen, add an exact Wi-Fi network name (SSID) and choose Automatic, Off or a provider hostname. Rules are stored on this device and are not uploaded. Enable **Wi-Fi DNS automation** to start the visible foreground monitor; it continues while the VPN is off and can restart after a reboot when Android permits and its required permissions remain granted. Shizuku, location access (to read the connected SSID) and notification permission are required.

When a rule first matches, Telos saves the current system Private DNS setting and applies the rule. When the phone leaves that Wi-Fi or monitoring stops, Telos restores the saved setting only if Android still reports the last value Telos applied. If you changed Private DNS manually in the meantime, Telos leaves your change alone. If Android/OEM restrictions hide the SSID, the monitor does not guess or apply a rule. These are system-wide settings and may conflict with DNS routing in another VPN.

## Blocklists and rules

DNS questions are also where [blocklists](blocklists-bypass.md) and domain rules ([Firewall](firewall.md#custom-rules)) apply.
A name blocked by a blocklist gets an empty answer (an unspecified address, `0.0.0.0` or `::`). A name blocked by a domain rule or by *Block everything* for the app is not sent to the server at all. DNS-over-TLS (port 853) questions sent to the tunnel's own DNS address are blocked, so apps use the plain question that Telos can answer.

## NextDNS account editor

Open **Telos Network → NextDNS** or **DNS → NextDNS editor**. Enter a six-character profile ID and the API key from [your NextDNS account](https://my.nextdns.io/account). The key is validated against the selected profile before being saved in Android Keystore-encrypted app-private storage excluded from backup. It is masked on screen, not saved in UI instance state, not logged, and sent only to the official HTTPS API without redirects.

The editor shows profile DNS history, domain search and a blocked-only filter. Load more retrieves the next page, with at most 1,000 history entries retained in memory. History availability depends on NextDNS logging settings and retention. It shows traffic from the entire profile, including other devices, rather than claiming all queries came from this phone. No background history polling or disk history cache is added.

The **Status** tab queries NextDNS analytics for 30 minutes, 6 hours, 24 hours, 7 days, 30 days or 3 months. Choose a profile device or **All**, then select the analytics category; the dashboard shows the returned total, bounded series and ranked rows where provided. Device names and analytics belong to the whole profile. The API key is sent only to the official NextDNS API, and analytics are fetched on demand rather than polled.

Open **Allowlist** or **Denylist** to inspect entries, add a domain, toggle an entry or remove it with confirmation. Domains are normalized to ASCII IDN form and invalid URLs, wildcards and header/path injection are refused. Changes affect every device using this profile. Opposing-list entries are not silently removed. Each list is fetched completely up to a 2,000-entry limit; oversized or malformed responses are reported rather than silently presenting an incomplete list. Mutations are not automatically retried. NextDNS application errors are handled even when returned with HTTP 200.

The **Domain rule** tab also lists NextDNS rewrites and lets you add a domain-to-answer mapping or remove one with confirmation. Names and answer values are validated before sending; the answer may be an IP address or a domain.

The **Network settings** tab exposes the profile's Security, Privacy, Family and General settings. It currently edits only boolean values returned by the API; other value types and unknown fields are left untouched. Each switch sends a nested PATCH for that single field and reloads the selected settings page.

**Use this profile for DNS** explicitly adds/reuses its `https://dns.nextdns.io/<profile>` server and selects it in Telos Network. It does not start the VPN or change Android Private DNS. Connecting the editor alone does not select a resolver. **Remove saved key** clears the encrypted credential and its dedicated encryption key; it does not remove server-side rules or change the currently selected DNS server. This action also permits recovery from an invalid local credential vault.

This completes the Copilot editor idea inside the existing Network service and Compose navigation. The incomplete standalone `app:network` module is superseded; it is not packaged alongside a second Network provider. The API contract follows [official NextDNS documentation](https://nextdns.github.io/api/). Contract tests use fixtures and fake transports; a live user API key was not available for end-to-end verification.

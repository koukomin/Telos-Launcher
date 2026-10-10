# Blocklists and exceptions

Blocklists are lists of domains that Telos Network answers with "does not exist". They work in the DNS layer, so they need Telos Network to be on and
the apps to use its DNS.

## Lists

- The lists are the [Rethink DNS](https://github.com/celzero/rethink-app) blocklists from `dl.rethinkdns.com`. They come in three sections, **Parental controls**, **Security** and **Privacy** (plus **Other**), each with groups such as adult content, piracy, gambling, threat intelligence, cryptojacking, social networks, tracking domains and native trackers. Switch a whole group or single lists on or off; each list shows its number of domains.
- Nothing is downloaded before you ask: use **Download blocklists**. Afterwards **Check for updates** and **Update now** keep them current. The screen shows the progress, the
  date of the last update, the storage used and errors. **Delete downloaded lists** removes them.
- **Update automatically** (off until you switch it on, available after the first download) checks once a day on an unmetered network.
- Each list counts how many queries it blocked. **Reset counters** starts again (after a confirmation).

## Exceptions (bypass)

If a list blocks something you need, add the name under **Allowed domains**: `example.com` (this name only) or `*.example.com` (the name and all subdomains), for **all apps** or for **one app**. The name is then
resolved without the blocklists. Exceptions do not skip the firewall rules; for that, use a *Trust* rule under [Custom rules](firewall.md#custom-rules).

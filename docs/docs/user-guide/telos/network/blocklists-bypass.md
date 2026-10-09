# Blocklists and exceptions

Blocklists are lists of domains that Telos Network answers with "does not exist". They work in the DNS layer, so they need Telos Network to be on and
the apps to use its DNS.

## Lists

- The lists come in **groups** (ads, trackers, malware, adult, social, gambling, other). Switch a whole group or single lists on or off.
- Nothing is downloaded before you ask: use **Update** to fetch the catalog and the lists you switched on. The screen shows the progress, the
  date of the last update and errors.
- Each list counts how many queries it blocked. **Reset counters** starts again.

## Exceptions (bypass)

If a list blocks something you need, add the name as an exception: `example.com` or `*.example.com`, for **all apps** or for **one app**. The name is then
resolved without the blocklists. Exceptions do not skip the firewall rules; for that, use a *Trust* rule under [Custom rules](firewall.md#custom-rules).

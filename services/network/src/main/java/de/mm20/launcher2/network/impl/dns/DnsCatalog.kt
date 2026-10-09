/*
 * The selection of Cloudflare, Google, Quad9, AdGuard, CleanBrowsing, ControlD and Rethink DNS
 * endpoints, the DNSCrypt stamps, the DNSCrypt relays and the ODoH targets are taken from the
 * prepopulated endpoint lists of RethinkDNS (https://github.com/celzero/rethink-app).
 *
 * Copyright 2020 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.mm20.launcher2.network.impl.dns

import de.mm20.launcher2.network.api.DnsKind
import de.mm20.launcher2.network.api.DnsServer
import de.mm20.launcher2.network.api.DnsTag

/** A DNSCrypt relay that can be put in front of a DNSCrypt server (Anonymized DNSCrypt). */
data class DnsRelay(val id: String, val name: String, val stamp: String)

/**
 * The built-in encrypted DNS servers. Every entry has a fixed id, so that the selection survives
 * updates. Only endpoints that are known to exist are listed. Descriptions are plain English notes
 * for developers; the app shows texts derived from [DnsServer.tags].
 */
object DnsCatalog {
    private val A = DnsTag.Adblock
    private val M = DnsTag.Malware
    private val F = DnsTag.Family
    private val P = DnsTag.Privacy
    private val U = DnsTag.Unfiltered

    private fun ips(vararg v: String) = v.toList()

    private fun doh(id: String, provider: String, name: String, url: String, b: List<String>, desc: String, vararg tags: DnsTag) =
        DnsServer(id, DnsKind.Doh, name, url, null, b, desc, true, provider, tags.toList())

    private fun dot(id: String, provider: String, name: String, host: String, b: List<String>, desc: String, vararg tags: DnsTag) =
        DnsServer(id, DnsKind.Dot, name, "tls://$host", null, b, desc, true, provider, tags.toList())

    private fun crypt(id: String, provider: String, name: String, stamp: String, desc: String, vararg tags: DnsTag) =
        DnsServer(id, DnsKind.DnsCrypt, name, stamp, null, emptyList(), desc, true, provider, tags.toList())

    private fun odoh(id: String, provider: String, name: String, target: String, desc: String, vararg tags: DnsTag) =
        DnsServer(id, DnsKind.Odoh, name, target, "", emptyList(), desc, true, provider, tags.toList())

    private fun plain(id: String, provider: String, name: String, addrs: String, desc: String, vararg tags: DnsTag) =
        DnsServer(id, DnsKind.Plain, name, addrs, null, emptyList(), desc, true, provider, tags.toList())

    private val cf = ips("1.1.1.1", "1.0.0.1", "2606:4700:4700::1111", "2606:4700:4700::1001")
    private val cfSec = ips("1.1.1.2", "1.0.0.2", "2606:4700:4700::1112", "2606:4700:4700::1002")
    private val cfFam = ips("1.1.1.3", "1.0.0.3", "2606:4700:4700::1113", "2606:4700:4700::1003")
    private val google = ips("8.8.8.8", "8.8.4.4", "2001:4860:4860::8888", "2001:4860:4860::8844")
    private val q9 = ips("9.9.9.9", "149.112.112.112", "2620:fe::fe", "2620:fe::9")
    private val q9Open = ips("9.9.9.10", "149.112.112.10", "2620:fe::10", "2620:fe::fe:10")
    private val q9Ecs = ips("9.9.9.11", "149.112.112.11", "2620:fe::11", "2620:fe::fe:11")
    private val ag = ips("94.140.14.14", "94.140.15.15", "2a10:50c0::ad1:ff", "2a10:50c0::ad2:ff")
    private val agUnf = ips("94.140.14.140", "94.140.14.141", "2a10:50c0::1:ff", "2a10:50c0::2:ff")
    private val agFam = ips("94.140.14.15", "94.140.15.16", "2a10:50c0::bad1:ff", "2a10:50c0::bad2:ff")
    private val cbFam = ips("185.228.168.168", "185.228.169.168", "2a0d:2a00:1::", "2a0d:2a00:2::")
    private val cbAdult = ips("185.228.168.10", "185.228.169.11", "2a0d:2a00:1::1", "2a0d:2a00:2::1")
    private val cbSec = ips("185.228.168.9", "185.228.169.9", "2a0d:2a00:1::2", "2a0d:2a00:2::2")
    private val dnsSb = ips("185.222.222.222", "45.11.45.11", "2a09::", "2a11::")
    private val odns = ips("208.67.222.222", "208.67.220.220", "2620:119:35::35", "2620:119:53::53")
    private val odnsFam = ips("208.67.222.123", "208.67.220.123")
    private val cd = ips("76.76.2.11", "2606:1a40::11")
    private val libre = ips("116.202.176.26")
    private val dns0 = ips("193.110.81.0", "185.253.5.0", "2a0f:fc80::", "2a0f:fc81::")
    private val dns0Zero = ips("193.110.81.9", "185.253.5.9", "2a0f:fc80::9", "2a0f:fc81::9")
    private val dns0Kids = ips("193.110.81.1", "185.253.5.1", "2a0f:fc80::1", "2a0f:fc81::1")
    private val switch = ips("130.59.31.248", "2001:620:0:ff::2")
    private val dg = ips("185.95.218.42", "185.95.218.43")
    private val ap = ips("146.255.56.98", "2a02:1b8:10:234::2")
    private val rethink = ips("172.67.70.254", "104.26.14.30", "104.26.15.30", "2606:4700:20::681a:e1e", "2606:4700:20::ac43:46fe", "2606:4700:20::681a:f1e")
    private val rethinkZero = ips("104.21.83.62", "104.26.15.30", "172.67.70.254", "2606:4700:20::ac43:46fe", "2606:4700:20::681a:f1e", "2606:4700:20::681a:e1e")

    /** DNSCrypt relays from the list of RethinkDNS. */
    val dnscryptRelays: List<DnsRelay> = listOf(
        DnsRelay("relay-nl", "Netherlands", "sdns://gRI1MS4xNS4xMjQuMjA4OjQzNDM"),
        DnsRelay("relay-fr", "France", "sdns://gREyMTIuMTI5LjQ2LjMyOjQ0Mw"),
        DnsRelay("relay-se", "Sweden", "sdns://gRMxMjguMTI3LjEwNC4xMDg6NDQz"),
        DnsRelay("relay-us", "US - Los Angeles", "sdns://gRAyMy4xOS42Ny4xMTY6NDQz"),
        DnsRelay("relay-sg", "Singapore", "sdns://gRMxNzQuMTM4LjI5LjE3NToxNDQz"),
    )

    val servers: List<DnsServer> = listOf(
        // Cloudflare
        doh("cloudflare-doh", "Cloudflare", "Cloudflare", "https://cloudflare-dns.com/dns-query", cf, "No filtering", P, U),
        dot("cloudflare-dot", "Cloudflare", "Cloudflare (TLS)", "one.one.one.one", cf, "No filtering", P, U),
        doh("cloudflare-security-doh", "Cloudflare", "Cloudflare Security", "https://security.cloudflare-dns.com/dns-query", cfSec, "Blocks malware and phishing", M, P),
        dot("cloudflare-security-dot", "Cloudflare", "Cloudflare Security (TLS)", "security.cloudflare-dns.com", cfSec, "Blocks malware and phishing", M, P),
        doh("cloudflare-family-doh", "Cloudflare", "Cloudflare Family", "https://family.cloudflare-dns.com/dns-query", cfFam, "Blocks malware and adult content", F, M),
        dot("cloudflare-family-dot", "Cloudflare", "Cloudflare Family (TLS)", "family.cloudflare-dns.com", cfFam, "Blocks malware and adult content", F, M),
        plain("cloudflare-plain", "Cloudflare", "Cloudflare (plain)", "1.1.1.1,1.0.0.1,2606:4700:4700::1111,2606:4700:4700::1001", "No filtering, not encrypted", U),
        // Google
        doh("google-doh", "Google", "Google", "https://dns.google/dns-query", google, "No filtering", U),
        dot("google-dot", "Google", "Google (TLS)", "dns.google", google, "No filtering", U),
        plain("google-plain", "Google", "Google (plain)", "8.8.8.8,8.8.4.4,2001:4860:4860::8888,2001:4860:4860::8844", "No filtering, not encrypted", U),
        // Quad9
        doh("quad9-doh", "Quad9", "Quad9 Secured", "https://dns.quad9.net/dns-query", q9, "Blocks malicious domains, DNSSEC", M, P),
        dot("quad9-dot", "Quad9", "Quad9 Secured (TLS)", "dns.quad9.net", q9, "Blocks malicious domains, DNSSEC", M, P),
        doh("quad9-ecs-doh", "Quad9", "Quad9 Secured with ECS", "https://dns11.quad9.net/dns-query", q9Ecs, "Blocks malicious domains, sends EDNS client subnet", M, P),
        dot("quad9-ecs-dot", "Quad9", "Quad9 Secured with ECS (TLS)", "dns11.quad9.net", q9Ecs, "Blocks malicious domains, sends EDNS client subnet", M, P),
        doh("quad9-unsecured-doh", "Quad9", "Quad9 Unsecured", "https://dns10.quad9.net/dns-query", q9Open, "No filtering", P, U),
        dot("quad9-unsecured-dot", "Quad9", "Quad9 Unsecured (TLS)", "dns10.quad9.net", q9Open, "No filtering", P, U),
        crypt("quad9-crypt", "Quad9", "Quad9 Secured (DNSCrypt)", "sdns://AQMAAAAAAAAADDkuOS45Ljk6ODQ0MyBnyEe4yHWM0SAkVUO-dWdG3zTfHYTAC4xHA2jfgh2GPhkyLmRuc2NyeXB0LWNlcnQucXVhZDkubmV0", "Blocks malicious domains, DNSSEC", M, P),
        crypt("quad9-unsecured-crypt", "Quad9", "Quad9 Unsecured (DNSCrypt)", "sdns://AQcAAAAAAAAADTkuOS45LjEwOjg0NDMgZ8hHuMh1jNEgJFVDvnVnRt803x2EwAuMRwNo34Idhj4ZMi5kbnNjcnlwdC1jZXJ0LnF1YWQ5Lm5ldA", "No filtering", P, U),
        plain("quad9-plain", "Quad9", "Quad9 (plain)", "9.9.9.9,149.112.112.112,2620:fe::fe,2620:fe::9", "Blocks malicious domains, not encrypted", M),
        // AdGuard
        doh("adguard-doh", "AdGuard", "AdGuard DNS", "https://dns.adguard-dns.com/dns-query", ag, "Blocks ads, trackers and phishing", A, M),
        dot("adguard-dot", "AdGuard", "AdGuard DNS (TLS)", "dns.adguard-dns.com", ag, "Blocks ads, trackers and phishing", A, M),
        crypt("adguard-crypt", "AdGuard", "AdGuard DNS (DNSCrypt)", "sdns://AQMAAAAAAAAAETk0LjE0MC4xNC4xNDo1NDQzINErR_JS3PLCu_iZEIbq95zkSV2LFsigxDIuUso_OQhzIjIuZG5zY3J5cHQuZGVmYXVsdC5uczEuYWRndWFyZC5jb20", "Blocks ads, trackers and phishing", A, M),
        doh("adguard-family-doh", "AdGuard", "AdGuard DNS Family", "https://family.adguard-dns.com/dns-query", agFam, "Blocks ads, adult content, enforces safe search", F, A),
        dot("adguard-family-dot", "AdGuard", "AdGuard DNS Family (TLS)", "family.adguard-dns.com", agFam, "Blocks ads, adult content, enforces safe search", F, A),
        crypt("adguard-family-crypt", "AdGuard", "AdGuard DNS Family (DNSCrypt)", "sdns://AQMAAAAAAAAAETk0LjE0MC4xNC4xNTo1NDQzILgxXdexS27jIKRw3C7Wsao5jMnlhvhdRUXWuMm1AFq6ITIuZG5zY3J5cHQuZmFtaWx5Lm5zMS5hZGd1YXJkLmNvbQ", "Blocks ads, adult content, enforces safe search", F, A),
        doh("adguard-unfiltered-doh", "AdGuard", "AdGuard DNS Non-filtering", "https://unfiltered.adguard-dns.com/dns-query", agUnf, "No filtering", U),
        dot("adguard-unfiltered-dot", "AdGuard", "AdGuard DNS Non-filtering (TLS)", "unfiltered.adguard-dns.com", agUnf, "No filtering", U),
        // CleanBrowsing
        doh("cleanbrowsing-family-doh", "CleanBrowsing", "CleanBrowsing Family", "https://doh.cleanbrowsing.org/doh/family-filter/", cbFam, "Blocks adult content, proxies and VPN domains, safe search", F),
        dot("cleanbrowsing-family-dot", "CleanBrowsing", "CleanBrowsing Family (TLS)", "family-filter-dns.cleanbrowsing.org", cbFam, "Blocks adult content, proxies and VPN domains, safe search", F),
        crypt("cleanbrowsing-family-crypt", "CleanBrowsing", "CleanBrowsing Family (DNSCrypt)", "sdns://AQMAAAAAAAAAFDE4NS4yMjguMTY4LjE2ODo4NDQzILysMvrVQ2kXHwgy1gdQJ8MgjO7w6OmflBjcd2Bl1I8pEWNsZWFuYnJvd3Npbmcub3Jn", "Blocks adult content, proxies and VPN domains, safe search", F),
        doh("cleanbrowsing-adult-doh", "CleanBrowsing", "CleanBrowsing Adult", "https://doh.cleanbrowsing.org/doh/adult-filter/", cbAdult, "Blocks adult content, allows mixed-content sites", F),
        dot("cleanbrowsing-adult-dot", "CleanBrowsing", "CleanBrowsing Adult (TLS)", "adult-filter-dns.cleanbrowsing.org", cbAdult, "Blocks adult content, allows mixed-content sites", F),
        doh("cleanbrowsing-security-doh", "CleanBrowsing", "CleanBrowsing Security", "https://doh.cleanbrowsing.org/doh/security-filter/", cbSec, "Blocks phishing, malware and malicious domains", M),
        dot("cleanbrowsing-security-dot", "CleanBrowsing", "CleanBrowsing Security (TLS)", "security-filter-dns.cleanbrowsing.org", cbSec, "Blocks phishing, malware and malicious domains", M),
        // DNS.SB
        doh("dnssb-doh", "DNS.SB", "DNS.SB", "https://doh.dns.sb/dns-query", dnsSb, "No filtering", P, U),
        dot("dnssb-dot", "DNS.SB", "DNS.SB (TLS)", "dot.sb", dnsSb, "No filtering", P, U),
        // OpenDNS
        doh("opendns-doh", "OpenDNS", "OpenDNS", "https://doh.opendns.com/dns-query", odns, "Blocks phishing", M),
        doh("opendns-family-doh", "OpenDNS", "OpenDNS FamilyShield", "https://doh.familyshield.opendns.com/dns-query", odnsFam, "Blocks adult content and phishing", F, M),
        // ControlD (free)
        doh("controld-unfiltered-doh", "Control D", "Control D Unfiltered", "https://freedns.controld.com/p0", cd, "No filtering", U),
        dot("controld-unfiltered-dot", "Control D", "Control D Unfiltered (TLS)", "p0.freedns.controld.com", cd, "No filtering", U),
        doh("controld-malware-doh", "Control D", "Control D Malware", "https://freedns.controld.com/p1", cd, "Blocks malware", M),
        dot("controld-malware-dot", "Control D", "Control D Malware (TLS)", "p1.freedns.controld.com", cd, "Blocks malware", M),
        doh("controld-ads-doh", "Control D", "Control D Ads & Trackers", "https://freedns.controld.com/p2", cd, "Blocks ads, spyware and trackers", A),
        dot("controld-ads-dot", "Control D", "Control D Ads & Trackers (TLS)", "p2.freedns.controld.com", cd, "Blocks ads, spyware and trackers", A),
        doh("controld-social-doh", "Control D", "Control D Ads, Malware & Social", "https://freedns.controld.com/p3", cd, "Blocks malware, ads, trackers and social media", A, M),
        dot("controld-social-dot", "Control D", "Control D Ads, Malware & Social (TLS)", "p3.freedns.controld.com", cd, "Blocks malware, ads, trackers and social media", A, M),
        doh("controld-family-doh", "Control D", "Control D Family", "https://freedns.controld.com/family", cd, "Family filter", F),
        dot("controld-family-dot", "Control D", "Control D Family (TLS)", "family.freedns.controld.com", cd, "Family filter", F),
        // LibreDNS
        doh("libredns-doh", "LibreDNS", "LibreDNS", "https://doh.libredns.gr/dns-query", libre, "No filtering", P, U),
        dot("libredns-dot", "LibreDNS", "LibreDNS (TLS)", "dot.libredns.gr", libre, "No filtering", P, U),
        doh("libredns-ads-doh", "LibreDNS", "LibreDNS Ads Blocking", "https://doh.libredns.gr/ads", libre, "Blocks ads and trackers", A, P),
        // dns0.eu
        doh("dns0-doh", "dns0.eu", "dns0.eu", "https://dns0.eu/", dns0, "European resolver, no filtering", P, U),
        dot("dns0-dot", "dns0.eu", "dns0.eu (TLS)", "dns0.eu", dns0, "European resolver, no filtering", P, U),
        doh("dns0-zero-doh", "dns0.eu", "dns0.eu ZERO", "https://zero.dns0.eu/", dns0Zero, "Blocks malware, phishing and newly registered domains", M, P),
        dot("dns0-zero-dot", "dns0.eu", "dns0.eu ZERO (TLS)", "zero.dns0.eu", dns0Zero, "Blocks malware, phishing and newly registered domains", M, P),
        doh("dns0-kids-doh", "dns0.eu", "dns0.eu Kids", "https://kids.dns0.eu/", dns0Kids, "Blocks adult content and malware", F, M),
        dot("dns0-kids-dot", "dns0.eu", "dns0.eu Kids (TLS)", "kids.dns0.eu", dns0Kids, "Blocks adult content and malware", F, M),
        // Swiss and privacy organisations
        doh("switch-doh", "Switch", "Switch", "https://dns.switch.ch/dns-query", switch, "Swiss academic network resolver", P),
        dot("switch-dot", "Switch", "Switch (TLS)", "dns.switch.ch", switch, "Swiss academic network resolver", P),
        doh("digitale-gesellschaft-doh", "Digitale Gesellschaft", "Digitale Gesellschaft", "https://dns.digitale-gesellschaft.ch/dns-query", dg, "No filtering", P, U),
        dot("digitale-gesellschaft-dot", "Digitale Gesellschaft", "Digitale Gesellschaft (TLS)", "dns.digitale-gesellschaft.ch", dg, "No filtering", P, U),
        doh("applied-privacy-doh", "Applied Privacy", "Applied Privacy", "https://doh.applied-privacy.net/query", ap, "No filtering", P, U),
        dot("applied-privacy-dot", "Applied Privacy", "Applied Privacy (TLS)", "dot1.applied-privacy.net", ap, "No filtering", P, U),
        // Rethink DNS
        doh("rethink-standard-doh", "Rethink DNS", "Rethink DNS", "https://zero.rethinkdns.com/dns-query", rethinkZero, "No blocklists", P, U),
        doh("rethink-basic-doh", "Rethink DNS", "Rethink DNS with blocklists", "https://basic.rethinkdns.com/1:YBcgAIAQIAAIAABgIAA=", rethink, "Blocks malware and more", M, P),
        // ODoH targets (the relay is chosen by the user)
        odoh("cloudflare-odoh", "Cloudflare", "Cloudflare (ODoH)", "https://odoh.cloudflare-dns.com/dns-query", "ODoH target, no filtering", P, U),
        odoh("odoh-crypto", "ODoH Crypto", "ODoH Crypto", "https://odoh.crypto.sx/dns-query", "ODoH target, no filtering", P, U),
        odoh("odoh-ibksturm", "Ibksturm", "Ibksturm (ODoH)", "https://ibksturm.synology.me/dns-query", "ODoH target, no filtering", P, U),
        // DNS proxy
        DnsServer(
            "orbot-dns", DnsKind.DnsProxy, "Orbot", "127.0.0.1:5400", null, emptyList(),
            "DNS over Tor, needs Orbot with its DNS port enabled", true, "Orbot", listOf(P),
        ),
    )
}

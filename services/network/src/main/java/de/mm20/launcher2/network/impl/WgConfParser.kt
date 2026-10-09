/*
 * The userspace format (toUserspace) follows Rethink DNS: Config.toWgUserspaceString,
 * WgInterface.toWgUserspaceString and Peer.toWgUserspaceString.
 *
 * Copyright 2023 RethinkDNS and its authors
 * Copyright (c) 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Adapted for Telos Network: a new, shorter parser for the wg-quick format.
 */
package de.mm20.launcher2.network.impl

import android.util.Base64
import de.mm20.launcher2.network.api.WgConfigError
import de.mm20.launcher2.network.api.WgConfigException
import de.mm20.launcher2.network.api.WgInterface
import de.mm20.launcher2.network.api.WgPeer
import de.mm20.launcher2.network.api.WireguardConfig
import de.mm20.launcher2.network.impl.wg.WgValidator

/** Reads and writes the wg-quick `.conf` format, and converts configs to the userspace format of firestack. */
internal object WgConfParser {

    const val MAX_TEXT = 256 * 1024

    /**
     * Parses a wg-quick file. Repeated `Address`, `DNS` and `AllowedIPs` lines are merged like wg-quick
     * does. Keys that are not needed here (PreUp, PostUp, Table, SaveConfig, Amnezia obfuscation
     * parameters...) are ignored. The result is validated; the id is 0 and the config is disabled.
     * @throws WgConfigException when the text is not a usable config
     */
    fun parse(text: String, name: String?): WireguardConfig {
        if (text.length > MAX_TEXT) throw WgConfigException(WgConfigError.TooLarge)
        var section = ""
        var comment: String? = null
        var hasInterface = false
        val iface = LinkedHashMap<String, String>()
        val peers = mutableListOf<LinkedHashMap<String, String>>()
        for (raw in text.removePrefix("﻿").lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#") || line.startsWith(";")) {
                if (comment == null && !hasInterface) comment = line.trimStart('#', ';').trim().takeIf { it.isNotEmpty() }
                continue
            }
            val noComment = line.substringBefore('#').trim()
            if (noComment.startsWith("[") && noComment.endsWith("]")) {
                section = noComment.substring(1, noComment.length - 1).trim().lowercase()
                when (section) {
                    "interface" -> {
                        if (hasInterface) throw WgConfigException(WgConfigError.Syntax, "[Interface]")
                        hasInterface = true
                    }
                    "peer" -> peers.add(LinkedHashMap())
                    else -> throw WgConfigException(WgConfigError.Syntax, "[$section]")
                }
                continue
            }
            val eq = noComment.indexOf('=')
            if (eq <= 0) throw WgConfigException(WgConfigError.Syntax, noComment.take(40))
            val key = noComment.substring(0, eq).trim().lowercase()
            val value = noComment.substring(eq + 1).trim()
            val target = when (section) {
                "interface" -> iface
                "peer" -> peers.last()
                else -> throw WgConfigException(WgConfigError.Syntax, key)
            }
            if (key in LIST_KEYS && target[key] != null) target[key] = target[key] + "," + value else target[key] = value
        }
        if (!hasInterface) throw WgConfigException(WgConfigError.MissingInterface)
        if (peers.isEmpty()) throw WgConfigException(WgConfigError.MissingPeer)
        val config = WireguardConfig(
            id = 0,
            name = name?.takeIf { it.isNotBlank() } ?: comment?.take(WgValidator.MAX_NAME) ?: "WireGuard",
            wgInterface = WgInterface(
                privateKey = iface["privatekey"].orEmpty(),
                addresses = splitList(iface["address"]),
                dns = splitList(iface["dns"]),
                mtu = number(iface["mtu"], "MTU"),
                listenPort = number(iface["listenport"], "ListenPort"),
            ),
            peers = peers.map { p ->
                WgPeer(
                    publicKey = p["publickey"].orEmpty(),
                    presharedKey = p["presharedkey"],
                    allowedIps = splitList(p["allowedips"]),
                    endpoint = p["endpoint"],
                    persistentKeepalive = number(p["persistentkeepalive"]?.takeUnless { it.equals("off", true) }, "PersistentKeepalive"),
                )
            },
        )
        return WgValidator.normalize(config)
    }

    fun export(config: WireguardConfig): String = buildString {
        appendLine("# ${config.name.replace('\n', ' ').replace('\r', ' ')}")
        appendLine("[Interface]")
        appendLine("PrivateKey = ${config.wgInterface.privateKey}")
        appendLine("Address = ${config.wgInterface.addresses.joinToString(", ")}")
        if (config.wgInterface.dns.isNotEmpty()) appendLine("DNS = ${config.wgInterface.dns.joinToString(", ")}")
        if (config.wgInterface.mtu > 0) appendLine("MTU = ${config.wgInterface.mtu}")
        if (config.wgInterface.listenPort > 0) appendLine("ListenPort = ${config.wgInterface.listenPort}")
        config.peers.forEach { p ->
            appendLine()
            appendLine("[Peer]")
            appendLine("PublicKey = ${p.publicKey}")
            p.presharedKey?.let { appendLine("PresharedKey = $it") }
            appendLine("AllowedIPs = ${p.allowedIps.joinToString(", ")}")
            p.endpoint?.let { appendLine("Endpoint = $it") }
            if (p.persistentKeepalive > 0) appendLine("PersistentKeepalive = ${p.persistentKeepalive}")
        }
    }

    /** The "key=value" text firestack's `Proxies.addProxy` takes for a WireGuard proxy (hex keys). */
    fun toUserspace(config: WireguardConfig): String = buildString {
        val i = config.wgInterface
        append("private_key=").append(hex(i.privateKey)).append('\n')
        if (i.listenPort > 0) append("listen_port=").append(i.listenPort).append('\n')
        // non-standard extension: address, dns and mtu are required by firestack
        append("address=").append(i.addresses.joinToString(",")).append('\n')
        append("dns=").append(i.dns.joinToString(",")).append('\n')
        append("mtu=").append(if (i.mtu > 0) i.mtu else DEFAULT_MTU).append('\n')
        append("replace_peers=true\n")
        config.peers.forEach { p ->
            append("public_key=").append(hex(p.publicKey)).append('\n')
            p.allowedIps.forEach { append("allowed_ip=").append(it).append('\n') }
            p.endpoint?.let { append("endpoint=").append(it).append('\n') }
            if (p.persistentKeepalive > 0) append("persistent_keepalive_interval=").append(p.persistentKeepalive).append('\n')
            p.presharedKey?.let { append("preshared_key=").append(hex(it)).append('\n') }
        }
    }

    fun hex(base64Key: String): String =
        Base64.decode(base64Key.trim(), Base64.DEFAULT).joinToString("") { "%02x".format(it) }

    private fun number(value: String?, what: String): Int {
        if (value.isNullOrBlank()) return 0
        return value.trim().toIntOrNull() ?: throw WgConfigException(WgConfigError.InvalidNumber, what)
    }

    private fun splitList(value: String?): List<String> =
        value?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    private val LIST_KEYS = setOf("address", "dns", "allowedips")
    private const val DEFAULT_MTU = 1280
}

package com.telos.desktop.vault

import java.io.File

/**
 * Mode-B carrier packing metadata written beside media shards.
 *
 * This is transport obfuscation metadata (offsets + sizes), not a claim of
 * forensic invisibility. Secrets stay in LUKS / trailer GCM — never in the
 * manifest.
 */
data class StegoCarrierShardEntry(
    val relativePath: String,
    val originalSize: Long,
    val payloadOffset: Long,
    val payloadBytes: Long,
)

data class StegoCarrierManifest(
    val version: Int = VERSION,
    val spanName: String,
    val shards: List<StegoCarrierShardEntry>,
) {
    fun write(file: File) {
        file.parentFile?.mkdirs()
        val body = buildString {
            append("{\n")
            append("  \"version\": $version,\n")
            append("  \"spanName\": ${jsonString(spanName)},\n")
            append("  \"shards\": [\n")
            shards.forEachIndexed { i, s ->
                append("    {")
                append("\"relativePath\": ${jsonString(s.relativePath)}, ")
                append("\"originalSize\": ${s.originalSize}, ")
                append("\"payloadOffset\": ${s.payloadOffset}, ")
                append("\"payloadBytes\": ${s.payloadBytes}")
                append("}")
                if (i < shards.lastIndex) append(",")
                append("\n")
            }
            append("  ]\n")
            append("}\n")
        }
        file.writeText(body)
    }

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "telos-carriers.json"

        fun read(file: File): StegoCarrierManifest? {
            if (!file.isFile) return null
            return runCatching { parse(file.readText()) }.getOrNull()
        }

        fun parse(text: String): StegoCarrierManifest {
            val version = intField(text, "version") ?: VERSION
            val spanName = stringField(text, "spanName") ?: "telos_stego_span"
            val shardBlock = text.substringAfter("\"shards\"").substringAfter("[").substringBeforeLast("]")
            val shards = shardBlock.split("{")
                .drop(1)
                .mapNotNull { chunk ->
                    val relative = stringField(chunk, "relativePath") ?: return@mapNotNull null
                    val orig = longField(chunk, "originalSize") ?: return@mapNotNull null
                    val offset = longField(chunk, "payloadOffset") ?: return@mapNotNull null
                    val bytes = longField(chunk, "payloadBytes") ?: return@mapNotNull null
                    StegoCarrierShardEntry(relative, orig, offset, bytes)
                }
            require(shards.isNotEmpty()) { "manifest has no shards" }
            return StegoCarrierManifest(version, spanName, shards)
        }

        private fun jsonString(value: String): String =
            "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

        private fun stringField(text: String, key: String): String? {
            val marker = "\"$key\""
            val i = text.indexOf(marker)
            if (i < 0) return null
            val after = text.substring(i + marker.length).trimStart()
            if (!after.startsWith(":")) return null
            val rest = after.substring(1).trimStart()
            if (!rest.startsWith("\"")) return null
            val sb = StringBuilder()
            var j = 1
            while (j < rest.length) {
                val c = rest[j]
                if (c == '\\' && j + 1 < rest.length) {
                    sb.append(rest[j + 1])
                    j += 2
                    continue
                }
                if (c == '"') break
                sb.append(c)
                j++
            }
            return sb.toString()
        }

        private fun longField(text: String, key: String): Long? {
            val marker = "\"$key\""
            val i = text.indexOf(marker)
            if (i < 0) return null
            val after = text.substring(i + marker.length).trimStart()
            if (!after.startsWith(":")) return null
            val num = after.substring(1).trimStart().takeWhile { it.isDigit() || it == '-' }
            return num.toLongOrNull()
        }

        private fun intField(text: String, key: String): Int? = longField(text, key)?.toInt()
    }
}

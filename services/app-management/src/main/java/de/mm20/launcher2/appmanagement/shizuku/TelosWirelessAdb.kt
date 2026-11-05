/* SPDX-License-Identifier: GPL-3.0-or-later
 * ADB TLS transport; protocol reviewed against Shizuku-Next (Apache-2.0).
 */
package de.mm20.launcher2.appmanagement.shizuku

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

internal object TelosWirelessAdb {
    suspend fun start(context: Context, command: String): Result<String> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 30) return@withContext Result.failure(
            IllegalStateException("Wireless pairing requires Android 11; use computer ADB or root"),
        )
        val endpoint = discover(context) ?: return@withContext Result.failure(
            IllegalStateException("Wireless ADB connection service not found; enable Wireless debugging"),
        )
        runCatching {
            Connection(endpoint.first, endpoint.second, TelosAdbKey(context)).use { client ->
                client.connect()
                val output = client.shell(command)
                TelosShizukuHost.saveLastPort(context, endpoint.second)
                output
            }
        }
    }

    @RequiresApi(30)
    private suspend fun discover(context: Context): Pair<String, Int>? = withTimeoutOrNull(12_000L) {
        suspendCancellableCoroutine { cont ->
            lateinit var discovery: TelosAdbPairingDiscovery
            discovery = TelosAdbPairingDiscovery(context, "_adb-tls-connect._tcp") { host, port ->
                if (cont.isActive) {
                    discovery.stop()
                    cont.resume(host to port)
                }
            }
            cont.invokeOnCancellation { discovery.stop() }
            discovery.start()
        }
    }

    /** TLS only: the certificate is the same persisted identity used for SPAKE2 pairing. */
    @RequiresApi(30)
    private class Connection(private val host: String, private val port: Int, private val key: TelosAdbKey) : Closeable {
        private val socket = Socket()
        private var tls: SSLSocket? = null
        private lateinit var input: DataInputStream
        private lateinit var output: DataOutputStream
        private data class Packet(val command: Int, val arg0: Int, val arg1: Int, val bytes: ByteArray)

        fun connect() {
            socket.connect(InetSocketAddress(host, port), 5_000)
            socket.soTimeout = 15_000
            socket.tcpNoDelay = true
            input = DataInputStream(socket.getInputStream())
            output = DataOutputStream(socket.getOutputStream())
            write(CNXN, 0x01000000, 4096, "host::\u0000".toByteArray())
            check(read().command == STLS) { "Wireless ADB requires a paired TLS endpoint" }
            write(STLS, 0x01000000, 0)
            val secured = (key.sslContext.socketFactory.createSocket(socket, host, port, true) as SSLSocket)
            tls = secured
            secured.soTimeout = 15_000
            secured.startHandshake()
            input = DataInputStream(secured.inputStream)
            output = DataOutputStream(secured.outputStream)
            check(read().command == CNXN) { "Wireless ADB authentication failed; pair again" }
        }

        fun shell(command: String): String {
            write(OPEN, 1, 0, "shell:$command\u0000".toByteArray())
            val first = read()
            check(first.command == OKAY) { "ADB rejected the starter command" }
            val result = java.io.ByteArrayOutputStream()
            while (true) {
                val message = read()
                check(message.arg1 == 1) { "Unexpected ADB stream" }
                when (message.command) {
                    WRTE -> {
                        val count = minOf(message.bytes.size, 64 * 1024 - result.size())
                        if (count > 0) result.write(message.bytes, 0, count)
                        write(OKAY, 1, message.arg0)
                    }
                    CLSE -> {
                        write(CLSE, 1, message.arg0)
                        return result.toString("UTF-8")
                    }
                    else -> error("Unexpected ADB response")
                }
            }
        }

        private fun write(command: Int, arg0: Int, arg1: Int, bytes: ByteArray = byteArrayOf()) {
            val checksum = bytes.sumOf { it.toInt() and 255 }
            output.write(ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(command).putInt(arg0).putInt(arg1).putInt(bytes.size)
                .putInt(checksum).putInt(command.inv()).array())
            output.write(bytes)
            output.flush()
        }

        private fun read(): Packet {
            val header = ByteArray(24).also { input.readFully(it) }
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val command = buffer.int
            val arg0 = buffer.int
            val arg1 = buffer.int
            val size = buffer.int
            val checksum = buffer.int
            check(buffer.int == command.inv() && size in 0..(1024 * 1024)) { "Invalid ADB header" }
            val bytes = ByteArray(size).also { input.readFully(it) }
            check(bytes.sumOf { it.toInt() and 255 } == checksum) { "Invalid ADB checksum" }
            return Packet(command, arg0, arg1, bytes)
        }

        override fun close() {
            runCatching { tls?.close() }
            runCatching { socket.close() }
        }
    }

    private const val CNXN = 0x4e584e43
    private const val STLS = 0x534c5453
    private const val OPEN = 0x4e45504f
    private const val OKAY = 0x59414b4f
    private const val WRTE = 0x45545257
    private const val CLSE = 0x45534c43
}

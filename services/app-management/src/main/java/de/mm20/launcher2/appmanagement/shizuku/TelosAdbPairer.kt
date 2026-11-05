package de.mm20.launcher2.appmanagement.shizuku

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigInteger
import java.net.Socket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAKeyGenParameterSpec
import java.security.spec.RSAPublicKeySpec
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509ExtendedTrustManager
import javax.security.auth.x500.X500Principal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Wireless ADB pairing (SPAKE2 over TLS), same protocol as Shizuku-Next's [AdbPairingClient].
 */
object TelosAdbPairer {
    private const val TAG = "TelosAdbPairer"

    suspend fun pair(context: Context, code: String, host: String?, port: Int?): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                return@withContext Result.failure(IllegalStateException("needs Android 11"))
            }
            val digits = code.filter { it.isDigit() }.take(6)
            if (digits.length != 6) {
                return@withContext Result.failure(IllegalArgumentException("need 6 digits"))
            }
            val endpoint = if (!host.isNullOrBlank() && port != null && port > 0) {
                host to port
            } else {
                discover(context) ?: return@withContext Result.failure(
                    IllegalStateException("pairing service not found"),
                )
            }
            runCatching {
                System.loadLibrary("adb")
                val key = TelosAdbKey(context)
                TelosAdbPairingClient(endpoint.first, endpoint.second, digits, key).use { it.start() }
            }.mapCatching { ok ->
                if (!ok) error("pairing rejected")
            }.onFailure { Log.w(TAG, "pair failed", it) }
        }

    @RequiresApi(Build.VERSION_CODES.R)
    internal suspend fun discover(context: Context, timeoutMs: Long = 8_000L): Pair<String, Int>? {
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                lateinit var mdns: TelosAdbPairingDiscovery
                mdns = TelosAdbPairingDiscovery(context) { host, port ->
                    if (port > 0 && cont.isActive) {
                        mdns.stop()
                        cont.resume(host to port)
                    }
                }
                cont.invokeOnCancellation { mdns.stop() }
                mdns.start()
            }
        }
    }
}

internal class PairingContext private constructor(private val nativePtr: Long) {
    val msg: ByteArray = nativeMsg(nativePtr)
    fun initCipher(theirMsg: ByteArray) = nativeInitCipher(nativePtr, theirMsg)
    fun encrypt(input: ByteArray) = nativeEncrypt(nativePtr, input)
    fun decrypt(input: ByteArray) = nativeDecrypt(nativePtr, input)
    fun destroy() = nativeDestroy(nativePtr)

    private external fun nativeMsg(nativePtr: Long): ByteArray
    private external fun nativeInitCipher(nativePtr: Long, theirMsg: ByteArray): Boolean
    private external fun nativeEncrypt(nativePtr: Long, inbuf: ByteArray): ByteArray?
    private external fun nativeDecrypt(nativePtr: Long, inbuf: ByteArray): ByteArray?
    private external fun nativeDestroy(nativePtr: Long)

    companion object {
        fun create(password: ByteArray): PairingContext? {
            val ptr = nativeConstructor(true, password)
            return if (ptr != 0L) PairingContext(ptr) else null
        }

        @JvmStatic
        private external fun nativeConstructor(isClient: Boolean, password: ByteArray): Long
    }
}

@RequiresApi(Build.VERSION_CODES.R)
internal class TelosAdbPairingClient(
    private val host: String,
    private val port: Int,
    private val pairCode: String,
    private val key: TelosAdbKey,
) : Closeable {
    private lateinit var socket: Socket
    private lateinit var inputStream: DataInputStream
    private lateinit var outputStream: DataOutputStream
    private lateinit var pairingContext: PairingContext
    private var started = false

    fun start(): Boolean {
        setupTls()
        started = true
        if (!exchangeMsgs()) return false
        if (!exchangePeerInfo()) return false
        return true
    }

    private fun setupTls() {
        socket = Socket().apply {
            connect(InetSocketAddress(host, this@TelosAdbPairingClient.port), 5_000)
            soTimeout = 15_000
        }
        socket.tcpNoDelay = true
        val ssl = key.sslContext.socketFactory.createSocket(socket, host, port, true) as SSLSocket
        ssl.soTimeout = 15_000
        ssl.startHandshake()
        inputStream = DataInputStream(ssl.inputStream)
        outputStream = DataOutputStream(ssl.outputStream)
        val pairBytes = pairCode.toByteArray()
        val material = exportKeyingMaterial(ssl)
        val password = ByteArray(pairBytes.size + material.size)
        pairBytes.copyInto(password)
        material.copyInto(password, pairBytes.size)
        pairingContext = checkNotNull(PairingContext.create(password)) { "PairingContext" }
    }

    private fun exportKeyingMaterial(ssl: SSLSocket): ByteArray {
        org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Lcom/android/org/conscrypt/Conscrypt;")
        val conscrypt = Class.forName("com.android.org.conscrypt.Conscrypt")
        val method = conscrypt.getMethod(
            "exportKeyingMaterial",
            SSLSocket::class.java,
            String::class.java,
            ByteArray::class.java,
            Int::class.javaPrimitiveType,
        )
        @Suppress("UNCHECKED_CAST")
        return method.invoke(null, ssl, "adb-label\u0000", null, 64) as ByteArray
    }

    private fun exchangeMsgs(): Boolean {
        val msg = pairingContext.msg
        writeHeader(0, msg)
        val payload = readPayload(0) ?: return false
        return pairingContext.initCipher(payload)
    }

    private fun exchangePeerInfo(): Boolean {
        val buf = ByteBuffer.allocate(8192).order(ByteOrder.BIG_ENDIAN)
        buf.put(0)
        buf.put(key.adbPublicKey.copyOf(minOf(key.adbPublicKey.size, 8191)))
        val encrypted = pairingContext.encrypt(buf.array()) ?: return false
        writeHeader(1, encrypted)
        val payload = readPayload(1) ?: return false
        pairingContext.decrypt(payload) ?: return false
        return true
    }

    private fun writeHeader(type: Byte, payload: ByteArray) {
        val header = ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN)
        header.put(1)
        header.put(type)
        header.putInt(payload.size)
        outputStream.write(header.array())
        outputStream.write(payload)
    }

    private fun readPayload(expectedType: Byte): ByteArray? {
        val header = ByteArray(6)
        inputStream.readFully(header)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
        val version = buf.get()
        val type = buf.get()
        val size = buf.int
        if (version.toInt() != 1 || type != expectedType || size <= 0 || size > 16384) return null
        val payload = ByteArray(size)
        inputStream.readFully(payload)
        return payload
    }

    override fun close() {
        runCatching { inputStream.close() }
        runCatching { outputStream.close() }
        runCatching { socket.close() }
        if (::pairingContext.isInitialized) runCatching { pairingContext.destroy() }
    }

}

internal class TelosAdbKey(context: Context) {
    private val privateKey: PrivateKey
    private val certificate: X509Certificate
    private val publicKey: RSAPublicKey

    init {
        // Conscrypt needs a software RSA key for TLS signing on some OEMs. Protect its
        // persisted encoding with a non-exportable Android Keystore AES-GCM key.
        val identity = TelosAdbIdentity.load(context)
        privateKey = identity.first
        certificate = identity.second
        publicKey = certificate.publicKey as RSAPublicKey
    }

    val adbPublicKey: ByteArray = publicKey.adbEncoded(if (context.packageName.endsWith(".test")) "telos-pairing-test" else "telos")

    private val keyManager = object : X509ExtendedKeyManager() {
        override fun chooseClientAlias(keyTypes: Array<out String>, issuers: Array<out Principal>?, socket: Socket?) =
            if (keyTypes.contains("RSA")) "key" else null
        override fun getCertificateChain(alias: String?) =
            if (alias == "key") arrayOf(certificate) else null
        override fun getPrivateKey(alias: String?) = if (alias == "key") privateKey else null
        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = null
        override fun getServerAliases(keyType: String, issuers: Array<out Principal>?) = null
        override fun chooseServerAlias(keyType: String, issuers: Array<out Principal>?, socket: Socket?) = null
    }

    private val trustManager = @RequiresApi(Build.VERSION_CODES.R) object : X509ExtendedTrustManager() {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) = Unit
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) = Unit
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    val sslContext: SSLContext = SSLContext.getInstance("TLSv1.3").apply {
        init(arrayOf(keyManager), arrayOf(trustManager), SecureRandom())
    }
}

private const val MODULUS_WORDS = 64
private const val RSA_PUBKEY_SIZE = 524

private fun BigInteger.toAdbEncoded(): IntArray {
    val encoded = IntArray(MODULUS_WORDS)
    val r32 = BigInteger.ZERO.setBit(32)
    var tmp = this
    for (i in 0 until MODULUS_WORDS) {
        val out = tmp.divideAndRemainder(r32)
        tmp = out[0]
        encoded[i] = out[1].toInt()
    }
    return encoded
}

private fun RSAPublicKey.adbEncoded(name: String): ByteArray {
    val r32 = BigInteger.ZERO.setBit(32)
    val n0inv = modulus.remainder(r32).modInverse(r32).negate()
    val r = BigInteger.ZERO.setBit(2048)
    val rr = r.modPow(BigInteger.valueOf(2), modulus)
    val buffer = ByteBuffer.allocate(RSA_PUBKEY_SIZE).order(ByteOrder.LITTLE_ENDIAN)
    buffer.putInt(MODULUS_WORDS)
    buffer.putInt(n0inv.toInt())
    modulus.toAdbEncoded().forEach { buffer.putInt(it) }
    rr.toAdbEncoded().forEach { buffer.putInt(it) }
    buffer.putInt(publicExponent.toInt())
    val b64 = Base64.encode(buffer.array(), Base64.NO_WRAP)
    val suffix = " $name\u0000".toByteArray()
    return b64 + suffix
}

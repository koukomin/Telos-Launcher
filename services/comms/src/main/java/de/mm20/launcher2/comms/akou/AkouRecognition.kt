package de.mm20.launcher2.comms.akou

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import de.mm20.launcher2.comms.akou.fingerprint.SignatureGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class RecognizedSong(val title: String, val artist: String, val album: String, val recognizedAt: Long)

/** Audile/SongRec interoperability: only an on-device fingerprint leaves the phone. */
object AkouRecognition {
    private val client by lazy { OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build() }

    @Suppress("MissingPermission")
    suspend fun record(): ShortArray = withContext(Dispatchers.IO) {
        val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "16 kHz microphone is unavailable" }
        val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 4096))
        val samples = ShortArray(16000 * 10)
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Microphone initialization failed" }
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone unavailable" }
            var offset = 0
            val deadline = android.os.SystemClock.elapsedRealtime() + 15000
            while (offset < samples.size) {
                currentCoroutineContext().ensureActive()
                check(android.os.SystemClock.elapsedRealtime() < deadline) { "Microphone recording timed out" }
                val count = recorder.read(samples, offset, minOf(2048, samples.size - offset))
                check(count > 0) { "Microphone read failed: $count" }
                offset += count
            }
            samples
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
    }

    suspend fun recognize(samples: ShortArray): RecognizedSong? {
        val signature = withContext(Dispatchers.Default) {
            require(samples.size in 16000..192000)
            currentCoroutineContext().ensureActive()
            SignatureGenerator.makeSignature(samples).also {
                check(it.peaksByBand.sumOf { band -> band.size } >= 30) { "Not enough musical audio" }
            }.encodeToUri()
        }
        val timestamp = System.currentTimeMillis() / 1000
        // Fixed protocol coordinates, never the user's location. Shape follows Audile.
        val payload = JSONObject().put("geolocation", JSONObject().put("altitude", 150.0)
            .put("latitude", 45.0).put("longitude", 2.0))
            .put("signature", JSONObject().put("samplems", samples.size * 1000L / 16000)
                .put("timestamp", timestamp).put("uri", signature))
            .put("timestamp", timestamp).put("timezone", "Europe/Paris").toString()
        val url = "https://amp.shazam.com/discovery/v5/en/US/android/-/tag/" +
            UUID.randomUUID().toString().uppercase() + "/" + UUID.randomUUID() +
            "?sync=true&webv3=true&sampling=true&connected=&shazamapiversion=v3&sharehub=true&video=v3"
        val request = Request.Builder().url(url).header("Content-Language", "en_US")
            .header("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 13; Pixel 7 Build/TQ3A.230805.001)")
            .post(payload.toRequestBody("application/json".toMediaType())).build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val song = response.use {
                            check(it.isSuccessful) { "Shazam HTTP ${it.code}" }
                            val input = it.body?.byteStream() ?: error("Empty recognition response")
                            val bytes = input.readBytesBounded(256 * 1024)
                            parseResponse(bytes.toString(Charsets.UTF_8))
                        }
                        if (continuation.isActive) continuation.resume(song)
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
            })
        }
    }

    internal fun parseResponse(json: String): RecognizedSong? {
        val root = JSONObject(json)
        val matches = root.optJSONArray("matches") ?: error("Missing recognition matches")
        val track = root.optJSONObject("track")
        if (matches.length() == 0) return null
        check(track != null) { "Match has no track metadata" }
        val title = track.getString("title").trim().take(500)
        check(title.isNotEmpty()) { "Missing song title" }
        var album = ""
        val sections = track.optJSONArray("sections")
        for (i in 0 until (sections?.length() ?: 0)) {
            val metadata = sections?.optJSONObject(i)?.optJSONArray("metadata") ?: continue
            for (j in 0 until metadata.length()) {
                val item = metadata.optJSONObject(j) ?: continue
                if (item.optString("title").equals("Album", true)) album = item.optString("text").take(500)
            }
        }
        return RecognizedSong(title, track.optString("subtitle").trim().take(500), album, System.currentTimeMillis())
    }

    private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            check(output.size() + count <= limit) { "Recognition response too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}

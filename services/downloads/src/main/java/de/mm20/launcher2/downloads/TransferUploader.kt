package de.mm20.launcher2.downloads

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import java.io.IOException
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

enum class UploadService(val displayName: String, val host: String) {
    FileIo("file.io (Ephemeral 1-time)", "file.io"),
    Catbox("catbox.moe (Permanent)", "catbox.moe"),
    Gofile("gofile.io (Fast Cloud)", "gofile.io")
}

data class UploadResult(
    val service: UploadService,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long
)

object TransferUploader {

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .callTimeout(10, TimeUnit.MINUTES)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun upload(context: Context, uri: Uri, service: UploadService): Result<UploadResult> = withContext(Dispatchers.IO) {
        runCatching {
            val contentResolver = context.contentResolver
            var fileName = "file"
            var fileSize = 0L

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }

            var uploadedSize = 0L
            val data = object : RequestBody() {
                override fun contentType() = "application/octet-stream".toMediaTypeOrNull()
                override fun contentLength() = if (fileSize > 0) fileSize else -1L
                override fun isOneShot() = true

                override fun writeTo(sink: BufferedSink) {
                    val input = contentResolver.openInputStream(uri) ?: throw IOException("Could not open file")
                    uploadedSize = input.source().use { sink.writeAll(it) }
                }
            }
            val rawLink = when (service) {
                UploadService.FileIo -> uploadToFileIo(fileName, data)
                UploadService.Catbox -> uploadToCatbox(fileName, data)
                UploadService.Gofile -> uploadToGofile(fileName, data)
            }
            val link = rawLink.toHttpUrlOrNull()?.takeIf {
                it.isHttps && it.port == 443 && it.username.isEmpty() && it.password.isEmpty() &&
                    (it.host == service.host || it.host.endsWith(".${service.host}"))
            }?.toString() ?: throw IOException("Invalid download link from provider")
            fileSize = uploadedSize

            UploadResult(service, fileName, link, fileSize)
        }.onFailure { if (it is CancellationException) throw it }
    }

    private fun responseText(response: Response): String {
        if (!response.isSuccessful) throw IOException("Upload provider returned HTTP ${response.code}")
        val source = response.body.source()
        if (source.request(65_537)) throw IOException("Upload provider response too large")
        return source.readUtf8()
    }

    private fun uploadToFileIo(fileName: String, data: RequestBody): String {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, data)
            .build()

        val request = Request.Builder()
            .url("https://file.io")
            .post(requestBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val jsonStr = responseText(response)
            val json = JSONObject(jsonStr)
            if (json.optBoolean("success")) {
                return json.getString("link")
            } else {
                throw IOException("file.io refused the upload")
            }
        }
    }

    private fun uploadToCatbox(fileName: String, data: RequestBody): String {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("reqtype", "fileupload")
            .addFormDataPart("fileToUpload", fileName, data)
            .build()

        val request = Request.Builder()
            .url("https://catbox.moe/user/api.php")
            .post(requestBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val link = responseText(response).trim()
            if (link.startsWith("http")) return link
            else throw IOException("Catbox refused the upload")
        }
    }

    private fun uploadToGofile(fileName: String, data: RequestBody): String {
        // Step 1: Get best server
        val serverReq = Request.Builder().url("https://api.gofile.io/servers").build()
        val serverName = httpClient.newCall(serverReq).execute().use { res ->
            val json = JSONObject(responseText(res))
            json.getJSONObject("data").getJSONArray("servers").getJSONObject(0).getString("name")
        }

        if (!Regex("[a-zA-Z0-9-]{1,63}").matches(serverName)) throw IOException("Invalid upload server")

        // Step 2: Upload
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, data)
            .build()

        val uploadReq = Request.Builder()
            .url("https://$serverName.gofile.io/contents/uploadfile")
            .post(requestBody)
            .build()

        httpClient.newCall(uploadReq).execute().use { res ->
            val json = JSONObject(responseText(res))
            if (json.optString("status") == "ok") {
                return json.getJSONObject("data").getString("downloadPage")
            } else {
                throw IllegalStateException("Gofile upload failed")
            }
        }
    }
}

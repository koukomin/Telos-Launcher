package de.mm20.launcher2.downloads

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.InputStream
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

            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("Could not read file data")

            if (fileSize <= 0) fileSize = bytes.size.toLong()

            val link = when (service) {
                UploadService.FileIo -> uploadToFileIo(fileName, bytes)
                UploadService.Catbox -> uploadToCatbox(fileName, bytes)
                UploadService.Gofile -> uploadToGofile(fileName, bytes)
            }

            UploadResult(service, fileName, link, fileSize)
        }
    }

    private fun uploadToFileIo(fileName: String, bytes: ByteArray): String {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
            .build()

        val request = Request.Builder()
            .url("https://file.io")
            .post(requestBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val jsonStr = response.body?.string() ?: throw IllegalStateException("Empty response from file.io")
            val json = JSONObject(jsonStr)
            if (json.optBoolean("success")) {
                return json.getString("link")
            } else {
                throw IllegalStateException(json.optString("message", "Upload to file.io failed"))
            }
        }
    }

    private fun uploadToCatbox(fileName: String, bytes: ByteArray): String {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("reqtype", "fileupload")
            .addFormDataPart("fileToUpload", fileName, bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
            .build()

        val request = Request.Builder()
            .url("https://catbox.moe/user/api.php")
            .post(requestBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val link = response.body?.string()?.trim() ?: throw IllegalStateException("Empty response from catbox.moe")
            if (link.startsWith("http")) return link
            else throw IllegalStateException("Catbox upload failed: $link")
        }
    }

    private fun uploadToGofile(fileName: String, bytes: ByteArray): String {
        // Step 1: Get best server
        val serverReq = Request.Builder().url("https://api.gofile.io/servers").build()
        val serverName = httpClient.newCall(serverReq).execute().use { res ->
            val json = JSONObject(res.body?.string() ?: "{}")
            json.getJSONObject("data").getJSONArray("servers").getJSONObject(0).getString("name")
        }

        // Step 2: Upload
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
            .build()

        val uploadReq = Request.Builder()
            .url("https://$serverName.gofile.io/contents/uploadfile")
            .post(requestBody)
            .build()

        httpClient.newCall(uploadReq).execute().use { res ->
            val json = JSONObject(res.body?.string() ?: "{}")
            if (json.optString("status") == "ok") {
                return json.getJSONObject("data").getString("downloadPage")
            } else {
                throw IllegalStateException("Gofile upload failed")
            }
        }
    }
}

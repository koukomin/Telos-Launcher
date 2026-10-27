package de.mm20.launcher2.ui.camera

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import kotlin.coroutines.coroutineContext

internal data class OcrLanguage(val code: String, val locale: String, val bytes: Long, val blob: String) {
    val name: String get() = Locale.forLanguageTag(locale).let { it.getDisplayName(it) }
}

/** Models are explicit downloads, pinned to source blobs; documents never leave this device. */
internal object CameraOcrModels {
    private const val DATA_REVISION = "87416418657359cb625c412a48b6e1d6d41c29bd"
    private const val CONFIG_REVISION = "3decf1c8252ba6dbeef0bf908f4b0aab7f18d113"
    private val mutex = Mutex()
    val languages = listOf(
        OcrLanguage("eng", "en", 4113088L, "bbef4675053b5b468cdb477053e28b1c698ba08e"),
        OcrLanguage("ell", "el", 1419514L, "ed98ae1a88d84414da316e6eeab3232f2c68639b"),
        OcrLanguage("deu", "de", 1525436L, "97ed7b2b60f2771c07040660ef0f6daf596dc7bf"),
        OcrLanguage("spa", "es", 2294433L, "72e901f13ca52cfe34cf239a368b9ed3c0ddaf26"),
        OcrLanguage("fra", "fr", 1130365L, "d9e2b2160be0d1ca3b8f1bf2730fae476ef3b4a6"),
        OcrLanguage("ita", "it", 2701314L, "edbffbe0e51892b240630b5eeabededa7e7c9c4f"),
        OcrLanguage("por", "pt", 1982756L, "e9f373e95c66b4bf557c263721ef31f78e5bc301"),
        OcrLanguage("rus", "ru", 3861738L, "b146cb2263acbc6383f8e92ea0ce759537687bb8"),
        OcrLanguage("tur", "tr", 4550554L, "1f0b331ef29f50ceb7c44da58e38c7ec42314d0a"),
        OcrLanguage("nld", "nl", 6050296L, "53ce95a9c2488a9a0228939944a184157373d0ae"),
        OcrLanguage("pol", "pl", 4765518L, "2382f568f0ce0c4c0a7f4380a291255514e5e97f"),
        OcrLanguage("ces", "cs", 3795684L, "dd6fc79a62f9fcd1c7c6da06b31bb4fdb10dc173"),
        OcrLanguage("slk", "sk", 4427661L, "ca99e3e82f69a0e16b48cdfec3c4e706758269f2"),
        OcrLanguage("slv", "sl", 3003829L, "835954e891897d3c8b63db48cb5c2712ef2e6b11"),
        OcrLanguage("hrv", "hr", 4103348L, "6dd4f8638a58eb0663e8f9a80a355c505dcf01a6"),
        OcrLanguage("bul", "bg", 1675212L, "1e65588d127d918ef3ba95b785b446f15827d60f"),
        OcrLanguage("ron", "ro", 2376323L, "04cd2bc7b471431354e9446e973f3472f442e189"),
        OcrLanguage("hun", "hu", 5296273L, "5d24fa8e922d6b57853dcb0cc490060c76643cab"),
        OcrLanguage("dan", "da", 2580059L, "85501b582937493f9ae6a64ccd0a6bb0a2ea33d5"),
        OcrLanguage("swe", "sv", 4167034L, "684e1fa66e4a55b983febf5f9ed04f26ad946e59"),
        OcrLanguage("fin", "fi", 7865732L, "73f92402f1891b9066e0e145f245145a2402105c"),
        OcrLanguage("est", "et", 4458101L, "a2b52c3388358e6dfc4eb8d64d8fc6afbb3630ea"),
        OcrLanguage("lav", "lv", 2717247L, "71d925c8b6c73980157fd0128412b46e6e8396b5"),
        OcrLanguage("lit", "lt", 3154896L, "af9e2b3cd6a65650206b7d89f1b93b51683b9692"),
        OcrLanguage("gle", "ga", 1181824L, "a7abc7be9aadc862b31ab7c7bc672051cd0245fb"),
        OcrLanguage("mlt", "mt", 2308796L, "4242f9cd016987f16feb06973e988eced9604787"),
        OcrLanguage("ukr", "uk", 3825102L, "d02cf90b2911a3da66dac6ae380bcefb18dd7663"),
        OcrLanguage("nor", "no", 3610079L, "9209df2c9904c127437c07e16c287cdefd024e7c"),
        OcrLanguage("chi_sim", "zh-CN", 2469156L, "388bac276d033d06e5ed5ba7a7ad14ae58f97dab"),
        OcrLanguage("chi_tra", "zh-TW", 2366642L, "1955cd8b8d076b898616e9c2c1ace65b43105356"),
        OcrLanguage("jpn", "ja", 2471260L, "c4178f89991bde90b7fdc647e3e1901868423bd0"),
        OcrLanguage("kor", "ko", 1677415L, "60986d44497689f3abace0b148199476d93292e1"),
        OcrLanguage("ara", "ar", 1432056L, "c8d129c67821c1592cac46836686b057ecc203dc"),
        OcrLanguage("hin", "hi", 1122751L, "a8f0aaec09378115b12a2c6287c57695d082aea2"),
        OcrLanguage("ben", "bn", 855841L, "7e9054d1de309714009a85555f5276168d41af9c"),
        OcrLanguage("grc", "grc", 2246328L, "e94990006fc18e88ab120e7ca6681cc3b290269b"),
        OcrLanguage("lat", "la", 3187463L, "30dab6a24339f262d1eb21a3ee272d16c46aeba3"),
    )

    suspend fun prepare(context: Context, selected: Set<String>): File = withContext(Dispatchers.IO) {
        require(selected.size in 1..3 && selected.all { code -> languages.any { it.code == code } })
        mutex.withLock {
            val root = File(context.noBackupFilesDir, "camera-ocr")
            val directory = File(root, "tessdata")
            check(directory.isDirectory || directory.mkdirs())
            for (code in selected.sorted()) {
                val model = languages.first { it.code == code }
                download(directory, "$code.traineddata", model.bytes, model.blob,
                    "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/$DATA_REVISION/$code.traineddata")
            }
            download(directory, "pdf.ttf", 572, "d1472b20ef1aebbf5e11573867e9ac13873681b9",
                "https://raw.githubusercontent.com/tesseract-ocr/tessconfigs/$CONFIG_REVISION/pdf.ttf")
            root
        }
    }

    private fun hash(file: File): String {
        val hash = MessageDigest.getInstance("SHA-1")
        hash.update(("blob " + file.length() + "\u0000").toByteArray(Charsets.UTF_8))
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) }
        }
        return hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private suspend fun download(directory: File, name: String, size: Long, blob: String, address: String) {
        coroutineContext.ensureActive()
        val target = File(directory, name)
        if (target.length() == size && hash(target) == blob) return
        val temp = File.createTempFile("model-", ".part", directory)
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000; connection.readTimeout = 30_000
            check(connection.responseCode == 200)
            check(connection.contentLengthLong == -1L || connection.contentLengthLong == size)
            connection.inputStream.use { input -> temp.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val count = input.read(buffer); if (count < 0) break
                    total += count; check(total <= size)
                    output.write(buffer, 0, count)
                }
                check(total == size)
            } }
            check(hash(temp) == blob)
            check(temp.renameTo(target))
        } finally { connection.disconnect(); temp.delete() }
    }
}

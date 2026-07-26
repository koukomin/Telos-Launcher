package de.mm20.launcher2.appmanagement

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import de.mm20.launcher2.settings.BaseSettings
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.InputStream
import java.io.OutputStream

@Serializable
data class FossUpdateData(
    val pendingUpdates: Set<String> = emptySet(),
)

private val JsonConfig = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal class FossUpdateDataSerializer : Serializer<FossUpdateData> {
    override val defaultValue: FossUpdateData = FossUpdateData()

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun readFrom(input: InputStream): FossUpdateData {
        return try {
            JsonConfig.decodeFromStream(input)
        } catch (e: SerializationException) {
            throw CorruptionException("Cannot read FossUpdateData", e)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun writeTo(t: FossUpdateData, output: OutputStream) {
        JsonConfig.encodeToStream(t, output)
    }
}

internal class FossUpdateDataStore(private val appContext: Context) : BaseSettings<FossUpdateData>(
    appContext,
    fileName = "foss_updates.json",
    serializer = FossUpdateDataSerializer(),
    migrations = emptyList()
) {
    val data get() = appContext.dataStore.data
    fun update(block: (FossUpdateData) -> FossUpdateData) = updateData(block)
}

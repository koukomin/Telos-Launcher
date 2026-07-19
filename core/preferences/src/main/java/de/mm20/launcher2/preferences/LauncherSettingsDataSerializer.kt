package de.mm20.launcher2.preferences

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.modules.SerializersModule
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

// A settings file must stay readable across app up- AND downgrades. ignoreUnknownKeys/
// coerceInputValues already cover unknown fields and unknown enum values, but not unknown
// type discriminators of the sealed hierarchies stored in this file: without a default
// deserializer, a single value written by a newer (or just different) build - e.g. a
// gesture action this build doesn't know - makes the whole file throw on decode, which
// DataStore treats as CorruptionException and answers by REPLACING THE ENTIRE FILE with
// defaults. That wiped every setting a user had, so each sealed type stored here maps
// unknown discriminators to its no-op/default variant instead.
internal val LauncherSettingsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
    serializersModule = SerializersModule {
        polymorphicDefaultDeserializer(GestureAction::class) {
            GestureAction.NoAction.serializer()
        }
        polymorphicDefaultDeserializer(ClockWidgetStyle::class) {
            ClockWidgetStyle.Digital1.serializer()
        }
        polymorphicDefaultDeserializer(ContextProfileTrigger::class) {
            ContextProfileTrigger.Manual.serializer()
        }
    }
}

internal class LauncherSettingsDataSerializer(private val context: Context) : Serializer<LauncherSettingsData> {

    internal val json = LauncherSettingsJson

    override val defaultValue: LauncherSettingsData
        get() = LauncherSettingsData(context)

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun readFrom(input: InputStream): LauncherSettingsData {
        try {
            return json.decodeFromStream(input)
        } catch (e: IllegalArgumentException) {
            throw (CorruptionException("Cannot read json.", e))
        } catch (e: SerializationException) {
            throw (CorruptionException("Cannot read json.", e))
        } catch (e: IOException) {
            throw (CorruptionException("Cannot read json.", e))
        }
    }

    override suspend fun writeTo(t: LauncherSettingsData, output: OutputStream) {
        json.encodeToStream(t, output)
    }
}

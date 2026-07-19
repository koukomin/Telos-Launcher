package de.mm20.launcher2.preferences

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the property whose absence wiped a real user's entire configuration: settings.json must
 * stay decodable even when it contains values this build doesn't know - whether from a newer
 * build (downgrade), an older build's since-removed values, or hand editing. A decode failure
 * isn't a localized problem: DataStore turns it into a CorruptionException and the corruption
 * handler replaces the WHOLE file with defaults, so "one unknown value" must never mean "throws".
 */
class LauncherSettingsDataForwardCompatTest {

    private val json = LauncherSettingsJson

    @Test
    fun `unknown gesture action discriminator decodes to NoAction instead of throwing`() {
        val data = json.decodeFromString<LauncherSettingsData>(
            """{"schemaVersion":10,"gesturesSwipeLeft":{"type":"some_future_action","someArg":42}}"""
        )
        assertEquals(GestureAction.NoAction, data.gesturesSwipeLeft)
    }

    @Test
    fun `unknown clock widget style discriminator decodes to Digital1 instead of throwing`() {
        val data = json.decodeFromString<LauncherSettingsData>(
            """{"schemaVersion":10,"clockWidgetStyle":{"type":"holographic"}}"""
        )
        assertTrue(data._clockWidgetStyle is ClockWidgetStyle.Digital1)
    }

    @Test
    fun `unknown context profile trigger decodes to Manual instead of throwing`() {
        val data = json.decodeFromString<LauncherSettingsData>(
            """
            {"schemaVersion":10,"contextProfiles":[{"id":"p1","name":"Test",
            "trigger":{"type":"geofence","lat":1.0,"lon":2.0}}]}
            """.trimIndent()
        )
        assertEquals(
            ContextProfileTrigger.Manual,
            data.contextProfiles.single().trigger,
        )
    }

    @Test
    fun `unknown enum value in a defaulted field is coerced, not fatal`() {
        // "icebox" is a FreezeBackendPreference value an older build wrote and this build removed.
        val data = json.decodeFromString<LauncherSettingsData>(
            """{"schemaVersion":10,"freezeBackend":"icebox"}"""
        )
        assertEquals(FreezeBackendPreference.Auto, data.freezeBackend)
    }

    @Test
    fun `unknown top-level fields are ignored, not fatal`() {
        val data = json.decodeFromString<LauncherSettingsData>(
            """{"schemaVersion":10,"someFieldFromTheFuture":{"a":[1,2,3]},"uiColorSchemeNightStart":21}"""
        )
        assertEquals(21, data.uiColorSchemeNightStart)
    }

    /**
     * The two color-int fields use ColorIntAsHexSerializer, whose decode side calls
     * android.graphics.Color - unavailable in a plain JVM unit test. Dropping them from an
     * encoded blob before decoding keeps these tests on the JVM; both fields still have defaults,
     * so their absence is otherwise irrelevant to what's asserted here.
     */
    private fun stripJvmUndecodableFields(encoded: String): MutableMap<String, kotlinx.serialization.json.JsonElement> {
        return Json.parseToJsonElement(encoded).jsonObject.toMutableMap().apply {
            remove("badgesNotificationColor")
            remove("floatingLauncherColor")
        }
    }

    @Test
    fun `full default settings round-trip through the production Json config`() {
        val original = LauncherSettingsData(
            gesturesSwipeLeft = GestureAction.WebAppsPanel,
            gesturesLongPress = GestureAction.HomeScreenMenu,
            _clockWidgetStyle = ClockWidgetStyle.Analog(showTicks = true),
        )
        val stripped = stripJvmUndecodableFields(json.encodeToString(original))
        val decoded = json.decodeFromString<LauncherSettingsData>(
            Json.encodeToString(kotlinx.serialization.json.JsonObject(stripped))
        )
        assertEquals(original, decoded)
    }

    @Test
    fun `known values survive alongside an unknown one in the same file`() {
        // The poisoned-file scenario reproduced on a device: one unknown value must not cost the
        // rest of the file anything.
        val encoded = json.encodeToString(
            LauncherSettingsData(uiColorSchemeNightStart = 22, wallpaperDim = true)
        )
        val poisoned = stripJvmUndecodableFields(encoded).apply {
            put(
                "gesturesDoubleTap",
                Json.parseToJsonElement("""{"type":"quantum_flick"}""")
            )
        }
        val decoded = json.decodeFromString<LauncherSettingsData>(
            Json.encodeToString(kotlinx.serialization.json.JsonObject(poisoned))
        )
        assertEquals(22, decoded.uiColorSchemeNightStart)
        assertEquals(true, decoded.wallpaperDim)
        assertEquals(GestureAction.NoAction, decoded.gesturesDoubleTap)
    }
}

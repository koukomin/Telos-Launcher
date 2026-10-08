package de.mm20.launcher2.weather

import de.mm20.launcher2.preferences.weather.WeatherAlertConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherAlertEvaluatorTest {

    private val now = 1_700_000_000_000L
    private val hour = 3_600_000L
    private val on = WeatherAlertConfig(enabled = true)

    private fun forecast(
        hours: Int,
        celsius: Double = 15.0,
        icon: Int = Forecast.CLEAR,
        probability: Int? = null,
        precipitation: Double? = null,
        wind: Double? = null,
        uv: Double? = null,
    ) = Forecast(
        timestamp = now + hours * hour,
        temperature = celsius + 273.15,
        icon = icon,
        condition = "",
        location = "Athens",
        provider = "test",
        precipProbability = probability,
        precipitation = precipitation,
        windSpeed = wind,
        uvIndex = uv,
        updateTime = now,
    )

    private fun types(forecasts: List<Forecast>, config: WeatherAlertConfig = on) =
        WeatherAlertEvaluator.evaluate(forecasts, config, now).map { it.type }.toSet()

    @Test
    fun `nothing happens when alerts are off`() {
        val stormy = listOf(forecast(1, icon = Forecast.THUNDERSTORM, probability = 100))
        assertTrue(WeatherAlertEvaluator.evaluate(stormy, on.copy(enabled = false), now).isEmpty())
    }

    @Test
    fun `calm weather gives no alert`() {
        assertTrue(types(listOf(forecast(1), forecast(5), forecast(20))).isEmpty())
    }

    @Test
    fun `rain from the chosen chance of rain`() {
        val low = listOf(forecast(2, probability = 40))
        val high = listOf(forecast(2, probability = 40), forecast(6, probability = 85))
        assertTrue(types(low).isEmpty())
        val alert = WeatherAlertEvaluator.evaluate(high, on, now).single()
        assertEquals(WeatherAlertType.Rain, alert.type)
        assertEquals(now + 6 * hour, alert.time)
        assertEquals(85.0, alert.value!!, 0.0)
    }

    @Test
    fun `a provider without a chance of rain uses the icon`() {
        assertEquals(setOf(WeatherAlertType.Rain), types(listOf(forecast(3, icon = Forecast.RAIN))))
        assertTrue(types(listOf(forecast(3, icon = Forecast.LIGHT_RAIN))).isEmpty())
    }

    @Test
    fun `heavy rain by icon or by amount`() {
        assertEquals(setOf(WeatherAlertType.HeavyRain), types(listOf(forecast(3, icon = Forecast.HEAVY_RAIN))))
        assertEquals(setOf(WeatherAlertType.HeavyRain), types(listOf(forecast(3, precipitation = 6.0))))
    }

    @Test
    fun `snow, sleet and thunder`() {
        assertEquals(setOf(WeatherAlertType.Snow), types(listOf(forecast(4, icon = Forecast.SNOW))))
        assertEquals(setOf(WeatherAlertType.Snow), types(listOf(forecast(4, icon = Forecast.SLEET))))
        assertEquals(setOf(WeatherAlertType.Thunder), types(listOf(forecast(4, icon = Forecast.HAIL))))
    }

    @Test
    fun `heat and frost use the thresholds in degrees celsius`() {
        val heat = WeatherAlertEvaluator.evaluate(listOf(forecast(5, celsius = 31.0), forecast(8, celsius = 37.0)), on, now)
        assertEquals(WeatherAlertType.Heat, heat.single().type)
        assertEquals(37.0, heat.single().value!!, 0.001)
        val frost = WeatherAlertEvaluator.evaluate(listOf(forecast(5, celsius = 4.0), forecast(8, celsius = -3.0)), on, now)
        assertEquals(WeatherAlertType.Frost, frost.single().type)
        assertEquals(-3.0, frost.single().value!!, 0.001)
        assertTrue(types(listOf(forecast(5, celsius = 34.9))).isEmpty())
    }

    @Test
    fun `wind is compared in km per hour`() {
        // 20 m/s is 72 km/h
        assertEquals(setOf(WeatherAlertType.Wind), types(listOf(forecast(2, wind = 20.0))))
        assertTrue(types(listOf(forecast(2, wind = 10.0))).isEmpty())
    }

    @Test
    fun `uv is off by default`() {
        val sunny = listOf(forecast(2, uv = 10.0))
        assertTrue(types(sunny).isEmpty())
        assertEquals(setOf(WeatherAlertType.Uv), types(sunny, on.copy(uv = true)))
    }

    @Test
    fun `only the chosen hours are checked`() {
        val later = listOf(forecast(30, icon = Forecast.SNOW))
        assertTrue(types(later, on.copy(hours = 24)).isEmpty())
        assertEquals(setOf(WeatherAlertType.Snow), types(later, on.copy(hours = 48)))
        // what is long past does not count
        assertTrue(types(listOf(forecast(-5, icon = Forecast.SNOW))).isEmpty())
    }

    @Test
    fun `a switched off kind is skipped`() {
        val forecasts = listOf(forecast(2, icon = Forecast.SNOW), forecast(3, icon = Forecast.THUNDERSTORM))
        assertEquals(setOf(WeatherAlertType.Thunder), types(forecasts, on.copy(snow = false)))
    }

    @Test
    fun `the location comes from the forecast`() {
        assertEquals("Athens", WeatherAlertEvaluator.evaluate(listOf(forecast(2, icon = Forecast.SNOW)), on, now).single().location)
        assertNull(WeatherAlertEvaluator.evaluate(emptyList(), on, now).firstOrNull())
    }

    @Test
    fun `same day compares the date and not the day of the year`() {
        val zone = java.time.ZoneId.of("UTC")
        fun millis(y: Int, m: Int, d: Int) = java.time.LocalDate.of(y, m, d).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        // 31 December 2024 is day 366 and 31 December 2023 is day 365, 1 January is day 1 in both
        assertTrue(WeatherAlertEvaluator.isSameDay(millis(2024, 5, 3), millis(2024, 5, 3) + hour, zone))
        assertTrue(!WeatherAlertEvaluator.isSameDay(millis(2024, 12, 31), millis(2025, 1, 1), zone))
        // the same day of the year, one year apart
        assertTrue(!WeatherAlertEvaluator.isSameDay(millis(2023, 3, 10), millis(2024, 3, 10), zone))
    }
}

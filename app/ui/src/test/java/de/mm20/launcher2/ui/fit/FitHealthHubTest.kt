package de.mm20.launcher2.ui.fit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FitHealthHubTest {
    @Test
    fun vendorPackagesCoverRequestedHealthApps() {
        val ids = FitHealthHub.vendors.map { it.id }.toSet()
        assertTrue(ids.containsAll(FitHealthSourceId.entries.toSet()))
        assertEquals("com.google.android.apps.healthdata", FitHealthHub.vendors.first { it.id == FitHealthSourceId.HealthConnect }.packageName)
        assertEquals("com.garmin.android.apps.connectmobile", FitHealthHub.vendors.first { it.id == FitHealthSourceId.Garmin }.packageName)
        assertEquals("com.sec.android.app.shealth", FitHealthHub.vendors.first { it.id == FitHealthSourceId.Samsung }.packageName)
        assertEquals("com.huawei.health", FitHealthHub.vendors.first { it.id == FitHealthSourceId.Huawei }.packageName)
    }
}

package de.mm20.launcher2.ui.fit

import de.mm20.launcher2.permissions.HealthConnectAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FitHealthHubTest {
    @Test
    fun vendorPackagesCoverRequestedHealthApps() {
        val ids = FitHealthHub.vendors.map { it.id }.toSet()
        assertTrue(ids.containsAll(FitHealthSourceId.entries.toSet()))
        assertEquals(HealthConnectAccess.HEALTH_CONNECT_PACKAGE, FitHealthHub.vendors.first { it.id == FitHealthSourceId.HealthConnect }.packageName)
        assertEquals(HealthConnectAccess.GARMIN_PACKAGE, FitHealthHub.vendors.first { it.id == FitHealthSourceId.Garmin }.packageName)
        assertEquals(HealthConnectAccess.SAMSUNG_HEALTH_PACKAGE, FitHealthHub.vendors.first { it.id == FitHealthSourceId.Samsung }.packageName)
        assertEquals(HealthConnectAccess.HUAWEI_HEALTH_PACKAGE, FitHealthHub.vendors.first { it.id == FitHealthSourceId.Huawei }.packageName)
        assertEquals("com.google.android.apps.healthdata", HealthConnectAccess.HEALTH_CONNECT_PACKAGE)
    }
}

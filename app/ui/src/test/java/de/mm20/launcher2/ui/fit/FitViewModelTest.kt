package de.mm20.launcher2.ui.fit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FitViewModelTest {
    @Test
    fun startsWithoutSampleMealsOrActivity() {
        val vm = FitViewModel()
        assertEquals(0, vm.steps.value)
        assertEquals(0, vm.waterIntake.value)
        assertTrue(vm.loggedMeals.value.isEmpty())
        assertNull(vm.heartRate.value)
        assertNull(vm.sleepHours.value)
        assertNull(vm.weight.value)
    }
}

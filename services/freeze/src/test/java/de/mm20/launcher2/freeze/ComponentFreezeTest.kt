package de.mm20.launcher2.freeze

import org.junit.Assert.*
import org.junit.Test

class ComponentFreezeTest {
    @Test fun inactiveAliasWithActiveLauncherIsNotFrozen() { assertFalse(isComponentFreeze(true, true)) }
    @Test fun allLaunchersDisabledIsFrozen() { assertTrue(isComponentFreeze(false, true)) }
    @Test fun headlessAppAloneIsNotFrozen() { assertFalse(isComponentFreeze(false, false)) }
}

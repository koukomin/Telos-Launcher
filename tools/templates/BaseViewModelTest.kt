package de.mm20.launcher2.test

import org.junit.Rule

/**
 * Base template for ViewModel unit tests in Telos Launcher.
 * Provides Coroutine MainDispatcher swap rule out-of-the-box.
 */
abstract class BaseViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
}

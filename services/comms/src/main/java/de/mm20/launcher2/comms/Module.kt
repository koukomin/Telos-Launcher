package de.mm20.launcher2.comms

import de.mm20.launcher2.comms.t9.T9SearchEngine
import org.koin.dsl.module

val commsModule = module {
    single { T9SearchEngine() }
}

package de.mm20.launcher2.ui.system

import android.content.Context
import de.mm20.launcher2.freeze.BloatDescriptions
import org.koin.core.context.GlobalContext

data class UadInfo(val description: String, val removal: String)

/** Same validated, bundled/offline data as Freeze Manager; never starts a second updater. */
object UadRepository {
    suspend fun getList(context: Context): Map<String, UadInfo> =
        GlobalContext.get().get<BloatDescriptions>().entries().mapValues { (_, row) ->
            UadInfo(row.description, row.recommendation)
        }
}

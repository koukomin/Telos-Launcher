package de.mm20.launcher2.appmanagement

import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.badges.BadgeIcon
import de.mm20.launcher2.badges.BadgeProvider
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.Searchable
import de.mm20.launcher2.base.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FossUpdateBadgeProvider(
    private val repository: FossUpdateRepository
) : BadgeProvider {
    override fun getBadge(searchable: Searchable): Flow<Badge?> {
        if (searchable !is Application) return kotlinx.coroutines.flow.flowOf(null)
        if (searchable.key.startsWith("telos_store_app")) {
            return repository.pendingUpdates.map { set ->
                val token = set.firstOrNull { it.startsWith("store-update:") }
                val n = token?.substringAfter("store-update:")?.toIntOrNull() ?: 0
                if (n > 0) Badge(number = n, icon = BadgeIcon(R.drawable.autorenew_24px)) else null
            }
        }
        val packageName = searchable.componentName.packageName
        return repository.pendingUpdates.map {
            if (it.contains(packageName)) {
                Badge(icon = BadgeIcon(R.drawable.autorenew_24px))
            } else {
                null
            }
        }
    }
}

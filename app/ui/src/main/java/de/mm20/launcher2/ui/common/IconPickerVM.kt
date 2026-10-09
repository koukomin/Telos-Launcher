package de.mm20.launcher2.ui.common

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import de.mm20.launcher2.icons.CustomIconWithPreview
import de.mm20.launcher2.icons.IconPack
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.search.SavableSearchable
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.coroutineContext

class IconPickerVM(
    private val searchable: SavableSearchable
): KoinComponent {
    private val iconService: IconService by inject()

    fun getDefaultIcon(size: Int) = flow {
        emit(iconService.getUncustomizedDefaultIcon(searchable, size))
    }

    fun getIconSuggestions(size: Int) = flow {
        emit(iconService.getCustomIconSuggestions(searchable, size))
    }

    val installedIconPacks = iconService.getInstalledIconPacks()

    val iconSearchResults = mutableStateOf(emptyList<CustomIconWithPreview>())
    val isSearchingIcons = mutableStateOf(false)


    private var debounceSearchJob: Job? = null
    suspend fun searchIcon(query: String, iconPack: IconPack?) {
        debounceSearchJob?.cancelAndJoin()
        if (query.isBlank()) {
            iconSearchResults.value = emptyList()
            isSearchingIcons.value = false
            return
        }
        // Shown as busy during the debounce too, so the UI never flashes "no results"
        isSearchingIcons.value = true
        withContext(coroutineContext) {
            debounceSearchJob = launch {
                delay(500)
                isSearchingIcons.value = true
                iconSearchResults.value = emptyList()
                try {
                    iconSearchResults.value = iconService.searchCustomIcons(query, iconPack)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A failing icon pack must not crash the picker
                    iconSearchResults.value = emptyList()
                }
                isSearchingIcons.value = false
            }
        }
    }
}
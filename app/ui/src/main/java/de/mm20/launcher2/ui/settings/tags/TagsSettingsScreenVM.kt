package de.mm20.launcher2.ui.settings.tags

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.Tag
import de.mm20.launcher2.services.tags.TagsService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TagsSettingsScreenVM: ViewModel(), KoinComponent {
    private val tagsService: TagsService by inject()
    private val iconService: IconService by inject()
    private val appRepository: AppRepository by inject()

    val tags = tagsService.getAllTags()

    var autoCategorizing = mutableStateOf(false)

    /**
     * "Smart folders": one-shot grouping of installed apps into tags named after their system
     * app category (Games, Social, Productivity, ...), reusing the existing tag/folder
     * mechanism rather than a separate grouping concept. Not automatic/continuous - apps
     * installed afterwards need a re-run - to avoid a new background listener for this.
     */
    fun autoCategorizeApps(context: Context) {
        if (!isAtLeastApiLevel(26)) return
        viewModelScope.launch {
            autoCategorizing.value = true
            try {
                val apps = appRepository.findMany().first()
                val pm = context.packageManager
                val byCategory = mutableMapOf<String, MutableList<Application>>()
                for (app in apps) {
                    val category = try {
                        pm.getApplicationInfo(app.componentName.packageName, 0).category
                    } catch (e: PackageManager.NameNotFoundException) {
                        ApplicationInfo.CATEGORY_UNDEFINED
                    }
                    if (category == ApplicationInfo.CATEGORY_UNDEFINED) continue
                    val title = ApplicationInfo.getCategoryTitle(context, category)?.toString()
                        ?: continue
                    byCategory.getOrPut(title) { mutableListOf() }.add(app)
                }
                val existingTags = tags.first()
                for ((category, categoryApps) in byCategory) {
                    if (categoryApps.size < 2) continue
                    if (existingTags.contains(category)) {
                        tagsService.updateTag(category, items = categoryApps)
                    } else {
                        tagsService.createTag(category, categoryApps)
                    }
                }
            } finally {
                autoCategorizing.value = false
            }
        }
    }

    var editTag = mutableStateOf<String?>(null)
    var createTag = mutableStateOf(false)

    fun duplicateTag(tag: String) {
        viewModelScope.launch {
            val allTags = tags.first()
            var i = 2
            var newName = "$tag ($i)"
            while(allTags.contains(newName)) {
                i++
                newName = "$tag ($i)"
            }
            tagsService.cloneTag(tag, newName)
        }
    }

    fun deleteTag(tag: String) {
        tagsService.deleteTag(tag)
    }

    fun getIcon(tag: String): Flow<LauncherIcon?> {
        return iconService.getIcon(Tag(tag), 1)
    }

}
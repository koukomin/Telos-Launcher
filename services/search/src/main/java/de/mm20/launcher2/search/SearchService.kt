package de.mm20.launcher2.search

import android.util.Log
import de.mm20.launcher2.calculator.CalculatorRepository
import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.data.customattrs.utils.withCustomLabels
import de.mm20.launcher2.profiles.Profile
import de.mm20.launcher2.profiles.ProfileManager
import de.mm20.launcher2.search.data.Calculator
import de.mm20.launcher2.search.data.UnitConverter
import de.mm20.launcher2.searchactions.SearchActionService
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.unitconverter.UnitConverterRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

interface SearchService {
    fun search(
        query: String,
        filters: SearchFilters,
        initialResults: SearchResults? = null,
    ): Flow<SearchResults>

    fun getAllApps(): Flow<AllAppsResults>
}

internal class SearchServiceImpl(
    private val appRepository: SearchableRepository<Application>,
    private val appShortcutRepository: SearchableRepository<AppShortcut>,
    private val calendarRepository: SearchableRepository<CalendarEvent>,
    private val contactRepository: SearchableRepository<Contact>,
    private val fileRepository: SearchableRepository<File>,
    private val articleRepository: SearchableRepository<Article>,
    private val locationRepository: SearchableRepository<Location>,
    private val unitConverterRepository: UnitConverterRepository,
    private val calculatorRepository: CalculatorRepository,
    private val websiteRepository: SearchableRepository<Website>,
    private val searchActionService: SearchActionService,
    private val customAttributesRepository: CustomAttributesRepository,
    private val profileManager: ProfileManager,
    private val webAppShortcutRepository: SearchableRepository<WebAppShortcut>,
) : SearchService {

    override fun search(
        query: String,
        filters: SearchFilters,
        initialResults: SearchResults?,
    ): Flow<SearchResults> = flow {
        supervisorScope {
            val results = MutableStateFlow(
                initialResults?.let {
                    it.copy(
                        apps = if (filters.apps) it.apps else null,
                        shortcuts = if (filters.shortcuts) it.shortcuts else null,
                        contacts = if (filters.contacts) it.contacts else null,
                        calendars = if (filters.events) it.calendars else null,
                        reminders = if (filters.reminders) it.reminders else null,
                        // Type-specific file categories are an alternative to the generic
                        // "files" list, not additional to it - otherwise a PDF shows up in both.
                        files = if (filters.files) it.files else null,
                        documents = if (!filters.files && filters.documents) it.documents else null,
                        images = if (!filters.files && filters.images) it.images else null,
                        video = if (!filters.files && filters.video) it.video else null,
                        music = if (!filters.files && filters.music) it.music else null,
                        calculators = if (filters.tools) it.calculators else null,
                        unitConverters = if (filters.tools) it.unitConverters else null,
                        websites = if (filters.websites) it.websites else null,
                        wikipedia = if (filters.articles) it.wikipedia else null,
                        locations = if (filters.places) it.locations else null,
                        webAppShortcuts = if (filters.apps) it.webAppShortcuts else null,
                    )
                }
                    ?: SearchResults())

            val customAttrResults = customAttributesRepository.search(query)
                .map { items ->
                    val apps = mutableListOf<Application>()
                    val shortcuts = mutableListOf<AppShortcut>()
                    val contacts = mutableListOf<Contact>()
                    val events = mutableListOf<CalendarEvent>()
                    val reminders = mutableListOf<CalendarEvent>()
                    val files = mutableListOf<File>()
                    val documents = mutableListOf<File>()
                    val images = mutableListOf<File>()
                    val video = mutableListOf<File>()
                    val music = mutableListOf<File>()
                    val unitConverters = mutableListOf<UnitConverter>()
                    val websites = mutableListOf<Website>()
                    val wikipedia = mutableListOf<Article>()
                    val locations = mutableListOf<Location>()
                    val searchActions = mutableListOf<SearchAction>()
                    for (it in items) {
                        when (it) {
                            is Application -> if (filters.apps) apps.add(it)
                            is AppShortcut -> if (filters.shortcuts) shortcuts.add(it)
                            is Contact -> if (filters.contacts) contacts.add(it)
                            is CalendarEvent -> {
                                if (it.isTask) {
                                    if (filters.reminders) reminders.add(it)
                                } else {
                                    if (filters.events) events.add(it)
                                }
                            }
                            is File -> {
                                if (filters.files) {
                                    files.add(it)
                                } else {
                                    // Only split into type categories when the generic "files"
                                    // filter is off, so a file never appears in two categories.
                                    if (it.isDocument() && filters.documents) documents.add(it)
                                    if (it.isImage() && filters.images) images.add(it)
                                    if (it.isVideo() && filters.video) video.add(it)
                                    if (it.isMusic() && filters.music) music.add(it)
                                }
                            }
                            is UnitConverter -> if (filters.tools) unitConverters.add(it)
                            is Website -> if (filters.websites) websites.add(it)
                            is Article -> if (filters.articles) wikipedia.add(it)
                            is Location -> if (filters.places) locations.add(it)
                            is SearchAction -> searchActions.add(it)
                        }
                    }
                    SearchResults(
                        apps = apps,
                        shortcuts = shortcuts,
                        contacts = contacts,
                        calendars = events,
                        reminders = reminders,
                        files = files,
                        documents = documents,
                        images = images,
                        video = video,
                        music = music,
                        unitConverters = unitConverters,
                        websites = websites,
                        wikipedia = wikipedia,
                        locations = locations,
                        searchActions = searchActions,
                    )
                }.shareIn(this, SharingStarted.WhileSubscribed(), 1)

            launch {
                searchActionService.search(query)
                    .collectLatest { r ->
                        results.update {
                            it.copy(searchActions = r)
                        }
                    }
            }
            if (filters.apps) {
                launch {
                    appRepository.search(query, filters.allowNetwork)
                        .combine(customAttrResults) { apps, customAttrs ->
                            if (customAttrs.apps != null) apps + customAttrs.apps
                            else apps
                        }
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(apps = r)
                            }
                        }
                }
            }
            if (filters.shortcuts) {
                launch {
                    appShortcutRepository.search(query, filters.allowNetwork)
                        .combine(customAttrResults) { shortcuts, customAttrs ->
                            if (customAttrs.shortcuts != null) shortcuts + customAttrs.shortcuts
                            else shortcuts
                        }
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(shortcuts = r)
                            }
                        }
                }
            }
            if (filters.contacts) {
                launch {
                    contactRepository.search(query, filters.allowNetwork)
                        .combine(customAttrResults) { contacts, customAttrs ->
                            if (customAttrs.contacts != null) contacts + customAttrs.contacts
                            else contacts
                        }
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(contacts = r)
                            }
                        }
                }
            }
            if (filters.events || filters.reminders) {
                launch {
                    calendarRepository.search(query, filters.allowNetwork)
                        .combine(customAttrResults) { calendars, customAttrs ->
                            val base = if (customAttrs.calendars != null || customAttrs.reminders != null) {
                                calendars + (customAttrs.calendars ?: emptyList()) + (customAttrs.reminders ?: emptyList())
                            } else calendars
                            base
                        }
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(
                                    calendars = if (filters.events) r.filter { !it.isTask } else null,
                                    reminders = if (filters.reminders) r.filter { it.isTask } else null
                                )
                            }
                        }
                }
            }
            if (filters.tools) {
                launch {
                    calculatorRepository.search(query).collectLatest { r ->
                        results.update {
                            it.copy(calculators = r?.let { listOf(it) }
                                ?: listOf())
                        }
                    }
                }
                launch {
                    unitConverterRepository.search(query)
                        .collectLatest { r ->
                            results.update {
                                it.copy(unitConverters = r?.let { listOf(it) }
                                    ?: listOf())
                            }
                        }
                }
            }
            if (filters.websites) {
                launch {
                    websiteRepository.search(query, filters.allowNetwork)
                        .combine(customAttrResults) { websites, customAttrs ->
                            if (customAttrs.websites != null) websites + customAttrs.websites
                            else websites
                        }
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(websites = r)
                            }
                        }
                }
            }
            if (filters.apps) {
                launch {
                    webAppShortcutRepository.search(query, filters.allowNetwork)
                        .collectLatest { r ->
                            results.update {
                                it.copy(webAppShortcuts = r)
                            }
                        }
                }
            }
            if (filters.articles) {
                launch {
                    delay(750)
                    articleRepository.search(query, filters.allowNetwork)
                        .combine(customAttrResults) { articles, customAttrs ->
                            if (customAttrs.wikipedia != null) articles + customAttrs.wikipedia
                            else articles
                        }
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(wikipedia = r)
                            }
                        }
                }
            }
            if (filters.places) {
                launch {
                    delay(250)
                    locationRepository.search(query, filters.allowNetwork)
                        .combine(customAttrResults) { locations, customAttrs ->
                            if (customAttrs.locations != null) locations + customAttrs.locations
                            else locations
                        }
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(locations = r)
                            }
                        }
                }
            }
            if (filters.files || filters.documents || filters.images || filters.video || filters.music) {
                launch {
                    fileRepository.search(
                        query,
                        filters.allowNetwork
                    )
                        .combine(customAttrResults) { files, customAttrs ->
                            val base = if (customAttrs.files != null || customAttrs.documents != null || customAttrs.images != null || customAttrs.video != null || customAttrs.music != null) {
                                files + (customAttrs.files ?: emptyList()) + (customAttrs.documents ?: emptyList()) + (customAttrs.images ?: emptyList()) + (customAttrs.video ?: emptyList()) + (customAttrs.music ?: emptyList())
                            } else files
                            base
                        }
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(
                                    files = if (filters.files) r else null,
                                    documents = if (!filters.files && filters.documents) r.filter { it.isDocument() } else null,
                                    images = if (!filters.files && filters.images) r.filter { it.isImage() } else null,
                                    video = if (!filters.files && filters.video) r.filter { it.isVideo() } else null,
                                    music = if (!filters.files && filters.music) r.filter { it.isMusic() } else null,
                                )
                            }
                        }
                }
            }
            emitAll(results)
        }
    }

    override fun getAllApps(): Flow<AllAppsResults> {
        return profileManager.profiles.flatMapLatest { profiles ->
            val standardProfile = profiles.find { it.type == Profile.Type.Personal }
            val workProfile = profiles.find { it.type == Profile.Type.Work }
            val privateSpace = profiles.find { it.type == Profile.Type.Private }
            appRepository.search("", false)
                .withCustomLabels(customAttributesRepository)
                .combine(webAppShortcutRepository.search("", false)) { apps, webAppShortcuts ->
                    apps to webAppShortcuts
                }
                .map { (apps, webAppShortcuts) ->
                    val standardProfileApps = mutableListOf<Application>()
                    val workProfileApps = mutableListOf<Application>()
                    val privateSpaceApps = mutableListOf<Application>()
                    for (app in apps) {
                        when {
                            standardProfile != null && app.user == standardProfile.userHandle -> standardProfileApps.add(
                                app
                            )

                            workProfile != null && app.user == workProfile.userHandle -> workProfileApps.add(
                                app
                            )

                            privateSpace != null && app.user == privateSpace.userHandle -> privateSpaceApps.add(
                                app
                            )

                            else -> {
                                Log.w(
                                    "MM20",
                                    "App ${app.label} does not belong to any known profile. Ignoring."
                                )
                            }
                        }
                    }

                    AllAppsResults(
                        standardProfileApps = standardProfileApps.sorted(),
                        workProfileApps = workProfileApps.sorted(),
                        privateSpaceApps = privateSpaceApps.sorted(),
                        webAppShortcuts = webAppShortcuts.sorted(),
                    )
                }
        }
    }
}

data class SearchResults(
    val apps: List<Application>? = null,
    val shortcuts: List<AppShortcut>? = null,
    val contacts: List<Contact>? = null,
    val calendars: List<CalendarEvent>? = null,
    val reminders: List<CalendarEvent>? = null,
    val files: List<File>? = null,
    val documents: List<File>? = null,
    val images: List<File>? = null,
    val video: List<File>? = null,
    val music: List<File>? = null,
    val calculators: List<Calculator>? = null,
    val unitConverters: List<UnitConverter>? = null,
    val websites: List<Website>? = null,
    val wikipedia: List<Article>? = null,
    val locations: List<Location>? = null,
    val searchActions: List<SearchAction>? = null,
    val webAppShortcuts: List<WebAppShortcut>? = null,
)

data class AllAppsResults(
    val standardProfileApps: List<Application>,
    val workProfileApps: List<Application>,
    val privateSpaceApps: List<Application>,
    val webAppShortcuts: List<WebAppShortcut> = emptyList(),
)

fun SearchResults.toList(): List<Searchable> {
    return listOfNotNull(
        apps,
        shortcuts,
        contacts,
        calendars,
        reminders,
        files,
        documents,
        images,
        video,
        music,
        calculators,
        unitConverters,
        websites,
        wikipedia,
        searchActions,
        webAppShortcuts,
    ).flatten()
}

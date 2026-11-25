# Telos Launcher - Component & Logic Registry (AI Database)

**ATTENTION ALL AI AGENTS:** 
DO NOT write raw UI layouts, repeated search logic, or raw shell commands if a component listed here exists. Use this registry to locate the correct tool for the job. 

## 1. UI Components (Jetpack Compose)
| Need | Component | Path/Package |
| --- | --- | --- |
| **Settings Screen Root** | `PreferenceScreen` | `de.mm20.launcher2.ui.component.preferences.PreferenceScreen` |
| **Settings Category** | `PreferenceCategory` | `de.mm20.launcher2.ui.component.preferences.PreferenceCategory` |
| **Toggle Switch** | `SwitchPreference` | `de.mm20.launcher2.ui.component.preferences.SwitchPreference` |
| **List/Enum Picker** | `ListPreference` | `de.mm20.launcher2.ui.component.preferences.ListPreference` |
| **Search Bar** | `TelosSearchBar` | `de.mm20.launcher2.ui.component.TelosSearchBar` |
| **Empty Search State** | `SearchEmptyState` | `de.mm20.launcher2.ui.component.SearchEmptyState` |
| **Cyber-Tech Titles** | `TelosCyberHeading` | `de.mm20.launcher2.ui.component.TelosCyberHeading` |
| **Tech Pills (Tabs)** | `TelosTechPill` | `de.mm20.launcher2.ui.component.TelosTechPill` |
| **Scrolling Text** | `MarqueeText` | `de.mm20.launcher2.ui.component.MarqueeText` |
| **Cyber Cards** | `Modifier.telosTechCard()` | `de.mm20.launcher2.ui.theme.TelosTechCard` |

## 2. Business Logic & Utilities
| Need | Utility / Function | Path/Package |
| --- | --- | --- |
| **Search Matching** | `TelosSearch.matches(query, *fields)` | `de.mm20.launcher2.comms.search.TelosSearch` |
| **String Folding (Greek/Latin)** | `GreekFold.fold(text)` | `de.mm20.launcher2.search.GreekFold` |
| **Execute Root/Shizuku Cmd** | `TelosShellEngine.execResult()` | `de.mm20.launcher2.base.TelosShellEngine` |
| **Virtual App Definitions** | `interface VirtualApplication` | `de.mm20.launcher2.search.VirtualApplication` |
| **Virtual App Icons** | `VirtualAppIcon.tileOnPlate()` | `de.mm20.launcher2.search.VirtualAppIcon` |

## 3. Repositories & State Management (Single Source of Truth)
| Need | Repository / Manager | Inject Type |
| --- | --- | --- |
| **Installed Apps** | `AppRepository` | `org.koin.core.component.inject` |
| **Package Freezing** | `FreezeManager` | `org.koin.core.component.inject` |
| **App Store Updates** | `StoreUpdateScheduler` | `org.koin.core.component.inject` |
| **App/Package Search** | `SavableSearchableRepository` | `org.koin.core.component.inject` |
| **Contacts** | `ContactRepository` | `org.koin.core.component.inject` |
| **Files & Storage** | `FilesRepository` | `org.koin.core.component.inject` |
| **Calendar Events** | `CalendarRepository` | `org.koin.core.component.inject` |
| **Weather Data** | `WeatherRepository` | `org.koin.core.component.inject` |
| **Websites & History** | `WebsiteRepository` | `org.koin.core.component.inject` |
| **Call Logs** | `CallLogRepository` | `org.koin.core.component.inject` |
| **SMS/MMS Messages** | `MessageRepository` | `org.koin.core.component.inject` |
| **Radio Stations** | `RadioRepository` | `org.koin.core.component.inject` |
| **Desktop Window Mgmt**| `DesktopWindowManager` | `org.koin.core.component.inject` |
| **App Lock & Security**| `AppLockManager` | `org.koin.core.component.inject` |

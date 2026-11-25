# Telos Launcher — AI Agent Command Blueprints

Quick-reference prompt shortcuts for AI Agents working on Telos Launcher workflows.

---

## Blueprint 1: Create New Virtual App
```text
Execute Blueprint "New Virtual App"
- Name: "Telos Scanner"
- Domain: "telos_scanner_app"
- Route: "ROUTE_SCANNER"
- Icon: "ic_app_scanner_fg"
Steps:
1. Inherit `VirtualApplication` in `VirtualCommsApps.kt` or dedicated class.
2. Register in `SettingsDeepLinkContract.kt`, `TelosAppLabels.kt`, `TelosAppIcons.kt`.
3. Add Rondo drawables in `core/base/src/main/res/drawable/`.
4. Register in `.artifacts/COMPONENT_REGISTRY.md` and `CONTEXT_MAP.json`.
```

## Blueprint 2: Create New Settings Preference Screen
```text
Execute Blueprint "New Preference Screen"
- Title: "Network Protection Settings"
- ViewModel: "NetworkSettingsVM"
Steps:
1. Inherit `ViewModel()`, `KoinComponent`.
2. Use `asPreferenceState(viewModelScope, default)` for Datastore flow bindings.
3. Build Composable UI using `PreferenceScreen`, `PreferenceCategory`, `SwitchPref`, and `ListPref`.
4. Keep file under 500 lines or decompose into `*Categories.kt`.
```

## Blueprint 3: Add New Search Provider / Quick Action
```text
Execute Blueprint "New Search Provider"
- Source: "Local Bookmarks"
Steps:
1. Implement `SearchableRepository` or domain matcher in `:data:*`.
2. Use `TelosSearch.matches(query, *fields)` for zero-allocation token matching.
3. Expose Flow of results through Koin DI.
4. Integrate with `SearchVM.kt`.
```

## Blueprint 4: Database Schema Migration
```text
Execute Blueprint "Room Database Migration"
- Entity: "AppDatabase"
Steps:
1. Bump Room `@Database(version = N)`.
2. Provide explicit `Migration(N-1, N)` object.
3. Test migration in `:data:database:test`.
```

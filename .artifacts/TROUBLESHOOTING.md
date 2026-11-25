# Telos Launcher — AI Agent Troubleshooting & Gotchas Memory Bank

This file records complex Android platform gotchas and architecture constraints in Telos to save debugging time for AI agents.

---

## 1. Jetpack Compose Recomposition & State
- **Unstable Parameters**: Standard `List<T>` or `Map<K, V>` parameters cause Jetpack Compose to recompose the entire list/card on every state change.
  - *Fix*: Mark model data classes with `@Immutable` or `@Stable` from `androidx.compose.runtime`.
- **Derived State**: Heavy filtering, sorting, or `GreekFold.matches` inside Composable bodies MUST be wrapped in `remember(keys) { ... }` or `derivedStateOf`.

## 2. Media3 & Video Player Lifecycle
- **Surface Release**: When switching streams or closing video overlays in `TelosVideo` / `Stremio`, ensure `PlayerSurface` is released via `DisposableEffect` to avoid native GPU surface leaks.
- **Background Audio**: Do not call `player.stop()` on Activity `onStop` when background audio playback is enabled.

## 3. Shizuku & Root Process IPC
- **Binder Transaction Size Limit**: Shizuku IPC calls have a 1MB Binder transaction limit. Never transfer large APK byte arrays over Shizuku Binder. Use `ParcelFileDescriptor` / `FileProvider`.
- **Process Info Guard**: Always check `ProcessInfo.init(context)` in `LauncherApplication` to prevent secondary background processes (like isolated video player) from initializing launcher Koin DI or UI overlays.

## 4. Room Database & Datastore Migrations
- **Room Schema Export**: In Room DAOs, avoid unversioned entity schema changes. Use `@Database(autoMigrations = ...)` or explicit migration scripts.
- **Datastore Main Thread Blocking**: Never call `.first()` on Datastore `Flow` inside `Composable` functions or on `Dispatchers.Main`. Always collect as state or use `asPreferenceState(viewModelScope, default)`.

## 5. Search Engine & GreekFold Matching
- **Char Allocations**: Never use `c.toString()` inside character folding loops. Append `Char` directly to `StringBuilder` to maintain zero-allocation 120Hz scrolling performance.

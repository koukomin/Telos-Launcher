# Telos Launcher — Session & Prompt Progress Tracker

> **Σκοπός**: Καταγραφή όλων των prompts, της προόδου και της κατάστασης εργασιών ώστε σε περίπτωση crash της εφαρμογής/IDE, ο AI agent να ανακτά άμεσα το σημείο στο οποίο βρισκόταν.

---

## 📍 Τρέχουσα Κατάσταση (Active State)

- **Ημερομηνία/Ώρα**: `2026-11-24`
- **Τρέχουσα Εργασία**: Ολοκλήρωση όλων των High Splits (`LauncherScaffold.kt`, `StoreDashboardScreen.kt`, `EventCache.kt`), συγχώνευση στο `main`, έκδοση `v1.45.80`, και παρακολούθηση του release build στο GitHub Actions.

---

## 🎯 Ολοκληρωμένες Εργασίες

- [x] **IME Split (Medium)**: Απομόνωση λογικής του `TelosKeyboardService.kt` στο `TelosKeyboardEditor.kt`.
- [x] **PR 136 Merge**: Εγκρίθηκε και συγχωνεύθηκε το PR #136 (Vue docs).
- [x] **PR 137 Resolution**: Έκλεισε το PR #137 (Gradle 9.9.2) για διατήρηση της σταθερότητας στο Gradle 9.9.1.
- [x] **CodeQL Fix**: Διορθώθηκε το `build-mode` σε `none` στο `.github/workflows/codeql-security-scan.yml`.
- [x] **High Split #1**: `LauncherScaffold.kt` χωρίστηκε σε `LauncherScaffoldState.kt` & `LauncherScaffoldModels.kt`.
- [x] **High Split #2**: `StoreDashboardScreen.kt` χωρίστηκε σε `StoreDashboardComponents.kt` & `StoreDashboardHelpers.kt`.
- [x] **High Split #3**: `EventCache.kt` χωρίστηκε σε `EventCacheSearch.kt`.
- [x] **Merge & Release v1.45.80**: Συγχώνευση του `code-cleanup` στο `main`, bump της έκδοσης σε `v1.45.80`, tagging και έναρξη του release workflow στο GitHub.

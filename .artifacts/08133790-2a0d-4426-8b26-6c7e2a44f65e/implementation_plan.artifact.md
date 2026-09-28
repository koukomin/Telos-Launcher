# Implementation Plan - Systematic Translation Update

This plan aims to provide complete Greek and German translations for all strings in the Telos Launcher, specifically focusing on the new customization features and any gaps in existing settings.

## User Review Required

> [!NOTE]
> I will translate all missing keys in `values-el/strings.xml` and `values-de/strings.xml`. If a translation seems ambiguous, I will use common Android terminology.

## Proposed Changes

### 1. Localization Update: Greek
- **[MODIFY] [strings.xml](file:///home/koukos/Projects/Telos-Launcher/core/i18n/src/main/res/values-el/strings.xml)**:
    - Add missing translations for all features:
        - Independent Grids (Home, Search, Dock).
        - Home Screen Folders (Creation, Covers, Management).
        - Desktop Lock.
        - Advanced Icon Labels (Size, Lines, Shadow).
        - Dock Styling (Background plate, Color, Opacity, Blur).
        - Web App UI Refinements.
        - At a Glance, Dynamic Island, Desktop Mode, Context Profiles, etc.

### 2. Localization Update: German
- **[MODIFY] [strings.xml](file:///home/koukos/Projects/Telos-Launcher/core/i18n/src/main/res/values-de/strings.xml)**:
    - Perform the same updates as for Greek to ensure parity.

## Verification Plan

### Manual Verification
1.  **Switch Language**: Change device language to Greek and verify all settings screens.
2.  **Switch Language**: Change device language to German and verify all settings screens.
3.  **UI Check**: Ensure translated strings fit within their UI components (no clipping).

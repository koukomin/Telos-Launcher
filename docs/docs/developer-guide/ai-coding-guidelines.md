# AI Agent Coding Guidelines for Telos

## Rondo icons on every new virtual app

When you add a Telos virtual app, you also add its Rondo icon in the same
change. Follow [ICON_DESIGN.md](/ICON_DESIGN.md) and
[Rondo icons](/docs/developer-guide/rondo-icons). Copy Phone / Files / Messages
(`ic_app_*_full.xml`). Do not invent a new silhouette language.

## String Resource Optimization (Deduplication)
To keep the APK size as small as possible, **do not duplicate translated text** across different string files or within the same language file.

When adding new UI components that require common labels (e.g., "Cancel", "Save", "Settings", "Search", "Close"):
1. **Search existing string resources** across the project (e.g., `hc_cancel`, `hc_save`, `action_settings`).
2. **Use Android String Aliasing** to link to the existing string instead of redefining the text.

**Example:**
Instead of creating a new translation:
```xml
<!-- WRONG: Duplicating text -->
<string name="new_feature_cancel">Cancel</string>
<string name="new_feature_save">Save</string>
```

Link to the existing strings:
```xml
<!-- CORRECT: Aliasing existing strings -->
<string name="new_feature_cancel">@string/hc_cancel</string>
<string name="new_feature_save">@string/hc_save</string>
```
All AI coding agents (Copilots) modifying this project MUST follow this directive stringently.

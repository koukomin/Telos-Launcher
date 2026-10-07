# Gesture Action Provider

Gesture action plugins expose one or more named actions that a user can bind to a launcher
gesture (swipe up/down/left/right, double tap, long press, etc.) from Settings > Gestures,
alongside the built-in gesture targets (open search, show widgets, launch an app, ...).

Gesture action plugins need to extend the
<a href="/Telos-Launcher/reference/plugins/sdk/de.mm20.launcher2.sdk.gestures/-gesture-action-provider/index.html" target="_blank">`GestureActionProvider`</a>
class:

```kt
class MyGestureActionPlugin : GestureActionProvider() {
    override suspend fun getActions(): List<PluginGestureAction> {
        return listOf(
            PluginGestureAction(id = "toggle_flashlight", label = "Toggle flashlight"),
        )
    }

    override suspend fun invokeAction(actionId: String) {
        when (actionId) {
            "toggle_flashlight" -> toggleFlashlight()
        }
    }
}
```

Unlike search-style plugin types, this one has no config object and no query/refresh/get
lifecycle - it's a short, static-ish list of actions plus a single entry point to invoke one of
them.

## List the available actions

```kt
suspend fun getActions(): List<PluginGestureAction>
```

Return the actions your plugin currently offers. The launcher calls this every time the user
opens the gesture picker, so the list can change over time (for example, based on how your
plugin is configured) - but keep it short, this is meant to be a small, scannable list, not a
search result set.

### The `PluginGestureAction` object

- `id`: A stable identifier for this action, unique within your plugin. This is what gets stored
  in the launcher's gesture settings and passed back to `invokeAction`. Do not change the
  meaning of an existing `id` once published - users who bound a gesture to it will silently
  start triggering something else.
- `label`: A short, human-readable label shown in the gesture picker.

## Invoke an action

```kt
suspend fun invokeAction(actionId: String)
```

Called when the user triggers a gesture that's bound to the action with this `actionId`. This
runs on a background thread with no foreground UI context - if your action needs to show
something, use a notification, a `PendingIntent`-backed UI, or similar; don't assume you can
start an activity without `FLAG_ACTIVITY_NEW_TASK`-style handling.

## Plugin state

<!--@include: ./common/_plugin_state.md-->

## Notes

- There's no `refresh`/`get`/storage strategy for this plugin type - actions aren't stored
  objects, just an id the launcher remembers.
- If `getActions()` no longer returns an `id` the user previously bound a gesture to, invoking it
  simply does nothing - it does not fall back or clear the user's setting for you. If you rename
  or remove an action, consider keeping the old `id` working (mapped to the new behavior) rather
  than breaking existing bindings silently.

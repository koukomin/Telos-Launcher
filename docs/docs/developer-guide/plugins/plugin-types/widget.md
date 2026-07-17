# Widget Provider

Widget plugins let your app add an entry to the launcher's home screen widget picker, without
implementing a real Android `AppWidgetProvider`. This is deliberately data-only: your plugin
supplies a short list of title/subtitle/value rows, and the launcher renders them using its own
widget UI. Your plugin does not draw anything itself.

If you need full control over the widget's visual appearance, publish a normal Android app widget
instead (`AppWidgetProvider`) - users can already add those to their Kvaesitso home screen through
the regular Android widget picker, no plugin needed. Use this plugin type when you just want to
surface a handful of at-a-glance values (a status, a count, a "next thing" reminder, etc.) without
maintaining a full `RemoteViews`-based widget.

Widget plugins need to extend the
<a href="/reference/plugins/sdk/de.mm20.launcher2.sdk.widget/-widget-provider/index.html" target="_blank">`WidgetProvider`</a>
class:

```kt
class MyWidgetPlugin : WidgetProvider() {
    override suspend fun getWidgetItems(lang: String): List<PluginWidgetItem> {
        return listOf(
            PluginWidgetItem(title = "Unread", value = "3"),
            PluginWidgetItem(title = "Next pickup", subtitle = "Tuesday", value = "08:00"),
        )
    }
}
```

## Get the widget's content

```kt
suspend fun getWidgetItems(lang: String): List<PluginWidgetItem>
```

Called every time the launcher refreshes the widget (currently every few minutes while the
widget is visible). Return the current content as a short list of rows - this isn't a scrollable
list, so keep it to what fits on a home screen widget (a handful of rows).

- `lang` is the ISO 639 language code the user has set for the launcher. Use it for any localized
  text if your plugin supports multiple languages.

### The `PluginWidgetItem` object

- `title`: The main text of this row.
- `subtitle` (optional): Secondary text, shown below the title.
- `value` (optional): A value shown aligned to the end of the row (a count, a percentage, a time,
  ...).

## Plugin state

<!--@include: ./common/_plugin_state.md-->

## Notes

- There's no tap-to-open-something contract for individual rows in this first version - the
  widget as a whole is read-only. If you need a widget row to open something in your app, expose
  that as a [gesture action](/docs/developer-guide/plugins/plugin-types/gesture-action.html)
  instead, or ship a real `AppWidgetProvider`.
- The widget's title, shown in the widget picker and as a fallback label, comes from your
  plugin's [label metadata](/docs/developer-guide/plugins/metadata.html), not from
  `getWidgetItems()`.

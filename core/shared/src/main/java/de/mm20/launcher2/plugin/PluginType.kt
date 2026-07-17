package de.mm20.launcher2.plugin

enum class PluginType {
    FileSearch,
    Weather,
    LocationSearch,
    Calendar,
    ContactSearch,

    /**
     * A plugin that offers one or more named actions that can be bound to launcher gestures
     * (e.g. swipe up, double tap). See [de.mm20.launcher2.plugin.contracts.GestureActionPluginContract].
     */
    GestureAction,

    /**
     * A plugin that supplies a small, read-only list of title/subtitle/value rows to be
     * displayed as a home screen widget. See [de.mm20.launcher2.plugin.contracts.WidgetPluginContract].
     */
    Widget,
}
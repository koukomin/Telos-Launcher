package de.mm20.launcher2.plugin.contracts

/**
 * Contract for [de.mm20.launcher2.plugin.PluginType.GestureAction] plugins: a plugin exposes a
 * small, fixed list of named actions (queried via [Paths.Actions]) that the user can bind to a
 * launcher gesture in settings, and that the launcher later invokes via [Methods.Invoke].
 */
object GestureActionPluginContract {
    object Paths {
        /** content://<authority>/actions - query for the list of available actions. */
        const val Actions = "actions"
    }

    object Methods {
        /**
         * ContentProvider.call() method name to invoke an action. The action id is passed as
         * the `arg` parameter of the call.
         */
        const val Invoke = "invokeAction"
    }

    object ActionColumns : Columns() {
        val Id = column<String>("id")
        val Label = column<String>("label")
    }
}

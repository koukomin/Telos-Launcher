package de.mm20.launcher2.ui.agents
import de.mm20.launcher2.i18n.R as I18nR

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.net.URLEncoder

object AgentLauncher {
    fun send(context: Context, query: String, agentId: String, transport: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return false
        val agent = AgentCatalog.byId(agentId)
        val ok = when (transport) {
            "app" -> openApp(context, agent, trimmed)
            "web" -> openWeb(context, agent, trimmed)
            "termux" -> openTermux(context, agent, trimmed)
            else -> openApp(context, agent, trimmed) || openWeb(context, agent, trimmed)
        }
        if (!ok) {
            Toast.makeText(context, I18nR.string.agent_launch_failed, Toast.LENGTH_SHORT).show()
        }
        return ok
    }

    private fun openApp(context: Context, agent: AgentSpec, query: String): Boolean {
        val pm = context.packageManager
        for (pkg in agent.packages) {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, query)
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (send.resolveActivity(pm) != null) {
                copyQuery(context, query)
                context.startActivity(send)
                return true
            }
            val launch = pm.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                copyQuery(context, query)
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                return true
            }
        }
        return false
    }

    private fun openWeb(context: Context, agent: AgentSpec, query: String): Boolean {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val uri = Uri.parse(agent.webBase + encoded)
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun openTermux(context: Context, agent: AgentSpec, query: String): Boolean {
        val escaped = query.replace("'", "'\\''")
        val cmd = "${agent.termuxCommand} '$escaped'"
        val run = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", cmd))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
            putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
        }
        val started = runCatching {
            context.startService(run)
            true
        }.getOrDefault(false)
        if (started) return true
        val launch = context.packageManager.getLaunchIntentForPackage("com.termux")
        if (launch != null) {
            copyQuery(context, query)
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            return true
        }
        return false
    }

    private fun copyQuery(context: Context, query: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("agent", query))
    }
}

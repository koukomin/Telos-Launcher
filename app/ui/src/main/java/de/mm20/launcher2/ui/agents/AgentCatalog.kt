package de.mm20.launcher2.ui.agents
import de.mm20.launcher2.i18n.R as I18nR

data class AgentSpec(
    val id: String,
    val labelRes: Int,
    val packages: List<String>,
    val webBase: String,
    val termuxCommand: String,
)

object AgentCatalog {
    val all: List<AgentSpec> = listOf(
        AgentSpec(
            id = "grok",
            labelRes = I18nR.string.agent_grok,
            packages = listOf("ai.x.grok", "com.x.android"),
            webBase = "https://grok.com/?q=",
            termuxCommand = "grok",
        ),
        AgentSpec(
            id = "claude",
            labelRes = I18nR.string.agent_claude,
            packages = listOf("com.anthropic.claude"),
            webBase = "https://claude.ai/new?q=",
            termuxCommand = "claude",
        ),
        AgentSpec(
            id = "chatgpt",
            labelRes = I18nR.string.agent_chatgpt,
            packages = listOf("com.openai.chatgpt"),
            webBase = "https://chatgpt.com/?q=",
            termuxCommand = "chatgpt",
        ),
        AgentSpec(
            id = "gemini",
            labelRes = I18nR.string.agent_gemini,
            packages = listOf("com.google.android.apps.bard"),
            webBase = "https://gemini.google.com/app?q=",
            termuxCommand = "gemini",
        ),
        AgentSpec(
            id = "copilot",
            labelRes = I18nR.string.agent_copilot,
            packages = listOf("com.microsoft.copilot"),
            webBase = "https://copilot.microsoft.com/?q=",
            termuxCommand = "copilot",
        ),
    )

    fun byId(id: String): AgentSpec = all.firstOrNull { it.id == id } ?: all.first()
}

package com.flex.assistant.ai

import com.flex.assistant.FlexConfig
import org.json.JSONArray
import org.json.JSONObject

/** The single AI brain. Every mode (chat, voice, vision...) must go through this class. */
class FlexBrain(
    private val client: GeminiClient,
    private val conversation: ConversationManager,
    private val tools: ToolRouter
) {
    private val systemPrompt = """
        You are ${FlexConfig.ASSISTANT_NAME}, a personal AI assistant running on the user's Android phone.
        Reply briefly and naturally. The user may speak English, Hindi or Kannada; answer in the language they use.
        When the user wants an app opened, call open_app. Never say an action succeeded unless the tool result has success=true.
        When the user tells you something worth keeping (birthdays, names, projects, plans, codes), call remember.
        When the user asks about something they told you before, call recall first, then answer from the result.
        If a request needs a capability you do not have a tool for, say plainly that it is not available yet. Never pretend.
    """.trimIndent()

    suspend fun process(userText: String): String {
        val contents = conversation.toContents()
        contents.put(textTurn("user", userText))

        for (step in 0 until FlexConfig.MAX_TOOL_STEPS) {
            val resp = client.generate(contents, systemPrompt, tools.declarations())
            val content = resp.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")
                ?: throw FlexException("Gemini returned no answer (it may have been blocked).")
            val parts = content.optJSONArray("parts") ?: JSONArray()

            val calls = ArrayList<JSONObject>()
            val text = StringBuilder()
            for (i in 0 until parts.length()) {
                val p = parts.getJSONObject(i)
                val fc = p.optJSONObject("functionCall")
                if (fc != null) calls.add(fc)
                if (p.has("text") && !p.optBoolean("thought", false)) text.append(p.getString("text"))
            }

            if (calls.isEmpty()) {
                val reply = text.toString().trim().ifEmpty { "..." }
                conversation.add("user", userText)
                conversation.add("model", reply)
                return reply
            }

            contents.put(content) // keep the model's function-call turn exactly as returned
            val responseParts = JSONArray()
            for (c in calls) {
                val name = c.getString("name")
                val out = tools.execute(name, c.optJSONObject("args") ?: JSONObject())
                responseParts.put(JSONObject().put("functionResponse",
                    JSONObject().put("name", name).put("response", out)))
            }
            contents.put(JSONObject().put("role", "user").put("parts", responseParts))
        }
        throw FlexException("Too many tool steps without a final answer.")
    }

    private fun textTurn(role: String, text: String) = JSONObject().put("role", role)
        .put("parts", JSONArray().put(JSONObject().put("text", text)))
}

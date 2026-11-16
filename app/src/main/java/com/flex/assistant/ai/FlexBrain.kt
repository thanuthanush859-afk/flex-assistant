package com.flex.assistant.ai

import com.flex.assistant.FlexApp
import com.flex.assistant.FlexConfig
import org.json.JSONArray
import org.json.JSONObject

/** The text brain. The voice brain (Gemini Live) shares the same tools, memory and persona. */
class FlexBrain(private val app: FlexApp, private val conversation: ConversationManager) {

    private fun systemPrompt(): String {
        val facts = app.memory.facts(30).joinToString("\n") { "- " + it.text }
        return Persona.text(facts)
    }

    suspend fun process(userText: String): String {
        val primary = app.models.forText(userText)
        return try {
            run(userText, primary)
        } catch (e: FlexException) {
            if (primary != app.models.chat() && (e.message ?: "").contains("not available")) {
                "(Pro model unavailable, answered with ${app.models.chat()}.)\n" + run(userText, app.models.chat())
            } else throw e
        }
    }

    private suspend fun run(userText: String, model: String): String {
        val contents = conversation.toContents()
        contents.put(turn("user", userText))
        for (step in 0 until FlexConfig.MAX_TOOL_STEPS) {
            val resp = app.gemini.generate(contents, systemPrompt(), app.tools.declarations(), model)
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
            contents.put(content)
            val responseParts = JSONArray()
            for (c in calls) {
                val name = c.getString("name")
                val out = app.tools.execute(name, c.optJSONObject("args") ?: JSONObject())
                responseParts.put(JSONObject().put("functionResponse", JSONObject().put("name", name).put("response", out)))
            }
            contents.put(JSONObject().put("role", "user").put("parts", responseParts))
        }
        throw FlexException("Too many tool steps without a final answer.")
    }

    private fun turn(role: String, text: String) = JSONObject().put("role", role)
        .put("parts", JSONArray().put(JSONObject().put("text", text)))

    fun clearContext() = conversation.clearContext()
}

object Persona {
    fun text(facts: String) = """
        You are FLEX, Thanush's personal AI assistant running on his Android phone. You were made by Thanush Gowda N.
        Speak and write in warm, clear Indian English (you may reply in Hindi or Kannada if the user does). Keep answers short.
        You can act on the phone with tools: open apps, call contacts, send WhatsApp messages and emails, play YouTube searches,
        search the web, read or control the screen, set reminders, open camera vision, generate images, and remember facts.
        Rules:
        - Call a tool for any action. Only say an action worked if the tool result has success=true; otherwise tell the real problem.
        - Before send_whatsapp or send_email, say the recipient and the exact message and wait for the user to say yes.
        - When the user shares something worth keeping (birthdays, names, projects, codes, plans) call remember.
        - When asked about something told earlier, use the known facts below or call recall.
        - For current information (news, prices, new phone specs) call web_search and mention the sources.
        - When the camera is on you receive live camera frames. Describe what you actually see and never guess what you cannot see.
        - If you have no tool for something, say so plainly.
        Known facts about the user:
        ${if (facts.isBlank()) "(none yet)" else facts}
    """.trimIndent()
}

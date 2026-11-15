package com.flex.assistant.ai

import com.flex.assistant.memory.MemoryStore
import org.json.JSONArray
import org.json.JSONObject

/** Short-term context for the model; every line is also saved permanently in the brain database. */
class ConversationManager(private val memory: MemoryStore, private val maxTurns: Int = 24) {
    private data class Turn(val role: String, val text: String)
    private val turns = ArrayList<Turn>()

    init {
        memory.recentMessages(maxTurns).forEach { turns.add(Turn(it.role, it.text)) }
        while (turns.isNotEmpty() && turns.first().role != "user") turns.removeAt(0)
    }

    @Synchronized fun add(role: String, text: String) {
        turns.add(Turn(role, text))
        memory.addMessage(role, text)
        while (turns.size > maxTurns) turns.removeAt(0)
    }

    @Synchronized fun toContents(): JSONArray {
        val arr = JSONArray()
        for (t in turns) {
            arr.put(JSONObject().put("role", t.role)
                .put("parts", JSONArray().put(JSONObject().put("text", t.text))))
        }
        return arr
    }

    @Synchronized fun clearContext() = turns.clear()
}

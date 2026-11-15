package com.flex.assistant.ai

import org.json.JSONArray
import org.json.JSONObject

/** Short-term context only. Long-term memory comes in a later part. */
class ConversationManager(private val maxTurns: Int = 20) {
    private data class Turn(val role: String, val text: String)
    private val turns = ArrayList<Turn>()

    @Synchronized fun add(role: String, text: String) {
        turns.add(Turn(role, text))
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

    @Synchronized fun clear() = turns.clear()
}

package com.flex.assistant.ai

import com.flex.assistant.device.AppLauncher
import com.flex.assistant.device.LaunchResult
import com.flex.assistant.memory.MemoryStore
import org.json.JSONArray
import org.json.JSONObject

/** Maps Gemini function calls to real Android actions. Only real, working tools are declared. */
class ToolRouter(private val launcher: AppLauncher, private val memory: MemoryStore) {

    private fun strParam(desc: String) = JSONObject().put("type", "STRING").put("description", desc)
    private fun fn(name: String, desc: String, param: String?, pdesc: String): JSONObject {
        val props = JSONObject()
        val req = JSONArray()
        if (param != null) { props.put(param, strParam(pdesc)); req.put(param) }
        val params = JSONObject().put("type", "OBJECT").put("properties", props)
        if (param != null) params.put("required", req)
        return JSONObject().put("name", name).put("description", desc).put("parameters", params)
    }

    fun declarations(): JSONArray = JSONArray().put(
        JSONObject().put("functionDeclarations", JSONArray()
            .put(fn("open_app", "Open an installed Android app by name, e.g. YouTube, WhatsApp, Instagram, Settings.", "app_name", "Name of the app"))
            .put(fn("list_installed_apps", "List the launchable apps installed on the phone.", null, ""))
            .put(fn("remember", "Save an important fact the user tells you (birthdays, people, projects, codes, plans) into permanent memory.", "fact", "The fact to store, written as a clear sentence"))
            .put(fn("recall", "Search permanent memory and past conversations for information the user told you before.", "query", "Keywords to look for, e.g. 'Amma birthday'"))
        )
    )

    fun execute(name: String, args: JSONObject): JSONObject = when (name) {
        "open_app" -> when (val r = launcher.open(args.optString("app_name"))) {
            is LaunchResult.Launched -> result(true, "${r.label} was launched.")
            is LaunchResult.NotInstalled -> result(false, "No installed app matches '${r.query}'.")
            is LaunchResult.Failed -> result(false, "Could not open ${r.label}: ${r.reason}")
        }
        "list_installed_apps" -> {
            val names = JSONArray()
            launcher.installedApps().take(150).forEach { names.put(it.label) }
            JSONObject().put("success", true).put("apps", names)
        }
        "remember" -> {
            val fact = args.optString("fact").trim()
            if (fact.isEmpty()) result(false, "Nothing to remember.")
            else { memory.addFact(fact); result(true, "Saved to memory.") }
        }
        "recall" -> {
            val hits = memory.search(args.optString("query"))
            if (hits.isEmpty()) result(false, "Nothing found in memory for that.")
            else JSONObject().put("success", true).put("matches", JSONArray(hits))
        }
        else -> result(false, "Tool '$name' is not available yet.")
    }

    private fun result(ok: Boolean, msg: String) =
        JSONObject().put("success", ok).put("message", msg)
}

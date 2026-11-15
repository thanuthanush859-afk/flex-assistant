package com.flex.assistant.ai

import com.flex.assistant.device.AppLauncher
import com.flex.assistant.device.LaunchResult
import org.json.JSONArray
import org.json.JSONObject

/** Maps Gemini function calls to real Android actions. Only real, working tools are declared. */
class ToolRouter(private val launcher: AppLauncher) {

    fun declarations(): JSONArray = JSONArray().put(
        JSONObject().put("functionDeclarations", JSONArray()
            .put(JSONObject()
                .put("name", "open_app")
                .put("description", "Open an installed Android app by its name, e.g. YouTube, WhatsApp, Instagram, Settings.")
                .put("parameters", JSONObject()
                    .put("type", "OBJECT")
                    .put("properties", JSONObject().put("app_name",
                        JSONObject().put("type", "STRING").put("description", "Name of the app")))
                    .put("required", JSONArray().put("app_name"))))
            .put(JSONObject()
                .put("name", "list_installed_apps")
                .put("description", "List the launchable apps installed on the phone.")
                .put("parameters", JSONObject().put("type", "OBJECT").put("properties", JSONObject())))
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
        else -> result(false, "Tool '$name' is not available yet.")
    }

    private fun result(ok: Boolean, msg: String) =
        JSONObject().put("success", ok).put("message", msg)
}

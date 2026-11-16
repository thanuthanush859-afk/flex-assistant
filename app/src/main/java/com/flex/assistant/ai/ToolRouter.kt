package com.flex.assistant.ai

import android.accessibilityservice.AccessibilityService
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import com.flex.assistant.FlexApp
import com.flex.assistant.FlexRuntime
import com.flex.assistant.MainActivity
import com.flex.assistant.device.FlexAccessibilityService
import com.flex.assistant.device.LaunchResult
import com.flex.assistant.school.SchoolModeManager
import org.json.JSONArray
import org.json.JSONObject

/** Maps model function calls (text and live voice) to real Android actions. */
class ToolRouter(private val app: FlexApp) {

    private fun fn(name: String, desc: String, vararg params: Triple<String, String, String>): JSONObject {
        val o = JSONObject().put("name", name).put("description", desc)
        if (params.isNotEmpty()) {
            val props = JSONObject()
            val req = JSONArray()
            for ((n, t, d) in params) { props.put(n, JSONObject().put("type", t).put("description", d)); req.put(n) }
            o.put("parameters", JSONObject().put("type", "OBJECT").put("properties", props).put("required", req))
        }
        return o
    }

    private fun s(n: String, d: String) = Triple(n, "STRING", d)

    fun declarations(): JSONArray = JSONArray().put(JSONObject().put("functionDeclarations", JSONArray()
        .put(fn("open_app", "Open an installed Android app by name.", s("app_name", "App name, e.g. YouTube")))
        .put(fn("list_installed_apps", "List launchable apps installed on the phone."))
        .put(fn("remember", "Save an important fact the user tells you into permanent memory.", s("fact", "The fact, as a clear sentence")))
        .put(fn("recall", "Search permanent memory and past conversations.", s("query", "Keywords to look for")))
        .put(fn("call_contact", "Call a saved contact (or a phone number).", s("name", "Contact name or number")))
        .put(fn("send_whatsapp", "Send a WhatsApp message to a contact. Confirm with the user first.", s("contact", "Contact name or number"), s("message", "Message text")))
        .put(fn("youtube_search_and_play", "Open YouTube, search for the query and play the first video.", s("query", "What to search for")))
        .put(fn("send_email", "Send an email with Gmail. Confirm with the user first.", s("to", "Contact name or email address"), s("subject", "Subject"), s("body", "Body text")))
        .put(fn("web_search", "Search the internet for current information and return an answer with sources.", s("query", "Search query")))
        .put(fn("go_back", "Press the Back button."))
        .put(fn("go_home", "Go to the home screen."))
        .put(fn("scroll", "Scroll the current screen.", s("direction", "up or down")))
        .put(fn("read_screen", "Read the visible text on the current screen."))
        .put(fn("click_on_screen", "Tap an item on the current screen by its visible text.", s("text", "Visible text or label")))
        .put(fn("set_reminder", "Set a reminder notification.", s("text", "Reminder text"), Triple("minutes", "INTEGER", "Minutes from now")))
        .put(fn("open_vision", "Open Vision mode so the camera can see what is in front of the user."))
        .put(fn("set_school_mode", "Turn School Mode on or off. When on, FLEX replies to messages and calls saying the user is at school.", Triple("enabled", "BOOLEAN", "true to enable")))
        .put(fn("generate_image", "Generate an image from a text description and save it to the gallery.", s("prompt", "Image description")))
    ))

    suspend fun execute(name: String, args: JSONObject): JSONObject = try {
        run(name, args)
    } catch (e: FlexException) {
        fail(e.message ?: "Failed")
    } catch (e: Exception) {
        fail("Tool $name failed: ${e.message}")
    }

    private fun ok(m: String) = app.actions.ok(m)
    private fun fail(m: String) = app.actions.fail(m)
    private fun needAcc() = fail("Accessibility is off. Open FLEX Settings > Permissions and enable the FLEX accessibility service.")

    private suspend fun run(name: String, a: JSONObject): JSONObject {
        val acc = FlexAccessibilityService.instance
        return when (name) {
            "open_app" -> when (val r = app.launcher.open(a.optString("app_name"))) {
                is LaunchResult.Launched -> ok("${r.label} was launched.")
                is LaunchResult.NotInstalled -> fail("No installed app matches '${r.query}'.")
                is LaunchResult.Failed -> fail("Could not open ${r.label}: ${r.reason}. If FLEX is in the background, enable 'Display over other apps' in Permissions.")
            }
            "list_installed_apps" -> {
                val names = JSONArray()
                app.launcher.installedApps().take(150).forEach { names.put(it.label) }
                JSONObject().put("success", true).put("apps", names)
            }
            "remember" -> {
                val f = a.optString("fact").trim()
                if (f.isEmpty()) fail("Nothing to remember.") else { app.memory.addFact(f); ok("Saved to memory.") }
            }
            "recall" -> {
                val hits = app.memory.search(a.optString("query"))
                if (hits.isEmpty()) fail("Nothing found in memory for that.")
                else JSONObject().put("success", true).put("matches", JSONArray(hits))
            }
            "call_contact" -> app.actions.call(a.optString("name"))
            "send_whatsapp" -> app.actions.whatsapp(a.optString("contact"), a.optString("message"))
            "youtube_search_and_play" -> app.actions.youtube(a.optString("query"))
            "send_email" -> app.actions.email(a.optString("to"), a.optString("subject"), a.optString("body"))
            "web_search" -> app.gemini.search(a.optString("query"))
            "go_back" -> if (acc == null) needAcc() else if (acc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) ok("Went back.") else fail("Back action failed.")
            "go_home" -> if (acc == null) needAcc() else if (acc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)) ok("Went to the home screen.") else fail("Home action failed.")
            "scroll" -> if (acc == null) needAcc() else {
                val down = !a.optString("direction").lowercase().startsWith("up")
                if (acc.scroll(down)) ok("Scrolled ${if (down) "down" else "up"}.") else fail("Nothing scrollable was found on screen.")
            }
            "read_screen" -> if (acc == null) needAcc() else {
                val t = acc.readScreen()
                if (t.isBlank()) fail("No readable text on screen.") else JSONObject().put("success", true).put("screen_text", t)
            }
            "click_on_screen" -> if (acc == null) needAcc() else {
                if (acc.clickNow(listOf(a.optString("text")), false)) ok("Tapped '${a.optString("text")}'.") else fail("Could not find '${a.optString("text")}' on screen.")
            }
            "set_reminder" -> app.actions.reminder(a.optString("text"), a.optInt("minutes", 1))
            "open_vision" -> {
                FlexRuntime.requestedPage.value = "vision"
                app.startActivity(Intent(app, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                ok("Vision mode is opening. Camera frames will now be streamed to you.")
            }
            "set_school_mode" -> {
                val on = a.optBoolean("enabled")
                SchoolModeManager.setEnabled(app, on)
                ok("School Mode is now ${if (on) "ON" else "OFF"}.")
            }
            "generate_image" -> {
                val img = app.gemini.generateImage(a.optString("prompt"), app.models.imageFast())
                    ?: return fail("The image model returned no image.")
                saveImage(img.first, img.second)
            }
            else -> fail("Tool '$name' is not available.")
        }
    }

    private fun saveImage(bytes: ByteArray, mime: String): JSONObject {
        val ext = if (mime.contains("jpeg")) "jpg" else "png"
        val v = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "FLEX_${System.currentTimeMillis()}.$ext")
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FLEX")
        }
        val uri = app.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)
            ?: return fail("Could not save the image to the gallery.")
        app.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        return ok("Image generated and saved to Pictures/FLEX in the gallery.")
    }
}

package com.flex.assistant.school

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.flex.assistant.FlexApp

/** In School Mode, answers WhatsApp messages with the user's school reply using the notification's own Reply action. */
class FlexNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = application as FlexApp
        if (!app.settings.schoolMode) return
        if (sbn.packageName != "com.whatsapp" && sbn.packageName != "com.whatsapp.w4b") return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        if (title.equals("WhatsApp", true) || title.contains("new messages", true)) return
        if (SchoolModeManager.isTrusted(app, title)) return
        if (!SchoolModeManager.shouldReply("wa:" + title)) return

        val actions = ArrayList<Notification.Action>()
        n.actions?.let { actions.addAll(it) }
        try { actions.addAll(Notification.WearableExtender(n).actions) } catch (e: Exception) { }
        val reply = actions.firstOrNull { a -> a.remoteInputs?.isNotEmpty() == true }
        if (reply == null) {
            app.memory.addLog("WhatsApp from $title: no reply option on this notification")
            return
        }
        try {
            val inputs = reply.remoteInputs
            val intent = Intent()
            val bundle = Bundle()
            for (ri in inputs) bundle.putCharSequence(ri.resultKey, app.settings.schoolReply)
            RemoteInput.addResultsToIntent(inputs, intent, bundle)
            reply.actionIntent.send(this, 0, intent)
            app.memory.addLog("Auto-replied on WhatsApp to $title")
        } catch (e: Exception) {
            app.memory.addLog("WhatsApp reply to $title failed: ${e.message}")
        }
    }
}

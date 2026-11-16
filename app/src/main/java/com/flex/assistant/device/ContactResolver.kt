package com.flex.assistant.device

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

class ContactResolver(private val ctx: Context) {
    data class Hit(val name: String, val value: String)

    fun phones(q: String): List<Hit> = query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER, q)

    fun emails(q: String): List<Hit> = query(
        ContactsContract.CommonDataKinds.Email.CONTENT_URI,
        ContactsContract.CommonDataKinds.Email.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Email.ADDRESS, q)

    private fun query(uri: Uri, nameCol: String, valCol: String, q: String): List<Hit> {
        val out = ArrayList<Hit>()
        try {
            ctx.contentResolver.query(uri, arrayOf(nameCol, valCol), "$nameCol LIKE ?", arrayOf("%$q%"), null)?.use { c ->
                while (c.moveToNext()) out.add(Hit(c.getString(0) ?: "", c.getString(1) ?: ""))
            }
        } catch (e: SecurityException) { return emptyList() }
        return out.distinctBy { it.value.filter { ch -> ch.isLetterOrDigit() || ch == '@' } }
    }

    fun nameForNumber(number: String): String? {
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: Exception) { null }
    }
}

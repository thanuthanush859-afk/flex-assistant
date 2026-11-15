package com.flex.assistant.memory

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class StoredMsg(val role: String, val text: String, val ts: Long)

/** FLEX's separate "brain": every conversation line and every remembered fact is kept on the phone. */
class MemoryStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "flex_brain.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT, text TEXT, ts INTEGER)")
        db.execSQL("CREATE TABLE facts(id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT, ts INTEGER)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun addMessage(role: String, text: String) {
        val v = ContentValues().apply { put("role", role); put("text", text); put("ts", System.currentTimeMillis()) }
        writableDatabase.insert("messages", null, v)
    }

    fun addFact(text: String) {
        val v = ContentValues().apply { put("text", text); put("ts", System.currentTimeMillis()) }
        writableDatabase.insert("facts", null, v)
    }

    fun recentMessages(limit: Int): List<StoredMsg> {
        val out = ArrayList<StoredMsg>()
        readableDatabase.rawQuery(
            "SELECT role,text,ts FROM messages ORDER BY id DESC LIMIT ?", arrayOf(limit.toString())
        ).use { c -> while (c.moveToNext()) out.add(StoredMsg(c.getString(0), c.getString(1), c.getLong(2))) }
        return out.reversed()
    }

    fun allMessages(): List<StoredMsg> = recentMessages(500)

    /** Keyword search over remembered facts and the whole conversation history. */
    fun search(query: String): List<String> {
        val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 2 }
        if (words.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        for (w in words) {
            val arg = arrayOf("%$w%")
            readableDatabase.rawQuery("SELECT text FROM facts WHERE lower(text) LIKE ? ORDER BY id DESC LIMIT 8", arg)
                .use { c -> while (c.moveToNext()) out.add("Fact: " + c.getString(0)) }
            readableDatabase.rawQuery("SELECT role,text FROM messages WHERE lower(text) LIKE ? ORDER BY id DESC LIMIT 8", arg)
                .use { c -> while (c.moveToNext()) out.add(c.getString(0) + " said: " + c.getString(1)) }
        }
        return out.take(14)
    }

    fun clearMessages() { writableDatabase.delete("messages", null, null) }
}

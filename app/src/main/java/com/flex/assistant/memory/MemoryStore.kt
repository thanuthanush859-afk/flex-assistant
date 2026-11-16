package com.flex.assistant.memory

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class StoredMsg(val role: String, val text: String, val ts: Long)
data class StoredFact(val id: Long, val text: String)

/** FLEX's separate brain: conversations, remembered facts and the activity log, all on the phone. */
class MemoryStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "flex_brain.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT, text TEXT, ts INTEGER)")
        db.execSQL("CREATE TABLE facts(id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT, ts INTEGER)")
        db.execSQL("CREATE TABLE logs(id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT, ts INTEGER)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("CREATE TABLE IF NOT EXISTS logs(id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT, ts INTEGER)")
    }

    private fun insert(table: String, vararg kv: Pair<String, Any>) {
        val v = ContentValues()
        for ((k, x) in kv) when (x) {
            is Long -> v.put(k, x)
            else -> v.put(k, x.toString())
        }
        writableDatabase.insert(table, null, v)
    }

    fun addMessage(role: String, text: String) {
        if (text.isBlank()) return
        insert("messages", "role" to role, "text" to text, "ts" to System.currentTimeMillis())
    }

    fun addFact(text: String) = insert("facts", "text" to text, "ts" to System.currentTimeMillis())
    fun addLog(text: String) = insert("logs", "text" to text, "ts" to System.currentTimeMillis())

    fun recentMessages(limit: Int): List<StoredMsg> {
        val out = ArrayList<StoredMsg>()
        readableDatabase.rawQuery("SELECT role,text,ts FROM messages ORDER BY id DESC LIMIT ?", arrayOf(limit.toString()))
            .use { c -> while (c.moveToNext()) out.add(StoredMsg(c.getString(0), c.getString(1), c.getLong(2))) }
        return out.reversed()
    }

    fun allMessages(): List<StoredMsg> = recentMessages(500)

    fun facts(limit: Int = 100): List<StoredFact> {
        val out = ArrayList<StoredFact>()
        readableDatabase.rawQuery("SELECT id,text FROM facts ORDER BY id DESC LIMIT ?", arrayOf(limit.toString()))
            .use { c -> while (c.moveToNext()) out.add(StoredFact(c.getLong(0), c.getString(1))) }
        return out
    }

    fun deleteFact(id: Long) { writableDatabase.delete("facts", "id=?", arrayOf(id.toString())) }

    fun recentLogs(limit: Int = 12): List<String> {
        val out = ArrayList<String>()
        readableDatabase.rawQuery("SELECT text,ts FROM logs ORDER BY id DESC LIMIT ?", arrayOf(limit.toString()))
            .use { c ->
                val f = java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.getDefault())
                while (c.moveToNext()) out.add(f.format(java.util.Date(c.getLong(1))) + "  " + c.getString(0))
            }
        return out
    }

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

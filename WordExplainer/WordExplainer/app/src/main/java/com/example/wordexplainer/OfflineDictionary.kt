package com.example.wordexplainer

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileOutputStream

data class WordResult(
    val definition: String,
    val isLearned: Boolean = false,
    val isOffline: Boolean = true
)

/**
 * Manages the offline English dictionary (176,064 base words + smart learning cache).
 * When words not found in the base dictionary are retrieved via AI, they are
 * automatically saved to 'LearnedWords' so they become permanently available offline.
 */
object OfflineDictionary {

    private const val DB_NAME = "dictionary.db"
    @Volatile private var db: SQLiteDatabase? = null

    /**
     * Initialise the database. Copies the asset DB to internal storage once,
     * then opens it in READWRITE mode so the app can learn new words.
     */
    @Synchronized
    fun init(context: Context) {
        if (db != null) return
        try {
            val dbFile = File(context.filesDir, DB_NAME)
            if (!dbFile.exists()) {
                context.assets.open(DB_NAME).use { input ->
                    FileOutputStream(dbFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            val database = SQLiteDatabase.openDatabase(
                dbFile.absolutePath, null,
                SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )
            // Create user learned words table
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS LearnedWords (
                    word TEXT PRIMARY KEY,
                    type TEXT,
                    description TEXT,
                    created_at INTEGER
                )
                """.trimIndent()
            )
            db = database
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Look up a word offline. Checks learned words first, then base dictionary with stemming.
     */
    fun lookup(rawWord: String): WordResult? {
        val d = db ?: return null
        val word = rawWord.trim()
        if (word.isEmpty()) return null

        // 1. Check user learned words table first
        val learned = queryLearned(d, word) ?: queryLearned(d, word.lowercase())
        if (learned != null) {
            return WordResult(learned, isLearned = true, isOffline = true)
        }

        // 2. Check base dictionary with exact, lowercase, and capitalisation
        val candidates = listOf(word, word.lowercase(), word.replaceFirstChar { it.uppercase() })
        for (w in candidates) {
            val res = queryBase(d, w)
            if (res != null) return WordResult(res, isLearned = false, isOffline = true)
        }

        // 3. Smart stemming for inflected forms (-ing, -ed, -s, -ly, -er, -est, etc.)
        val stem = stem(word.lowercase())
        if (stem != word.lowercase()) {
            val res = queryBase(d, stem) ?: queryBase(d, stem.replaceFirstChar { it.uppercase() })
            if (res != null) return WordResult(res, isLearned = false, isOffline = true)
        }

        return null
    }

    /**
     * Automatically save a newly explained word to the offline database.
     */
    fun saveWord(word: String, type: String = "AI", description: String) {
        val d = db ?: return
        if (word.isBlank() || description.isBlank()) return
        try {
            val cv = ContentValues().apply {
                put("word", word.trim().lowercase())
                put("type", type.trim())
                put("description", description.trim())
                put("created_at", System.currentTimeMillis())
            }
            d.insertWithOnConflict("LearnedWords", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getLearnedCount(): Int {
        val d = db ?: return 0
        return try {
            val c = d.rawQuery("SELECT count(*) FROM LearnedWords", null)
            c.use { if (it.moveToNext()) it.getInt(0) else 0 }
        } catch (_: Exception) {
            0
        }
    }

    fun clearLearnedWords(): Int {
        val d = db ?: return 0
        return try {
            d.delete("LearnedWords", null, null)
        } catch (_: Exception) {
            0
        }
    }


    private fun queryLearned(d: SQLiteDatabase, word: String): String? {
        return try {
            val cursor = d.rawQuery(
                "SELECT description FROM LearnedWords WHERE lower(word) = ? LIMIT 1",
                arrayOf(word.lowercase())
            )
            cursor.use {
                if (it.moveToNext()) it.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun queryBase(d: SQLiteDatabase, word: String): String? {
        return try {
            val cursor = d.rawQuery(
                "SELECT Word, Type, Description FROM Word WHERE Word = ? LIMIT 3",
                arrayOf(word)
            )
            val results = mutableListOf<String>()
            cursor.use {
                while (it.moveToNext()) {
                    val type = it.getString(1)?.trim() ?: ""
                    val desc = it.getString(2)?.trim() ?: continue
                    if (desc.isBlank()) continue
                    val typeLabel = formatType(type)
                    results.add(if (typeLabel.isNotEmpty()) "$typeLabel\n$desc" else desc)
                }
            }
            if (results.isEmpty()) null else results.joinToString("\n\n")
        } catch (_: Exception) {
            null
        }
    }

    private fun formatType(raw: String): String {
        return when {
            raw.contains("n.", ignoreCase = true) && !raw.contains("v.", ignoreCase = true) -> "**noun**"
            raw.contains("v.", ignoreCase = true) -> "**verb**"
            raw.contains("a.", ignoreCase = true) || raw.contains("adj", ignoreCase = true) -> "**adjective**"
            raw.contains("adv", ignoreCase = true) -> "**adverb**"
            raw.contains("prep", ignoreCase = true) -> "**preposition**"
            raw.contains("conj", ignoreCase = true) -> "**conjunction**"
            raw.contains("interj", ignoreCase = true) -> "**interjection**"
            raw.contains("pron", ignoreCase = true) -> "**pronoun**"
            raw.isNotBlank() && raw != "()" -> raw.trim('(', ')', ' ')
            else -> ""
        }
    }

    private fun stem(word: String): String {
        return when {
            word.length > 4 && word.endsWith("ing") -> word.dropLast(3)
            word.length > 4 && word.endsWith("tion") -> word.dropLast(4) + "t"
            word.length > 3 && word.endsWith("ed") -> word.dropLast(2)
            word.length > 3 && word.endsWith("ly") -> word.dropLast(2)
            word.length > 3 && word.endsWith("er") -> word.dropLast(2)
            word.length > 4 && word.endsWith("est") -> word.dropLast(3)
            word.length > 2 && word.endsWith("s") && !word.endsWith("ss") -> word.dropLast(1)
            else -> word
        }
    }

    @Synchronized
    fun close() {
        try {
            db?.close()
        } catch (_: Exception) {}
        db = null
    }
}

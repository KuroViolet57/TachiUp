package com.tachiup.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * App-wide diagnostic log. Keeps entries in memory for the UI, mirrors them to
 * logcat, and appends them to a file so a session's history survives restarts
 * and can be exported even after a crash.
 */
object Logger {

    enum class Level { DEBUG, INFO, WARN, ERROR }

    data class Entry(val time: Long, val level: Level, val message: String) {
        fun format(): String {
            val tag = when (level) {
                Level.DEBUG -> "D"
                Level.INFO -> "I"
                Level.WARN -> "W"
                Level.ERROR -> "E"
            }
            return "${TIME_FMT.format(Date(time))} $tag/ $message"
        }
    }

    private const val MAX_ENTRIES = 1500
    private const val TAG = "TachiUp"

    private val TIME_FMT = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val lock = Any()
    private var file: File? = null

    /** Must be called once before logging (safe to call repeatedly). */
    fun init(context: Context) {
        synchronized(lock) {
            if (file != null) return
            val dir = File(context.cacheDir, "logs").apply { mkdirs() }
            file = File(dir, "tachiup.log")
        }
        i("=== TachiUp session started (v10) ===")
    }

    fun d(message: String) = add(Level.DEBUG, message, null)
    fun i(message: String) = add(Level.INFO, message, null)
    fun w(message: String, t: Throwable? = null) = add(Level.WARN, message, t)
    fun e(message: String, t: Throwable? = null) = add(Level.ERROR, message, t)

    private fun add(level: Level, message: String, t: Throwable?) {
        val full = if (t != null) "$message\n${Log.getStackTraceString(t)}" else message
        val entry = Entry(System.currentTimeMillis(), level, full)

        when (level) {
            Level.DEBUG -> Log.d(TAG, full)
            Level.INFO -> Log.i(TAG, full)
            Level.WARN -> Log.w(TAG, full)
            Level.ERROR -> Log.e(TAG, full)
        }

        _entries.value = (_entries.value + entry).takeLast(MAX_ENTRIES)

        val target = file ?: return
        synchronized(lock) {
            runCatching { target.appendText(entry.format() + "\n") }
        }
    }

    /** Full session log as plain text. */
    fun exportText(): String = buildString {
        append("TachiUp diagnostic log\n")
        append("Generated: ${TIME_FMT.format(Date())}\n\n")
        _entries.value.forEach { append(it.format()).append('\n') }
    }

    /**
     * Ensures the on-disk log reflects the current entries and returns it,
     * suitable for sharing through a FileProvider.
     */
    fun exportFile(context: Context): File {
        init(context)
        val dir = File(context.cacheDir, "logs").apply { mkdirs() }
        val out = File(dir, "tachiup-log.txt")
        synchronized(lock) {
            runCatching { out.writeText(exportText()) }
        }
        return out
    }

    fun clear() {
        _entries.value = emptyList()
        synchronized(lock) {
            runCatching { file?.writeText("") }
        }
        i("Log cleared")
    }
}

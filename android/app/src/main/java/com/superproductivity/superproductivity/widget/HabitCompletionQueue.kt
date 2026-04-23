package com.superproductivity.superproductivity.widget

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Queue of habit-completion events produced by external apps (e.g. the
 * sp-morning-gate companion app) and drained by the Angular app on resume.
 *
 * Pattern mirrors [WidgetTaskQueue]: entries are persisted in
 * SharedPreferences so they survive process death, and are pulled via
 * [com.superproductivity.superproductivity.webview.JavaScriptInterface.getHabitCompletionQueue].
 *
 * Each entry:
 *  - title:   exact task title to mark done for today (e.g. "수면 성공", "헬스장")
 *  - dateIso: YYYY-MM-DD the completion refers to
 *  - sourcePkg: broadcast source package (audit/logging)
 *  - receivedAt: epoch ms the receiver saw the broadcast
 */
object HabitCompletionQueue {
    private const val PREFS_NAME = "SuperProductivityHabitBridge"
    private const val KEY_QUEUE = "HABIT_COMPLETION_QUEUE"

    private fun getPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun addCompletion(
        context: Context,
        title: String,
        dateIso: String,
        sourcePkg: String?,
    ) {
        val entry = JSONObject().apply {
            put("title", title.trim())
            put("dateIso", dateIso)
            put("sourcePkg", sourcePkg ?: "")
            put("receivedAt", System.currentTimeMillis())
        }

        val prefs = getPrefs(context)
        val existing = prefs.getString(KEY_QUEUE, null)
        val queue = if (existing != null) {
            runCatching { JSONObject(existing) }.getOrDefault(
                JSONObject().put("completions", JSONArray()),
            )
        } else {
            JSONObject().put("completions", JSONArray())
        }

        val completions = queue.optJSONArray("completions") ?: JSONArray()
        completions.put(entry)
        queue.put("completions", completions)
        prefs.edit().putString(KEY_QUEUE, queue.toString()).apply()
    }

    /**
     * Atomically read and clear the queue.
     * @return JSON string of the queue, or null if empty.
     */
    @Synchronized
    fun getAndClearQueue(context: Context): String? {
        val prefs = getPrefs(context)
        val queueJson = prefs.getString(KEY_QUEUE, null) ?: return null

        prefs.edit().remove(KEY_QUEUE).commit()

        return runCatching {
            val queue = JSONObject(queueJson)
            val completions = queue.optJSONArray("completions")
            if (completions != null && completions.length() > 0) queueJson else null
        }.getOrNull()
    }
}

package com.superproductivity.superproductivity.widget

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import com.superproductivity.superproductivity.App
import org.json.JSONArray

/**
 * Read-only ContentProvider that exposes today's tasks to the companion
 * sp-today-widget app for lockscreen rendering.
 *
 * Data flow:
 *  Angular NgRx (selectTodayTasksForWidget)
 *    → AndroidWidgetTodayEffects
 *      → androidInterface.saveToDbWrapped("today_tasks", JSON)
 *        → JavaScriptInterface.saveToDb
 *          → KeyValStore SQLite (key="today_tasks")
 *          → notifyChange(CONTENT_URI)   // wakes observers
 *
 *  sp-today-widget.apk
 *    → contentResolver.query(CONTENT_URI)
 *      → TodayTasksProvider.query()
 *        → KeyValStore.get("today_tasks")
 *        → MatrixCursor with one row per task
 *
 * Access is gated by a signature-level permission, so only an APK signed
 * with the same keystore may read the cursor.
 */
class TodayTasksProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val cursor = MatrixCursor(COLUMNS)
        val ctx = context ?: return cursor

        // Defensive cast: in instrumentation / test harnesses the
        // applicationContext may not be our App subclass. Don't crash —
        // just return an empty cursor so the widget shows its empty state.
        val app = ctx.applicationContext as? App
        if (app == null) {
            Log.w(TAG, "applicationContext is not App; returning empty cursor")
            cursor.setNotificationUri(ctx.contentResolver, uri)
            return cursor
        }
        val store = app.keyValStore
        val json = store.get(KEY_TODAY_TASKS, "[]")

        try {
            val tasks = JSONArray(json)
            for (i in 0 until tasks.length()) {
                val task = tasks.getJSONObject(i)
                cursor.addRow(
                    arrayOf<Any>(
                        task.optString("id", ""),
                        task.optString("title", ""),
                        if (task.optBoolean("isDone", false)) 1 else 0,
                        task.optString("projectName", ""),
                        task.optString("projectColor", ""),
                        i,
                    ),
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse today_tasks JSON", e)
        }

        cursor.setNotificationUri(ctx.contentResolver, uri)
        return cursor
    }

    override fun getType(uri: Uri): String = CONTENT_TYPE

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        private const val TAG = "TodayTasksProvider"

        const val AUTHORITY = "com.superproductivity.superproductivity.today"
        const val KEY_TODAY_TASKS = "today_tasks"

        const val COL_ID = "id"
        const val COL_TITLE = "title"
        const val COL_IS_DONE = "is_done"
        const val COL_PROJECT_NAME = "project_name"
        const val COL_PROJECT_COLOR = "project_color"
        const val COL_ORDER_INDEX = "order_index"

        val COLUMNS: Array<String> = arrayOf(
            COL_ID,
            COL_TITLE,
            COL_IS_DONE,
            COL_PROJECT_NAME,
            COL_PROJECT_COLOR,
            COL_ORDER_INDEX,
        )

        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/tasks")

        private const val CONTENT_TYPE =
            "vnd.android.cursor.dir/vnd.superproductivity.today-task"
    }
}

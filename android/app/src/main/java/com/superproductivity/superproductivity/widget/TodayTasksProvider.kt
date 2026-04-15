package com.superproductivity.superproductivity.widget

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import com.superproductivity.superproductivity.App
import org.json.JSONArray

/**
 * Read-only ContentProvider that exposes today's tasks and the 7-day schedule
 * snapshot to the companion sp-today-widget app for lockscreen rendering.
 *
 * Data flow (tasks):
 *  Angular NgRx (selectTodayTasksForWidget)
 *    → AndroidWidgetTodayEffects
 *      → androidInterface.saveToDbWrapped("today_tasks", JSON)
 *        → JavaScriptInterface.saveToDb
 *          → KeyValStore SQLite (key="today_tasks")
 *          → notifyChange(TASKS_URI)
 *
 * Data flow (schedule):
 *  Angular NgRx (selectScheduleWidgetData)
 *    → AndroidScheduleWidgetEffects
 *      → androidInterface.saveToDbWrapped("schedule_widget_data", JSON)
 *        → JavaScriptInterface.saveToDb
 *          → KeyValStore SQLite (key="schedule_widget_data")
 *          → notifyChange(SCHEDULE_URI)
 *
 *  sp-today-widget.apk
 *    → contentResolver.query(<uri>)
 *      → TodayTasksProvider.query()
 *        → KeyValStore.get(<key>)
 *        → MatrixCursor
 *
 * For the `/tasks` path we expand the JSON into one row per task; for the
 * `/schedule` path we return a single-row cursor with the full JSON in a
 * `data` column, and the widget parses it client-side.
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
        val ctx = context
        if (ctx == null) {
            return MatrixCursor(TASKS_COLUMNS)
        }
        return when (URI_MATCHER.match(uri)) {
            MATCH_TASKS -> queryTasks(ctx, uri)
            MATCH_SCHEDULE -> querySchedule(ctx, uri)
            else -> MatrixCursor(TASKS_COLUMNS)
        }
    }

    private fun queryTasks(ctx: android.content.Context, uri: Uri): Cursor {
        val cursor = MatrixCursor(TASKS_COLUMNS)
        val app = ctx.applicationContext as? App
        if (app == null) {
            Log.w(TAG, "applicationContext is not App; returning empty tasks cursor")
            cursor.setNotificationUri(ctx.contentResolver, uri)
            return cursor
        }
        val json = app.keyValStore.get(KEY_TODAY_TASKS, "[]")
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

    private fun querySchedule(ctx: android.content.Context, uri: Uri): Cursor {
        val cursor = MatrixCursor(SCHEDULE_COLUMNS)
        val app = ctx.applicationContext as? App
        if (app == null) {
            Log.w(TAG, "applicationContext is not App; returning empty schedule cursor")
            cursor.setNotificationUri(ctx.contentResolver, uri)
            return cursor
        }
        val json = app.keyValStore.get(KEY_SCHEDULE, "")
        cursor.addRow(arrayOf<Any>(json))
        cursor.setNotificationUri(ctx.contentResolver, uri)
        return cursor
    }

    override fun getType(uri: Uri): String = when (URI_MATCHER.match(uri)) {
        MATCH_TASKS -> CONTENT_TYPE_TASKS
        MATCH_SCHEDULE -> CONTENT_TYPE_SCHEDULE
        else -> CONTENT_TYPE_TASKS
    }

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
        const val KEY_SCHEDULE = "schedule_widget_data"

        const val COL_ID = "id"
        const val COL_TITLE = "title"
        const val COL_IS_DONE = "is_done"
        const val COL_PROJECT_NAME = "project_name"
        const val COL_PROJECT_COLOR = "project_color"
        const val COL_ORDER_INDEX = "order_index"
        const val COL_DATA = "data"

        val TASKS_COLUMNS: Array<String> = arrayOf(
            COL_ID,
            COL_TITLE,
            COL_IS_DONE,
            COL_PROJECT_NAME,
            COL_PROJECT_COLOR,
            COL_ORDER_INDEX,
        )

        // Kept for backward-source-compat with existing callers (e.g. the
        // sp-today-widget apk). Equivalent to TASKS_COLUMNS.
        @Deprecated("Use TASKS_COLUMNS", ReplaceWith("TASKS_COLUMNS"))
        val COLUMNS: Array<String> = TASKS_COLUMNS

        val SCHEDULE_COLUMNS: Array<String> = arrayOf(COL_DATA)

        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/tasks")
        val SCHEDULE_URI: Uri = Uri.parse("content://$AUTHORITY/schedule")

        private const val PATH_TASKS = "tasks"
        private const val PATH_SCHEDULE = "schedule"
        private const val MATCH_TASKS = 1
        private const val MATCH_SCHEDULE = 2

        private val URI_MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, PATH_TASKS, MATCH_TASKS)
            addURI(AUTHORITY, PATH_SCHEDULE, MATCH_SCHEDULE)
        }

        private const val CONTENT_TYPE_TASKS =
            "vnd.android.cursor.dir/vnd.superproductivity.today-task"
        private const val CONTENT_TYPE_SCHEDULE =
            "vnd.android.cursor.item/vnd.superproductivity.schedule"
    }
}

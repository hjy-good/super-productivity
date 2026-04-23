package com.superproductivity.superproductivity.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.superproductivity.superproductivity.widget.HabitCompletionQueue

/**
 * Exported, signature-permission-gated receiver that accepts habit-completion
 * broadcasts from sibling apps signed with the same keystore (primarily
 * sp-morning-gate).
 *
 * Registered in AndroidManifest with
 * [android:permission="com.superproductivity.superproductivity.permission.SEND_HABIT_COMPLETE"]
 * and action [ACTION_HABIT_COMPLETE]. The broadcast queues a completion in
 * [HabitCompletionQueue]; the Angular layer drains the queue on app resume
 * and marks the matching today-task done.
 *
 * Extras:
 *   EXTRA_HABIT_TITLE (String, required)  exact task title to mark done
 *   EXTRA_DATE_ISO   (String, optional)   YYYY-MM-DD — defaults to today
 */
class HabitCompletionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_HABIT_COMPLETE) {
            return
        }

        val title = intent.getStringExtra(EXTRA_HABIT_TITLE)?.trim().orEmpty()
        if (title.isEmpty()) {
            Log.w(TAG, "Dropping broadcast: empty $EXTRA_HABIT_TITLE")
            return
        }

        val dateIso = intent.getStringExtra(EXTRA_DATE_ISO)?.trim()?.takeIf { it.isNotEmpty() }
            ?: isoToday()

        val sourcePkg = sourcePackage(context, intent)

        runCatching {
            HabitCompletionQueue.addCompletion(context, title, dateIso, sourcePkg)
            Log.i(
                TAG,
                "Queued habit completion title=\"$title\" date=$dateIso from=$sourcePkg",
            )
        }.onFailure { e ->
            Log.e(TAG, "Failed to queue habit completion", e)
        }
    }

    private fun sourcePackage(context: Context, intent: Intent): String? {
        // Signature permission enforces caller identity; we still log the declared package.
        return runCatching {
            @Suppress("DEPRECATION")
            intent.`package` ?: context.packageManager.getNameForUid(android.os.Binder.getCallingUid())
        }.getOrNull()
    }

    private fun isoToday(): String {
        val cal = java.util.Calendar.getInstance()
        val y = cal.get(java.util.Calendar.YEAR)
        val m = cal.get(java.util.Calendar.MONTH) + 1
        val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
        return "%04d-%02d-%02d".format(y, m, d)
    }

    companion object {
        private const val TAG = "HabitCompletionRcvr"

        const val ACTION_HABIT_COMPLETE =
            "com.superproductivity.superproductivity.ACTION_HABIT_COMPLETE"
        const val PERMISSION_SEND =
            "com.superproductivity.superproductivity.permission.SEND_HABIT_COMPLETE"

        const val EXTRA_HABIT_TITLE = "habit_title"
        const val EXTRA_DATE_ISO = "date_iso"
    }
}

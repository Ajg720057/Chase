package com.chase.lifeboard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import com.chase.lifeboard.alarm.Notifications
import com.chase.lifeboard.ui.LifeBoardNavHost
import com.chase.lifeboard.ui.theme.LifeBoardTheme

/** A screen another entry point (notification, widget) asked the app to open. */
data class OpenRequest(val kind: Kind, val id: Long, val isNew: Boolean) {
    enum class Kind { TASK, ENTRY }
}

class MainActivity : ComponentActivity() {
    private val openRequest = mutableStateOf<OpenRequest?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.canPost(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            LifeBoardTheme {
                LifeBoardNavHost(
                    openRequest = openRequest.value,
                    onOpenHandled = { openRequest.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val isNew = intent.getBooleanExtra(EXTRA_IS_NEW, false)
        val taskId = intent.getLongExtra(EXTRA_OPEN_TASK, -1L)
        val entryId = intent.getLongExtra(EXTRA_OPEN_ENTRY, -1L)
        when {
            taskId >= 0 -> {
                openRequest.value = OpenRequest(OpenRequest.Kind.TASK, taskId, isNew)
                Notifications.cancel(this, taskId)
            }
            entryId >= 0 -> openRequest.value = OpenRequest(OpenRequest.Kind.ENTRY, entryId, isNew)
        }
        intent.removeExtra(EXTRA_OPEN_TASK)
        intent.removeExtra(EXTRA_OPEN_ENTRY)
    }

    companion object {
        const val EXTRA_OPEN_TASK = "open_task"
        const val EXTRA_OPEN_ENTRY = "open_entry"
        const val EXTRA_IS_NEW = "is_new"

        fun openTaskIntent(context: Context, id: Long, isNew: Boolean = false): Intent =
            Intent(context, MainActivity::class.java)
                // Unique data so each task gets its own PendingIntent.
                .setData(Uri.parse("lifeboard://task/$id"))
                .putExtra(EXTRA_OPEN_TASK, id)
                .putExtra(EXTRA_IS_NEW, isNew)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        fun openEntryIntent(context: Context, id: Long, isNew: Boolean = false): Intent =
            Intent(context, MainActivity::class.java)
                .setData(Uri.parse("lifeboard://entry/$id"))
                .putExtra(EXTRA_OPEN_ENTRY, id)
                .putExtra(EXTRA_IS_NEW, isNew)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

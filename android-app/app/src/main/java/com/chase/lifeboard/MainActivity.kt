package com.chase.lifeboard

import android.Manifest
import android.content.Intent
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

class MainActivity : ComponentActivity() {
    /** Task to open because the user tapped an alarm notification. */
    private val openTaskRequest = mutableStateOf<Long?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.canPost(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            LifeBoardTheme {
                LifeBoardNavHost(
                    openTaskId = openTaskRequest.value,
                    onOpenTaskHandled = { openTaskRequest.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_OPEN_TASK, -1L) ?: -1L
        if (id >= 0) {
            openTaskRequest.value = id
            Notifications.cancel(this, id)
            intent?.removeExtra(EXTRA_OPEN_TASK)
        }
    }

    companion object {
        const val EXTRA_OPEN_TASK = "open_task"
    }
}

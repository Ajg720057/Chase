package com.chase.planboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.chase.planboard.ui.PlanBoardNavHost
import com.chase.planboard.ui.theme.PlanBoardTheme
import kotlinx.coroutines.launch

/** A screen another entry point (the home-screen widget) asked the app to open. */
data class OpenRequest(val kind: Kind, val planId: Long = 0) {
    enum class Kind { TODAY, PLAN, NEW_DAY_PLAN }
}

class MainActivity : ComponentActivity() {
    private val openRequest = mutableStateOf<OpenRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            PlanBoardTheme {
                PlanBoardNavHost(
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

    override fun onResume() {
        super.onResume()
        // Pick up to-dos checked off, renamed or deleted in LifeBoard while we were away.
        val app = application as PlanBoardApp
        app.appScope.launch { app.plans.syncWithLifeBoard() }
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val kind = intent.getStringExtra(EXTRA_OPEN)?.let { runCatching { OpenRequest.Kind.valueOf(it) }.getOrNull() } ?: return
        openRequest.value = OpenRequest(kind, intent.getLongExtra(EXTRA_PLAN_ID, 0))
        intent.removeExtra(EXTRA_OPEN)
    }

    companion object {
        private const val EXTRA_OPEN = "open"
        private const val EXTRA_PLAN_ID = "plan_id"

        fun openIntent(context: Context, kind: OpenRequest.Kind, planId: Long = 0): Intent =
            Intent(context, MainActivity::class.java)
                // Unique data so each widget row gets its own PendingIntent.
                .setData(Uri.parse("planboard://${kind.name.lowercase()}/$planId"))
                .putExtra(EXTRA_OPEN, kind.name)
                .putExtra(EXTRA_PLAN_ID, planId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

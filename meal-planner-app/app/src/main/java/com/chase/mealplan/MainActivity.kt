package com.chase.mealplan

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.chase.mealplan.importer.RecipeImporter
import com.chase.mealplan.reminder.Reminders
import com.chase.mealplan.ui.MealPlanNavHost
import com.chase.mealplan.ui.theme.MealPlanTheme

class MainActivity : ComponentActivity() {
    /** Bumped each time the reminder notification is tapped, so the plan jumps to next week. */
    private val showNextWeek = mutableIntStateOf(0)
    /** A recipe web address shared to the app, waiting to be imported. */
    private val importUrl = mutableStateOf<String?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        val app = application as MealPlanApp
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            app.settings.reminder.value.enabled && !Reminders.canNotify(this)
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MealPlanTheme {
                MealPlanNavHost(
                    showNextWeek = showNextWeek.intValue,
                    importUrl = importUrl.value,
                    onImportHandled = { importUrl.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            RecipeImporter.findUrl(text)?.let { importUrl.value = it }
            intent.action = null
        }
        if (intent?.getBooleanExtra(EXTRA_NEXT_WEEK, false) == true) {
            showNextWeek.intValue += 1
            intent.removeExtra(EXTRA_NEXT_WEEK)
        }
    }

    companion object {
        const val EXTRA_NEXT_WEEK = "show_next_week"
    }
}

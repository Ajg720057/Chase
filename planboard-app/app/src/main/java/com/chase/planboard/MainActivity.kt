package com.chase.planboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chase.planboard.ui.PlanBoardNavHost
import com.chase.planboard.ui.theme.PlanBoardTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlanBoardTheme {
                PlanBoardNavHost()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Pick up to-dos checked off, renamed or deleted in LifeBoard while we were away.
        val app = application as PlanBoardApp
        app.appScope.launch { app.plans.syncWithLifeBoard() }
    }
}

package com.chase.mealplan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chase.mealplan.ui.MealPlanNavHost
import com.chase.mealplan.ui.theme.MealPlanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MealPlanTheme {
                MealPlanNavHost()
            }
        }
    }
}

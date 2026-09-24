package com.chase.workoutlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chase.workoutlog.ui.WorkoutApp
import com.chase.workoutlog.ui.theme.WorkoutLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WorkoutLogTheme {
                WorkoutApp()
            }
        }
    }
}

package com.chase.mealplan

import android.app.Application
import com.chase.mealplan.data.AppDatabase
import com.chase.mealplan.data.Backup
import com.chase.mealplan.data.MealRepository
import com.chase.mealplan.data.PhotoStore
import com.chase.mealplan.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MealPlanApp : Application() {
    /** Outlives screens, so a save started just before navigating away still finishes. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database by lazy { AppDatabase.create(this) }
    val settings by lazy { Settings(this) }
    val photos by lazy { PhotoStore(this) }
    val repo by lazy { MealRepository(database, photos, settings) }
    val backup by lazy { Backup(this, database, photos, settings) }

    override fun onCreate() {
        super.onCreate()
        appScope.launch { runCatching { repo.cleanUpPhotos() } }
    }
}

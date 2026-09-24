package com.chase.planboard

import android.app.Application
import com.chase.planboard.data.AppDatabase
import com.chase.planboard.data.Backup
import com.chase.planboard.data.PlanRepository
import com.chase.planboard.link.LifeBoardLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PlanBoardApp : Application() {
    /** Outlives screens, so edits made just before navigating away still get saved. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database by lazy { AppDatabase.create(this) }
    val lifeBoard by lazy { LifeBoardLink(this) }
    val plans by lazy { PlanRepository(database, lifeBoard) }
    val backup by lazy { Backup(this, database) }
}

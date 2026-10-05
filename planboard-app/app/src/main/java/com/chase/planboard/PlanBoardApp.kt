package com.chase.planboard

import android.app.Application
import com.chase.planboard.data.AppDatabase
import com.chase.planboard.data.Backup
import com.chase.planboard.data.PlanRepository
import com.chase.planboard.export.ItineraryExporter
import com.chase.planboard.link.LifeBoardLink
import com.chase.planboard.widget.TodayWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class PlanBoardApp : Application() {
    /** Outlives screens, so edits made just before navigating away still get saved. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database by lazy { AppDatabase.create(this) }
    val lifeBoard by lazy { LifeBoardLink(this) }
    val plans by lazy { PlanRepository(database, lifeBoard) }
    val backup by lazy { Backup(this, database) }
    val itinerary by lazy { ItineraryExporter(this, database) }

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        // Keep the home-screen widget in step with any change made in the app.
        appScope.launch {
            combine(plans.allPlans, plans.allTodos) { p, t -> p to t }
                .distinctUntilChanged()
                .drop(1)
                .debounce(400)
                .collect { runCatching { TodayWidget.refresh(this@PlanBoardApp) } }
        }
    }
}

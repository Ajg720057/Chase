package com.chase.lifeboard

import android.app.Application
import com.chase.lifeboard.alarm.AlarmScheduler
import com.chase.lifeboard.alarm.Notifications
import com.chase.lifeboard.data.AppDatabase
import com.chase.lifeboard.data.Backup
import com.chase.lifeboard.data.JournalRepository
import com.chase.lifeboard.data.TaskRepository
import com.chase.lifeboard.widget.LifeBoardWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class LifeBoardApp : Application() {
    /** Outlives screens, so edits made just before navigating away still get saved. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database by lazy { AppDatabase.create(this) }
    val alarmScheduler by lazy { AlarmScheduler(this) }
    val tasks by lazy { TaskRepository(database.taskDao(), alarmScheduler) }
    val journal by lazy { JournalRepository(this, database.journalDao()) }
    val backup by lazy { Backup(this, database, tasks, journal) }

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        // Keep the home-screen widget in step with any change made in the app.
        appScope.launch {
            tasks.allTasks
                .distinctUntilChanged()
                .drop(1)
                .debounce(400)
                .collect { runCatching { LifeBoardWidget.refresh(this@LifeBoardApp) } }
        }
    }
}

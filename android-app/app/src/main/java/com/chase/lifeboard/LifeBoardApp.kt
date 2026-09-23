package com.chase.lifeboard

import android.app.Application
import com.chase.lifeboard.alarm.AlarmScheduler
import com.chase.lifeboard.alarm.Notifications
import com.chase.lifeboard.data.AppDatabase
import com.chase.lifeboard.data.Backup
import com.chase.lifeboard.data.JournalRepository
import com.chase.lifeboard.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class LifeBoardApp : Application() {
    /** Outlives screens, so edits made just before navigating away still get saved. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database by lazy { AppDatabase.create(this) }
    val alarmScheduler by lazy { AlarmScheduler(this) }
    val tasks by lazy { TaskRepository(database.taskDao(), alarmScheduler) }
    val journal by lazy { JournalRepository(this, database.journalDao()) }
    val backup by lazy { Backup(this, database, tasks, journal) }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}

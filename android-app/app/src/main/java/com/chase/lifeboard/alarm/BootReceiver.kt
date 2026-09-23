package com.chase.lifeboard.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chase.lifeboard.LifeBoardApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Alarms are wiped on reboot, app update and clock changes, so put them back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as LifeBoardApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.tasks.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}

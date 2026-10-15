package com.lookup.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts monitoring after a reboot or app update, if it was switched on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs(context).monitoringEnabled && Permissions.hasUsageAccess(context)) {
            MonitorService.start(context)
        }
    }
}

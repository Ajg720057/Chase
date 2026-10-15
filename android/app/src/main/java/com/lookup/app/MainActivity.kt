package com.lookup.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.AdapterView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private val thresholds = listOf(5, 10, 15, 20, 30, 45, 60)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        findViewById<Button>(R.id.grant_usage).setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        findViewById<Button>(R.id.grant_overlay).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        findViewById<Button>(R.id.grant_notif).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        val spinner = findViewById<Spinner>(R.id.threshold)
        spinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            thresholds.map { getString(R.string.minutes_fmt, it) }
        )
        spinner.setSelection(thresholds.indexOf(prefs.thresholdMinutes).coerceAtLeast(0))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (prefs.thresholdMinutes != thresholds[pos]) {
                    prefs.thresholdMinutes = thresholds[pos]
                    // Restart so the new limit applies right away.
                    if (prefs.monitoringEnabled) { MonitorService.stop(this@MainActivity); MonitorService.start(this@MainActivity) }
                }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        findViewById<MaterialSwitch>(R.id.enabled).setOnCheckedChangeListener { sw, on ->
            if (on && !Permissions.hasUsageAccess(this)) {
                sw.isChecked = false
                Toast.makeText(this, R.string.need_usage_first, Toast.LENGTH_LONG).show()
                return@setOnCheckedChangeListener
            }
            prefs.monitoringEnabled = on
            if (on) MonitorService.start(this) else MonitorService.stop(this)
        }

        findViewById<Button>(R.id.pick_apps).setOnClickListener {
            startActivity(Intent(this, AppPickerActivity::class.java))
        }

        findViewById<Button>(R.id.preview).setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                QuoteOverlay(this).show(
                    Quotes.random(),
                    getString(R.string.nudge_header, prefs.thresholdMinutes, "Instagram"),
                    hint = null,
                    snoozesLeft = 3,
                    onSnooze = {},
                )
            } else {
                Toast.makeText(this, R.string.need_overlay_first, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val usage = Permissions.hasUsageAccess(this)
        val overlay = Settings.canDrawOverlays(this)
        val notif = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

        setStep(R.id.grant_usage, R.id.usage_status, usage)
        setStep(R.id.grant_overlay, R.id.overlay_status, overlay)
        setStep(R.id.grant_notif, R.id.notif_status, notif)

        val sw = findViewById<MaterialSwitch>(R.id.enabled)
        sw.isChecked = prefs.monitoringEnabled && usage
        if (sw.isChecked) MonitorService.start(this)

        val mins = prefs.todayMs / 60_000
        findViewById<TextView>(R.id.today).text = getString(R.string.today_fmt, mins / 60, mins % 60)
        val nudges = resources.getQuantityString(R.plurals.nudges_fmt, prefs.todayNudges, prefs.todayNudges)
        val breakLeft = prefs.cooldownUntil - System.currentTimeMillis()
        findViewById<TextView>(R.id.today_nudges).text = if (breakLeft > 0) {
            nudges + "\n" + getString(R.string.today_break, ((breakLeft + 59_999) / 60_000).toInt())
        } else nudges
        findViewById<TextView>(R.id.tracked_count).text =
            getString(R.string.tracked_fmt, prefs.trackedPackages.count { isInstalled(it) })
    }

    private fun setStep(buttonId: Int, statusId: Int, granted: Boolean) {
        findViewById<Button>(buttonId).visibility = if (granted) View.GONE else View.VISIBLE
        findViewById<TextView>(statusId).text = getString(if (granted) R.string.granted else R.string.not_granted)
    }

    private fun isInstalled(pkg: String) = try {
        packageManager.getApplicationInfo(pkg, 0); true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

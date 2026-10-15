package com.lookup.app

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Lists installed apps so the user can choose which ones count as social media. */
class AppPickerActivity : AppCompatActivity() {
    private data class Entry(val pkg: String, val label: String, val icon: Drawable)

    private lateinit var prefs: Prefs
    private val selected = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        selected += prefs.trackedPackages

        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val entries = pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != packageName }
            .map { Entry(it.packageName, pm.getApplicationLabel(it).toString(), pm.getApplicationIcon(it)) }
            // Tracked apps first, then alphabetical.
            .sortedWith(compareBy({ it.pkg !in selected }, { it.label.lowercase() }))

        val list = RecyclerView(this)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = Adapter(entries)
        setContentView(list)
    }

    override fun onPause() {
        super.onPause()
        prefs.trackedPackages = selected
    }

    private inner class Adapter(val items: List<Entry>) : RecyclerView.Adapter<Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: Holder, position: Int) {
            val e = items[position]
            h.icon.setImageDrawable(e.icon)
            h.label.text = e.label
            h.check.setOnCheckedChangeListener(null)
            h.check.isChecked = e.pkg in selected
            h.check.setOnCheckedChangeListener { _, on -> if (on) selected += e.pkg else selected -= e.pkg }
            h.itemView.setOnClickListener { h.check.toggle() }
        }
    }

    private class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.icon)
        val label: TextView = v.findViewById(R.id.label)
        val check: CheckBox = v.findViewById(R.id.check)
    }
}

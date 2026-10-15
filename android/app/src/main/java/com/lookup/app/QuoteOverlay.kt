package com.lookup.app

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView

/** The full-screen quote card drawn on top of whatever app is open. */
class QuoteOverlay(context: Context) {
    private val ctx = ContextThemeWrapper(context, R.style.Theme_LookUp)
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null

    val isShowing get() = view != null

    fun show(quote: Quote, minutes: Int, appName: String, onSnooze: () -> Unit) {
        if (view != null) return
        // Wrapper that treats the Back key like "5 more minutes" instead of trapping the user.
        val root = object : FrameLayout(ctx) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    dismiss(); onSnooze(); return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        LayoutInflater.from(ctx).inflate(R.layout.overlay_quote, root, true)

        root.findViewById<TextView>(R.id.header).text =
            ctx.getString(R.string.nudge_header, minutes, appName)
        root.findViewById<TextView>(R.id.quote).text = "“${quote.text}”"
        root.findViewById<TextView>(R.id.author).text = "— ${quote.author}"
        root.findViewById<Button>(R.id.done).setOnClickListener {
            dismiss()
            ctx.startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        root.findViewById<Button>(R.id.snooze).setOnClickListener {
            dismiss(); onSnooze()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(root, params)
        view = root
    }

    fun dismiss() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }
}

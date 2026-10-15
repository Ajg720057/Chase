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
import android.widget.ImageView
import android.widget.TextView

/** The full-screen quote card, over a scenic background, drawn on top of whatever app is open. */
class QuoteOverlay(context: Context) {
    private val ctx = ContextThemeWrapper(context, R.style.Theme_LookUp)
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null

    val isShowing get() = view != null

    /**
     * @param snoozesLeft how many "5 more minutes" are left. At 0 the only way out is to leave.
     * @param hint optional line under the quote, e.g. how long the break has left.
     */
    fun show(
        quote: Quote,
        header: String,
        hint: String?,
        snoozesLeft: Int,
        onSnooze: () -> Unit,
    ) {
        if (view != null) return
        val canSnooze = snoozesLeft > 0
        // Back snoozes while snoozes are left; after that it leaves the app like the main button.
        val root = object : FrameLayout(ctx) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) {
                        if (canSnooze) { dismiss(); onSnooze() } else leave()
                    }
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        LayoutInflater.from(ctx).inflate(R.layout.overlay_quote, root, true)

        root.findViewById<ImageView>(R.id.scene).setImageResource(Scenes.random())
        root.findViewById<TextView>(R.id.header).text = header
        root.findViewById<TextView>(R.id.quote).text = "“${quote.text}”"
        root.findViewById<TextView>(R.id.author).text = "— ${quote.author}"
        root.findViewById<TextView>(R.id.hint).apply {
            text = hint
            visibility = if (hint == null) View.GONE else View.VISIBLE
        }
        root.findViewById<Button>(R.id.done).setOnClickListener { leave() }
        root.findViewById<Button>(R.id.snooze).apply {
            visibility = if (canSnooze) View.VISIBLE else View.GONE
            text = ctx.resources.getQuantityString(R.plurals.five_more_left, snoozesLeft, snoozesLeft)
            setOnClickListener { dismiss(); onSnooze() }
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

    /** Closes the card and goes to the home screen. */
    private fun leave() {
        dismiss()
        ctx.startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun dismiss() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }
}

package me.rerere.rikkahub.ui.pages.study

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import me.rerere.rikkahub.plugin.webview.PomodoroTimerService

/**
 * Small in-app timer bar attached to the activity content view.
 * It survives Compose navigation without requesting system overlay permission.
 */
object PomodoroInAppOverlayController {
    private const val VIEW_TAG = "study-pomodoro-mini-bar"
    private val handler = Handler(Looper.getMainLooper())
    private var ticker: Runnable? = null

    fun show(
        activity: Activity,
        task: String,
        onOpen: () -> Unit,
        onStop: () -> Unit,
    ) {
        hide(activity)
        if (!PomodoroTimerService.isRunning()) return

        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(Color.argb(242, 247, 244, 255))
            setStroke(dp(1), Color.argb(40, 73, 69, 107))
        }
        val bar = LinearLayout(activity).apply {
            tag = VIEW_TAG
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(9), dp(8), dp(9))
            this.background = background
            elevation = dp(8).toFloat()
        }
        val title = TextView(activity).apply {
            text = task.ifBlank { "番茄钟专注中" }
            textSize = 14f
            setTextColor(Color.rgb(55, 52, 74))
            maxLines = 1
            typeface = Typeface.DEFAULT_BOLD
        }
        val time = TextView(activity).apply {
            textSize = 16f
            setTextColor(Color.rgb(91, 80, 139))
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(dp(10), 0, dp(10), 0)
        }
        val open = TextView(activity).apply {
            text = "返回"
            textSize = 14f
            setTextColor(Color.rgb(75, 97, 148))
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(7), dp(10), dp(7))
            setOnClickListener {
                hide(activity)
                onOpen()
            }
        }
        val stop = TextView(activity).apply {
            text = "结束"
            textSize = 14f
            setTextColor(Color.rgb(151, 73, 82))
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(7), dp(10), dp(7))
            setOnClickListener {
                onStop()
                hide(activity)
            }
        }

        bar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(time, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        bar.addView(open, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        bar.addView(stop, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM,
        ).apply {
            setMargins(dp(14), 0, dp(14), dp(88))
        }
        root.addView(bar, params)

        val tick = object : Runnable {
            override fun run() {
                if (!PomodoroTimerService.isRunning()) {
                    hide(activity)
                    return
                }
                val remaining = PomodoroTimerService.getRemainingSeconds().coerceAtLeast(0)
                time.text = "%02d:%02d".format(remaining / 60, remaining % 60)
                handler.postDelayed(this, 500L)
            }
        }
        ticker = tick
        handler.post(tick)
    }

    fun hide(activity: Activity) {
        ticker?.let(handler::removeCallbacks)
        ticker = null
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        root.findViewWithTag<View>(VIEW_TAG)?.let(root::removeView)
    }
}

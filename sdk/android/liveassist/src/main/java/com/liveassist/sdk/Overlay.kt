package com.liveassist.sdk

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Everything the SDK draws, in a layer added to the activity's decor view NEXT TO (not inside) the app content.
 * Co-browse captures only the content view, so this layer is never sent to the advisor.
 * Touches pass through to the app except on the SDK's own controls.
 */
internal class OverlayController(context: Context) {
    var onEnd: (() -> Unit)? = null
    var onMute: (() -> Boolean)? = null
    var onCamera: (() -> Boolean)? = null
    var onFlip: (() -> Unit)? = null
    var onSendChat: ((String) -> Boolean)? = null
    var onStopSharing: (() -> Unit)? = null
    var onTakeBack: (() -> Unit)? = null

    private val ctx = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val brand = Color.rgb(47, 124, 246)
    private val orange = Color.rgb(255, 107, 0)
    private var activity: Activity? = null
    private val root = FrameLayout(ctx)

    // floating call window
    private val pip = LinearLayout(ctx)
    private val videoBox = FrameLayout(ctx)
    private val waiting = TextView(ctx)
    private val status = TextView(ctx)
    private var big = false
    // co-browse
    private val border = View(ctx)
    private val banner = LinearLayout(ctx)
    private val bannerText = TextView(ctx)
    private val takeBack = Button(ctx)
    private val stopBtn = Button(ctx)
    private val pointer = TextView(ctx)
    private val ring = View(ctx)
    private val toast = TextView(ctx)
    // chat
    private val chat = LinearLayout(ctx)
    private val chatList = LinearLayout(ctx)
    private val chatScroll = ScrollView(ctx)
    private val chatInput = EditText(ctx)
    private var dialog: AlertDialog? = null

    init { build() }

    private fun dp(v: Number) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()
    private fun pill(color: Int, radius: Int = 999) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }

    /** Attach to (or move to) the current activity */
    fun attach(a: Activity) {
        if (activity === a && root.parent != null) return
        (root.parent as? ViewGroup)?.removeView(root)
        activity = a
        (a.window.decorView as ViewGroup).addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun detach() { dialog?.dismiss(); dialog = null; (root.parent as? ViewGroup)?.removeView(root); activity = null }

    /** offset of the app content view inside this overlay (content coords → overlay coords) */
    private fun contentOffset(): Pair<Int, Int> {
        val content = activity?.findViewById<View>(android.R.id.content) ?: return 0 to 0
        val a = IntArray(2); val b = IntArray(2)
        content.getLocationInWindow(a); root.getLocationInWindow(b)
        return (a[0] - b[0]) to (a[1] - b[1])
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun build() {
        // Sharing frame (non-interactive)
        border.background = GradientDrawable().apply { setColor(Color.TRANSPARENT); setStroke(dp(4), brand) }
        border.visibility = View.GONE
        root.addView(border, FrameLayout.LayoutParams(-1, -1))

        // Floating call window
        pip.orientation = LinearLayout.VERTICAL
        pip.background = pill(Color.rgb(13, 17, 23), 16)
        pip.clipToOutline = true
        waiting.setTextColor(Color.WHITE); waiting.textSize = 12f; waiting.gravity = Gravity.CENTER
        waiting.setPadding(dp(8), dp(18), dp(8), dp(18)); waiting.text = "Connecting to an advisor…"
        pip.addView(waiting, LinearLayout.LayoutParams(-1, -2))
        videoBox.visibility = View.GONE
        pip.addView(videoBox, LinearLayout.LayoutParams(-1, dp(176)))
        status.setTextColor(Color.WHITE); status.textSize = 10f; status.setPadding(dp(5), dp(2), dp(5), dp(2))
        status.background = pill(Color.argb(140, 0, 0, 0), 6)
        val bar = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(dp(4), dp(6), dp(4), dp(6)) }
        fun round(label: String, color: Int = Color.argb(40, 255, 255, 255), action: (TextView) -> Unit) = TextView(ctx).apply {
            text = label; textSize = 13f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); background = pill(color)
            setOnClickListener { action(this) }
            bar.addView(this, LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginStart = dp(2); marginEnd = dp(2) })
        }
        round("🎙") { v -> val on = onMute?.invoke() ?: true; v.background = pill(if (on) Color.argb(40, 255, 255, 255) else Color.WHITE) }
        round("📷") { v -> val on = onCamera?.invoke() ?: true; v.background = pill(if (on) Color.argb(40, 255, 255, 255) else Color.WHITE) }
        round("🔄") { onFlip?.invoke() }
        round("💬") { setChat(chat.visibility != View.VISIBLE) }
        round("✕", Color.rgb(208, 52, 44)) { onEnd?.invoke() }
        pip.addView(bar, LinearLayout.LayoutParams(-1, -2))
        pip.visibility = View.GONE
        root.addView(pip, FrameLayout.LayoutParams(dp(140), -2, Gravity.TOP or Gravity.END).apply { topMargin = dp(36); marginEnd = dp(12) })
        var downX = 0f; var downY = 0f; var startX = 0f; var startY = 0f; var moved = false
        pip.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = v.translationX; startY = v.translationY; moved = false; true }
                MotionEvent.ACTION_MOVE -> { val dx = e.rawX - downX; val dy = e.rawY - downY; if (Math.abs(dx) + Math.abs(dy) > dp(6)) moved = true
                    if (moved) { v.translationX = startX + dx; v.translationY = startY + dy }; true }
                MotionEvent.ACTION_UP -> { if (!moved) toggleSize(); true }
                else -> false
            }
        }

        // Banner
        banner.orientation = LinearLayout.HORIZONTAL; banner.gravity = Gravity.CENTER_VERTICAL
        banner.setPadding(dp(12), dp(4), dp(4), dp(4)); banner.background = pill(brand)
        bannerText.setTextColor(Color.WHITE); bannerText.textSize = 12f
        banner.addView(bannerText)
        for ((b, label) in listOf(takeBack to "Take back", stopBtn to "Stop")) {
            b.text = label; b.textSize = 12f; b.isAllCaps = false; b.minHeight = 0; b.minimumHeight = 0; b.minWidth = 0; b.minimumWidth = 0
            b.setPadding(dp(10), dp(4), dp(10), dp(4)); b.background = pill(Color.WHITE); b.stateListAnimator = null
            banner.addView(b, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(6) })
        }
        takeBack.setOnClickListener { onTakeBack?.invoke() }
        stopBtn.setOnClickListener { onStopSharing?.invoke() }
        banner.visibility = View.GONE
        root.addView(banner, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(32) })

        // Pointer, ring, toast
        pointer.text = "  Advisor"; pointer.textSize = 10f; pointer.setTextColor(Color.WHITE)
        pointer.setCompoundDrawablesWithIntrinsicBounds(GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(orange); setStroke(dp(3), Color.WHITE); setSize(dp(18), dp(18)) }, null, null, null)
        pointer.visibility = View.GONE
        root.addView(pointer, FrameLayout.LayoutParams(-2, -2))
        ring.background = GradientDrawable().apply { setColor(Color.TRANSPARENT); setStroke(dp(3), orange); cornerRadius = dp(10).toFloat() }
        ring.visibility = View.GONE
        root.addView(ring, FrameLayout.LayoutParams(0, 0))
        toast.setTextColor(Color.WHITE); toast.textSize = 13f; toast.setPadding(dp(14), dp(8), dp(14), dp(8))
        toast.background = pill(Color.argb(235, 17, 24, 39)); toast.visibility = View.GONE
        root.addView(toast, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(110) })

        // Chat
        chat.orientation = LinearLayout.VERTICAL
        chat.background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadii = floatArrayOf(dp(16).toFloat(), dp(16).toFloat(), dp(16).toFloat(), dp(16).toFloat(), 0f, 0f, 0f, 0f) }
        chat.elevation = dp(12).toFloat()
        chat.setPadding(dp(14), dp(10), dp(14), dp(10))
        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(ctx).apply { text = "Chat with your advisor"; textSize = 14f; setTextColor(Color.BLACK); paint.isFakeBoldText = true }, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(TextView(ctx).apply { text = "✕"; textSize = 18f; setPadding(dp(8), 0, dp(8), 0); setOnClickListener { setChat(false) } })
        chat.addView(head)
        chatList.orientation = LinearLayout.VERTICAL
        chatScroll.addView(chatList)
        chat.addView(chatScroll, LinearLayout.LayoutParams(-1, dp(190)))
        val input = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        chatInput.hint = "Type a message"; chatInput.inputType = InputType.TYPE_CLASS_TEXT; chatInput.imeOptions = EditorInfo.IME_ACTION_SEND
        chatInput.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_SEND) { sendChat(); true } else false }
        input.addView(chatInput, LinearLayout.LayoutParams(0, -2, 1f))
        input.addView(Button(ctx).apply { text = "Send"; isAllCaps = false; setOnClickListener { sendChat() } })
        chat.addView(input)
        chat.visibility = View.GONE
        root.addView(chat, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
    }

    // ---- call window ----

    fun showWaiting(text: String) { pip.visibility = View.VISIBLE; waiting.visibility = View.VISIBLE; waiting.text = text }

    fun showInCall(remote: View, local: View, advisor: String) {
        pip.visibility = View.VISIBLE; waiting.visibility = View.GONE; videoBox.visibility = View.VISIBLE
        if (remote.parent !== videoBox) {
            (remote.parent as? ViewGroup)?.removeView(remote); (local.parent as? ViewGroup)?.removeView(local); (status.parent as? ViewGroup)?.removeView(status)
            videoBox.addView(remote, FrameLayout.LayoutParams(-1, -1))
            videoBox.addView(local, FrameLayout.LayoutParams(dp(44), dp(58), Gravity.TOP or Gravity.END).apply { topMargin = dp(5); marginEnd = dp(5) })
            videoBox.addView(status, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { topMargin = dp(5); marginStart = dp(5) })
        }
        status.text = advisor
    }

    fun setStatus(text: String) { status.text = text }

    fun hideCall() {
        pip.visibility = View.GONE; videoBox.removeAllViews(); videoBox.visibility = View.GONE
        setChat(false); chatList.removeAllViews()
        setCoBrowse(active = false, control = false)
    }

    private fun toggleSize() {
        big = !big
        val w = if (big) minOf(dp(360), root.width - dp(24)) else dp(140)
        pip.layoutParams = (pip.layoutParams as FrameLayout.LayoutParams).apply { width = w }
        videoBox.layoutParams = (videoBox.layoutParams as LinearLayout.LayoutParams).apply { height = (w * 4 / 3 * 0.9).toInt() }
    }

    // ---- co-browse indicators ----

    fun setCoBrowse(active: Boolean, control: Boolean) {
        val color = if (control) orange else brand
        border.visibility = if (active) View.VISIBLE else View.GONE
        banner.visibility = if (active) View.VISIBLE else View.GONE
        (border.background as GradientDrawable).setStroke(dp(4), color)
        banner.background = pill(color)
        bannerText.text = if (control) "✍️ Advisor can help fill in" else "👁 Your advisor can see this screen"
        takeBack.visibility = if (control) View.VISIBLE else View.GONE
        takeBack.setTextColor(color); stopBtn.setTextColor(color)
        if (!active) { pointer.visibility = View.GONE; ring.visibility = View.GONE }
        banner.bringToFront()
    }

    private val hidePointer = Runnable { pointer.visibility = View.GONE }
    /** x, y in content-view pixels */
    fun showPointer(x: Float, y: Float) {
        val (ox, oy) = contentOffset()
        pointer.visibility = View.VISIBLE
        pointer.animate().translationX(ox + x - dp(9)).translationY(oy + y - dp(9)).setDuration(80).start()
        main.removeCallbacks(hidePointer); main.postDelayed(hidePointer, 4000)
    }
    fun hidePointer() { pointer.visibility = View.GONE }

    private val hideRing = Runnable { ring.visibility = View.GONE }
    /** rect in content-view pixels */
    fun showRing(r: Rect) {
        val (ox, oy) = contentOffset()
        ring.layoutParams = FrameLayout.LayoutParams(r.width() + dp(12), r.height() + dp(12))
        ring.translationX = (ox + r.left - dp(6)).toFloat(); ring.translationY = (oy + r.top - dp(6)).toFloat()
        ring.visibility = View.VISIBLE
        ring.scaleX = 1f; ring.scaleY = 1f
        ring.animate().scaleX(1.05f).scaleY(1.05f).setDuration(450).withEndAction { ring.animate().scaleX(1f).scaleY(1f).setDuration(450).start() }.start()
        ring.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        main.removeCallbacks(hideRing); main.postDelayed(hideRing, 3000)
    }

    private val hideToast = Runnable { toast.visibility = View.GONE }
    fun toast(text: String) {
        toast.text = text; toast.visibility = View.VISIBLE; toast.bringToFront()
        main.removeCallbacks(hideToast); main.postDelayed(hideToast, 2200)
    }

    // ---- consent ----

    fun ask(title: String, message: String, no: String, yes: String, done: (Boolean) -> Unit) {
        val a = activity ?: return done(false)
        dialog?.dismiss()
        dialog = AlertDialog.Builder(a).setTitle(title).setMessage(message).setCancelable(false)
            .setNegativeButton(no) { _, _ -> done(false) }
            .setPositiveButton(yes) { _, _ -> done(true) }
            .show()
    }

    fun dismissPrompts() { dialog?.dismiss(); dialog = null }

    // ---- chat ----

    fun setChat(visible: Boolean) {
        chat.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) chat.bringToFront()
        else (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(chatInput.windowToken, 0)
    }

    /** mine: true = customer, false = advisor, null = system note */
    fun addChat(text: String, mine: Boolean?) {
        val t = TextView(ctx).apply {
            this.text = text; textSize = if (mine == null) 12f else 14f; setPadding(dp(12), dp(7), dp(12), dp(7))
            when (mine) {
                true -> { setTextColor(Color.WHITE); background = pill(brand, 10) }
                false -> { setTextColor(Color.BLACK); background = pill(Color.rgb(238, 242, 247), 10) }
                null -> setTextColor(Color.GRAY)
            }
        }
        chatList.addView(t, LinearLayout.LayoutParams(-2, -2).apply {
            topMargin = dp(4); gravity = when (mine) { true -> Gravity.END; false -> Gravity.START; null -> Gravity.CENTER_HORIZONTAL }
        })
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
        if (mine == false && chat.visibility != View.VISIBLE) setChat(true)
    }

    private fun sendChat() {
        val t = chatInput.text.toString().trim()
        if (t.isNotEmpty() && onSendChat?.invoke(t) == true) chatInput.setText("")
    }
}

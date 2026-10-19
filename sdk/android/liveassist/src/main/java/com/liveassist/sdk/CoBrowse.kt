package com.liveassist.sdk

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Base64
import android.view.ActionMode
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.SearchEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Spinner
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference

/**
 * Native co-browse (sdk/PROTOCOL.md §3): streams the activity's CONTENT view only — never other apps,
 * never the keyboard, never the SDK overlay — with sensitive areas painted dark ON THE PHONE before encoding.
 * Assisted-control rules are enforced here, whatever the operator console sends.
 */
internal class CoBrowseEngine {
    enum class State { IDLE, ASKING, ACTIVE }
    var state = State.IDLE; private set
    var control = false; private set

    var send: ((JSONObject) -> Boolean)? = null
    var bufferedAmount: (() -> Long)? = null
    var overlay: OverlayController? = null
    private var activityRef: WeakReference<Activity>? = null

    /** width in pixels of the JPEG sent to the advisor */
    var frameWidth = 480
    var jpegQuality = 55

    private val main = Handler(Looper.getMainLooper())
    private var lastHash = 0L
    private var forceFrame = true
    private var bitmap: Bitmap? = null
    private var pixels = IntArray(0)
    private val fields = HashMap<String, FieldRef>()
    private var nextId = 1
    private var wrappedCallback: Window.Callback? = null
    private val maskPaint = Paint().apply { color = Color.rgb(17, 24, 39) }
    private val maskText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY; isFakeBoldText = true }

    private class FieldRef(val view: WeakReference<View>, val virtualId: Int = View.NO_ID)

    fun setActivity(a: Activity?) {
        val old = activityRef?.get()
        if (old === a) return
        if (state == State.ACTIVE) { unhookActivity(old); activityRef = a?.let { WeakReference(it) }; hookActivity(a); forceFrame = true }
        else activityRef = a?.let { WeakReference(it) }
    }

    private val activity get() = activityRef?.get()
    private val content: View? get() = activity?.findViewById(android.R.id.content)

    // ---------------- messages from the operator ----------------

    fun handle(m: JSONObject) {
        when (m.optString("kind")) {
            "cb:request" -> {
                if (state == State.ACTIVE) { send?.invoke(JSONObject().put("kind", "cb:accepted")); forceFrame = true; return }
                state = State.ASKING
                overlay?.ask("Share your screen with your advisor?",
                    "Your advisor will see this app's screens to guide you.\n\n• They can't tap, type or make transactions for you.\n• Passwords, PINs, card numbers and codes are hidden.\n• You can stop at any time.",
                    "Not now", "Allow") { ok -> if (ok) accept() else decline() }
            }
            "cb:stop" -> stop(notify = false)
            "cb:control-request" -> {
                if (state != State.ACTIVE) return
                if (control) { send?.invoke(JSONObject().put("kind", "cb:control-granted")); return }
                overlay?.ask("Let your advisor fill in fields for you?",
                    "They can type in forms, choose options and move between screens.\n\n• Only you can press Send, Confirm or Save.\n• They can't see or fill in passwords, PINs or codes.\n• Everything they change is highlighted. Tap “Take back” any time.",
                    "No, just watch", "Allow") { ok ->
                    if (ok) { setControl(true); send?.invoke(JSONObject().put("kind", "cb:control-granted")); overlay?.toast("Your advisor can now help fill in") }
                    else send?.invoke(JSONObject().put("kind", "cb:control-denied"))
                }
            }
            "cb:control-release" -> setControl(false, "advisor")
            "cb:pointer" -> if (state == State.ACTIVE) {
                if (m.optBoolean("hide")) overlay?.hidePointer() else point(m)?.let { overlay?.showPointer(it.first, it.second) }
            }
            "cb:highlight" -> if (state == State.ACTIVE) {
                val p = point(m) ?: return; val root = content ?: return
                val v = target(root, p) ?: return
                val r = rectOf(v, root)
                overlay?.showRing(r)
                send?.invoke(JSONObject().put("kind", "cb:highlighted").put("rect", norm(r, root)))
            }
            "cb:remote" -> if (state == State.ACTIVE) remote(m)
        }
    }

    private fun accept() {
        state = State.ACTIVE
        send?.invoke(JSONObject().put("kind", "cb:accepted"))
        overlay?.setCoBrowse(active = true, control = false)
        forceFrame = true
        hookActivity(activity)
        main.post(loop)
    }

    private fun decline() { state = State.IDLE; send?.invoke(JSONObject().put("kind", "cb:declined")) }

    fun stop(notify: Boolean) {
        if (state == State.IDLE) return
        overlay?.dismissPrompts()
        if (control) setControl(false, "stop")
        state = State.IDLE
        main.removeCallbacks(loop)
        unhookActivity(activity)
        overlay?.setCoBrowse(active = false, control = false)
        fields.clear()
        if (notify) send?.invoke(JSONObject().put("kind", "cb:stopped"))
    }

    fun setControl(on: Boolean, by: String = "customer") {
        if (control == on) return
        control = on
        overlay?.setCoBrowse(active = state == State.ACTIVE, control = on)
        if (!on && by == "customer") { send?.invoke(JSONObject().put("kind", "cb:control-revoked")); overlay?.toast("You took back control") }
        if (!on && by == "advisor") overlay?.toast("Advisor released control")
    }

    // ---------------- capture loop (≤ 10 fps, only on change, only when the channel isn't congested) ----------------

    private val loop = object : Runnable {
        override fun run() {
            if (state != State.ACTIVE) return
            runCatching { captureFrame() }
            main.postDelayed(this, 100)
        }
    }

    private fun captureFrame() {
        val a = activity ?: return
        val root = content ?: return
        if (root.width == 0 || root.height == 0) return
        if ((a.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0) return   // the app forbids capture on this screen
        if ((bufferedAmount?.invoke() ?: 0L) > 512L * 1024) return

        val scale = frameWidth.toFloat() / root.width
        val w = frameWidth; val h = (root.height * scale).toInt().coerceAtLeast(1)
        val bmp = bitmap?.takeIf { it.width == w && it.height == h } ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap = it; pixels = IntArray(w * h) }
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        c.save(); c.scale(scale, scale)
        root.draw(c)
        paintMasks(c, root)
        c.restore()

        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        var hash = 1469598103934665603L
        var i = 0
        while (i < pixels.size) { hash = (hash xor pixels[i].toLong()) * 1099511628211L; i += 37 }
        if (hash == lastHash && !forceFrame) return

        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
        val density = a.resources.displayMetrics.density
        val msg = JSONObject().put("kind", "cb:frame")
            .put("w", (root.width / density).toInt()).put("h", (root.height / density).toInt())
            .put("jpg", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
            .put("title", a.title?.toString() ?: "Mobile app")
        if (send?.invoke(msg) == true) { lastHash = hash; forceFrame = false }
    }

    /** Paints every sensitive area dark — before encoding, so secrets never leave the phone */
    private fun paintMasks(c: Canvas, root: View) {
        val rects = ArrayList<Rect>()
        fun walk(v: View) {
            if (v.visibility != View.VISIBLE || v.alpha < 0.01f) return
            if (Sensitivity.isMaskedSelf(v)) { rects.add(rectOf(v, root)); return }
            if (v is ViewGroup) for (k in 0 until v.childCount) walk(v.getChildAt(k))
        }
        walk(root)
        val loc = IntArray(2); root.getLocationInWindow(loc)
        LiveAssistRegistry.contentOffsetX = loc[0]; LiveAssistRegistry.contentOffsetY = loc[1]
        rects += LiveAssistRegistry.all(LiveAssistRegistry.Kind.MASKED)
        maskText.textSize = 12f * root.resources.displayMetrics.density
        for (r in rects) {
            if (r.width() <= 0 || r.height() <= 0) continue
            c.drawRect((r.left - 2).toFloat(), (r.top - 2).toFloat(), (r.right + 2).toFloat(), (r.bottom + 2).toFloat(), maskPaint)
            c.drawText("🔒 hidden", r.left + 16f, r.exactCenterY() + maskText.textSize / 3, maskText)
        }
    }

    // ---------------- customer activity (taps, focused field) ----------------

    private val focusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
        if (state != State.ACTIVE) return@OnGlobalFocusChangeListener
        val root = content ?: return@OnGlobalFocusChangeListener
        val rect: Any = if (newFocus is EditText) norm(rectOf(newFocus, root), root) else JSONObject.NULL
        send?.invoke(JSONObject().put("kind", "cb:focus").put("rect", rect))
    }

    private fun hookActivity(a: Activity?) {
        a ?: return
        content?.viewTreeObserver?.addOnGlobalFocusChangeListener(focusListener)
        val original = a.window.callback ?: return
        if (original is TouchSpy) return
        val spy = TouchSpy(original) { e ->
            if (e.actionMasked == MotionEvent.ACTION_UP && state == State.ACTIVE) {
                val root = content ?: return@TouchSpy
                val loc = IntArray(2); root.getLocationOnScreen(loc)
                send?.invoke(JSONObject().put("kind", "cb:tap").put("nx", ((e.rawX - loc[0]) / root.width).toDouble()).put("ny", ((e.rawY - loc[1]) / root.height).toDouble()))
                forceFrame = true
            }
        }
        a.window.callback = spy
        wrappedCallback = original
    }

    private fun unhookActivity(a: Activity?) {
        a ?: return
        runCatching { content?.viewTreeObserver?.removeOnGlobalFocusChangeListener(focusListener) }
        val cb = a.window.callback
        if (cb is TouchSpy) a.window.callback = cb.inner
        wrappedCallback = null
    }

    // ---------------- assisted control (rules enforced here) ----------------

    private fun remote(m: JSONObject) {
        val action = m.optString("action")
        fun deny(reason: String) { send?.invoke(JSONObject().put("kind", "cb:denied").put("action", action).put("reason", reason)) }
        if (!control) return deny("control is not enabled")
        val root = content ?: return

        when (action) {
            "scroll" -> {
                val p = point(m) ?: return
                var v: View? = deepest(root, p)
                while (v != null && !(v.canScrollVertically(1) || v.canScrollVertically(-1))) v = v.parent as? View
                val sv = v ?: return
                val dy = (m.optDouble("dy", 0.0) * root.height).toInt()
                when (sv) {
                    is ScrollView -> sv.smoothScrollBy(0, dy)
                    is AbsListView -> sv.smoothScrollBy(dy, 200)
                    else -> sv.scrollBy(0, dy)
                }
                forceFrame = true
            }
            "tap" -> {
                val p = point(m) ?: return
                val hit = deepest(root, p) ?: return deny("nothing to tap there")
                if (Sensitivity.isMasked(hit, p)) return deny("sensitive field — only the customer can fill it")
                if (Sensitivity.isNoRemote(hit, p)) return deny("only the customer can use this")
                val t = target(root, p) ?: hit
                if (Sensitivity.isMasked(t)) return deny("sensitive field — only the customer can fill it")
                if (Sensitivity.isNoRemote(t)) return deny("only the customer can use this")
                val rect = rectOf(t, root)
                when {
                    t is EditText -> {
                        if (!t.isEnabled) return deny("disabled")
                        val id = register(t)
                        send?.invoke(JSONObject().put("kind", "cb:field").put("id", id).put("rect", norm(rect, root))
                            .put("value", t.text?.toString() ?: "").put("placeholder", t.hint?.toString() ?: "").put("input", inputKind(t.inputType)))
                    }
                    t is Spinner -> {
                        if (!t.isEnabled) return deny("disabled")
                        val id = register(t)
                        val opts = JSONArray(); val ad = t.adapter
                        for (k in 0 until (ad?.count ?: 0)) opts.put(ad?.getItem(k)?.toString() ?: "Option ${k + 1}")
                        send?.invoke(JSONObject().put("kind", "cb:choices").put("id", id).put("rect", norm(rect, root)).put("options", opts).put("selected", t.selectedItemPosition))
                    }
                    t is CompoundButton -> {
                        if (!t.isEnabled) return deny("disabled")
                        t.performClick()   // toggles and fires the app's own listeners
                        applied(t, rect, "Changed by your advisor")
                    }
                    t.parent is AdapterView<*> && t.parent !is Spinner -> {
                        val list = t.parent as AdapterView<*>
                        val pos = list.getPositionForView(t)
                        if (pos == AdapterView.INVALID_POSITION) return deny("item no longer on screen")
                        list.performItemClick(t, pos, list.getItemIdAtPosition(pos))
                        applied(t, rect, "Advisor tapped “${Sensitivity.labelOf(t) ?: "an item"}”")
                    }
                    t.isClickable -> {
                        if (!t.isEnabled) return deny("disabled")
                        if (Sensitivity.looksLikeCommit(Sensitivity.labelOf(t))) return deny("only the customer can submit")
                        t.performClick()
                        applied(t, rect, "Advisor tapped “${Sensitivity.labelOf(t) ?: "an item"}”")
                    }
                    else -> virtualTap(t, p, rect, root) ?: deny("this item can't be tapped remotely")
                }
            }
            "set" -> {
                val ref = fields[m.optString("id")] ?: return deny("field no longer on screen")
                val v = ref.view.get()?.takeIf { it.isAttachedToWindow } ?: return deny("field no longer on screen")
                if (Sensitivity.isMasked(v)) return deny("sensitive field — only the customer can fill it")
                if (Sensitivity.isNoRemote(v)) return deny("only the customer can use this")
                val value = m.optString("value").take(500)
                if (ref.virtualId != View.NO_ID) {
                    val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }
                    v.accessibilityNodeProvider?.performAction(ref.virtualId, AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                } else if (v is EditText) {
                    v.setText(value); v.setSelection(value.length)
                } else return deny("not a text field")
                applied(v, rectOf(v, root), "Filled in by your advisor", m.optString("id"))
            }
            "choose" -> {
                val v = fields[m.optString("id")]?.view?.get() as? Spinner ?: return deny("no longer on screen")
                if (Sensitivity.isNoRemote(v)) return deny("only the customer can use this")
                val i = m.optInt("index", -1)
                if (i < 0 || i >= (v.adapter?.count ?: 0)) return deny("invalid option")
                v.setSelection(i)
                applied(v, rectOf(v, root), "Changed by your advisor", m.optString("id"))
            }
            else -> deny("not allowed")
        }
    }

    /**
     * Jetpack Compose & custom-drawn views: use the accessibility tree (like TalkBack does) to find the element
     * under the point and click it / edit it. Returns null if nothing usable was found.
     */
    private fun virtualTap(host: View, p: Pair<Float, Float>, rect: Rect, root: View): Unit? {
        val provider = host.accessibilityNodeProvider ?: return null
        val loc = IntArray(2); root.getLocationOnScreen(loc)
        val sx = (p.first + loc[0]).toInt(); val sy = (p.second + loc[1]).toInt()
        var best: Pair<Int, AccessibilityNodeInfo>? = null
        fun visit(id: Int, depth: Int) {
            if (depth > 30) return
            val n = provider.createAccessibilityNodeInfo(id) ?: return
            val b = Rect(); n.getBoundsInScreen(b)
            if (!b.contains(sx, sy)) return
            if ((n.isClickable || n.isEditable || n.isCheckable) && (best == null || b.width() * b.height() < Rect().also { best!!.second.getBoundsInScreen(it) }.let { it.width() * it.height() })) best = id to n
            for (k in 0 until n.childCount) {
                // child ids aren't public: walk virtual ids via the provider's own child info
                val childId = childVirtualId(n, k) ?: continue
                visit(childId, depth + 1)
            }
        }
        visit(AccessibilityNodeProvider_HOST_VIEW_ID, 0)
        val (vid, node) = best ?: return null
        val b = Rect(); node.getBoundsInScreen(b); b.offset(-loc[0], -loc[1])
        val label = node.text ?: node.contentDescription
        if (node.isPassword) { deny("sensitive field — only the customer can fill it"); return Unit }
        if (LiveAssistRegistry.contains(p, LiveAssistRegistry.Kind.NO_REMOTE)) { deny("only the customer can use this"); return Unit }
        if (node.isEditable) {
            val id = "f${nextId++}"
            fields[id] = FieldRef(WeakReference(host), vid)
            send?.invoke(JSONObject().put("kind", "cb:field").put("id", id).put("rect", norm(b, root)).put("value", node.text?.toString() ?: "").put("placeholder", "").put("input", "text"))
            return Unit
        }
        if (Sensitivity.looksLikeCommit(label)) { deny("only the customer can submit"); return Unit }
        if (!provider.performAction(vid, AccessibilityNodeInfo.ACTION_CLICK, null)) return null
        applied(null, b, "Advisor tapped “${label ?: "an item"}”")
        return Unit
    }

    private fun deny(reason: String) { send?.invoke(JSONObject().put("kind", "cb:denied").put("action", "tap").put("reason", reason)) }

    /** AccessibilityNodeInfo doesn't expose virtual child ids publicly; read them reflectively if available */
    private fun childVirtualId(n: AccessibilityNodeInfo, index: Int): Int? = runCatching {
        val m = AccessibilityNodeInfo::class.java.getDeclaredMethod("getChildId", Int::class.javaPrimitiveType)
        m.isAccessible = true
        val sourceId = m.invoke(n, index) as Long
        (sourceId shr 32).toInt()
    }.getOrNull()

    private fun applied(v: View?, rect: Rect, message: String, id: String? = null) {
        val root = content ?: return
        overlay?.showRing(rect)
        overlay?.toast(message)
        val msg = JSONObject().put("kind", "cb:applied").put("rect", norm(rect, root))
        if (id != null) msg.put("id", id)
        send?.invoke(msg)
        forceFrame = true
    }

    // ---------------- helpers ----------------

    /** deepest visible view under a point (content coordinates), topmost child first */
    private fun deepest(v: View, p: Pair<Float, Float>, root: View = v): View? {
        if (v.visibility != View.VISIBLE) return null
        val r = rectOf(v, root)
        if (!r.contains(p.first.toInt(), p.second.toInt())) return null
        if (v is ViewGroup) for (k in v.childCount - 1 downTo 0) deepest(v.getChildAt(k), p, root)?.let { return it }
        return v
    }

    /** the meaningful element at a point: nearest text field / switch / spinner / list item / clickable view */
    private fun target(root: View, p: Pair<Float, Float>): View? {
        var v: View? = deepest(root, p) ?: return null
        while (v != null && v !== root) {
            if (v is EditText || v is CompoundButton || v is Spinner || v.isClickable || (v.parent is AdapterView<*> && v.parent !is Spinner)) return v
            v = v.parent as? View
        }
        return deepest(root, p)
    }

    private fun register(v: View): String {
        fields.entries.firstOrNull { it.value.view.get() === v && it.value.virtualId == View.NO_ID }?.let { return it.key }
        val id = "f${nextId++}"; fields[id] = FieldRef(WeakReference(v)); return id
    }

    private fun rectOf(v: View, root: View): Rect {
        val a = IntArray(2); val b = IntArray(2)
        v.getLocationInWindow(a); root.getLocationInWindow(b)
        return Rect(a[0] - b[0], a[1] - b[1], a[0] - b[0] + v.width, a[1] - b[1] + v.height)
    }

    private fun point(m: JSONObject): Pair<Float, Float>? {
        val root = content ?: return null
        if (!m.has("nx") || !m.has("ny")) return null
        return (m.optDouble("nx") * root.width).toFloat() to (m.optDouble("ny") * root.height).toFloat()
    }

    private fun norm(r: Rect, root: View) = JSONArray().put(r.left.toDouble() / root.width).put(r.top.toDouble() / root.height)
        .put(r.width().toDouble() / root.width).put(r.height().toDouble() / root.height)

    private fun inputKind(t: Int): String {
        val cls = t and InputType.TYPE_MASK_CLASS; val variation = t and InputType.TYPE_MASK_VARIATION
        return when {
            cls == InputType.TYPE_CLASS_NUMBER && (t and InputType.TYPE_NUMBER_FLAG_DECIMAL) != 0 -> "decimal"
            cls == InputType.TYPE_CLASS_NUMBER -> "number"
            cls == InputType.TYPE_CLASS_PHONE -> "phone"
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> "email"
            (t and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0 -> "multiline"
            else -> "text"
        }
    }

    companion object {
        /** AccessibilityNodeProvider.HOST_VIEW_ID */
        private const val AccessibilityNodeProvider_HOST_VIEW_ID = -1
    }
}

/** Forwards everything to the activity's own Window.Callback, and lets the SDK observe touches (read-only). */
internal class TouchSpy(val inner: Window.Callback, private val onTouch: (MotionEvent) -> Unit) : Window.Callback by inner {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean { runCatching { onTouch(event) }; return inner.dispatchTouchEvent(event) }
    // explicit overrides for members with default implementations so delegation is complete on all API levels
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = inner.dispatchKeyEvent(event)
    override fun onSearchRequested(searchEvent: SearchEvent): Boolean = inner.onSearchRequested(searchEvent)
    override fun onWindowStartingActionMode(callback: ActionMode.Callback, type: Int): ActionMode? = inner.onWindowStartingActionMode(callback, type)
    override fun onCreatePanelMenu(featureId: Int, menu: Menu): Boolean = inner.onCreatePanelMenu(featureId, menu)
    override fun onMenuItemSelected(featureId: Int, item: MenuItem): Boolean = inner.onMenuItemSelected(featureId, item)
    override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent): Boolean = inner.dispatchPopulateAccessibilityEvent(event)
}

package com.liveassist.sdk

import android.graphics.Rect
import android.os.Build
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.TextView

/*
 * Privacy markers
 *
 *   LiveAssist.mask(cardNumberView)       // painted dark in every frame sent to the advisor
 *   LiveAssist.noRemote(confirmButton)    // the advisor can never tap / change it, even with control
 *
 *   Compose:  Modifier.liveAssistMasked()  /  Modifier.liveAssistNoRemote()     (see ComposeMasking.kt)
 *
 * Automatically masked (no code needed): password EditTexts (any password inputType) and fields whose
 * autofill hints are password / credit card / SMS one-time code.
 */

internal object Sensitivity {
    private val maskedTag = R.id.liveassist_masked
    private val noRemoteTag = R.id.liveassist_no_remote

    fun setMasked(v: View, on: Boolean) = v.setTag(maskedTag, on)
    fun setNoRemote(v: View, on: Boolean) = v.setTag(noRemoteTag, on)
    private fun flag(v: View, tag: Int) = v.getTag(tag) == true

    fun isSecureField(v: View): Boolean {
        if (v !is EditText) return false
        val t = v.inputType
        val variation = t and InputType.TYPE_MASK_VARIATION
        val cls = t and InputType.TYPE_MASK_CLASS
        if (cls == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) return true
        if (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val hints = v.autofillHints?.map { it.lowercase() } ?: emptyList()
            if (hints.any { it.contains("password") || it.contains("creditcard") || it.contains("otp") || it.contains("securitycode") }) return true
        }
        return false
    }

    /** true if the view or any ancestor is masked (or the point lies in a masked Compose area) */
    fun isMasked(v: View, contentPoint: Pair<Float, Float>? = null): Boolean {
        var cur: View? = v
        while (cur != null) { if (flag(cur, maskedTag) || isSecureField(cur)) return true; cur = cur.parent as? View }
        return contentPoint != null && LiveAssistRegistry.contains(contentPoint, LiveAssistRegistry.Kind.MASKED)
    }

    fun isNoRemote(v: View, contentPoint: Pair<Float, Float>? = null): Boolean {
        var cur: View? = v
        while (cur != null) { if (flag(cur, noRemoteTag)) return true; cur = cur.parent as? View }
        return contentPoint != null && LiveAssistRegistry.contains(contentPoint, LiveAssistRegistry.Kind.NO_REMOTE)
    }

    fun isMaskedSelf(v: View) = flag(v, maskedTag) || isSecureField(v)

    /** Safety net: buttons whose text suggests a commitment are never pressed by the advisor */
    private val commit = Regex("\\b(send|confirm|pay|submit|approve|save|delete|sign|transfer now|authori[sz]e)\\b", RegexOption.IGNORE_CASE)
    fun looksLikeCommit(text: CharSequence?): Boolean = text != null && commit.containsMatchIn(text)
    fun labelOf(v: View): CharSequence? = (v as? TextView)?.text?.takeIf { it.isNotBlank() } ?: v.contentDescription
}

/**
 * Areas registered by Compose modifiers, in WINDOW coordinates (converted to content coordinates when used).
 */
internal object LiveAssistRegistry {
    enum class Kind { MASKED, NO_REMOTE }
    private val rects = HashMap<Any, Pair<Rect, Kind>>()
    /** offset of the captured content view inside the window, set by the capture loop */
    @Volatile var contentOffsetX = 0; @Volatile var contentOffsetY = 0

    @Synchronized fun set(key: Any, rect: Rect, kind: Kind) { rects[key] = rect to kind }
    @Synchronized fun remove(key: Any) { rects.remove(key) }
    @Synchronized fun all(kind: Kind): List<Rect> = rects.values.filter { it.second == kind }.map {
        Rect(it.first).apply { offset(-contentOffsetX, -contentOffsetY) }
    }
    fun contains(p: Pair<Float, Float>, kind: Kind) = all(kind).any { it.contains(p.first.toInt(), p.second.toInt()) }
}

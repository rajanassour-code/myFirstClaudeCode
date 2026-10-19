package com.rajanassour.demobank

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import com.liveassist.sdk.LiveAssist

class DemoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LiveAssist.configure(this, BuildConfig.LIVE_ASSIST_SERVER)
    }
}

/** Cedar Bank demo: four tabs built with plain Android views, using the Live Assist SDK like a real bank app would. */
class MainActivity : Activity() {
    private val brand = Color.rgb(11, 92, 173)
    private lateinit var body: LinearLayout
    private lateinit var tabs: LinearLayout
    private var currentTab = "Home"

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(24)) }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Color.WHITE) }
        for (t in listOf("Home", "Transfer", "Cards", "Help")) {
            tabs.addView(TextView(this).apply {
                text = t; gravity = Gravity.CENTER; textSize = 13f; setPadding(0, dp(14), 0, dp(14)); tag = t
                setOnClickListener { show(t) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(tabs)
        setContentView(root)
        LiveAssist.onStateChange = { s -> if (s is LiveAssist.State.Ended) title = "Cedar Bank" }
        show("Home")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "📹 Advisor").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1) { startCall(currentTab); return true }
        return super.onOptionsItemSelected(item)
    }

    private fun startCall(topic: String) = LiveAssist.startCall(name = "Layla", topic = topic, reference = "ANDROID-APP-${(1000..9999).random()}")

    private fun show(tab: String) {
        currentTab = tab
        title = "Cedar Bank — $tab"
        for (i in 0 until tabs.childCount) (tabs.getChildAt(i) as TextView).apply {
            val on = tag == tab; setTextColor(if (on) brand else Color.GRAY); setTypeface(null, if (on) Typeface.BOLD else Typeface.NORMAL)
        }
        body.removeAllViews()
        when (tab) { "Home" -> home(); "Transfer" -> transfer(); "Cards" -> cards(); else -> help() }
    }

    // ---- building blocks ----
    private fun card(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16))
        background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(16).toFloat() }
        views.forEach { addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) }) }
    }.also { body.addView(it, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }) }

    private fun label(t: String, size: Float = 15f, bold: Boolean = false, color: Int = Color.rgb(17, 24, 39)) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color); if (bold) setTypeface(null, Typeface.BOLD)
    }
    private fun small(t: String) = label(t, 12f, color = Color.GRAY)
    private fun field(hint: String, type: Int = InputType.TYPE_CLASS_TEXT) = EditText(this).apply { this.hint = hint; inputType = type; isSingleLine = true }
    private fun button(t: String, action: () -> Unit) = Button(this).apply { text = t; isAllCaps = false; setOnClickListener { action() } }
    private fun row(left: View, right: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(left, LinearLayout.LayoutParams(0, -2, 1f)); addView(right)
    }

    // ---- screens ----
    private fun home() {
        body.addView(label("Good evening, Layla", 22f, bold = true), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val iban1 = small("LB62 0999 0000 0001 0019 0122 9114").also { LiveAssist.mask(it) }   // 🔒 never shown to the advisor
        val iban2 = small("LB62 0999 0000 0001 0019 0122 7730").also { LiveAssist.mask(it) }
        card(small("Your accounts"), row(label("Current account", bold = true), label("$4,812.50", bold = true)), iban1,
            row(label("Savings", bold = true), label("$18,240.00", bold = true)), iban2)
        card(small("Recent activity"), row(label("Salary — Acme Ltd"), label("+$3,200.00", color = Color.rgb(21, 128, 61))),
            row(label("Spinneys Supermarket"), label("−$86.40", color = Color.RED)), row(label("Electricity bill"), label("−$54.10", color = Color.RED)))
        card(button("📹 Talk to an advisor") { startCall("General") })
    }

    private fun transfer() {
        val from = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Current — $4,812.50", "Savings — $18,240.00")) }
        val beneficiary = field("Beneficiary name")
        val iban = field("Beneficiary IBAN")
        val amount = field("0.00", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val reference = field("Reference (optional)")
        val otp = field("6-digit SMS code", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)   // 🔒 automatic
        val done = label("", 14f, color = Color.rgb(21, 128, 61))
        val send = button("Send money") { done.text = "✅ Transfer submitted (demo — no money moved)"; otp.setText("") }
        LiveAssist.noRemote(send)                                                                     // only the customer can confirm
        card(small("From"), from, small("Beneficiary name"), beneficiary, small("Beneficiary IBAN"), iban,
            small("Amount (USD)"), amount, small("Reference"), reference, small("SMS code"), otp, send, done)
    }

    @Suppress("UseSwitchCompatOrMaterialCode")
    private fun cards() {
        val pan = label("4921 6612 0045 9182", 20f, color = Color.WHITE).also { LiveAssist.mask(it) }
        val expiry = label("09/29", 13f, color = Color.WHITE).also { LiveAssist.mask(it) }
        val face = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable().apply { setColor(Color.rgb(30, 41, 59)); cornerRadius = dp(18).toFloat() }
            addView(label("Cedar Visa · Debit", 13f, color = Color.WHITE))
            addView(pan, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(24); bottomMargin = dp(24) })
            addView(row(label("LAYLA NASSER", 13f, color = Color.WHITE), expiry))
        }
        body.addView(face, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        body.addView(label("CVV 731").also { LiveAssist.mask(it) }, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(12) })
        val freeze = Switch(this); val online = Switch(this).apply { isChecked = true }; val abroad = Switch(this)
        val limit = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("$500", "$1,000", "$2,000")); setSelection(1)
        }
        val pin = field("New 4-digit PIN", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        val save = button("Save settings") {}.also { LiveAssist.noRemote(it) }
        card(small("Card settings"), row(label("Freeze card"), freeze), row(label("Online payments"), online), row(label("Payments abroad"), abroad),
            small("Daily ATM limit"), limit, small("Change PIN"), pin, save)
    }

    private fun help() {
        val topic = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Cards", "Loans", "Transfers", "Other")) }
        val status = small("")
        LiveAssist.onStateChange = { s ->
            status.text = when (s) {
                is LiveAssist.State.Idle -> ""
                is LiveAssist.State.Connecting -> "Connecting…"
                is LiveAssist.State.Waiting -> "You're #${s.position} in line"
                is LiveAssist.State.InCall -> "In a call with ${s.advisor}"
                is LiveAssist.State.Ended -> "Call ended (${s.reason})"
            }
        }
        card(label("Need help?", 20f, bold = true),
            label("Talk face to face with an advisor. If you agree, they can see this app's screens to guide you, but they can't tap or type for you unless you allow it."),
            small("Topic"), topic, button("📹 Start video call") { startCall(topic.selectedItem.toString()) }, status)
    }
}

package com.nvlide

import android.annotation.SuppressLint
import android.app.Activity as AndroidActivity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.nvlide.backend.ShizukuAudio
import com.nvlide.ui.AppCardView
import com.nvlide.ui.dp

/** Setup checklist, pill position settings and a live app card (handy for checking players without the overlay). */
class MainActivity : AndroidActivity() {
    private class Step(val status: TextView, val button: Button)

    private val app get() = application as NvlideApp
    private val content by lazy { LinearLayout(this).apply { orientation = LinearLayout.VERTICAL } }
    private val card by lazy { AppCardView(this, app.controller) }
    private val cardTitle by lazy { text("Apps with audio open", 18f) }
    private lateinit var shizuku: Step
    private lateinit var permission: Step
    private lateinit var accessibility: Step
    private lateinit var battery: Step
    private val onChange = { card.update(app.controller.apps()) }
    private val onState = { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = dp(20)
        content.setPadding(pad, pad, pad, pad)
        setContentView(ScrollView(this).apply { addView(content) })

        content.addView(text("Setup", 22f))
        shizuku = step("Shizuku is running", "Open Shizuku") { openShizuku() }
        permission = step("nvlide may use Shizuku", "Allow") { app.backend.requestPermission() }
        accessibility = step("nvlide accessibility service is on", "Open settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        content.addView(text(
            "If the switch is greyed out (Android 13+ \"restricted setting\"): App info → ⋮ → Allow restricted settings, then try again.",
            13f,
        ))
        content.addView(Button(this).apply {
            text = "App info"
            setOnClickListener { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri())) }
        })
        battery = step("Battery optimisation is off for nvlide", "Turn off") { askBatteryExemption() }

        content.addView(text("Pill position", 22f).apply { setPadding(0, dp(24), 0, 0) })
        content.addView(text("The pill shows where it lands while you move these (needs the accessibility service on).", 13f))
        for (landscape in listOf(false, true)) offsetSliders(landscape)

        content.addView(cardTitle.apply { setPadding(0, dp(24), 0, dp(8)) })
        content.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    override fun onResume() {
        super.onResume()
        app.backend.listeners += onState
        app.controller.listeners += onChange
        refresh()
    }

    override fun onPause() {
        super.onPause()
        app.backend.listeners -= onState
        app.controller.listeners -= onChange
    }

    private fun refresh() {
        val state = app.backend.state
        shizuku.set(state != ShizukuAudio.State.NOT_RUNNING)
        permission.set(state == ShizukuAudio.State.CONNECTING || state == ShizukuAudio.State.READY)
        permission.button.isEnabled = state == ShizukuAudio.State.NO_PERMISSION
        accessibility.set(accessibilityEnabled())
        battery.set(getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName))
        val ready = state == ShizukuAudio.State.READY
        cardTitle.visibility = if (ready) View.VISIBLE else View.GONE
        card.visibility = if (ready) View.VISIBLE else View.GONE
        card.update(app.controller.apps())
    }

    private fun Step.set(done: Boolean) {
        status.text = if (done) "✓" else "•"
        button.visibility = if (done) View.GONE else View.VISIBLE
    }

    private fun step(title: String, action: String, onClick: () -> Unit): Step {
        val status = text("•", 18f).apply { minWidth = dp(28) }
        val button = Button(this).apply { text = action; setOnClickListener { onClick() } }
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(status)
            addView(text(title, 16f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(button)
        })
        return Step(status, button)
    }

    private fun offsetSliders(landscape: Boolean) {
        val name = if (landscape) "Landscape" else "Portrait"
        val prefs = app.overlayPrefs
        val label = text("", 14f)
        fun show() = prefs.offset(landscape).let { label.text = "$name: ${it.xDp} dp from the right, ${it.yDp} dp from the centre" }
        fun moved(offset: Offset) {
            prefs.setOffset(landscape, offset)
            show()
            app.overlay?.previewPosition()
        }
        show()
        content.addView(label)
        content.addView(seek(0, 400, prefs.offset(landscape).xDp) { moved(prefs.offset(landscape).copy(xDp = it)) })
        content.addView(seek(-500, 500, prefs.offset(landscape).yDp) { moved(prefs.offset(landscape).copy(yDp = it)) })
    }

    private fun seek(min: Int, max: Int, value: Int, onChange: (Int) -> Unit) = SeekBar(this).apply {
        this.min = min
        this.max = max
        progress = value
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, p: Int, fromUser: Boolean) { if (fromUser) onChange(p) }
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {}
        })
    }

    private fun text(value: String, sizeSp: Float) = TextView(this).apply {
        text = value
        textSize = sizeSp
        setPadding(0, dp(6), 0, dp(6))
    }

    private fun openShizuku() {
        val launch = packageManager.getLaunchIntentForPackage(SHIZUKU)
        startActivity(launch ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
    }

    @SuppressLint("BatteryLife")
    private fun askBatteryExemption() {
        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri()))
    }

    private fun accessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(this, VolumeKeyService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun packageUri() = Uri.parse("package:$packageName")

    private companion object {
        const val SHIZUKU = "moe.shizuku.privileged.api"
    }
}

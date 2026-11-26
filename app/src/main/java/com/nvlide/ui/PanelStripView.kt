package com.nvlide.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import com.nvlide.AppState
import com.nvlide.AppVolumeController
import com.nvlide.R
import com.nvlide.StreamVolumes
import com.nvlide.stockIconName

/**
 * A row of stock-style slider columns, scrolled sideways: the stock panel's stream rows first (same system
 * volumes), then one column per app, or a hint when nvlide can't work yet. As wide as the stock row it covers, so
 * as many columns show at once as the stock panel shows. Rebuilds only when the columns change, so a drag is never cut.
 */
@SuppressLint("ViewConstructor")
class PanelStripView(
    context: Context,
    private val controller: AppVolumeController,
    private val streams: StreamVolumes,
    /** System UI's resources, for its own stream icons; null if unavailable (icons stay empty). */
    private val systemUi: Resources?,
    private val explain: (String) -> Unit,
) : HorizontalScrollView(context) {
    private class Column(val view: View, val slider: AppSlider, val icon: ImageView, var iconName: String? = null)

    private val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val columns = mutableMapOf<String, Column>()
    private val hint = HintView(context)
    private var shown = emptyList<String>()

    init {
        isHorizontalScrollBarEnabled = false
        addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }

    /** Shows [streamList] then [apps]; with a [hintText] instead of apps, a hint button that runs [onHint]. */
    fun update(streamList: List<Int>, apps: List<AppState>, hintText: String? = null, onHint: () -> Unit = {}) {
        val appKeys = if (hintText != null) listOf(HINT) else apps.map { it.packageName }
        val keys = streamList.map(::streamKey) + appKeys
        if (keys != shown) rebuild(keys)
        for (s in streamList) {
            val c = columns.getValue(streamKey(s))
            c.slider.level = streams.level(s)
            setStockIcon(c, stockIconName(s, c.slider.level))
        }
        if (hintText != null) {
            hint.show(hintText)
            hint.setOnClickListener { onHint() }
            return
        }
        for (app in apps) {
            val c = columns.getValue(app.packageName)
            val label = label(app.packageName)
            c.slider.level = app.volume.level
            // A low-latency app's mute and level don't apply, so it shows neither (no red, no dimmed icon).
            c.slider.muted = app.volume.muted && !app.lowLatency
            c.icon.alpha = if (c.slider.muted) Style.MUTED_ICON_ALPHA else 1f
            // Struck through at 0 % or muted, like the stock stream icons.
            // Not for low-latency apps: their saved level doesn't apply, so they aren't actually silent.
            val silent = !app.lowLatency && (app.volume.muted || app.volume.level == 0)
            c.icon.foreground = if (silent) context.getDrawable(R.drawable.strike) else null
            if (app.lowLatency) {
                // Its sound skips the mixer, so the level would do nothing: grey it out and say why on a tap.
                c.view.alpha = Style.UNAVAILABLE_ALPHA
                c.slider.isEnabled = false
                val why = View.OnClickListener {
                    explain("$label uses low-latency audio, which nvlide can't change. Use its own volume setting.")
                }
                c.view.setOnClickListener(why)
                c.icon.setOnClickListener(why)
                c.slider.contentDescription = "$label volume, not available: low-latency audio"
                c.icon.contentDescription = null
            } else {
                c.view.alpha = 1f
                c.slider.isEnabled = true
                c.view.setOnClickListener(null)
                c.view.isClickable = false
                c.icon.setOnClickListener { controller.toggleMute(app.packageName) }
                c.slider.contentDescription = "$label volume"
                c.icon.contentDescription = if (app.volume.muted) "Unmute $label" else "Mute $label"
            }
        }
    }

    private fun rebuild(keys: List<String>) {
        row.removeAllViews()
        columns.keys.retainAll(keys.toSet())
        for (key in keys) {
            if (key == HINT) {
                row.addView(hint, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    marginStart = context.dp(8)
                })
                continue
            }
            val c = columns.getOrPut(key) { column(key) }
            row.addView(c.view, LinearLayout.LayoutParams(context.dp(Style.COLUMN_DP), LinearLayout.LayoutParams.MATCH_PARENT))
        }
        shown = keys
    }

    private fun column(key: String): Column {
        val stream = streamOf(key)
        lateinit var column: Column
        val slider = AppSlider(context) { level ->
            if (stream != null) {
                streams.setLevel(stream, level)
                setStockIcon(column, stockIconName(stream, level))
            } else {
                controller.setLevel(key, level)
            }
        }
        val icon = ImageView(context).apply {
            if (stream != null) {
                imageTintList = ColorStateList.valueOf(Style.ICON)
            } else {
                setImageDrawable(appIcon(key))
                colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
            }
        }
        // Same column as the stock panel's: the track fills the top, the icon sits centred in the strip below it.
        val view = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(slider, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            // Glyph size and position measured against the stock icons.
            addView(icon, LinearLayout.LayoutParams(context.dp(23), context.dp(23)).apply {
                topMargin = context.dp(12)
                bottomMargin = context.dp(7)
            })
        }
        column = Column(view, slider, icon)
        return column
    }

    /** Shows System UI's own drawable [name] (exactly the stock icon); left empty if this System UI lacks it. */
    @SuppressLint("DiscouragedApi")
    private fun setStockIcon(c: Column, name: String) {
        if (name == c.iconName) return
        c.iconName = name
        val res = systemUi ?: return
        val id = res.getIdentifier(name, "drawable", SYSTEM_UI)
        c.icon.setImageDrawable(if (id == 0) null else res.getDrawable(id, null))
    }

    private fun streamKey(stream: Int) = "$STREAM$stream"

    private fun streamOf(key: String) = if (key.startsWith(STREAM)) key.removePrefix(STREAM).toInt() else null

    private fun appIcon(pkg: String): Drawable = try {
        context.packageManager.getApplicationIcon(pkg)
    } catch (e: PackageManager.NameNotFoundException) {
        context.packageManager.defaultActivityIcon
    }

    private fun label(pkg: String) = try {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }

    private companion object {
        const val STREAM = "stream:"
        const val SYSTEM_UI = "com.android.systemui"
        const val HINT = "hint"
    }
}

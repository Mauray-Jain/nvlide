package com.nvlide.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import com.nvlide.Activity
import com.nvlide.AppState
import com.nvlide.AppVolumeController

/** The card with one slider per app. Rebuilds only when the app list changes, so a drag is never cut. */
@SuppressLint("ViewConstructor")
class AppCardView(context: Context, private val controller: AppVolumeController) : FrameLayout(context) {
    private class Column(val slider: AppSlider, val icon: ImageView)

    private val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val columns = mutableMapOf<String, Column>()
    private var shown = emptyList<String>()
    private var sliderHeight = context.dp(200)
    var maxWidth = Int.MAX_VALUE

    init {
        background = Style.surface(context, 28)
        val pad = context.dp(16)
        setPadding(pad, pad, pad, pad)
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        })
    }

    fun setSliderHeight(px: Int) {
        if (px == sliderHeight) return
        sliderHeight = px
        shown = emptyList()
        update(controller.apps())
    }

    fun update(apps: List<AppState>) {
        val packages = apps.map { it.packageName }
        if (packages != shown) rebuild(packages)
        for (app in apps) {
            val c = columns.getValue(app.packageName)
            c.slider.level = app.volume.level
            c.slider.muted = app.volume.muted
            c.slider.alpha = if (app.activity == Activity.PLAYING) 1f else Style.PAUSED_ALPHA
            c.icon.alpha = if (app.volume.muted) Style.MUTED_ICON_ALPHA else 1f
            val label = label(app.packageName)
            c.slider.contentDescription = "$label volume"
            c.icon.contentDescription = if (app.volume.muted) "Unmute $label" else "Mute $label"
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = MeasureSpec.getSize(widthMeasureSpec).coerceAtMost(maxWidth)
        super.onMeasure(MeasureSpec.makeMeasureSpec(size, MeasureSpec.AT_MOST), heightMeasureSpec)
    }

    private fun rebuild(packages: List<String>) {
        row.removeAllViews()
        columns.keys.retainAll(packages.toSet())
        for ((i, pkg) in packages.withIndex()) {
            val c = columns.getOrPut(pkg) { column(pkg) }
            (c.slider.parent as? LinearLayout)?.let { row.addView(it) }
            (row.getChildAt(i).layoutParams as LinearLayout.LayoutParams).marginStart = if (i == 0) 0 else context.dp(12)
            c.slider.layoutParams = LinearLayout.LayoutParams(context.dp(40), sliderHeight)
        }
        shown = packages
    }

    private fun column(pkg: String): Column {
        val slider = AppSlider(context) { controller.setLevel(pkg, it) }
        val icon = ImageView(context).apply {
            setImageDrawable(icon(pkg))
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
            setOnClickListener { controller.toggleMute(pkg) }
        }
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(slider, LinearLayout.LayoutParams(context.dp(40), sliderHeight))
            addView(icon, LinearLayout.LayoutParams(context.dp(28), context.dp(28)).apply { topMargin = context.dp(12) })
        }
        return Column(slider, icon)
    }

    private fun icon(pkg: String) = try {
        context.packageManager.getApplicationIcon(pkg)
    } catch (e: PackageManager.NameNotFoundException) {
        context.packageManager.defaultActivityIcon
    }

    private fun label(pkg: String) = try {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }
}

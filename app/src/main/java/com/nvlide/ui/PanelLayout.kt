package com.nvlide.ui

/** A rectangle in screen pixels; like android.graphics.Rect, but plain enough for unit tests. */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

/** The stock panel's row of stream sliders: where it is, and each slider's label, left to right. */
data class StockRow(val box: Box, val labels: List<String>)

/** Whether the stock panel is expanded (several stream sliders side by side) rather than showing one [column]. */
fun isExpanded(row: Box, column: Int) = row.width > column * 3 / 2

/** The stock row, or null while it slides in or out (it slides from the left screen edge). */
fun restingBody(row: Box) = row.takeIf { it.left > 0 }

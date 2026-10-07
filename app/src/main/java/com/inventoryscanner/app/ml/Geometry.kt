package com.inventoryscanner.app.ml

import android.graphics.RectF
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class Detection(val box: RectF, val conf: Float, val cls: Int) {
    val cx get() = (box.left + box.right) / 2f
    val cy get() = (box.top + box.bottom) / 2f
    val size get() = max(box.width(), box.height())
}

object Geometry {
    fun iou(a: RectF, b: RectF): Float {
        val iw = max(0f, min(a.right, b.right) - max(a.left, b.left))
        val ih = max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
        val inter = iw * ih
        val union = a.width() * a.height() + b.width() * b.height() - inter
        return if (union > 0f) inter / union else 0f
    }

    /** Two-pass merge used for tiled inference (port of utils/tiling.py). */
    fun mergeTileDetections(
        dets: List<Detection>,
        iouThr: Float = 0.35f,
        distRatio: Float = 0.6f,
        minSide: Float = 18f,
    ): List<Detection> {
        val sorted = dets.sortedByDescending { it.conf }
        val afterNms = ArrayList<Detection>()
        for (d in sorted) if (afterNms.none { iou(it.box, d.box) >= iouThr }) afterNms.add(d)

        val afterDist = ArrayList<Detection>()
        for (d in afterNms) {
            val dup = afterDist.any {
                hypot(d.cx - it.cx, d.cy - it.cy) < distRatio * max(d.size, it.size)
            }
            if (!dup) afterDist.add(d)
        }
        return afterDist.filter { it.size >= minSide }
    }
}

package com.inventoryscanner.app.ml

import android.graphics.RectF
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class Track(val id: Int, val box: RectF, val cls: Int, val conf: Float, val detected: Boolean) {
    val cx get() = (box.left + box.right) / 2f
    val cy get() = (box.top + box.bottom) / 2f
}

/** Port of utils/tracker.py: centroid tracker with EMA smoothing and gap bridging. */
class CentroidTracker(
    private val maxDistance: Float = 55f,
    private val maxMissed: Int = 15,
    private val sizeRatioTol: Float = 1.8f,
    private val alpha: Float = 0.55f,
) {
    private class State(var box: RectF, var cls: Int, var conf: Float, var missed: Int = 0) {
        val cx get() = (box.left + box.right) / 2f
        val cy get() = (box.top + box.bottom) / 2f
        val size get() = max(box.width(), box.height())
    }

    private var nextId = 0
    private val tracks = LinkedHashMap<Int, State>()

    fun reset() { tracks.clear(); nextId = 0 }

    fun update(dets: List<Detection>): List<Track> {
        val assigned = IntArray(dets.size) { -1 }
        val pairs = ArrayList<Triple<Float, Int, Int>>()
        for ((i, d) in dets.withIndex()) for ((id, t) in tracks) {
            val dist = hypot(d.cx - t.cx, d.cy - t.cy)
            val ratio = max(d.size, t.size) / max(1f, min(d.size, t.size))
            val limit = min(maxDistance, 0.9f * max(d.size, t.size))
            if (dist <= limit && ratio <= sizeRatioTol) pairs.add(Triple(dist, i, id))
        }
        pairs.sortBy { it.first }
        val usedDet = HashSet<Int>(); val usedTrack = HashSet<Int>()
        for ((_, i, id) in pairs) {
            if (i in usedDet || id in usedTrack) continue
            assigned[i] = id; usedDet.add(i); usedTrack.add(id)
        }

        for ((i, id) in assigned.withIndex()) {
            if (id < 0) continue
            val t = tracks.getValue(id); val d = dets[i]
            t.box = RectF(
                alpha * d.box.left + (1 - alpha) * t.box.left,
                alpha * d.box.top + (1 - alpha) * t.box.top,
                alpha * d.box.right + (1 - alpha) * t.box.right,
                alpha * d.box.bottom + (1 - alpha) * t.box.bottom,
            )
            t.cls = d.cls; t.conf = d.conf; t.missed = 0
        }
        for ((i, d) in dets.withIndex()) if (assigned[i] < 0) {
            val id = nextId++
            tracks[id] = State(RectF(d.box), d.cls, d.conf)
            assigned[i] = id
        }

        val matched = assigned.toSet()
        val it = tracks.entries.iterator()
        while (it.hasNext()) {
            val (id, t) = it.next()
            if (id !in matched) { t.missed++; if (t.missed > maxMissed) it.remove() }
        }
        return tracks.map { (id, t) -> Track(id, t.box, t.cls, t.conf, t.missed == 0) }
    }
}

/** Port of utils/crossing.py. */
object Crossing {
    fun side(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Int {
        val c = (x2 - x1) * (py - y1) - (y2 - y1) * (px - x1)
        return if (c > 0) 1 else if (c < 0) -1 else 0
    }
}

/** Counts each track once, the first time its centre switches side of a line (fractions of frame size). */
class LineCounter(classCount: Int) {
    val perClass = IntArray(classCount)
    val total get() = counted.size
    private val lastSide = HashMap<Int, Int>()
    private val counted = HashSet<Int>()

    fun reset() { perClass.fill(0); lastSide.clear(); counted.clear() }

    fun update(tracks: List<Track>, w: Float, h: Float, line: FloatArray) {
        val x1 = line[0] * w; val y1 = line[1] * h; val x2 = line[2] * w; val y2 = line[3] * h
        for (t in tracks) {
            val s = Crossing.side(t.cx, t.cy, x1, y1, x2, y2)
            if (s == 0) continue
            val prev = lastSide[t.id]
            if (prev != null && prev != s && counted.add(t.id)) perClass[t.cls]++
            lastSide[t.id] = s
        }
    }
}

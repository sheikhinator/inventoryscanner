package com.inventoryscanner.app.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * YOLO26 (NMS-free, end-to-end) ONNX detector. Expects an export whose output is [1, N, 6]
 * with rows (x1, y1, x2, y2, conf, class) in the letterboxed 640x640 input space.
 * See tools/export_model.py.
 */
class Detector(context: Context) : AutoCloseable {
    val labels: List<String> =
        context.assets.open("labels.txt").bufferedReader().readLines().filter { it.isNotBlank() }

    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputName: String
    private val input: FloatBuffer =
        ByteBuffer.allocateDirect(4 * 3 * SIZE * SIZE).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val canvasBmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
    private val pixels = IntArray(SIZE * SIZE)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    init {
        val bytes = context.assets.open("model.onnx").use { it.readBytes() }
        val opts = OrtSession.SessionOptions().apply { setIntraOpNumThreads(4) }
        session = env.createSession(bytes, opts)
        inputName = session.inputNames.first()
    }

    /** Single-pass detection on the whole bitmap (fast; used for the live belt mode). */
    @Synchronized
    fun detect(src: Bitmap, conf: Float): List<Detection> = infer(src, conf)

    /** Tiled detection for dense shelves/piles (port of utils/tiling.py). */
    @Synchronized
    fun detectTiled(src: Bitmap, conf: Float, tile: Int = 640, overlap: Float = 0.3f): List<Detection> {
        val w = src.width; val h = src.height
        if (w <= tile && h <= tile) return infer(src, conf)
        val step = max(1, (tile * (1 - overlap)).toInt())
        val all = ArrayList<Detection>()
        var y = 0
        while (y < h) {
            val y1 = min(y, max(0, h - tile)); val y2 = min(y1 + tile, h)
            var x = 0
            while (x < w) {
                val x1 = min(x, max(0, w - tile)); val x2 = min(x1 + tile, w)
                val crop = Bitmap.createBitmap(src, x1, y1, x2 - x1, y2 - y1)
                for (d in infer(crop, conf)) {
                    d.box.offset(x1.toFloat(), y1.toFloat()); all.add(d)
                }
                if (crop !== src) crop.recycle()
                if (x2 >= w) break
                x += step
            }
            if (y2 >= h) break
            y += step
        }
        return Geometry.mergeTileDetections(all)
    }

    private fun infer(src: Bitmap, conf: Float): List<Detection> {
        val scale = min(SIZE.toFloat() / src.width, SIZE.toFloat() / src.height)
        val nw = (src.width * scale).roundToInt(); val nh = (src.height * scale).roundToInt()
        val padX = (SIZE - nw) / 2f; val padY = (SIZE - nh) / 2f

        val c = Canvas(canvasBmp)
        c.drawColor(Color.rgb(114, 114, 114))
        c.drawBitmap(src, null, RectF(padX, padY, padX + nw, padY + nh), paint)
        canvasBmp.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)

        val plane = SIZE * SIZE
        input.rewind()
        for (i in 0 until plane) {
            val p = pixels[i]
            input.put(i, ((p shr 16) and 0xFF) / 255f)
            input.put(plane + i, ((p shr 8) and 0xFF) / 255f)
            input.put(2 * plane + i, (p and 0xFF) / 255f)
        }
        input.rewind()

        val out = ArrayList<Detection>()
        OnnxTensor.createTensor(env, input, longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())).use { t ->
            session.run(mapOf(inputName to t)).use { res ->
                @Suppress("UNCHECKED_CAST")
                val rows = (res[0].value as Array<Array<FloatArray>>)[0]
                for (r in rows) {
                    if (r[4] < conf) continue
                    val x1 = ((r[0] - padX) / scale).coerceIn(0f, src.width.toFloat())
                    val y1 = ((r[1] - padY) / scale).coerceIn(0f, src.height.toFloat())
                    val x2 = ((r[2] - padX) / scale).coerceIn(0f, src.width.toFloat())
                    val y2 = ((r[3] - padY) / scale).coerceIn(0f, src.height.toFloat())
                    if (x2 - x1 < 2f || y2 - y1 < 2f) continue
                    out.add(Detection(RectF(x1, y1, x2, y2), r[4], r[5].toInt().coerceIn(0, labels.size - 1)))
                }
            }
        }
        return out
    }

    override fun close() { session.close() }

    companion object { const val SIZE = 640 }
}

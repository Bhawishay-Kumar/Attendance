package com.example.ui.developer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val SOFT = 0.0016f          // soft leading edge of the reveal, in time-map units
private const val TRAIL = 0.030f         // "wet ink" shine trailing the pen, in time-map units (~190 ms)
private const val TRAIL_GAIN = 0.80f     // how white the freshest ink gets
private const val PULSE = 0.075f         // glow that blooms over a letter right after it is finished (~480 ms)
private const val PULSE_GAIN = 0.42f
private const val SHIMMER_MS = 1100      // one soft light sweep across the finished signature

/**
 * Pixel-exact traced signature (res/drawable-nodpi/signature_art.png) plus a 16-bit "pen time"
 * map (signature_time.png: R = high byte, G = low byte) that follows the real stroke order, and
 * a smoothed pen-tip path (res/raw/signature_pen.json). Rendering reveals each ink pixel once the
 * progress passes its time value, so strokes are drawn at pen speed instead of wiped in.
 * The time map is built letter by letter (B, h, a, w, i, s, h, y, a, K, u, m, a, r, then the underline
 * swash): each letter is completed before the next begins, with small pen-lift pauses between letters,
 * longer ones between the two words, and a fast decelerating flick for the underline.
 */
internal class SignatureRenderer private constructor(
    private val w: Int,
    private val h: Int,
    private val base: IntArray,
    private val inks: IntArray,
    private val time: FloatArray,
    private val letter: ByteArray,          // letter id per pixel (blue channel of the time map)
    private val letterEnd: FloatArray,      // time (0..1) at which each letter is finished
    private val pen: FloatArray,            // x, y, alpha triplets in artwork pixels
    val durationMs: Int,                    // total write time, from signature_timing.json
) {
    private val out = IntArray(w * h)
    val bitmap: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val image: ImageBitmap = bitmap.asImageBitmap()
    val aspect: Float get() = w.toFloat() / h

    private var tipX = -1f
    private var tipY = -1f
    private var tipA = 1f

    fun resetPen() { tipX = -1f; tipY = -1f; tipA = 1f }

    /**
     * Rebuilds the bitmap for progress [p] (0..1). Only ink pixels are visited.
     * Ink is revealed in pen order; the freshest ink (the stroke the pen just drew) is blended
     * towards white and cools down over [TRAIL]; a finished letter blooms once ([PULSE]).
     * [shimmer] (0..1) is a light sweep across the finished name; values outside (0,1) disable it.
     */
    fun render(p: Float, shimmer: Float = -1f) {
        java.util.Arrays.fill(out, 0)
        val sweep = shimmer > 0f && shimmer < 1f
        val band = w * 0.16f
        val centre = shimmer * (w + 2f * band) - band

        for (n in inks.indices) {
            val i = inks[n]
            val d = p - time[i]
            val k = (d / SOFT + 1f).coerceIn(0f, 1f)
            if (k <= 0f) continue

            val px = base[i]
            val a = ((px ushr 24) * k).toInt()
            var s = 0f
            if (d < TRAIL) { val f = 1f - (d.coerceAtLeast(0f) / TRAIL); s = TRAIL_GAIN * f * f }
            val q = p - letterEnd[letter[i].toInt() and 0xFF]
            if (q in 0f..PULSE) { val f = 1f - q / PULSE; s = maxOf(s, PULSE_GAIN * f * f * (3f - 2f * f)) }
            if (sweep) {
                val f = 1f - kotlin.math.abs((i % w) - centre) / band
                if (f > 0f) s = maxOf(s, 0.55f * f * f)
            }
            if (s > 0.004f) {
                val r = (px shr 16) and 0xFF; val g = (px shr 8) and 0xFF; val b = px and 0xFF
                out[i] = (a shl 24) or ((r + ((255 - r) * s).toInt()) shl 16) or
                    ((g + ((255 - g) * s).toInt()) shl 8) or (b + ((255 - b) * s).toInt())
            } else {
                out[i] = (a shl 24) or (px and 0xFFFFFF)
            }
        }
        bitmap.setPixels(out, 0, w, 0, 0, w, h)
    }

    /** Draws the glowing pen tip for progress [p]; fades in after jumps so it never teleports. */
    fun DrawScope.drawPenTip(p: Float) {
        if (p <= 0f || p >= 1f) return
        val s = size.width / w
        val idx = (p * (pen.size / 3 - 1)).roundToInt() * 3
        val x = pen[idx]; val y = pen[idx + 1]

        tipA = if (tipX >= 0f && hypot(x - tipX, y - tipY) > 46f) 0f else (tipA + 0.14f).coerceAtMost(1f)
        tipX = x; tipY = y

        val a = pen[idx + 2] * tipA
        if (a <= 0.02f) return

        val c = Offset(x * s, y * s); val r = 24f * s
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f * a), Color(0xFF78DCFF).copy(alpha = 0.45f * a), Color.Transparent), c, r),
            r, c,
        )
        drawCircle(Color.White.copy(alpha = 0.95f * a), 2.2f * s, c)
    }

    /** Four-point sparkle at the end of the underline, fading in over the last 5% of the write. */
    fun DrawScope.drawSparkle(p: Float) {
        if (p <= 0.95f) return
        val f = ((p - 0.95f) / 0.05f).coerceIn(0f, 1f)
        val s = size.width / w
        val c = Offset(1030f * s, 158f * s); val r = 18f * s; val len = 30f * f * s
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = f), Color(0xFFD88CFF).copy(alpha = 0.75f * f), Color.Transparent), c, r), r, c)
        val line = Color.White.copy(alpha = 0.85f * f)
        drawLine(line, Offset(c.x - len, c.y), Offset(c.x + len, c.y), strokeWidth = 1.3f * s)
        drawLine(line, Offset(c.x, c.y - len * 0.8f), Offset(c.x, c.y + len * 0.8f), strokeWidth = 1.3f * s)
    }

    companion object {
        /** Decodes the assets. Call off the main thread. */
        fun load(context: Context): SignatureRenderer {
            val opts = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val art = BitmapFactory.decodeResource(context.resources, R.drawable.signature_art, opts)
            val tm = BitmapFactory.decodeResource(context.resources, R.drawable.signature_time, opts)
            val w = art.width; val h = art.height

            val base = IntArray(w * h).also { art.getPixels(it, 0, w, 0, 0, w, h) }
            val tpx = IntArray(w * h).also { tm.getPixels(it, 0, w, 0, 0, w, h) }
            art.recycle(); tm.recycle()

            val time = FloatArray(w * h) { (((tpx[it] shr 16) and 0xFF) * 256 + ((tpx[it] shr 8) and 0xFF)) / 65535f }
            val letter = ByteArray(w * h) { (tpx[it] and 0xFF).toByte() }
            val inks = (0 until w * h).filter { (base[it] ushr 24) != 0 }.toIntArray()

            val arr = JSONArray(context.resources.openRawResource(R.raw.signature_pen).bufferedReader().use { it.readText() })
            val pen = FloatArray(arr.length() * 3)
            for (i in 0 until arr.length()) {
                val e = arr.getJSONArray(i)
                pen[i * 3] = e.getDouble(0).toFloat(); pen[i * 3 + 1] = e.getDouble(1).toFloat(); pen[i * 3 + 2] = e.getDouble(2).toFloat()
            }

            val timing = org.json.JSONObject(context.resources.openRawResource(R.raw.signature_timing).bufferedReader().use { it.readText() })
            val ends = timing.getJSONArray("letterEnd")
            val letterEnd = FloatArray(ends.length()) { ends.getDouble(it).toFloat() }

            return SignatureRenderer(w, h, base, inks, time, letter, letterEnd, pen, timing.getInt("durationMs"))
        }
    }
}

/**
 * Handwritten signature.
 * - Starts once per composition (screen visit), the first time [visible] is true (about half on screen).
 * - Double-tap cancels the running write, clears it and writes again from the start.
 * - A single tap does nothing. The finished signature stays visible.
 */
@Composable
fun AnimatedSignature(
    visible: Boolean = true,
    modifier: Modifier = Modifier,
    isVisible: Boolean = visible,
) {
    val actualVisible = if (isVisible != visible) isVisible else visible
    val context = LocalContext.current
    val renderer by produceState<SignatureRenderer?>(null) {
        value = withContext(Dispatchers.Default) { SignatureRenderer.load(context.applicationContext) }
    }
    val visibleNow = rememberUpdatedState(actualVisible)
    val rendererNow = rememberUpdatedState(renderer)

    // Linear 0..1 clock. All human pacing (slow starts, pauses between letters, the fast final swash)
    // is baked into the pen-time map, so the clock itself must stay linear.
    val t = remember { Animatable(0f) }
    val shimmer = remember { Animatable(0f) }         // one-off light sweep after the write finishes
    var replayToken by remember { mutableIntStateOf(0) }

    // Keyed on replayToken: a replay relaunches this effect, which cancels the previous run.
    LaunchedEffect(replayToken) {
        snapshotFlow { rendererNow.value != null }.first { it }
        if (replayToken == 0) snapshotFlow { visibleNow.value }.first { it }   // wait for first visibility
        val rr = rendererNow.value ?: return@LaunchedEffect
        rr.resetPen()
        shimmer.snapTo(0f)
        t.snapTo(0f)
        t.animateTo(1f, tween(rr.durationMs, easing = LinearEasing))
        delay(120)
        shimmer.animateTo(1f, tween(SHIMMER_MS, easing = FastOutSlowInEasing))
    }

    val r = renderer
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(r?.aspect ?: (1070f / 260f))
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { replayToken++ }) },
    ) {
        if (r == null) return@Canvas
        val p = t.value                               // read in draw scope: redraws only, no recomposition
        r.render(p, shimmer.value)
        drawImage(
            image = r.image,
            srcOffset = IntOffset.Zero, srcSize = IntSize(r.bitmap.width, r.bitmap.height),
            dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.High,
        )
        with(r) { drawPenTip(p); drawSparkle(p) }
    }
}

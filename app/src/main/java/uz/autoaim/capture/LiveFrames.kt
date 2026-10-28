package uz.autoaim.capture

import android.os.SystemClock
import uz.autoaim.vision.Image
import uz.autoaim.vision.PixelSource
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Jonli ekran kadrlari ([CaptureService] dan, MediaProjection). Ikki xil foydalanish:
 *  - **analizatorlar** — har kadrda capture oqimida, nusxasiz, to'g'ridan-to'g'ri buferdan (tez kuzatish:
 *    quti qatori, uchayotgan sharcha). Tez bo'lishi shart — kadr ular tugaguncha yopilmaydi;
 *  - **snapshot** — keyingi kadrning to'liq nusxasi ([Image]) to'liq tahlil uchun.
 *
 * Vaqt — `SystemClock.uptimeMillis()` bilan bir xil soat (kadr vaqt belgisi CLOCK_MONOTONIC).
 */
object LiveFrames {

    fun interface Analyzer {
        fun onFrame(frame: PixelSource, uptimeMs: Long)
    }

    @Volatile
    var running = false
        internal set

    @Volatile
    var frames = 0L
        private set

    @Volatile
    var lastFrameUptime = 0L
        private set

    private val analyzers = CopyOnWriteArrayList<Analyzer>()
    private val lock = Any()
    private var pending: Request? = null

    private class Request {
        val latch = CountDownLatch(1)
        var image: Image? = null
        var uptime = 0L
    }

    fun addAnalyzer(a: Analyzer) {
        analyzers += a
    }

    fun removeAnalyzer(a: Analyzer) {
        analyzers -= a
    }

    /**
     * Keyingi kadrning to'liq nusxasi. Ekran o'zgarmasa (kadr kelmasa) [timeoutMs] dan keyin null —
     * chaqiruvchi screencap'ga qaytadi.
     */
    fun snapshot(timeoutMs: Long): Pair<Image, Long>? {
        if (!running) return null
        val r = Request()
        synchronized(lock) { pending = r }
        val ok = r.latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        synchronized(lock) { if (pending === r) pending = null }
        val image = r.image
        return if (ok && image != null) image to r.uptime else null
    }

    /** Capture oqimida chaqiriladi (RGBA_8888 kadr). */
    internal fun onImage(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int, timestampNs: Long) {
        val uptime = timestampNs / 1_000_000
        frames++
        lastFrameUptime = uptime
        val view = BufferView(buffer, width, height, rowStride, pixelStride)
        for (a in analyzers) runCatching { a.onFrame(view, uptime) }

        val r = synchronized(lock) { pending?.also { pending = null } } ?: return
        r.image = view.toImage()
        r.uptime = uptime
        r.latch.countDown()
    }

    internal fun reset() {
        running = false
        frames = 0
        synchronized(lock) {
            pending?.latch?.countDown()
            pending = null
        }
    }

    /** Taxminiy kadr tezligi uchun: oxirgi kadrdan beri o'tgan vaqt. */
    fun sinceLastFrameMs() = SystemClock.uptimeMillis() - lastFrameUptime

    /** RGBA bufer ustidan [PixelSource] (nusxasiz). */
    private class BufferView(
        private val buffer: ByteBuffer,
        override val width: Int,
        override val height: Int,
        private val rowStride: Int,
        private val pixelStride: Int,
    ) : PixelSource {
        override fun argb(x: Int, y: Int): Int {
            val i = y * rowStride + x * pixelStride
            val r = buffer.get(i).toInt() and 0xFF
            val g = buffer.get(i + 1).toInt() and 0xFF
            val b = buffer.get(i + 2).toInt() and 0xFF
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        fun toImage(): Image {
            val px = IntArray(width * height)
            val row = ByteArray(rowStride)
            for (y in 0 until height) {
                buffer.position(y * rowStride)
                buffer.get(row, 0, minOf(rowStride, buffer.remaining()))
                var i = 0
                val base = y * width
                for (x in 0 until width) {
                    px[base + x] = (0xFF shl 24) or ((row[i].toInt() and 0xFF) shl 16) or
                        ((row[i + 1].toInt() and 0xFF) shl 8) or (row[i + 2].toInt() and 0xFF)
                    i += pixelStride
                }
            }
            buffer.rewind()
            return Image(width, height, px)
        }
    }
}

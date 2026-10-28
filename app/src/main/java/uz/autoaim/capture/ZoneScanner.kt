package uz.autoaim.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import uz.autoaim.IAutoAimService
import uz.autoaim.vision.GameZone
import uz.autoaim.vision.GameZoneDetector
import uz.autoaim.aim.GeometryGate
import uz.autoaim.aim.ShotCheck
import uz.autoaim.aim.Verdict
import uz.autoaim.vision.DotDetector
import uz.autoaim.vision.Image
import uz.autoaim.vision.Trajectory
import uz.autoaim.vision.TrajectoryFitter
import java.util.Locale

/** Bitta skanerlash natijasi: topilgan zona (yoki null), asl ekran rasmi va belgilangan nusxasi. */
class ZoneScanResult(
    val zone: GameZone?,
    /** O'yin chizgan nuqtali yoy (bo'lsa) — tiklangan parabola. */
    val arc: Trajectory?,
    val screenWidth: Int,
    val screenHeight: Int,
    /** Asl ekran rasmi (chizilmagan) — offline test uchun. */
    val screenshot: Bitmap,
    val captureMs: Long,
    val totalMs: Long,
    /** Rasm ostidagi qatorga qo'shimcha matn (masalan, mo'ljal olish hisoboti). */
    val extraLines: List<String> = emptyList(),
    /** Barmoq: bosilgan nuqta va hozirgi joyi (x0, y0, x, y) — rasmga chiziladi. */
    val touch: FloatArray? = null,
) {
    fun withExtras(lines: List<String>, touch: FloatArray?) =
        ZoneScanResult(zone, arc, screenWidth, screenHeight, screenshot, captureMs, totalMs, lines, touch)

    /** Belgilangan nusxa: ramkalar + nomlar + pastda koordinatalar qatori (ko'rsatish va saqlash uchun). */
    val preview: Bitmap by lazy { ZoneScanner.annotate(this) }

    /** Qisqa matn (panel va asosiy ekran uchun). */
    fun summary(): String {
        val zone = zone ?: return "❌ O'yin zonasi topilmadi (ekran $screenWidth×$screenHeight).\n" +
            "O'yin ochiq va sharcha ko'rinib turganiga ishonch hosil qiling."
        val ball = zone.ball
        return buildString {
            append(if (zone.confidence >= 1f) "✅ " else "⚠️ ")
            append("Zona: ${zone.left}, ${zone.top} – ${zone.right}, ${zone.bottom}  (${zone.width}×${zone.height})\n")
            append("Ishonch: ${(zone.confidence * 100).toInt()}%\n")
            append("Sharcha: ${ball?.let { "markaz (%.1f, %.1f), r %.1f".format(Locale.ROOT, it.centerX, it.centerY, it.radius) } ?: "topilmadi"}\n")
            append("Quti: ${zone.box?.let { "(${it.left}, ${it.top}) – (${it.right}, ${it.bottom})" } ?: "topilmadi"}\n")
            append("Slot: ${zone.slot?.let { "x ${it.left}–${it.right}, y ${it.top}–${it.bottom}  (${it.width}×${it.height})" } ?: "topilmadi"}")
            append("\n").append(arcSummary(zone))
        }
    }

    private fun arcSummary(zone: GameZone): String {
        val arc = arc ?: return "Yoy: ko'rinmayapti"
        val head = "Yoy: ${arc.dots.size} nuqta, qaytish ${arc.bounces}, xato %.1f px".format(Locale.ROOT, arc.rms)
        val verdict = verdict(zone, arc) ?: return head
        return "$head\n$verdict"
    }

    /** Sharchaning butun yo'li (radiusi bilan) bo'yicha natija — [GeometryGate]. */
    val check: ShotCheck? by lazy {
        val zone = zone ?: return@lazy null
        val arc = arc ?: return@lazy null
        val ball = zone.ball ?: return@lazy null
        val box = zone.box ?: return@lazy null
        val slot = zone.slot ?: return@lazy null
        GeometryGate.check(arc, ball, box, slot, zone.bottom.toFloat())
    }

    fun verdict(zone: GameZone, arc: Trajectory): String? {
        val check = check ?: return null
        val ball = zone.ball ?: return null
        val slot = zone.slot ?: return null
        val mark = if (check.verdict.success) "✅" else if (check.verdict == Verdict.RIM) "⚠️" else "❌"
        val where = "(%.0f, %.0f)".format(Locale.ROOT, check.x, check.y)
        val offset = check.slotOffset?.let {
            ", markazdan %+.1f px (ruxsat ±%.0f)".format(Locale.ROOT, it, (slot.width - 2 * ball.radius) / 2)
        } ?: ""
        return "$mark ${check.verdict.text}: $where$offset"
    }
}

object ZoneScanner {

    /** Ekranni Shizuku orqali oladi va o'yin zonasini qidiradi. Bloklovchi — fon oqimida chaqiring. */
    fun scan(service: IAutoAimService, annotate: Boolean = true): ZoneScanResult {
        val start = SystemClock.uptimeMillis()
        // Jonli ekran yoqilgan bo'lsa — keyingi kadr (tez); ekran o'zgarmasa yoki o'chiq bo'lsa — screencap.
        val live = LiveFrames.snapshot(LIVE_WAIT_MS)
        val image: Image
        val bitmap: Bitmap
        if (live != null) {
            image = live.first
            bitmap = Bitmap.createBitmap(image.pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
        } else {
            bitmap = ParcelFileDescriptor.AutoCloseInputStream(service.screencap()).use {
                BitmapFactory.decodeStream(it)
            } ?: error("ekran rasmi o'qilmadi")
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            image = Image(bitmap.width, bitmap.height, pixels)
        }
        val captureMs = SystemClock.uptimeMillis() - start
        val zone = GameZoneDetector().detect(image)
        val arc = zone?.ball?.let { ball ->
            TrajectoryFitter.fit(DotDetector().detect(image, zone), ball, zone.left + ball.radius, zone.right - ball.radius)
        }
        val totalMs = SystemClock.uptimeMillis() - start

        return ZoneScanResult(zone, arc, bitmap.width, bitmap.height, bitmap, captureMs, totalMs)
            .also { if (annotate) it.preview } // belgilangan nusxa shu fon oqimida tayyorlanadi
    }

    /** Asl rasm ustiga belgilarni chizadi va pastiga koordinatalar qatorini qo'shadi. */
    fun annotate(result: ZoneScanResult): Bitmap {
        val source = result.screenshot
        val w = source.width
        val lines = result.summary().lines() + "rasm ${result.captureMs} ms, jami ${result.totalMs} ms" + result.extraLines
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = w / 34f
            typeface = Typeface.MONOSPACE
        }
        val lineHeight = text.textSize * 1.35f
        val padding = text.textSize
        val stripHeight = (lines.size * lineHeight + padding * 1.5f).toInt()

        val out = Bitmap.createBitmap(w, source.height + stripHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(source, 0f, 0f, null)
        result.zone?.let {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = w / 150f
            }
            drawZone(canvas, it, paint, dx = 0f, dy = 0f)
            result.arc?.let { arc -> drawArc(canvas, result, it, arc, paint.strokeWidth) }
        }
        result.touch?.let { drawTouch(canvas, it, w / 150f) }

        canvas.drawRect(0f, source.height.toFloat(), w.toFloat(), out.height.toFloat(), Paint().apply { color = STRIP_COLOR })
        var y = source.height + padding + text.textSize * 0.8f
        for (line in lines) {
            canvas.drawText(line, padding, y, text)
            y += lineHeight
        }
        return out
    }

    /** Umumiy chizish: rasm ustida ham, o'yin ustidagi overlay'da ham ishlatiladi. */
    fun drawZone(canvas: Canvas, zone: GameZone, paint: Paint, dx: Float, dy: Float) {
        val stroke = paint.strokeWidth
        paint.color = ZONE_COLOR
        canvas.drawRect(zone.left + dx, zone.top + dy, zone.right + dx, zone.bottom + dy, paint)
        label(canvas, "ZONA", zone.left + dx + stroke * 2, zone.bottom + dy - stroke * 2, ZONE_COLOR, stroke, above = true)

        zone.ball?.let {
            // Aylana sharchaning chetidan o'tadi (chiziq tashqariga chiziladi, sharchani to'smaydi) + markaz nuqtasi.
            paint.color = BALL_COLOR
            canvas.drawCircle(it.centerX + dx, it.centerY + dy, it.radius + stroke / 2, paint)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(it.centerX + dx, it.centerY + dy, stroke * 0.6f, paint)
            paint.style = Paint.Style.STROKE
            label(canvas, "SHARCHA", it.centerX + dx - it.radius, it.centerY + dy - it.radius - stroke * 2, BALL_COLOR, stroke, above = true)
        }
        zone.box?.let {
            paint.color = BOX_COLOR
            canvas.drawRect(it.left + dx, it.top + dy, it.right + dx, it.bottom + dy, paint)
            label(canvas, "QUTI", it.left + dx, it.bottom + dy + stroke * 2, BOX_COLOR, stroke, above = false)
        }
        zone.slot?.let {
            // Slot — nishon: ingichka chiziq bilan, ostidagi tirqish ko'rinib tursin; markazdan yuqoriga strelka.
            paint.strokeWidth = stroke / 2
            paint.color = SLOT_COLOR
            canvas.drawRect(it.left + dx, it.top + dy, it.right + dx, it.bottom + dy, paint)
            canvas.drawLine(it.centerX + dx, it.top + dy - stroke * 8, it.centerX + dx, it.top + dy - stroke, paint)
            paint.strokeWidth = stroke
            label(canvas, "SLOT", it.centerX + dx + stroke, it.top + dy - stroke * 8, SLOT_COLOR, stroke, above = true)
        }
    }

    /**
     * Yoy: topilgan nuqtalar halqa bilan, tiklangan parabola (davomi bilan) va natija lahzasidagi
     * sharcha + X belgisi + natija nomi (TUSHADI, CHAP YONGA URILADI, ...).
     */
    private fun drawArc(canvas: Canvas, result: ZoneScanResult, zone: GameZone, arc: Trajectory, stroke: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke / 2.5f
            color = ARC_COLOR
        }
        for (dot in arc.dots) canvas.drawCircle(dot.x, dot.y, stroke * 1.6f, paint)

        // Parabola: birinchi nuqtadan sharcha urilgan/tushgan lahzagacha (GeometryGate).
        val check = result.check
        val tEnd = check?.t ?: arc.descendingTimeAt(zone.bottom.toFloat()) ?: (arc.times.last() + 5f)
        val path = Path()
        var t = arc.times.first() - 1f
        path.moveTo(arc.xAt(t), arc.yAt(t))
        while (t < tEnd) {
            t = minOf(t + 0.1f, tEnd)
            val x = arc.xAt(t)
            val prev = arc.xAt(t - 0.1f)
            if (kotlin.math.abs(x - prev) > zone.width / 4f) path.moveTo(x, arc.yAt(t)) else path.lineTo(x, arc.yAt(t))
        }
        paint.pathEffect = DashPathEffect(floatArrayOf(stroke * 2, stroke * 1.5f), 0f)
        canvas.drawPath(path, paint)
        paint.pathEffect = null

        check ?: return
        // Natija lahzasidagi sharcha (radiusi bilan) va X belgisi.
        val x = check.x
        val y = check.y
        val color = if (check.verdict.success) ARC_COLOR else MISS_COLOR
        paint.color = color
        paint.strokeWidth = stroke / 2
        zone.ball?.let { canvas.drawCircle(x, y, it.radius, paint) }
        val r = stroke * 2.2f
        paint.strokeWidth = stroke / 1.5f
        canvas.drawLine(x - r, y - r, x + r, y + r, paint)
        canvas.drawLine(x - r, y + r, x + r, y - r, paint)
        label(canvas, check.verdict.text, x + r * 1.5f, y - r * 3, color, stroke, above = true)
    }

    /** Barmoq: bosilgan nuqta (halqa), hozirgi joyi (to'la doira) va ular orasidagi chiziq. */
    private fun drawTouch(canvas: Canvas, touch: FloatArray, stroke: Float) {
        val (x0, y0, x, y) = touch.toList()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TOUCH_COLOR
            style = Paint.Style.STROKE
            strokeWidth = stroke / 1.5f
        }
        canvas.drawCircle(x0, y0, stroke * 3, paint)
        canvas.drawLine(x0, y0, x, y, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(x, y, stroke * 2.2f, paint)
        label(canvas, "BARMOQ", x + stroke * 3, y + stroke * 2, TOUCH_COLOR, stroke, above = false)
    }

    /** Rangli fonli kichik yozuv; [above] = true bo'lsa matn (x, y) dan yuqoriga yoziladi. */
    private fun label(canvas: Canvas, text: String, x: Float, y: Float, color: Int, stroke: Float, above: Boolean) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = stroke * 3.6f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val pad = stroke * 0.6f
        val width = paint.measureText(text)
        val top = if (above) y - paint.textSize - pad * 2 else y
        paint.color = color
        canvas.drawRoundRect(x, top, x + width + pad * 2, top + paint.textSize + pad * 2, pad, pad, paint)
        paint.color = Color.BLACK
        canvas.drawText(text, x + pad, top + pad + paint.textSize * 0.82f, paint)
    }

    /** Jonli kadr kutish: ekran o'zgarmasa kadr kelmaydi — keyin screencap. */
    private const val LIVE_WAIT_MS = 250L

    private val ZONE_COLOR = Color.rgb(125, 211, 181)
    private val BALL_COLOR = Color.rgb(255, 80, 200)
    private val BOX_COLOR = Color.rgb(255, 200, 60)
    private val SLOT_COLOR = Color.rgb(255, 60, 60)
    private val STRIP_COLOR = Color.rgb(24, 24, 28)
    private val ARC_COLOR = Color.rgb(80, 200, 255)
    private val MISS_COLOR = Color.rgb(255, 120, 60)
    private val TOUCH_COLOR = Color.rgb(255, 255, 120)
}

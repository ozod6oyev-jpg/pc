package uz.autoaim.aim

import android.content.Context
import android.os.SystemClock
import uz.autoaim.IAutoAimService
import uz.autoaim.capture.ZoneScanResult
import uz.autoaim.capture.ZoneScanner
import uz.autoaim.core.MediaSaver
import uz.autoaim.shizuku.InputZone
import uz.autoaim.track.LeadPlanner
import uz.autoaim.vision.Blob
import uz.autoaim.vision.Ball
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max

/**
 * Otmasdan mo'ljal olish (REJA 3.3 + foydalanuvchi talabi):
 *  1. kadr → sharcha, quti, slot va **tinch holatdagi yoy** (tegish o'yinga yetganini bilish uchun);
 *  2. sharchadan tepada (gorizontal chiziq ustida) barmoq bosiladi;
 *  3. [AimController] buyrug'i bilan barmoq suriladi, har safar kadr olinib yoy o'qiladi;
 *  4. mo'ljal tayyor bo'lgach — **bekor qilishdan oldin chizmalar bilan skrinshot** (Galereyaga);
 *  5. barmoq aynan bosilgan nuqtaga qaytariladi va qo'yib yuboriladi (kuch ≈ 0 → otilmaydi);
 *  6. sharcha joyida qolganini tekshiradi. Hamma qadam CSV jurnalga yoziladi (`Download/AutoAim/`).
 *
 * Tegish ikki usulda yuboriladi: avval inject (tez), o'yin javob bermasa (yoy va sharcha o'zgarmasa) —
 * tizimning `input touchscreen motionevent` buyrug'i (sekinroq, lekin oddiy barmoq kabi).
 *
 * Bloklovchi — fon oqimida chaqiring. [progress] — panelga qisqa holat matni.
 */
class AimRunner(
    private val context: Context,
    private val service: IAutoAimService,
    private val progress: (String) -> Unit,
    /** true — mo'ljal tayyor bo'lsa barmoq o'sha joyda qo'yib yuboriladi (haqiqiy otish); false — bekor qilinadi. */
    private val fire: Boolean = false,
    /** Foydalanuvchi to'xtatishni so'radimi — har qadamda tekshiriladi (otish o'rniga bekor qilinadi). */
    private val stop: () -> Boolean = { false },
    /** Harakatlanuvchi quti: mo'ljal shu (virtual) quti/slotga olinadi — kadrda aniqlangan qutiga emas. */
    private val fixedTarget: Pair<Blob, Blob>? = null,
    /** Harakatlanuvchi quti: mo'ljal tayyor bo'lgach barmoq ushlab turiladi va shu darvoza ruxsat berganda otiladi. */
    private val releaseGate: ReleaseGate? = null,
) {
    /** Qo'yib yuborish lahzasini kutadi; true — hozir ot, false — kutish tugadi (bekor qilinadi). */
    fun interface ReleaseGate {
        fun await(flightSteps: Double, stop: () -> Boolean): Boolean
    }

    class Report(
        val text: String,
        val imagePath: String?,
        val csvPath: String?,
        val ready: Boolean,
        /** Haqiqatan otildi. */
        val fired: Boolean = false,
        /** Barmoq qo'yib yuborilgan lahza (SystemClock.uptimeMillis). */
        val releaseUptime: Long = 0L,
        /** Otishdagi bashorat: tezlik, natija. */
        val aim: AimAction.Ready? = null,
        /** Otish lahzasidagi sharcha (tortilgan holatda) va nishon. */
        val releaseFrame: ZoneScanResult? = null,
        /** Sharcha slot balandligigacha necha nuqta-qadam uchadi (bashorat). */
        val flightSteps: Double? = null,
    )

    /** Tegish yuborish usuli. */
    private interface Touch {
        val name: String
        /** Barmoqni surishda bir qadam, px (sekin usulda kattaroq). */
        val moveStepPx: Float
        fun down(x: Float, y: Float): Boolean
        fun move(x: Float, y: Float)
        fun up(x: Float, y: Float)
    }

    private inner class InjectTouch : Touch {
        override val name = "inject"
        override val moveStepPx = 12f
        private var downTime = -1L
        override fun down(x: Float, y: Float): Boolean {
            downTime = service.touchDown(x, y)
            return downTime >= 0
        }
        override fun move(x: Float, y: Float) {
            service.touchMove(downTime, x, y)
            SystemClock.sleep(8)
        }
        override fun up(x: Float, y: Float) {
            service.touchUp(downTime, x, y)
        }
    }

    private inner class ShellTouch : Touch {
        override val name = "input motionevent"
        override val moveStepPx = 60f
        private fun send(action: String, x: Float, y: Float): Boolean =
            service.exec("input touchscreen motionevent $action ${x.toInt()} ${y.toInt()}").startsWith("exit=0")
        override fun down(x: Float, y: Float) = send("DOWN", x, y)
        override fun move(x: Float, y: Float) {
            send("MOVE", x, y)
        }
        override fun up(x: Float, y: Float) {
            send("UP", x, y)
        }
    }

    /** Bitta urinish natijasi; [ignored] — o'yin bu usuldagi tegishga umuman javob bermadi. */
    private class Attempt(val report: Report?, val ignored: Boolean)

    private val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date())
    private val csv = StringBuilder(CSV_HEADER).append('\n')

    fun run(): Report {
        progress("⏳ Kadr olinmoqda…")
        val first = ZoneScanner.scan(service, annotate = false)
        val zone = first.zone ?: return Report("❌ O'yin zonasi topilmadi", null, null, false)
        val ball = zone.ball ?: return Report("❌ Sharcha topilmadi", null, null, false)
        if (zone.box == null || zone.slot == null) return Report("❌ Quti yoki slot topilmadi", null, null, false)
        // Tegilmagan holatdagi yoy (o'yin uni har doim chizib turadi) — tegish yetganini shu bilan solishtiramiz.
        val rest = first.arc?.takeIf { AimController.isReliable(it) }?.let { AimController.launchVelocity(it, ball) }

        val notes = mutableListOf<String>()
        for (touch in listOf(InjectTouch(), ShellTouch())) {
            val attempt = attempt(touch, first, ball, rest)
            if (!attempt.ignored) return attempt.report!!
            notes += "${touch.name}: o'yin javob bermadi (yoy va sharcha o'zgarmadi)"
        }
        val csvPath = runCatching { MediaSaver.saveText(context, csv.toString(), "AutoAim_mojal_$stamp.csv") }.getOrNull()
        val text = buildString {
            appendLine("❌ Tegishlar o'yinga yetmayapti")
            notes.forEach { appendLine(it) }
            csvPath?.let { append("📄 $it") }
        }
        return Report(text.trim(), null, csvPath, false)
    }

    private fun attempt(touch: Touch, first: ZoneScanResult, ball: Ball, rest: Velocity?): Attempt {
        val zone = first.zone!!
        // Ruxsat etilgan soha (foydalanuvchi ko'rsatgan): zona ichida, sharchaning pastki chetidan tepada —
        // istalgan joyidan bosib mo'ljal olish mumkin. Bosish nuqtasi — sohaning o'rtasi: har tomonga
        // tortishga joy ko'p (rogatka: kuchli otish uchun uzoq tortish kerak).
        val line = InputZone.fromBall(ball.centerY, ball.radius, LINE_PADDING_PX)
        val left = zone.left + EDGE_PX
        val right = zone.right - EDGE_PX
        val top = zone.top + zone.height * REGION_TOP
        val bottom = line.lineY - 1f
        val x0 = (left + right) / 2
        val y0 = (top + bottom) / 2
        fun finger(d: Drag): Pair<Float, Float> = Pair(
            (x0 + d.dx).coerceIn(left, right),
            (y0 + d.dy).coerceIn(top, bottom),
        )
        val maxDrag = minOf(right - x0, bottom - y0) * 0.95f

        val targetSlot = fixedTarget?.second ?: zone.slot!!
        val side = if (targetSlot.centerX >= ball.centerX) 1f else -1f
        val controller = AimController(rest = rest, restBall = ball, targetSide = side, maxDrag = maxDrag)
        var action = controller.start()
        var fx = x0
        var fy = y0
        var lastFrame: ZoneScanResult = first
        var steps = 0
        val log = mutableListOf<String>()
        progress("👆 ${touch.name}: barmoq bosilmoqda…")
        if (!touch.down(x0, y0)) return Attempt(null, ignored = true)

        var imagePath: String? = null
        var ready: AimAction.Ready? = null
        var failure: AimAction.Fail? = null
        var ignored = false
        var completed = false
        var fired = false
        var releaseUptime = 0L
        var flightSteps: Double? = null
        try {
            while (steps < MAX_STEPS) {
                val a = action
                if (a !is AimAction.Move) break
                if (stop()) {
                    action = AimAction.Fail("foydalanuvchi to'xtatdi", null)
                    break
                }
                steps++
                val (tx, ty) = finger(a.drag)
                moveSmooth(touch, fx, fy, tx, ty)
                fx = tx
                fy = ty
                SystemClock.sleep(SETTLE_MS)
                progress("⏳ ${touch.name} $steps: ${a.why}")
                lastFrame = ZoneScanner.scan(service, annotate = false)
                val obs = observation(lastFrame, Drag(fx - x0, fy - y0))
                if (obs == null) {
                    log += "$steps: kadrda quti/sharcha yo'q"
                    action = AimAction.Fail("kadrda quti yoki sharcha yo'qoldi", null)
                    break
                }
                action = controller.next(obs)
                logStep(touch, steps, a.why, obs, lastFrame)
                log += "$steps. ${a.why} → ${frameVerdict(obs)}"
                // Bir necha kadrda hech narsa o'zgarmadi — bu usuldagi tegish o'yinga yetmayapti.
                if (!controller.anyResponse && controller.ignoredFrames >= IGNORED_FRAMES_LIMIT) {
                    ignored = true
                    break
                }
            }
            if (!ignored) {
                when (val a = action) {
                    is AimAction.Ready -> ready = a
                    is AimAction.Fail -> failure = a
                    is AimAction.Move -> failure = AimAction.Fail("$MAX_STEPS qadamda tugamadi", null)
                }

                // Tayyor mo'ljal boshqa drag'da bo'lsa (eng yaxshi oldingi urinish) — o'sha joyga qaytib kadr olamiz.
                val finalDrag = ready?.drag ?: failure?.best?.first
                if (finalDrag != null && hypot(finalDrag.dx - (fx - x0), finalDrag.dy - (fy - y0)) > 1f) {
                    val (tx, ty) = finger(finalDrag)
                    moveSmooth(touch, fx, fy, tx, ty)
                    fx = tx
                    fy = ty
                    SystemClock.sleep(SETTLE_MS)
                    lastFrame = ZoneScanner.scan(service, annotate = false)
                }

                // Bekor qilishdan oldin: chizmalar bilan skrinshot.
                progress("📸 Chizmali skrinshot saqlanmoqda…")
                val summary = buildList {
                    add(
                        when {
                            ready == null -> "⚠️ MO'LJAL TOPILMADI: ${failure?.reason}"
                            fire -> "🔥 MO'LJAL TAYYOR — OTILADI"
                            else -> "🎯 MO'LJAL TAYYOR (otilmadi, bekor qilinadi)"
                        }
                    )
                    add("Tegish: ${touch.name}; bosish (%.0f, %.0f) → barmoq (%.0f, %.0f), drag (%+.0f, %+.0f), $steps qadam"
                        .format(Locale.ROOT, x0, y0, fx, fy, fx - x0, fy - y0))
                    ready?.let { add("Uchish tezligi vx %.2f vy %.2f (px/qadam)".format(Locale.ROOT, it.velocity.vx, it.velocity.vy)) }
                }
                val annotated = lastFrame.withExtras(summary, floatArrayOf(x0, y0, fx, fy))
                imagePath = runCatching { MediaSaver.savePng(context, annotated.preview, "AutoAim_mojal_$stamp.png") }.getOrNull()
                runCatching { MediaSaver.savePng(context, lastFrame.screenshot, "AutoAim_mojal_${stamp}_asl.png") }
            }
            completed = true
        } finally {
            // Otish: faqat hamma qadam xatosiz tugagan, mo'ljal tayyor va oxirgi kadr ham TUSHADI deganda.
            val lastCheck = observation(lastFrame, Drag(fx - x0, fy - y0))?.let { obs ->
                obs.arc?.let { GeometryGate.check(it, obs.ball, obs.box, obs.slot, obs.floorY) }
            }
            val canFire = fire && completed && ready != null && lastCheck?.verdict == Verdict.HIT && !stop()
            // Harakatlanuvchi quti: barmoq ushlab turiladi, quti kerakli joyga kelishi kutiladi.
            val gate = releaseGate
            val gateOpen = canFire && (gate == null || run {
                val obs = observation(lastFrame, Drag(fx - x0, fy - y0))
                val steps = obs?.arc?.let { arc ->
                    LeadPlanner.flightSteps(obs.ball.centerY.toDouble(), ready!!.velocity.vy.toDouble(), arc.g.toDouble(), obs.slot.top.toDouble())
                }
                flightSteps = steps
                progress("⏳ Quti nishon nuqtasiga kelishi kutilmoqda…")
                steps != null && runCatching { gate.await(steps, stop) }.getOrDefault(false)
            })
            if (gateOpen && !stop()) {
                progress("🔥 Otildi")
                releaseUptime = SystemClock.uptimeMillis()
                fired = runCatching { touch.up(fx, fy) }.isSuccess
            } else {
                // Bekor qilish: aynan bosilgan nuqtaga qaytib, kutib, qo'yib yuborish. Qaytishda xato bo'lsa ham
                // barmoq albatta uziladi — aks holda o'yin "bosilgan" holatda qotib qoladi.
                progress("↩️ Bekor qilinmoqda…")
                runCatching { moveSmooth(touch, fx, fy, x0, y0) }
                SystemClock.sleep(CANCEL_SETTLE_MS)
                runCatching { touch.up(x0, y0) }
            }
        }
        if (ignored) {
            csv.append("${touch.name},ignored,,,,,,,,,,,,,,\n")
            return Attempt(null, ignored = true)
        }

        if (fired) {
            csv.append("${touch.name},fire,,,,,,,,,,,,,,released\n")
            val csvPath = runCatching { MediaSaver.saveText(context, csv.toString(), "AutoAim_mojal_$stamp.csv") }.getOrNull()
            val r = ready!!
            val text = "🔥 Otildi: bashorat ${r.check.verdict.text} %+.1f px, drag (%+.0f, %+.0f), $steps qadam"
                .format(Locale.ROOT, r.check.slotOffset ?: 0f, fx - x0, fy - y0)
            return Attempt(
                Report(
                    text, imagePath, csvPath, ready = true, fired = true, releaseUptime = releaseUptime, aim = r,
                    releaseFrame = lastFrame, flightSteps = flightSteps,
                ),
                ignored = false,
            )
        }

        // Sharcha joyida qoldimi?
        SystemClock.sleep(AFTER_UP_MS)
        val after = runCatching { ZoneScanner.scan(service, annotate = false) }.getOrNull()
        val afterBall = after?.zone?.ball
        val cancelled = afterBall != null && hypot(afterBall.centerX - ball.centerX, afterBall.centerY - ball.centerY) < ball.radius
        val cancelText = if (cancelled) "✅ Bekor qilindi — sharcha joyida" else "⚠️ Sharcha joyida topilmadi — otilgan bo'lishi mumkin!"
        csv.append("${touch.name},cancel,,,,,,,,,,,,,,${if (cancelled) "ok" else "ball_missing"}\n")
        val csvPath = runCatching { MediaSaver.saveText(context, csv.toString(), "AutoAim_mojal_$stamp.csv") }.getOrNull()

        val head = ready?.let {
            "🎯 Mo'ljal tayyor: ${it.check.verdict.text}, markazdan %+.1f px".format(Locale.ROOT, it.check.slotOffset ?: 0f)
        } ?: "⚠️ Mo'ljal topilmadi: ${failure?.reason}"
        val text = buildString {
            appendLine(head)
            appendLine("tegish: ${touch.name}, drag (%+.0f, %+.0f), $steps qadam".format(Locale.ROOT, fx - x0, fy - y0))
            appendLine(cancelText)
            log.takeLast(6).forEach { appendLine(it) }
            imagePath?.let { appendLine("📸 $it") }
            csvPath?.let { append("📄 $it") }
        }
        return Attempt(Report(text.trim(), imagePath, csvPath, ready != null), ignored = false)
    }

    private fun observation(frame: ZoneScanResult, drag: Drag): Observation? {
        val zone = frame.zone ?: return null
        val ball = zone.ball ?: return null
        val (box, slot) = fixedTarget ?: Pair(zone.box ?: return null, zone.slot ?: return null)
        return Observation(
            drag, ball, box, slot, frame.arc,
            wallLeft = zone.left + ball.radius, wallRight = zone.right - ball.radius, floorY = zone.bottom.toFloat(),
        )
    }

    private fun frameVerdict(obs: Observation): String {
        val arc = obs.arc ?: return "yoy yo'q"
        val check = GeometryGate.check(arc, obs.ball, obs.box, obs.slot, obs.floorY)
        return check.verdict.text + (check.slotOffset?.let { " %+.1f".format(Locale.ROOT, it) } ?: "")
    }

    private fun logStep(touch: Touch, step: Int, why: String, obs: Observation, frame: ZoneScanResult) {
        val arc = obs.arc
        val v = arc?.let { AimController.launchVelocity(it, obs.ball) }
        val check = arc?.let { GeometryGate.check(it, obs.ball, obs.box, obs.slot, obs.floorY) }
        csv.append(
            listOf(
                touch.name, step, "\"${why.replace("\"", "'")}\"",
                f(obs.drag.dx), f(obs.drag.dy), f(obs.ball.centerX), f(obs.ball.centerY), f(obs.ball.radius),
                arc?.dots?.size ?: 0, v?.let { f(it.vx) } ?: "", v?.let { f(it.vy) } ?: "", arc?.let { f(it.g) } ?: "",
                arc?.let { f(it.rms) } ?: "", check?.verdict ?: "", check?.slotOffset?.let { f(it) } ?: "", frame.captureMs,
            ).joinToString(",")
        ).append('\n')
    }

    private fun f(v: Float) = "%.2f".format(Locale.ROOT, v)

    /** Barmoqni bosqichma-bosqich suradi — o'yin oraliq holatlarni ham ko'radi. */
    private fun moveSmooth(touch: Touch, x1: Float, y1: Float, x2: Float, y2: Float) {
        val steps = max(1, (hypot(x2 - x1, y2 - y1) / touch.moveStepPx).toInt())
        for (i in 1..steps) {
            val k = i.toFloat() / steps
            touch.move(x1 + (x2 - x1) * k, y1 + (y2 - y1) * k)
        }
    }

    companion object {
        private const val CSV_HEADER =
            "touch,step,why,drag_dx,drag_dy,ball_x,ball_y,ball_r,dots,vx,vy,g,rms,verdict,slot_offset,capture_ms"
        /** Chiziq sharchaning pastki chetidan 4 px tepada (undan pastdagi tegish o'yinda ishlamaydi). */
        private const val LINE_PADDING_PX = -4f
        /** Soha tepasi: zona balandligining 6% i (ball yozuvlari ostidan). */
        private const val REGION_TOP = 0.06f
        private const val EDGE_PX = 10f
        private const val MAX_STEPS = 14
        private const val IGNORED_FRAMES_LIMIT = 2
        private const val SETTLE_MS = 150L
        private const val CANCEL_SETTLE_MS = 300L
        private const val AFTER_UP_MS = 600L
    }
}

package uz.autoaim.aim

import android.content.Context
import android.media.MediaScannerConnection
import android.os.SystemClock
import uz.autoaim.IAutoAimService
import uz.autoaim.capture.LiveFrames
import uz.autoaim.capture.ZoneScanResult
import uz.autoaim.capture.ZoneScanner
import uz.autoaim.core.MediaSaver
import uz.autoaim.track.BallFinder
import uz.autoaim.track.BoxRowTracker
import uz.autoaim.track.FlightCalibration
import uz.autoaim.track.LeadPlanner
import uz.autoaim.track.MotionFitter
import uz.autoaim.track.RollingMotion
import uz.autoaim.track.Sample
import uz.autoaim.vision.Blob
import uz.autoaim.vision.GameZone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Avtomatik o'yin (foydalanuvchi talabi):
 *  - har bosqichda quti harakatlanmayotganini tekshiradi → mo'ljal oladi → **haqiqatan otadi** → uchishni
 *    kadrlarda kuzatadi → natija (sharcha nishon-kartaga tushib, yangi bosqich boshlandimi);
 *  - **harakatlanuvchi quti** paydo bo'lsa: otishni to'xtatadi, **ekranni videoga yozishni boshlaydi**, qutining
 *    harakatini kadrma-kadr kuzatadi, so'ng mo'ljal olib **bekor qiladi** (pauza) va yozuvni yakunlaydi;
 *  - hammasi CSV ga (`Download/AutoAim/AutoAim_oyin_<vaqt>.csv`), video — `Movies/AutoAim/`.
 *
 * To'xtaydi: harakatlanuvchi quti, tushirolmaslik, [maxShots] ta otish yoki foydalanuvchi so'rovi ([stop]).
 */
class AutoPlayRunner(
    private val context: Context,
    private val service: IAutoAimService,
    private val progress: (String) -> Unit,
    private val stop: () -> Boolean,
    private val maxShots: Int = 10,
) {
    class Report(val text: String, val csvPath: String?, val videoPath: String?)

    private val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date())
    private val csv = StringBuilder(CSV_HEADER).append('\n')
    private val startUptime = SystemClock.uptimeMillis()
    private val lines = mutableListOf<String>()

    fun run(): Report {
        var videoPath: String? = null
        var shots = 0
        var hits = 0
        var reason = "$maxShots ta otish bajarildi"
        try {
            while (shots < maxShots) {
                if (stop()) { reason = "foydalanuvchi to'xtatdi"; break }

                // 1. Quti harakatlanyaptimi? (ikki kadr, ~0.5 s oraliq)
                progress("🔎 ${shots + 1}-bosqich: quti tekshirilmoqda…")
                val a = frame("check", shots + 1)
                val b = frame("check", shots + 1)
                val sa = a.result.zone?.slot
                val sb = b.result.zone?.slot
                if (sa == null || sb == null || a.result.zone?.ball == null) {
                    reason = "o'yin zonasi / sharcha / slot topilmadi"
                    break
                }
                var moved = hypot(sb.centerX - sa.centerX, sb.centerY - sa.centerY)
                if (moved > MOVING_PX) {
                    // Tasdiqlash: sahna surilish animatsiyasi emas, haqiqatan harakat — keyingi juftda ham siljiydi.
                    val c = frame("check", shots + 1)
                    val sc = c.result.zone?.slot
                    moved = if (sc == null) 0f else hypot(sc.centerX - sb.centerX, sc.centerY - sb.centerY)
                }
                if (moved > MOVING_PX) {
                    val movingZone = b.result.zone!!
                    lines += "🚦 ${shots + 1}-bosqich: quti harakatlanyapti (${"%.0f".format(Locale.ROOT, moved)} px / ~${b.uptime - a.uptime} ms)"
                    if (!LiveFrames.running) {
                        // Jonli ko'rishsiz quti harakatini aniq kuzatib bo'lmaydi — faqat kuzatish va pauza.
                        videoPath = recordMovingBox(shots + 1, movingZone)
                        reason = "harakatlanuvchi quti — jonli ko'rish o'chiq, faqat kuzatildi va pauza qilindi"
                        break
                    }
                    shots++
                    val moving = shootMoving(shots, movingZone)
                    videoPath = moving.video ?: videoPath
                    lines += moving.outcome.line
                    if (moving.outcome.hit) { hits++; continue }
                    reason = if (moving.fired) "harakatlanuvchi quti: tushmadi — to'xtatildi" else "harakatlanuvchi quti: otilmadi"
                    if (!moving.fired) shots--
                    break
                }

                // 2. Mo'ljal va otish.
                shots++
                progress("🎯 $shots-otish: mo'ljal olinmoqda…")
                // Jonli ko'rish yoqilgan bo'lsa — sharcha har kadrda yoziladi (uchish vaqtini kalibrlash uchun).
                val ballTrack = startBallTrack(a.result.zone!!)
                val aim = try {
                    AimRunner(context, service, { progress("🎯 $shots: $it") }, fire = true, stop = stop).run()
                } catch (t: Throwable) {
                    ballTrack?.let { LiveFrames.removeAnalyzer(it.analyzer) }
                    throw t
                }
                if (!aim.fired) {
                    lines += "⚠️ $shots-otish: otilmadi — ${aim.text.lineSequence().first()}"
                    reason = "mo'ljal olinmadi"
                    shots--
                    break
                }

                // 3. Uchish va natija.
                val outcome = observeShot(shots, aim, ballTrack)
                lines += outcome.line
                if (outcome.hit) hits++ else { reason = "tushmadi — to'xtatildi"; break }
            }
        } finally {
            progress("💾 Natijalar saqlanmoqda…")
        }

        val csvPath = runCatching { MediaSaver.saveText(context, csv.toString(), "AutoAim_oyin_$stamp.csv") }.getOrNull()
        val text = buildString {
            appendLine("🏁 $shots ta otish, $hits ta tushdi. To'xtash sababi: $reason")
            lines.takeLast(8).forEach { appendLine(it) }
            videoPath?.let { appendLine("🎬 $it") }
            csvPath?.let { append("📄 $it") }
        }
        return Report(text.trim(), csvPath, videoPath)
    }

    private class Frame(val result: ZoneScanResult, val uptime: Long)

    /** Kadr oladi va CSV ga yozadi. Vaqt — kadr olish boshlanishi (screencap kadrni deyarli darhol oladi). */
    private fun frame(stage: String, shot: Int, note: String = ""): Frame {
        val t = SystemClock.uptimeMillis()
        val r = ZoneScanner.scan(service, annotate = false)
        val z = r.zone
        csv.append(
            listOf(
                shot, stage, t - startUptime, r.captureMs,
                z?.ball?.let { f(it.centerX) } ?: "", z?.ball?.let { f(it.centerY) } ?: "",
                z?.slot?.let { f(it.centerX) } ?: "", z?.slot?.top ?: "",
                z?.box?.left ?: "", z?.box?.right ?: "",
                r.arc?.dots?.size ?: 0, "\"$note\"",
            ).joinToString(",")
        ).append('\n')
        return Frame(r, t)
    }

    private class Outcome(val hit: Boolean, val line: String)

    /**
     * Otishdan keyin ~[OBSERVE_MS] davomida kadrlar: uchayotgan sharcha (vaqt kalibrlash uchun — bir nuqta
     * qadami necha ms), so'ng natija: sharcha nishon-slotga o'tirdimi (yangi bosqich).
     */
    /** Jonli kadrlarda sharcha joylashuvi (vaqt, x, y). */
    private class BallTrack(val analyzer: LiveFrames.Analyzer, val points: MutableList<Triple<Long, Float, Float>>)

    private fun startBallTrack(zone: GameZone): BallTrack? {
        if (!LiveFrames.running) return null
        val finder = BallFinder(zone.left + 4, zone.top, zone.right - 4, zone.bottom)
        val points = java.util.Collections.synchronizedList(mutableListOf<Triple<Long, Float, Float>>())
        val analyzer = LiveFrames.Analyzer { frame, t -> finder.find(frame)?.let { points += Triple(t, it.first, it.second) } }
        LiveFrames.addAnalyzer(analyzer)
        return BallTrack(analyzer, points)
    }

    private fun observeShot(shot: Int, aim: AimRunner.Report, track: BallTrack?, target: Blob? = null): Outcome {
        val release = aim.releaseFrame?.zone ?: return Outcome(false, "❓ $shot: otish kadri yo'q")
        // Harakatlanuvchi qutida — mo'ljal olingan (virtual) slot, kadrdagi emas.
        val slot = target ?: release.slot!!
        val launchBall = release.ball!!
        val v = aim.aim!!.velocity
        val predicted = aim.aim.check
        val g = aim.releaseFrame.arc?.g ?: G_FALLBACK

        val stepMs = mutableListOf<Float>()
        var last: Frame? = null
        while (SystemClock.uptimeMillis() - aim.releaseUptime < OBSERVE_MS) {
            val fr = frame("flight", shot)
            last = fr
            val ball = fr.result.zone?.ball ?: continue
            // Uchayotgan sharcha: x dan yoy vaqti t (nuqta qadamlarida), shu t da bashorat qilingan y ga mos kelsa —
            // haqiqatan yoy ustida (tushgan/sahna surilgan kadrlar hisobga olinmaydi). 1 qadam = (kadr vaqti) / t.
            if (abs(v.vx) > 1f) {
                val t = (ball.centerX - launchBall.centerX) / v.vx
                val yPredicted = launchBall.centerY + v.vy * t + g * t * t / 2
                if (t > 0.5f && abs(ball.centerY - yPredicted) < launchBall.radius * 2) {
                    stepMs += (fr.uptime - aim.releaseUptime) / t
                }
            }
        }
        // Natija: to'g'ri tushganda sahna suriladi — nishon-karta sharcha bilan platforma o'rniga tushadi
        // (telefonda: x saqlanadi, y = platforma balandligi) va yangi nishon boshqa joyda paydo bo'ladi.
        val end = last?.result?.zone
        val endBall = end?.ball
        val newSlot = end?.slot
        val slotChanged = newSlot != null && hypot(newSlot.centerX - slot.centerX, newSlot.centerY - slot.centerY) > SLOT_CHANGED_PX
        val landed = endBall != null &&
            abs(endBall.centerX - slot.centerX) < slot.width / 2f &&
            (abs(endBall.centerX - launchBall.centerX) > launchBall.radius * 2 || slotChanged)
        // Jonli kadrlar bo'lsa — aniq kalibrlash: kadr vaqti = kechikish + t·(1 qadam ms).
        val calibration = track?.let { tr ->
            LiveFrames.removeAnalyzer(tr.analyzer)
            val pts = synchronized(tr.points) { tr.points.toList() }
            val flightPoints = pts.mapNotNull { (t, x, y) ->
                if (t <= aim.releaseUptime || abs(v.vx) < 1f) return@mapNotNull null
                val steps = (x - launchBall.centerX) / v.vx
                val yPredicted = launchBall.centerY + v.vy * steps + g * steps * steps / 2
                if (steps > 0.5f && abs(y - yPredicted) < launchBall.radius) (t - aim.releaseUptime).toDouble() to steps.toDouble() else null
            }
            pts.forEach { (t, x, y) ->
                csv.append("$shot,live_ball,${t - startUptime},,${f(x)},${f(y)},,,,,,\"\"\n")
            }
            FlightCalibration.fit(flightPoints)
        }
        calibration?.let {
            (context.applicationContext as uz.autoaim.AutoAimApp).config.let { c ->
                c.flightMsPerStep = it.msPerStep.toFloat()
                c.flightLatencyMs = it.latencyMs.toFloat()
            }
            csv.append("$shot,calibration,,,,,,,,,,\"ms_per_step=%.2f latency_ms=%.1f points=${it.points} rms_ms=%.1f\"\n"
                .format(Locale.ROOT, it.msPerStep, it.latencyMs, it.rmsMs))
        }
        val msPerStep = calibration?.msPerStep?.toFloat() ?: stepMs.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
        val flight = when {
            calibration != null -> " | uchish: 1 qadam = %.1f ms, kechikish %.0f ms (${calibration.points} jonli kadr)"
                .format(Locale.ROOT, calibration.msPerStep, calibration.latencyMs)
            msPerStep != null -> " | uchish: 1 qadam ≈ %.0f ms (${stepMs.size} kadr)".format(Locale.ROOT, msPerStep)
            else -> ""
        }
        csv.append("$shot,result,${SystemClock.uptimeMillis() - startUptime},,,,,,,,,\"${if (landed) "HIT" else "MISS"} pred=${predicted.verdict} %+.1f ms_per_step=${msPerStep ?: ""}\"\n"
            .format(Locale.ROOT, predicted.slotOffset ?: 0f))
        val line = if (landed) "✅ $shot-otish: TUSHDI (bashorat %+.1f px)$flight".format(Locale.ROOT, predicted.slotOffset ?: 0f)
        else "❌ $shot-otish: tushmadi (bashorat ${predicted.verdict.text} %+.1f px)$flight".format(Locale.ROOT, predicted.slotOffset ?: 0f)
        return Outcome(landed, line)
    }

    /**
     * Harakatlanuvchi quti: video yozish → ~[TRACK_MS] kadrma-kadr kuzatish (slot markazi va vaqt) →
     * mo'ljal olib bekor qilish (pauza) → yozuvni yakunlash. Harakat xulosasi panelga.
     */
    private fun recordMovingBox(shot: Int, zone: GameZone): String? {
        val path = "/sdcard/Movies/AutoAim/AutoAim_harakat_$stamp.mp4"
        progress("🎬 Harakatlanuvchi quti — video yozilmoqda…")
        val pid = runCatching { service.startScreenRecord(path, MAX_RECORD_S) }.getOrDefault(-1)
        if (pid < 0) lines += "⚠️ Video yozish boshlanmadi (screenrecord)"
        try {
            if (LiveFrames.running) {
                trackLive(shot, zone)
            } else {
                val xs = mutableListOf<Pair<Long, Float>>()
                val start = SystemClock.uptimeMillis()
                while (SystemClock.uptimeMillis() - start < TRACK_MS && !stop()) {
                    progress("📈 Quti kuzatilmoqda: ${xs.size} kadr")
                    val fr = frame("track", shot)
                    fr.result.zone?.slot?.let { xs += fr.uptime to it.centerX }
                }
                lines += movementSummary(xs)
            }

            if (!stop()) {
                progress("⏸ Mo'ljal olib, bekor qilinmoqda (pauza)…")
                val pause = AimRunner(context, service, { progress("⏸ $it") }, fire = false, stop = stop).run()
                lines += "⏸ Pauza: ${pause.text.lineSequence().first()}"
                csv.append("$shot,pause,${SystemClock.uptimeMillis() - startUptime},,,,,,,,,\"${pause.text.lineSequence().first().replace("\"", "'")}\"\n")
            }
        } finally {
            if (pid > 0) {
                runCatching { service.stopScreenRecord(pid) }
                SystemClock.sleep(1500) // mp4 yopilishi uchun
                MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf("video/mp4"), null)
            }
        }
        return if (pid > 0) path.removePrefix("/sdcard/") else null
    }

    private class MovingShot(val outcome: Outcome, val fired: Boolean, val video: String?)

    /**
     * Harakatlanuvchi quti (jonli ko'rish bilan): video yozish → qutini [TRACK_MS] kuzatib harakat qonunini
     * moslash → nishon nuqtasi (sinusoidada — chekka) → qutining o'sha nuqtadagi virtual nusxasiga mo'ljal →
     * barmoq ushlab turiladi, quti sharcha yetib keladigan lahzada nishon nuqtasida bo'ladigan payt kutiladi
     * (model har ~250 ms qayta moslanadi) → otish → natija.
     */
    private fun shootMoving(shot: Int, zone: GameZone): MovingShot {
        val box = zone.box!!
        val slot = zone.slot!!
        val path = "/sdcard/Movies/AutoAim/AutoAim_harakat_${stamp}_$shot.mp4"
        progress("🎬 $shot: harakatlanuvchi quti — video yozilmoqda…")
        val pid = runCatching { service.startScreenRecord(path, MAX_RECORD_S) }.getOrDefault(-1)

        val tracker = BoxRowTracker(
            (box.top + box.bottom) / 2, zone.left + 2, zone.right - 2,
            slot.centerX - (box.left + box.right) / 2f, box.width / 2,
        )
        val rolling = RollingMotion()
        val all = java.util.Collections.synchronizedList(mutableListOf<Sample>())
        val analyzer = LiveFrames.Analyzer { frame, t ->
            tracker.slotX(frame)?.let { Sample(t.toDouble(), it.toDouble()).also { s -> rolling.add(s); all += s } }
        }
        LiveFrames.addAnalyzer(analyzer)
        var ballTrack: BallTrack? = null
        try {
            // 1. Harakat qonunini o'rganish.
            val start = SystemClock.uptimeMillis()
            while (SystemClock.uptimeMillis() - start < TRACK_MS && !stop()) {
                progress("📡 $shot: quti kuzatilmoqda — ${rolling.snapshot().size} kadr")
                SystemClock.sleep(200)
            }
            if (stop()) return MovingShot(Outcome(false, "⏹ $shot: to'xtatildi"), false, null)
            val fit = rolling.fit() ?: return MovingShot(Outcome(false, "❌ $shot: quti harakatini moslab bo'lmadi"), false, null)
            if (!MotionFitter.isReliable(fit)) {
                csv.append("$shot,motion,,,,,,,,,,\"ishonchsiz: ${fit.motion.describe().replace("\"", "'")} rms=%.2f\"\n".format(Locale.ROOT, fit.rmsPx))
                return MovingShot(
                    Outcome(false, "❌ $shot: harakat modeli ishonchsiz (${fit.motion.describe()}, xato %.1f px) — otilmadi".format(Locale.ROOT, fit.rmsPx)),
                    false, videoOf(pid, path),
                )
            }
            val now = SystemClock.uptimeMillis().toDouble()
            val aimPoint = LeadPlanner.chooseAim(fit.motion, now)
            val dx = (aimPoint.x - slot.centerX).toFloat()
            val target = Pair(shift(box, dx), shift(slot, dx))
            lines += "📡 $shot: ${fit.motion.describe()}, xato %.1f px → nishon: ${aimPoint.description} (x %.0f)"
                .format(Locale.ROOT, fit.rmsPx, aimPoint.x)
            csv.append("$shot,motion,,,,,,,,,,\"${fit.motion.describe().replace("\"", "'")} rms=%.2f aim=${aimPoint.description} x=%.1f\"\n"
                .format(Locale.ROOT, fit.rmsPx, aimPoint.x))

            // 2. Qo'yib yuborish darvozasi: quti nishon nuqtasiga keladigan lahzani kutadi.
            val config = (context.applicationContext as uz.autoaim.AutoAimApp).config
            val msPerStep = config.flightMsPerStep.takeIf { it > 0 } ?: DEFAULT_MS_PER_STEP
            val latency = config.flightLatencyMs.takeIf { it > 0 } ?: DEFAULT_LATENCY_MS
            var gateNote = ""
            var liveMismatches = 0
            val gate = AimRunner.ReleaseGate { steps, stopFn ->
                val delay = latency + steps * msPerStep
                val deadline = SystemClock.uptimeMillis() + LeadPlanner.waitLimitMs(fit.motion)
                var model = fit.motion
                var fittedAt = 0L
                while (SystemClock.uptimeMillis() < deadline && !stopFn()) {
                    // Model ~250 ms da bir qayta moslanadi (faza aniqlashadi), oraliqda — oxirgisi.
                    if (SystemClock.uptimeMillis() - fittedAt >= REFIT_MS) {
                        // Faqat ishonchli moslash qabul qilinadi; aks holda oxirgi yaxshi model qoladi.
                        model = rolling.fit()?.takeIf { MotionFitter.isReliable(it) }?.motion ?: model
                        fittedAt = SystemClock.uptimeMillis()
                    }
                    val t = SystemClock.uptimeMillis().toDouble()
                    val release = LeadPlanner.nextRelease(model, t, delay, aimPoint.x, RELEASE_TOLERANCE_PX, deadline - t)
                        ?: return@ReleaseGate false
                    if (release - t <= 3) {
                        // Jonli tekshiruv: oxirgi kadrdagi haqiqiy slot model aytgan joyda bo'lishi shart.
                        val last = rolling.snapshot().lastOrNull()
                        val drift = last?.let { abs(it.x - model.x(it.tMs)) }
                        if (last == null || t - last.tMs > LIVE_MAX_AGE_MS || drift!! > LIVE_DRIFT_PX) {
                            liveMismatches++
                            gateNote = "jonli tekshiruv: slot ${last?.let { "%.1f".format(Locale.ROOT, it.x) }}, model ${last?.let { "%.1f".format(Locale.ROOT, model.x(it.tMs)) }}"
                            model = rolling.fit()?.takeIf { MotionFitter.isReliable(it) }?.motion ?: model
                            fittedAt = SystemClock.uptimeMillis()
                            if (liveMismatches >= LIVE_MISMATCH_LIMIT) return@ReleaseGate false
                            SystemClock.sleep(REFIT_MS)
                            continue
                        }
                        gateNote = "uchish %.1f qadam × %.1f ms + %.0f ms = %.0f ms; bashorat: slot x %.1f (nishon %.1f)"
                            .format(Locale.ROOT, steps, msPerStep, latency, delay, model.x(t + delay), aimPoint.x)
                        return@ReleaseGate true
                    }
                    progress("⏳ $shot: otishgacha ${((release - t) / 1000).let { "%.1f".format(Locale.ROOT, it) }} s")
                    SystemClock.sleep(minOf(50L, ((release - t) / 2).toLong().coerceAtLeast(1)))
                }
                false
            }

            // 3. Mo'ljal (virtual nishonga) va otish.
            ballTrack = startBallTrack(zone)
            val aim = AimRunner(
                context, service, { progress("🎯 $shot: $it") }, fire = true, stop = stop,
                fixedTarget = target, releaseGate = gate,
            ).run()
            if (!aim.fired) {
                ballTrack?.let { LiveFrames.removeAnalyzer(it.analyzer) }
                return MovingShot(Outcome(false, "⚠️ $shot: otilmadi — ${aim.text.lineSequence().first()}"), false, videoOf(pid, path))
            }
            if (gateNote.isNotEmpty()) {
                lines += "⏱ $shot: $gateNote"
                csv.append("$shot,release,${aim.releaseUptime - startUptime},,,,,,,,,\"$gateNote\"\n")
            }
            val outcome = observeShot(shot, aim, ballTrack, target.second)
            ballTrack = null
            return MovingShot(outcome, true, videoOf(pid, path))
        } finally {
            LiveFrames.removeAnalyzer(analyzer)
            ballTrack?.let { LiveFrames.removeAnalyzer(it.analyzer) }
            synchronized(all) { all.toList() }.forEach {
                csv.append("$shot,live_box,${it.tMs.toLong() - startUptime},,,,${f(it.x.toFloat())},,,,,\"\"\n")
            }
            if (pid > 0) {
                runCatching { service.stopScreenRecord(pid) }
                SystemClock.sleep(1500)
                MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf("video/mp4"), null)
            }
        }
    }

    private fun videoOf(pid: Int, path: String) = if (pid > 0) path.removePrefix("/sdcard/") else null

    private fun shift(b: Blob, dx: Float) =
        b.copy(left = b.left + dx.roundToInt(), right = b.right + dx.roundToInt(), centerX = b.centerX + dx)

    /**
     * Jonli kuzatuv: quti tanasidan o'tuvchi bitta qator har kadrda o'qiladi (~60 fps) → slot markazi x(t) →
     * harakat qonuni ([MotionFitter]): sinusoida yoki doimiy tezlikda borib-kelish.
     */
    private fun trackLive(shot: Int, zone: GameZone) {
        val box = zone.box ?: return
        val slot = zone.slot ?: return
        val tracker = BoxRowTracker(
            (box.top + box.bottom) / 2, zone.left + 2, zone.right - 2,
            slot.centerX - (box.left + box.right) / 2f, box.width / 2,
        )
        val samples = java.util.Collections.synchronizedList(mutableListOf<Sample>())
        val analyzer = LiveFrames.Analyzer { frame, t -> tracker.slotX(frame)?.let { samples += Sample(t.toDouble(), it.toDouble()) } }
        LiveFrames.addAnalyzer(analyzer)
        val start = SystemClock.uptimeMillis()
        try {
            while (SystemClock.uptimeMillis() - start < TRACK_MS && !stop()) {
                progress("📡 Quti jonli kuzatilmoqda: ${samples.size} kadr")
                SystemClock.sleep(200)
            }
        } finally {
            LiveFrames.removeAnalyzer(analyzer)
        }
        val list = synchronized(samples) { samples.toList() }
        list.forEach { csv.append("$shot,live_box,${it.tMs.toLong() - startUptime},,,,${f(it.x.toFloat())},,,,,\"\"\n") }
        val seconds = if (list.size > 1) (list.last().tMs - list.first().tMs) / 1000 else 0.0
        val fps = if (seconds > 0) list.size / seconds else 0.0
        val fit = MotionFitter.fit(list)
        lines += if (fit == null) "📡 Quti: jonli kadrlar yetarli emas (${list.size})"
        else "📡 Quti (${list.size} kadr, %.0f fps): ${fit.motion.describe()}, xato %.1f px".format(Locale.ROOT, fps, fit.rmsPx)
        fit?.let {
            csv.append("$shot,motion,,,,,,,,,,\"${it.motion.describe().replace("\"", "'")} rms=%.2f\"\n".format(Locale.ROOT, it.rmsPx))
        }
    }

    /** Slot markazining harakati: oraliq, o'rtacha tezlik, burilishlar (yo'nalish o'zgarishi) va taxminiy davr. */
    private fun movementSummary(xs: List<Pair<Long, Float>>): String {
        if (xs.size < 3) return "📈 Quti: kadrlar yetarli emas (${xs.size})"
        val min = xs.minOf { it.second }
        val max = xs.maxOf { it.second }
        var path = 0f
        val turns = mutableListOf<Long>()
        for (i in 1 until xs.size) {
            path += abs(xs[i].second - xs[i - 1].second)
            if (i >= 2) {
                val d1 = xs[i - 1].second - xs[i - 2].second
                val d2 = xs[i].second - xs[i - 1].second
                if (d1 * d2 < 0) turns += xs[i - 1].first
            }
        }
        val seconds = (xs.last().first - xs.first().first) / 1000f
        val speed = if (seconds > 0) path / seconds else 0f
        val period = if (turns.size >= 2) (turns.last() - turns.first()) / (turns.size - 1) * 2 else null
        return "📈 Quti: x %.0f…%.0f (oraliq %.0f px), o'rtacha tezlik %.0f px/s, burilish ${turns.size} ta%s, ${xs.size} kadr"
            .format(Locale.ROOT, min, max, max - min, speed, period?.let { ", davr ≈ %d ms".format(Locale.ROOT, it) } ?: "")
    }

    private fun f(v: Float) = "%.1f".format(Locale.ROOT, v)

    companion object {
        private const val CSV_HEADER = "shot,stage,t_ms,capture_ms,ball_x,ball_y,slot_x,slot_top,box_left,box_right,dots,note"
        /** Ikki kadr orasida slot shundan ko'p siljisa — quti harakatlanyapti. */
        private const val MOVING_PX = 4f
        private const val SLOT_CHANGED_PX = 40f
        private const val G_FALLBACK = 8.59f
        private const val OBSERVE_MS = 3000L
        private const val TRACK_MS = 6000L
        private const val MAX_RECORD_S = 60
        /** Uchish vaqti kalibrlanmagan bo'lsa (taxmin; chekka strategiyasi bunga chidamli). */
        // Telefonda o'lchangan (06:16, 9 jonli kadr, xato 3.8 ms): 53.8 ms/qadam, kechikish 20 ms.
        private const val DEFAULT_MS_PER_STEP = 54f
        private const val DEFAULT_LATENCY_MS = 20f
        /** Otish oldidan: oxirgi jonli kadr shundan eski bo'lmasin va slot model aytgan joydan shuncha px ichida bo'lsin. */
        private const val LIVE_MAX_AGE_MS = 150.0
        private const val LIVE_DRIFT_PX = 6.0
        private const val LIVE_MISMATCH_LIMIT = 8
        /** Bashorat qilingan slot nishon nuqtasidan shu px ichida bo'lganda otiladi. */
        private const val RELEASE_TOLERANCE_PX = 4.0
        private const val REFIT_MS = 250L
    }
}

package uz.autoaim

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import uz.autoaim.capture.ZoneScanResult
import uz.autoaim.shizuku.ShizukuManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Asosiy ekran: Shizuku holati va ruxsati, o'yin zonasini skanerlash, tegish/surish sinovlari.
 */
class MainActivity : Activity() {

    private val app get() = application as AutoAimApp
    private val shizuku get() = app.shizuku
    private val config get() = app.config

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.ROOT)

    private lateinit var shizukuState: TextView
    private lateinit var shizukuInfo: TextView
    private lateinit var zoneInfo: TextView
    private lateinit var zonePreview: ImageView
    private lateinit var screenInfo: TextView
    private lateinit var log: TextView
    private lateinit var inputX: EditText
    private lateinit var inputY: EditText
    private lateinit var inputX2: EditText
    private lateinit var inputY2: EditText
    private lateinit var inputDelay: EditText
    private lateinit var inputDuration: EditText
    private lateinit var inputHold: EditText

    private val stateListener: () -> Unit = { renderState() }
    private var shownScan: ZoneScanResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        shizukuState = findViewById(R.id.shizukuState)
        shizukuInfo = findViewById(R.id.shizukuInfo)
        zoneInfo = findViewById(R.id.zoneInfo)
        zonePreview = findViewById(R.id.zonePreview)
        screenInfo = findViewById(R.id.screenInfo)
        log = findViewById(R.id.log)
        inputX = findViewById(R.id.inputX)
        inputY = findViewById(R.id.inputY)
        inputX2 = findViewById(R.id.inputX2)
        inputY2 = findViewById(R.id.inputY2)
        inputDelay = findViewById(R.id.inputDelay)
        inputDuration = findViewById(R.id.inputDuration)
        inputHold = findViewById(R.id.inputHold)

        findViewById<Button>(R.id.btnPermission).setOnClickListener { shizuku.requestPermission() }
        findViewById<Button>(R.id.btnScanZone).setOnClickListener { scanZone() }
        findViewById<Button>(R.id.btnTapShell).setOnClickListener { runTest(TestKind.TAP_SHELL) }
        findViewById<Button>(R.id.btnTapInject).setOnClickListener { runTest(TestKind.TAP_INJECT) }
        findViewById<Button>(R.id.btnSwipeInject).setOnClickListener { runTest(TestKind.SWIPE_INJECT) }

        fillDefaults()
        config.gameZone?.let { zoneInfo.text = "Oxirgi zona: ${it.joinToString(", ")}" }
        shizuku.addListener(stateListener)
    }

    override fun onResume() {
        super.onResume()
        renderState()
        renderLastScan()
    }

    override fun onDestroy() {
        shizuku.removeListener(stateListener)
        worker.shutdown()
        super.onDestroy()
    }

    // ---------- Shizuku ----------

    private fun renderState() {
        val state = shizuku.state
        shizukuState.setText(
            when (state) {
                ShizukuManager.State.NOT_RUNNING -> R.string.state_not_running
                ShizukuManager.State.TOO_OLD -> R.string.state_too_old
                ShizukuManager.State.NO_PERMISSION -> R.string.state_no_permission
                ShizukuManager.State.CONNECTING -> R.string.state_connecting
                ShizukuManager.State.READY -> R.string.state_ready
            }
        )
        if (state == ShizukuManager.State.CONNECTING) shizuku.bindService()

        val server = shizuku.serverInfo()?.let { "Shizuku $it" } ?: ""
        val service = shizuku.service
        if (service == null) {
            shizukuInfo.text = server
        } else {
            worker.execute {
                val details = try {
                    "xizmat uid ${service.uid}, inject: ${service.injectorInfo()}"
                } catch (t: Throwable) {
                    "xizmat xatosi: ${t.message}"
                }
                main.post { shizukuInfo.text = "$server\n$details" }
            }
        }
    }

    // ---------- O'yin zonasi ----------

    /** Qalqib turuvchi panelni ochadi va ilovani orqaga yuboradi — foydalanuvchi o'yinni o'zi ochadi. */
    private fun scanZone() {
        if (shizuku.service == null) return log("Shizuku xizmati ulanmagan — avval Shizuku bo'limidagi tugmani bosing")
        withOverlayPermission {
            app.zonePanel.show()
            log("Panel ochildi — o'yinni oching va paneldagi «Skanerlash» ni bosing")
            moveTaskToBack(true)
        }
    }

    /**
     * «Boshqa ilovalar ustidan ko'rsatish» ruxsati: avval Shizuku orqali o'zimiz beramiz (appops),
     * bo'lmasa — tizim sozlamalari oynasi ochiladi.
     */
    private fun withOverlayPermission(action: () -> Unit) {
        if (Settings.canDrawOverlays(this)) return action()
        val service = shizuku.service
        worker.execute {
            runCatching { service?.exec("appops set $packageName SYSTEM_ALERT_WINDOW allow") }
            SystemClock.sleep(300)
            main.post {
                if (Settings.canDrawOverlays(this)) {
                    log("Panel ruxsati Shizuku orqali berildi")
                    action()
                } else {
                    log(getString(R.string.overlay_permission_needed))
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    )
                }
            }
        }
    }

    /** Panelda qilingan oxirgi skanerlash natijasini ko'rsatadi. */
    private fun renderLastScan() {
        val scan = app.lastScan ?: return
        if (scan === shownScan) return
        shownScan = scan
        zoneInfo.text = scan.summary() + "\n(rasm ${scan.captureMs} ms, jami ${scan.totalMs} ms)"
        zonePreview.setImageBitmap(scan.preview)
        zonePreview.visibility = View.VISIBLE

        // Sinov koordinatalari: sharchadan tepada (chiziq ustida) bosish va yuqori-o'ngga surish.
        val zone = scan.zone ?: return
        val ball = zone.ball ?: return
        val up = zone.height * 0.11f
        inputX.setText(ball.centerX.toInt().toString())
        inputY.setText((ball.centerY - up).toInt().toString())
        inputX2.setText((ball.centerX + zone.width * 0.15f).toInt().toString())
        inputY2.setText((ball.centerY - up * 2).toInt().toString())
        log("Zona: ${zone.left},${zone.top},${zone.right},${zone.bottom} — sinov koordinatalari sharchaga moslandi")
    }

    // ---------- Sinovlar ----------

    private enum class TestKind { TAP_SHELL, TAP_INJECT, SWIPE_INJECT }

    @Suppress("DEPRECATION")
    private fun fillDefaults() {
        val metrics = DisplayMetrics().also { windowManager.defaultDisplay.getRealMetrics(it) }
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        screenInfo.text = "Ekran: $w × $h px"

        fun value(saved: Float, fallback: Int) = (if (saved >= 0) saved.toInt() else fallback).toString()
        inputX.setText(value(config.testX, w / 2))
        inputY.setText(value(config.testY, h / 2))
        inputX2.setText(value(config.testX2, w / 2 + w / 5))
        inputY2.setText(value(config.testY2, h / 2 - h / 10))
        inputDelay.setText(config.testDelaySec.toString())
        inputDuration.setText(config.swipeDurationMs.toString())
        inputHold.setText(config.swipeHoldMs.toString())
    }

    private fun runTest(kind: TestKind) {
        val service = shizuku.service ?: return log("Shizuku xizmati ulanmagan — avval Shizuku bo'limidagi tugmani bosing")
        val x = inputX.number() ?: return log("x kiritilmagan")
        val y = inputY.number() ?: return log("y kiritilmagan")
        val x2 = inputX2.number() ?: x
        val y2 = inputY2.number() ?: y
        val delaySec = inputDelay.number()?.toInt() ?: config.testDelaySec
        val duration = inputDuration.number()?.toLong() ?: 400L
        val hold = inputHold.number()?.toLong() ?: 500L

        config.testX = x
        config.testY = y
        config.testX2 = x2
        config.testY2 = y2
        config.testDelaySec = delaySec
        config.swipeDurationMs = duration
        config.swipeHoldMs = hold

        log("Sinov: $kind, ${delaySec}s dan keyin — o'yinni oching")

        worker.execute {
            SystemClock.sleep(delaySec * 1000L)
            val start = SystemClock.uptimeMillis()
            val result = try {
                when (kind) {
                    TestKind.TAP_SHELL -> service.exec("input tap ${x.toInt()} ${y.toInt()}").trim()
                    TestKind.TAP_INJECT -> "ok=${service.tap(x, y)}"
                    TestKind.SWIPE_INJECT -> "ok=${service.swipe(x, y, x2, y2, duration, hold)}"
                }
            } catch (t: Throwable) {
                "xato: ${t.javaClass.simpleName}: ${t.message}"
            }
            val elapsed = SystemClock.uptimeMillis() - start
            main.post { log("$kind → $result (${elapsed} ms)") }
        }
    }

    private fun EditText.number(): Float? = text.toString().trim().toFloatOrNull()

    private fun log(message: String) {
        val line = "${timeFormat.format(Date())}  $message"
        log.text = if (log.text.isEmpty()) line else "$line\n${log.text}"
    }
}

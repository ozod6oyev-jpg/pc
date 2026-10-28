package uz.autoaim.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.os.SystemClock
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import uz.autoaim.AutoAimApp
import uz.autoaim.MainActivity
import uz.autoaim.R
import uz.autoaim.aim.AimRunner
import uz.autoaim.aim.AutoPlayRunner
import uz.autoaim.capture.CaptureRequestActivity
import uz.autoaim.capture.CaptureService
import uz.autoaim.capture.LiveFrames
import uz.autoaim.capture.ZoneScanResult
import uz.autoaim.capture.ZoneScanner
import uz.autoaim.vision.GameZone
import java.util.concurrent.Executors

/**
 * O'yin ustida qalqib turuvchi panel: foydalanuvchi o'yinni ochadi, so'ng paneldagi
 * «Skanerlash» ni bosadi → panel yashirinadi → ekran olinadi → zona qidiriladi → natija
 * panelda va o'yin ustida (3 s) chizib ko'rsatiladi.
 */
class ZoneScanPanel(private val app: AutoAimApp) {

    private val context: Context = ContextThemeWrapper(app, R.style.Theme_AutoAim)
    private val windowManager = app.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    private var panel: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var highlight: View? = null
    private var scanning = false
    private var stopWindow: View? = null
    private var playConfirmUntil = 0L
    @Volatile private var stopRequested = false

    val isShowing get() = panel != null

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (panel != null) return
        val view = LayoutInflater.from(context).inflate(R.layout.overlay_zone_panel, null)
        val params = WindowManager.LayoutParams(
            dp(PANEL_WIDTH_DP),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(120)
        }

        view.findViewById<View>(R.id.panelHandle).setOnTouchListener(DragListener(view, params))
        view.findViewById<View>(R.id.panelClose).setOnClickListener { hide() }
        view.findViewById<Button>(R.id.panelScan).setOnClickListener { scan() }
        view.findViewById<Button>(R.id.panelSave).setOnClickListener { saveScreenshot() }
        view.findViewById<Button>(R.id.panelAim).setOnClickListener { aim() }
        view.findViewById<Button>(R.id.panelPlay).setOnClickListener { play() }
        view.findViewById<Button>(R.id.panelLive).setOnClickListener { toggleLive() }
        renderLive(view)
        view.findViewById<Button>(R.id.panelOpenApp).setOnClickListener { openApp() }

        windowManager.addView(view, params)
        panel = view
        panelParams = params
    }

    fun hide() {
        removeHighlight()
        removeStopWindow()
        panel?.let { windowManager.removeView(it) }
        panel = null
        panelParams = null
    }

    private fun scan() {
        val view = panel ?: return
        if (scanning) return
        val status = view.findViewById<TextView>(R.id.panelStatus)
        val service = app.shizuku.service
        if (service == null) {
            status.setText(R.string.panel_no_service)
            return
        }
        scanning = true
        removeHighlight()
        status.setText(R.string.panel_scanning)
        // Panel skrinshotga tushmasligi va o'yinni to'smasligi uchun bir lahzaga yashiriladi.
        view.visibility = View.INVISIBLE

        main.postDelayed({
            worker.execute {
                val result = runCatching { ZoneScanner.scan(service) }
                main.post { onScanned(result) }
            }
        }, HIDE_BEFORE_CAPTURE_MS)
    }

    private fun onScanned(result: Result<ZoneScanResult>) {
        scanning = false
        val view = panel ?: return
        view.visibility = View.VISIBLE
        val status = view.findViewById<TextView>(R.id.panelStatus)
        val scan = result.getOrElse {
            status.text = "❌ Skanerlash xatosi: ${it.message}"
            return
        }
        app.lastScan = scan
        scan.zone?.let { app.config.gameZone = intArrayOf(it.left, it.top, it.right, it.bottom) }

        status.text = scan.summary()
        view.findViewById<Button>(R.id.panelScan).setText(R.string.panel_rescan)
        view.findViewById<View>(R.id.panelOpenApp).visibility = View.VISIBLE
        view.findViewById<View>(R.id.panelSave).visibility = View.VISIBLE
        val zone = scan.zone
        if (zone != null) showHighlight(zone)
        if (app.config.askSaveScreenshot) {
            // Avval zona o'yin ustida ko'rsatiladi, keyin "Saqlash" oynasi ochiladi.
            main.postDelayed({ if (app.lastScan === scan && !scanning) saveScreenshot() }, if (zone != null) HIGHLIGHT_MS else 0L)
        }
    }

    /** "Saqlash" oynasini ochadi; panel u yopilguncha yashiriladi (oynani to'smasligi uchun). */
    private fun saveScreenshot() {
        if (app.lastScan == null) return
        removeHighlight()
        panel?.visibility = View.INVISIBLE
        app.startActivity(
            Intent(app, SaveScreenshotActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** [SaveScreenshotActivity] yopilganda chaqiriladi. */
    fun onSaveFinished(message: String) {
        val view = panel ?: return
        view.visibility = View.VISIBLE
        view.findViewById<TextView>(R.id.panelStatus).text = (app.lastScan?.summary() ?: "") + "\n\n" + message
    }

    /**
     * Otmasdan mo'ljal olish ([AimRunner]). Bu vaqtda panel ko'rinmaydi va tegishlarni o'tkazib yuboradi —
     * bot yuborgan barmoq harakatlari panelga emas, o'yinga borishi kerak.
     */
    private fun aim() {
        val view = panel ?: return
        if (scanning) return
        val status = view.findViewById<TextView>(R.id.panelStatus)
        val service = app.shizuku.service ?: run {
            status.setText(R.string.panel_no_service)
            return
        }
        scanning = true
        removeHighlight()
        setTouchable(false)
        view.visibility = View.INVISIBLE
        Toast.makeText(app, R.string.aim_started, Toast.LENGTH_LONG).show()

        main.postDelayed({
            worker.execute {
                val result = runCatching { AimRunner(app, service, progress = { }).run() }
                main.post { onAimed(result) }
            }
        }, HIDE_BEFORE_CAPTURE_MS)
    }

    private fun onAimed(result: Result<AimRunner.Report>) {
        scanning = false
        val view = panel ?: return
        setTouchable(true)
        view.visibility = View.VISIBLE
        view.findViewById<TextView>(R.id.panelStatus).text = result.fold(
            onSuccess = { it.text },
            onFailure = { "❌ Mo'ljal olishda xato: ${it.javaClass.simpleName}: ${it.message}" },
        )
    }

    /**
     * Avtomatik o'yin ([AutoPlayRunner]): ikki marta bosib tasdiqlanadi (yurak sarflanadi). Jarayon davomida
     * panel yashirin, ekran pastida — holat va ⏹ To'xtatish tugmasi (o'yin zonasidan tashqarida).
     */
    private fun play() {
        val view = panel ?: return
        if (scanning) return
        val button = view.findViewById<Button>(R.id.panelPlay)
        val now = SystemClock.uptimeMillis()
        if (now > playConfirmUntil) {
            playConfirmUntil = now + CONFIRM_MS
            button.setText(R.string.panel_play_confirm)
            main.postDelayed({ if (SystemClock.uptimeMillis() > playConfirmUntil) button.setText(R.string.panel_play) }, CONFIRM_MS + 50)
            return
        }
        playConfirmUntil = 0L
        button.setText(R.string.panel_play)
        val service = app.shizuku.service ?: run {
            view.findViewById<TextView>(R.id.panelStatus).setText(R.string.panel_no_service)
            return
        }
        scanning = true
        stopRequested = false
        removeHighlight()
        setTouchable(false)
        view.visibility = View.INVISIBLE
        val status = showStopWindow()

        main.postDelayed({
            worker.execute {
                val result = runCatching {
                    AutoPlayRunner(app, service, { msg -> main.post { status.text = msg } }, { stopRequested }).run()
                }
                main.post {
                    scanning = false
                    removeStopWindow()
                    setTouchable(true)
                    view.visibility = View.VISIBLE
                    view.findViewById<TextView>(R.id.panelStatus).text = result.fold(
                        onSuccess = { it.text },
                        onFailure = { "❌ O'yinda xato: ${it.javaClass.simpleName}: ${it.message}" },
                    )
                }
            }
        }, HIDE_BEFORE_CAPTURE_MS)
    }

    /** Ekran pastidagi kichik oyna: holat matni va To'xtatish tugmasi. Holat matni TextView'ini qaytaradi. */
    private fun showStopWindow(): TextView {
        removeStopWindow()
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_panel)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val status = TextView(context).apply {
            setTextColor(context.getColor(R.color.text))
            textSize = 12f
            maxLines = 2
        }
        val button = Button(context, null, 0, R.style.Button_Secondary).apply {
            setText(R.string.play_stop)
            setOnClickListener {
                stopRequested = true
                setText(R.string.play_stopping)
            }
        }
        box.addView(status, LinearLayout.LayoutParams(dp(260), LinearLayout.LayoutParams.WRAP_CONTENT))
        box.addView(button, LinearLayout.LayoutParams(dp(260), dp(42)))
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(STOP_WINDOW_BOTTOM_DP)
        }
        windowManager.addView(box, params)
        stopWindow = box
        return status
    }

    private fun removeStopWindow() {
        stopWindow?.let { windowManager.removeView(it) }
        stopWindow = null
    }

    /** Jonli ekranni yoqish (ruxsat oynasi — panel yashiriladi, tugmani to'smasligi uchun) yoki o'chirish. */
    private fun toggleLive() {
        val view = panel ?: return
        if (scanning) return
        if (LiveFrames.running) {
            CaptureService.stop(app)
            main.postDelayed({ panel?.let { renderLive(it) } }, 300)
            return
        }
        view.visibility = View.INVISIBLE
        setTouchable(false)
        app.startActivity(Intent(app, CaptureRequestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** [CaptureRequestActivity] yopilganda. */
    fun onCaptureRequestFinished(granted: Boolean) {
        val view = panel ?: return
        setTouchable(true)
        view.visibility = View.VISIBLE
        // Xizmat kadrlarni bir oz kechroq boshlaydi.
        main.postDelayed({ panel?.let { renderLive(it) } }, 800)
        if (granted) view.findViewById<TextView>(R.id.panelStatus).text = "📡 Jonli ko'rish yoqildi"
    }

    private fun renderLive(view: View) {
        view.findViewById<Button>(R.id.panelLive).setText(if (LiveFrames.running) R.string.panel_live_off else R.string.panel_live_on)
    }

    private fun setTouchable(touchable: Boolean) {
        val view = panel ?: return
        val params = panelParams ?: return
        params.flags = if (touchable) params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        else params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        windowManager.updateViewLayout(view, params)
    }

    private fun openApp() {
        hide()
        val intent = Intent(app, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        app.startActivity(intent)
    }

    // ---------- O'yin ustida zonani ko'rsatish ----------

    private fun showHighlight(zone: GameZone) {
        removeHighlight()
        val view = HighlightView(context, zone)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        windowManager.addView(view, params)
        highlight = view
        main.postDelayed({ if (highlight === view) removeHighlight() }, HIGHLIGHT_MS)
    }

    private fun removeHighlight() {
        highlight?.let { windowManager.removeView(it) }
        highlight = null
    }

    /** Zona ramkasini ekran koordinatalarida chizadi (oyna joylashuvi hisobga olinadi). */
    private class HighlightView(context: Context, private val zone: GameZone) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4 * context.resources.displayMetrics.density
        }
        private val location = IntArray(2)

        override fun onDraw(canvas: Canvas) {
            getLocationOnScreen(location)
            ZoneScanner.drawZone(canvas, zone, paint, dx = -location[0].toFloat(), dy = -location[1].toFloat())
        }
    }

    // ---------- Panelni surish ----------

    private inner class DragListener(
        private val view: View,
        private val params: WindowManager.LayoutParams,
    ) : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(view, params)
                }
            }
            return true
        }
    }

    private fun dp(value: Int) = (value * app.resources.displayMetrics.density).toInt()

    companion object {
        private const val PANEL_WIDTH_DP = 270
        private const val HIDE_BEFORE_CAPTURE_MS = 250L
        private const val HIGHLIGHT_MS = 3000L
        private const val CONFIRM_MS = 3000L
        /** To'xtatish oynasi ekran pastidan — o'yin zonasidan pastda (pastki menyu ustida). */
        private const val STOP_WINDOW_BOTTOM_DP = 70
    }
}

package uz.autoaim.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import uz.autoaim.R

/**
 * Jonli ekran (REJA 3.1): MediaProjection → VirtualDisplay → ImageReader (RGBA_8888, to'liq o'lcham —
 * koordinatalar screencap bilan bir xil). Kadrlar [LiveFrames] ga beriladi. Android 14+: foreground
 * xizmat (mediaProjection turi) ruxsatdan keyin, MediaProjection olishdan oldin ishga tushiriladi.
 */
class CaptureService : Service() {

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat()
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        else @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_DATA)
        if (data == null || projection != null) return START_NOT_STICKY

        val manager = getSystemService(MediaProjectionManager::class.java)
        val mp = manager.getMediaProjection(code, data) ?: run { stopSelf(); return START_NOT_STICKY }
        projection = mp

        val handlerThread = HandlerThread("live-capture").also { it.start() }
        thread = handlerThread
        val handler = Handler(handlerThread.looper)
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, handler)

        @Suppress("DEPRECATION")
        val metrics = DisplayMetrics().also { getSystemService(WindowManager::class.java).defaultDisplay.getRealMetrics(it) }
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        val imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3)
        reader = imageReader
        imageReader.setOnImageAvailableListener({ r ->
            val image = runCatching { r.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            try {
                val plane = image.planes[0]
                LiveFrames.onImage(plane.buffer, image.width, image.height, plane.rowStride, plane.pixelStride, image.timestamp)
            } finally {
                image.close()
            }
        }, handler)
        display = mp.createVirtualDisplay(
            "AutoAim", w, h, metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, handler,
        )
        LiveFrames.running = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        LiveFrames.reset()
        runCatching { display?.release() }
        runCatching { reader?.close() }
        runCatching { projection?.stop() }
        thread?.quitSafely()
        display = null
        reader = null
        projection = null
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Jonli ekran", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("AutoAim — jonli ekran")
            .setContentText("Ekran kuzatilmoqda (MediaProjection)")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val ACTION_STOP = "uz.autoaim.STOP_CAPTURE"
        private const val CHANNEL = "capture"
        private const val NOTIFICATION_ID = 7

        fun stop(context: Context) {
            context.startService(Intent(context, CaptureService::class.java).setAction(ACTION_STOP))
        }
    }
}

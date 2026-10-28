package uz.autoaim

import android.app.Application
import uz.autoaim.capture.ZoneScanResult
import uz.autoaim.core.Config
import uz.autoaim.shizuku.ShizukuManager
import uz.autoaim.ui.ZoneScanPanel

class AutoAimApp : Application() {

    lateinit var shizuku: ShizukuManager
        private set

    lateinit var config: Config
        private set

    /** O'yin ustidagi skanerlash paneli — ilova orqa fonda bo'lganda ham yashaydi. */
    val zonePanel by lazy { ZoneScanPanel(this) }

    /** Oxirgi skanerlash natijasi (asosiy ekranda ko'rsatish uchun). */
    @Volatile
    var lastScan: ZoneScanResult? = null

    override fun onCreate() {
        super.onCreate()
        config = Config(this)
        shizuku = ShizukuManager(packageName).also { it.start() }
    }
}

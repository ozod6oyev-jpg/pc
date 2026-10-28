package uz.autoaim.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import rikka.shizuku.Shizuku
import uz.autoaim.BuildConfig
import uz.autoaim.IAutoAimService

/**
 * Shizuku holati, ruxsat va UserService ulanishini boshqaradi.
 * Ilova darajasida bitta nusxa (AutoAimApp) — o'yin oldinga chiqqanda ham ulanish uzilmaydi.
 */
class ShizukuManager(packageName: String) {

    enum class State { NOT_RUNNING, TOO_OLD, NO_PERMISSION, CONNECTING, READY }

    var service: IAutoAimService? = null
        private set

    private val listeners = mutableListOf<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    private val serviceArgs = Shizuku.UserServiceArgs(
        ComponentName(packageName, AutoAimUserService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("input")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
            service = binder?.takeIf { it.pingBinder() }?.let { IAutoAimService.Stub.asInterface(it) }
            notifyChanged()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            notifyChanged()
        }
    }

    private var bindRequested = false

    private val binderReceived = Shizuku.OnBinderReceivedListener {
        if (hasPermission()) bindService()
        notifyChanged()
    }

    private val binderDead = Shizuku.OnBinderDeadListener {
        service = null
        bindRequested = false
        notifyChanged()
    }

    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, result ->
        if (result == PackageManager.PERMISSION_GRANTED) bindService()
        notifyChanged()
    }

    fun start() {
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
    }

    val state: State
        get() = when {
            !Shizuku.pingBinder() -> State.NOT_RUNNING
            Shizuku.isPreV11() -> State.TOO_OLD
            !hasPermission() -> State.NO_PERMISSION
            service == null -> State.CONNECTING
            else -> State.READY
        }

    /** Shizuku versiyasi va uid (0 = root, 2000 = adb). Shizuku ishlamasa null. */
    fun serverInfo(): String? =
        if (Shizuku.pingBinder()) "v${Shizuku.getVersion()}, uid ${Shizuku.getUid()}" else null

    fun hasPermission(): Boolean =
        Shizuku.pingBinder() && !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    fun requestPermission() {
        if (!Shizuku.pingBinder() || Shizuku.isPreV11()) return
        if (hasPermission()) bindService() else Shizuku.requestPermission(REQUEST_CODE)
    }

    fun bindService() {
        if (bindRequested && service != null) return
        bindRequested = true
        Shizuku.bindUserService(serviceArgs, connection)
        notifyChanged()
    }

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    private fun notifyChanged() {
        main.post { listeners.toList().forEach { it() } }
    }

    companion object {
        private const val REQUEST_CODE = 1001
    }
}

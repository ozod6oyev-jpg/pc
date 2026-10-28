package uz.autoaim.shizuku

import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Method

/**
 * Tizimga sensor (touchscreen) hodisalarini yuboradi.
 *
 * Faqat Shizuku UserService ichida (shell/root jarayoni) ishlaydi — oddiy ilova jarayonida
 * INJECT_EVENTS huquqi yo'q. Yashirin API'lar reflection orqali chaqiriladi, Android
 * versiyasiga qarab bir nechta yo'l sinab ko'riladi.
 */
class InputInjector {

    private class Target(val instance: Any, val method: Method, val name: String)

    private val target: Target by lazy { resolve() }

    /** Haqiqiy sensor ekran qurilmasi (bo'lsa) — ba'zi ilovalar (WebView) qurilmasiz hodisalarni e'tiborsiz qoldiradi. */
    private val touchDeviceId: Int by lazy {
        InputDevice.getDeviceIds().firstOrNull {
            InputDevice.getDevice(it)?.supportsSource(InputDevice.SOURCE_TOUCHSCREEN) == true
        } ?: 0
    }

    val info: String get() = "${target.name}, qurilma $touchDeviceId"

    fun inject(event: InputEvent, mode: Int = MODE_WAIT_FOR_FINISH): Boolean =
        try {
            target.method.invoke(target.instance, event, mode) as Boolean
        } catch (t: Throwable) {
            false
        }

    /**
     * Haqiqiy barmoqqa o'xshash hodisa: barmoq turi FINGER, bosim va o'lcham 1, sensor ekran qurilmasi —
     * tizimning `input` buyrug'i kabi. (Oddiy `MotionEvent.obtain(x, y)` barmoq turini "noma'lum" qoldiradi,
     * veb-o'yinlar bunday tegishni qabul qilmasligi mumkin.)
     */
    fun touch(action: Int, downTime: Long, x: Float, y: Float): Boolean {
        val properties = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_FINGER
        }
        val coords = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 1f
            size = 1f
        }
        val event = MotionEvent.obtain(
            downTime, SystemClock.uptimeMillis(), action, 1, arrayOf(properties), arrayOf(coords),
            0, 0, 1f, 1f, touchDeviceId, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
        )
        return try {
            inject(event)
        } finally {
            event.recycle()
        }
    }

    fun tap(x: Float, y: Float): Boolean {
        val downTime = SystemClock.uptimeMillis()
        if (!touch(MotionEvent.ACTION_DOWN, downTime, x, y)) return false
        SystemClock.sleep(TAP_HOLD_MS)
        return touch(MotionEvent.ACTION_UP, downTime, x, y)
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long, holdMs: Long): Boolean {
        val downTime = SystemClock.uptimeMillis()
        if (!touch(MotionEvent.ACTION_DOWN, downTime, x1, y1)) return false
        var lastX = x1
        var lastY = y1
        try {
            val steps = (durationMs / MOVE_INTERVAL_MS).coerceAtLeast(1)
            for (i in 1..steps) {
                val f = i.toFloat() / steps
                lastX = x1 + (x2 - x1) * f
                lastY = y1 + (y2 - y1) * f
                touch(MotionEvent.ACTION_MOVE, downTime, lastX, lastY)
                SystemClock.sleep(MOVE_INTERVAL_MS)
            }
            if (holdMs > 0) SystemClock.sleep(holdMs)
        } finally {
            // Har qanday holatda barmoq "uziladi" — aks holda o'yin bosilgan holatda qotib qoladi.
            touch(MotionEvent.ACTION_UP, downTime, lastX, lastY)
        }
        return true
    }

    private fun resolve(): Target {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("")
            } catch (_: Throwable) {
            }
        }
        val eventType = InputEvent::class.java
        val intType = Int::class.javaPrimitiveType

        // Android 14+: InputManagerGlobal
        try {
            val cls = Class.forName("android.hardware.input.InputManagerGlobal")
            val instance = cls.getMethod("getInstance").invoke(null)!!
            return Target(instance, cls.getMethod("injectInputEvent", eventType, intType), "InputManagerGlobal")
        } catch (_: Throwable) {
        }

        // Android 8–13: InputManager.getInstance()
        try {
            val cls = Class.forName("android.hardware.input.InputManager")
            val instance = cls.getMethod("getInstance").invoke(null)!!
            return Target(instance, cls.getMethod("injectInputEvent", eventType, intType), "InputManager")
        } catch (_: Throwable) {
        }

        // Oxirgi zaxira: "input" binderidan to'g'ridan-to'g'ri IInputManager
        val binder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, "input") as IBinder
        val stub = Class.forName("android.hardware.input.IInputManager\$Stub")
        val instance = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)!!
        val method = Class.forName("android.hardware.input.IInputManager")
            .getMethod("injectInputEvent", eventType, intType)
        return Target(instance, method, "IInputManager")
    }

    companion object {
        const val MODE_ASYNC = 0
        const val MODE_WAIT_FOR_FINISH = 2
        private const val TAP_HOLD_MS = 40L
        private const val MOVE_INTERVAL_MS = 8L
    }
}

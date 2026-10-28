package uz.autoaim.shizuku

import android.os.ParcelFileDescriptor
import android.os.Process
import android.view.MotionEvent
import uz.autoaim.IAutoAimService
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Shizuku UserService: alohida jarayonda shell (uid 2000) yoki root huquqi bilan ishlaydi.
 * Shizuku bu klassni no-arg konstruktor orqali yaratadi.
 */
class AutoAimUserService : IAutoAimService.Stub() {

    private val injector = InputInjector()

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String): String {
        val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val code = process.waitFor()
        return "exit=$code\n$output"
    }

    override fun getUid(): Int = Process.myUid()

    override fun tap(x: Float, y: Float): Boolean = injector.tap(x, y)

    override fun touchDown(x: Float, y: Float): Long {
        val downTime = android.os.SystemClock.uptimeMillis()
        return if (injector.touch(MotionEvent.ACTION_DOWN, downTime, x, y)) downTime else -1
    }

    override fun touchMove(downTime: Long, x: Float, y: Float): Boolean =
        injector.touch(MotionEvent.ACTION_MOVE, downTime, x, y)

    override fun touchUp(downTime: Long, x: Float, y: Float): Boolean =
        injector.touch(MotionEvent.ACTION_UP, downTime, x, y)

    override fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long, holdMs: Long): Boolean =
        injector.swipe(x1, y1, x2, y2, durationMs, holdMs)

    override fun screencap(): ParcelFileDescriptor {
        val (read, write) = ParcelFileDescriptor.createPipe()
        val process = ProcessBuilder("screencap", "-p").start()
        thread(name = "screencap") {
            ParcelFileDescriptor.AutoCloseOutputStream(write).use { out ->
                process.inputStream.use { it.copyTo(out) }
            }
            process.waitFor()
        }
        return read
    }

    override fun startScreenRecord(path: String, maxSeconds: Int): Int {
        val dir = path.substringBeforeLast('/')
        val command = "mkdir -p '$dir' && screenrecord --time-limit $maxSeconds '$path' >/dev/null 2>&1 & echo \$!"
        val process = ProcessBuilder("sh", "-c", command).start()
        val pid = process.inputStream.bufferedReader().readLine()?.trim()?.toIntOrNull() ?: -1
        process.waitFor()
        return pid
    }

    override fun stopScreenRecord(pid: Int) {
        if (pid > 0) ProcessBuilder("kill", "-2", pid.toString()).start().waitFor()
    }

    override fun injectorInfo(): String =
        try {
            injector.info
        } catch (t: Throwable) {
            "xato: ${t.javaClass.simpleName}: ${t.message}"
        }
}

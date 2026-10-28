package uz.autoaim.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import uz.autoaim.AutoAimApp
import uz.autoaim.R
import uz.autoaim.core.MediaSaver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Ko'rinmas oraliq oyna: tizimning "Saqlash" oynasini (fayl tanlagich) ochadi va oxirgi
 * skanerlashning **belgilangan** rasmini (ramkalar, nomlar, koordinatalar) tanlangan joyga PNG
 * qilib yozadi. Asl (chizilmagan) rasm esa Galereyaga (`Pictures/AutoAim/`) avtomatik saqlanadi —
 * detektorlarni offline test qilish uchun.
 * O'z vazifasida (taskAffinity="") ishlaydi — yopilganda o'yinga qaytiladi.
 */
class SaveScreenshotActivity : Activity() {

    private val app get() = application as AutoAimApp
    private var message: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        if (app.lastScan == null) return finish()

        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date())
        saveRawCopy("AutoAim_skan_${stamp}_asl.png")
        val name = "AutoAim_skan_$stamp.png"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("image/png")
            .putExtra(Intent.EXTRA_TITLE, name)
        app.config.lastSaveUri?.let { intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(it)) }
        startActivityForResult(intent, REQUEST_SAVE)
    }

    @Deprecated("Activity API (AndroidX ishlatilmaydi)")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        val bitmap = app.lastScan?.preview
        if (requestCode != REQUEST_SAVE || resultCode != RESULT_OK || uri == null || bitmap == null) {
            message = getString(R.string.save_cancelled)
            return finish()
        }
        thread(name = "save-screenshot") {
            val result = runCatching {
                contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            runOnUiThread {
                message = result.fold(
                    onSuccess = {
                        app.config.lastSaveUri = uri.toString()
                        getString(R.string.save_done, uri.lastPathSegment ?: uri.toString())
                    },
                    onFailure = { getString(R.string.save_failed, it.message) },
                )
                Toast.makeText(app, message, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    /** Asl rasmni Galereyaga (`Pictures/AutoAim/`) yozadi — detektorlarni offline test qilish uchun. */
    private fun saveRawCopy(name: String) {
        val bitmap = app.lastScan?.screenshot ?: return
        thread(name = "save-raw") { runCatching { MediaSaver.savePng(app, bitmap, name) } }
    }

    override fun onDestroy() {
        if (isFinishing) app.zonePanel.onSaveFinished(message ?: getString(R.string.save_cancelled))
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_SAVE = 1
    }
}

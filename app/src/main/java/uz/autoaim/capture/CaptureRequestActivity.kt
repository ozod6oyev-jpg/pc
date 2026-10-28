package uz.autoaim.capture

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import uz.autoaim.AutoAimApp

/**
 * Ko'rinmas oraliq oyna: tizimning "ekranni yozib olish" ruxsatini so'raydi va [CaptureService] ni
 * ishga tushiradi. O'z vazifasida ochiladi — yopilganda o'yinga qaytiladi.
 */
class CaptureRequestActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val manager = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST)
    }

    @Deprecated("Activity API (AndroidX ishlatilmaydi)")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val granted = requestCode == REQUEST && resultCode == RESULT_OK && data != null
        if (granted) {
            startForegroundService(
                Intent(this, CaptureService::class.java)
                    .putExtra(CaptureService.EXTRA_CODE, resultCode)
                    .putExtra(CaptureService.EXTRA_DATA, data)
            )
        } else {
            Toast.makeText(this, "Jonli ekran ruxsati berilmadi", Toast.LENGTH_SHORT).show()
        }
        (application as AutoAimApp).zonePanel.onCaptureRequestFinished(granted)
        finish()
    }

    companion object {
        private const val REQUEST = 2
    }
}

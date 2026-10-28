package uz.autoaim.core

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Fayllarni foydalanuvchi ko'radigan joyga ruxsatsiz yozadi (Android 10+ MediaStore):
 * rasmlar — `Pictures/AutoAim/`, jurnallar (CSV) — `Download/AutoAim/`.
 * Eski Android'da — ilovaning o'z papkasiga (`Android/data/uz.autoaim/files/`).
 * Natija: saqlangan joyning qisqa nomi (foydalanuvchiga ko'rsatish uchun).
 */
object MediaSaver {

    fun savePng(context: Context, bitmap: Bitmap, name: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val dir = "${Environment.DIRECTORY_PICTURES}/AutoAim"
            insert(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, name, "image/png", dir) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            return "$dir/$name"
        }
        val file = File(context.getExternalFilesDir("scans"), name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file.path
    }

    fun saveText(context: Context, text: String, name: String, mime: String = "text/csv"): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val dir = "${Environment.DIRECTORY_DOWNLOADS}/AutoAim"
            insert(context, MediaStore.Downloads.EXTERNAL_CONTENT_URI, name, mime, dir) {
                it.write(text.toByteArray(Charsets.UTF_8))
            }
            return "$dir/$name"
        }
        val file = File(context.getExternalFilesDir("logs"), name)
        file.writeText(text)
        return file.path
    }

    private fun insert(
        context: Context,
        collection: android.net.Uri,
        name: String,
        mime: String,
        relativePath: String,
        write: (java.io.OutputStream) -> Unit,
    ) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("MediaStore: $name yaratilmadi")
        resolver.openOutputStream(uri)!!.use(write)
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }
}

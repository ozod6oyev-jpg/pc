package uz.autoaim.vision

/**
 * Android'ga bog'liq bo'lmagan oddiy rasm: ARGB piksellar qatorma-qator.
 * Vision kodi shu bilan ishlaydi — telefonda (Bitmap'dan) ham, kompyuterdagi testlarda ham.
 */
/** Piksellarni o'qish manbai: to'liq rasm ([Image]) yoki jonli kadr (nusxasiz, to'g'ridan-to'g'ri buferdan). */
interface PixelSource {
    val width: Int
    val height: Int
    fun argb(x: Int, y: Int): Int
}

class Image(override val width: Int, override val height: Int, val pixels: IntArray) : PixelSource {

    init {
        require(pixels.size == width * height) { "piksellar soni ${width}×$height ga mos emas" }
    }

    override fun argb(x: Int, y: Int): Int = pixels[y * width + x]

    /** Yorqinlik 0..255 (ITU-R BT.601). */
    fun luma(x: Int, y: Int): Int = luma(pixels[y * width + x])

    fun lumaArray(): IntArray = IntArray(pixels.size) { luma(pixels[it]) }

    companion object {
        fun red(c: Int) = (c shr 16) and 0xFF
        fun green(c: Int) = (c shr 8) and 0xFF
        fun blue(c: Int) = c and 0xFF
        fun luma(c: Int) = (red(c) * 299 + green(c) * 587 + blue(c) * 114) / 1000
    }
}

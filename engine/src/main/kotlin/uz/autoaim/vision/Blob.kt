package uz.autoaim.vision

/** Bog'langan piksellar guruhi (rasm koordinatalarida, chegaralar qo'shilgan holda). */
data class Blob(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val area: Int,
    val centerX: Float,
    val centerY: Float,
) {
    val width get() = right - left + 1
    val height get() = bottom - top + 1
}

object Blobs {

    /**
     * (x0, y0)–(x1, y1) to'rtburchakda `test(argb)` ni qanoatlantiruvchi eng katta 4-bog'langan guruhni topadi.
     * Topilmasa yoki maydoni minArea dan kichik bo'lsa — null.
     */
    inline fun largest(
        image: Image,
        x0: Int, y0: Int, x1: Int, y1: Int,
        minArea: Int = 1,
        test: (Int) -> Boolean,
    ): Blob? = all(image, x0, y0, x1, y1, minArea, test).maxByOrNull { it.area }

    /** Sohadagi barcha guruhlar ([minArea] dan kichiklari tashlanadi). `inline` — har piksel uchun boxing bo'lmasin. */
    inline fun all(
        image: Image,
        x0: Int, y0: Int, x1: Int, y1: Int,
        minArea: Int = 1,
        test: (Int) -> Boolean,
    ): List<Blob> {
        val w = x1 - x0 + 1
        val h = y1 - y0 + 1
        if (w <= 0 || h <= 0) return emptyList()
        val mask = BooleanArray(w * h)
        for (y in 0 until h) {
            val row = (y + y0) * image.width + x0
            for (x in 0 until w) mask[y * w + x] = test(image.pixels[row + x])
        }
        return components(mask, w, h, x0, y0, minArea)
    }

    fun components(mask: BooleanArray, w: Int, h: Int, x0: Int, y0: Int, minArea: Int): List<Blob> {
        val stack = IntArray(w * h)
        val result = ArrayList<Blob>()
        for (start in mask.indices) {
            if (!mask[start]) continue
            mask[start] = false
            var top = 0
            stack[top++] = start
            var area = 0
            var sumX = 0L
            var sumY = 0L
            var minX = Int.MAX_VALUE
            var minY = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE
            var maxY = Int.MIN_VALUE
            while (top > 0) {
                val i = stack[--top]
                val x = i % w
                val y = i / w
                area++
                sumX += x
                sumY += y
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                if (x > 0 && mask[i - 1]) { mask[i - 1] = false; stack[top++] = i - 1 }
                if (x < w - 1 && mask[i + 1]) { mask[i + 1] = false; stack[top++] = i + 1 }
                if (y > 0 && mask[i - w]) { mask[i - w] = false; stack[top++] = i - w }
                if (y < h - 1 && mask[i + w]) { mask[i + w] = false; stack[top++] = i + w }
            }
            if (area >= minArea) {
                result += Blob(
                    left = minX + x0, top = minY + y0, right = maxX + x0, bottom = maxY + y0,
                    area = area,
                    centerX = sumX.toFloat() / area + x0,
                    centerY = sumY.toFloat() / area + y0,
                )
            }
        }
        return result
    }
}

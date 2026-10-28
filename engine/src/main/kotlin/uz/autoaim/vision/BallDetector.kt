package uz.autoaim.vision

import kotlin.math.max

/**
 * Sharcha: aylana markazi va radiusi (rasm px), hamda yashil nurning ko'rinib turgan qismi.
 */
data class Ball(val centerX: Float, val centerY: Float, val radius: Float, val glow: Blob)

/**
 * Sharchani topadi. Yashil nur (glow) — ekrandagi yagona shunday rang.
 *
 * Sharcha platformadagi tirqishga **botib turadi** — pastki qismi ko'rinmaydi (namunalarda nur
 * 52×47, 54×38 px). Shuning uchun markaz yashil piksellarning o'rtachasidan emas (u yuqoriga
 * siljiydi, 5–11 px), doim ko'rinadigan chap, o'ng va tepa chetlardan aylana sifatida olinadi:
 * radius = kenglik / 2, markaz x = chetlar o'rtasi, markaz y = tepa + radius.
 */
class BallDetector {

    fun detect(image: Image, x0: Int, y0: Int, x1: Int, y1: Int): Ball? {
        // Faqat sharchaga o'xshagan yashil guruh: o'lchami (1080 kenglikda ~52 px) va dumaloqligi (pasti
        // platformaga botgan bo'lishi mumkin — balandlik kenglikdan kichik). Ekrandagi boshqa yashil narsalar
        // (masalan, overlay oynaning ramkasi) shu yerda rad etiladi.
        val minSize = image.width / MAX_SIZE_DIVISOR
        val maxSize = image.width / MIN_SIZE_DIVISOR
        val glow = Blobs.all(image, x0, y0, x1, y1, minArea = max(20, image.width * image.width / 20000)) { isGlow(it) }
            .filter { it.width in minSize..maxSize && it.height in minSize..maxSize }
            .filter { it.height <= it.width * 13 / 10 && it.height >= it.width * 4 / 10 }
            .maxByOrNull { it.area } ?: return null
        val radius = glow.width / 2f
        return Ball(
            centerX = (glow.left + glow.right) / 2f,
            centerY = glow.top - 0.5f + radius,
            radius = radius,
            glow = glow,
        )
    }

    companion object {
        /** Sharcha nuri kengligi: ekran kengligining 1/60 … 1/10 qismi (1080 da 18…108 px; o'lchangan 51–54). */
        private const val MAX_SIZE_DIVISOR = 60
        private const val MIN_SIZE_DIVISOR = 10

        /** Sharchaning yashil nuri (REJA 5-band). */
        fun isGlow(c: Int): Boolean {
            val r = Image.red(c)
            val g = Image.green(c)
            val b = Image.blue(c)
            return g > r + 25 && g > b + 15 && g > 120
        }
    }
}

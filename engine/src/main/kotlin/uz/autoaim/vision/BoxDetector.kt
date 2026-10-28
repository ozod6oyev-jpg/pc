package uz.autoaim.vision

import kotlin.math.max
import kotlin.math.min

/**
 * Nishon: qutining to'liq tanasi (barcha qatlamlar) va uning tepasidagi slot tirqishi.
 * [slot] — sharcha kirishi kerak bo'lgan qora tirqish; uning [Blob.left]..[Blob.right] — kirish teshigi.
 */
data class Target(val body: Blob, val slot: Blob?)

/**
 * Qutini (bir-birining ustidagi kartalar) va slotni topadi.
 *
 * Namuna kadrlardan o'lchangan tuzilish (tepadan pastga, `ggg.jpg`, x=765):
 *  - ingichka och hoshiya (~200), so'ng **slot** — qora tirqish ~(4,4,6), ~10 px balandlikda;
 *  - orqa qatlam ~(148,152,161), o'rta qatlam ~(172,177,183), oldingi karta ~(225,229,232);
 *  - qatlamlar orasida ~110–145 li ajratgichlar.
 * Shuning uchun tana = neytral kulrang, `min(RGB) > 95` bo'lgan eng katta bog'langan guruh
 * (faqat oldingi kartani emas, barcha qatlamlarni qamraydi).
 */
class BoxDetector {

    /** Zona ichida (x0, y0)–(x1, y1) qutini qidiradi; exclude — sharcha kabi chetlab o'tiladigan soha. */
    fun detect(image: Image, x0: Int, y0: Int, x1: Int, y1: Int, exclude: Blob? = null): Target? {
        val minArea = max(50, image.width * image.width / 2000)
        val body = Blobs.all(image, x0, y0, x1, y1, minArea) { isBoxBody(it) }
            .filter { exclude == null || !overlaps(it, exclude) }
            .filter { it.width > it.height / 2 } // yotiq (karta), tik ingichka narsalar emas
            .maxByOrNull { it.area } ?: return null
        return Target(body, findSlot(image, body))
    }

    /**
     * Tananing yuqori qismida, chap/o'ng chetlarga tegmaydigan eng keng yotiq qora guruh — slot.
     * Chetga tegadigan qora guruhlar — yumaloq burchaklardagi fon.
     */
    private fun findSlot(image: Image, body: Blob): Blob? {
        val sx0 = body.left + 1
        val sx1 = body.right - 1
        val sy0 = body.top + 1
        val sy1 = min(body.bottom - 1, body.top + max(6, body.height * 30 / 100))
        return Blobs.all(image, sx0, sy0, sx1, sy1, minArea = max(6, body.width / 10)) { isSlot(it) }
            .filter { it.left > sx0 && it.right < sx1 && it.top > sy0 }
            .filter { it.width >= it.height * 3 && it.width >= body.width / 5 }
            .maxByOrNull { it.width }
    }

    private fun overlaps(a: Blob, b: Blob) =
        a.left <= b.right && b.left <= a.right && a.top <= b.bottom && b.top <= a.bottom

    companion object {
        fun isBoxBody(c: Int): Boolean {
            val r = Image.red(c)
            val g = Image.green(c)
            val b = Image.blue(c)
            val lo = min(r, min(g, b))
            return lo > 95 && max(r, max(g, b)) - lo < 30
        }

        fun isSlot(c: Int): Boolean =
            max(Image.red(c), max(Image.green(c), Image.blue(c))) < 40
    }
}

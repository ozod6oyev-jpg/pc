package uz.autoaim.core

import android.content.Context

/** Sozlamalar (SharedPreferences). Keyingi bosqichlarda REJA 8-banddagi qiymatlar shu yerga qo'shiladi. */
class Config(context: Context) {

    private val prefs = context.getSharedPreferences("autoaim", Context.MODE_PRIVATE)

    /** Oxirgi skanerlangan o'yin zonasi (ekran px): chap, tepa, o'ng, past. Yo'q bo'lsa null. */
    var gameZone: IntArray?
        get() = prefs.getString(KEY_GAME_ZONE, null)
            ?.split(',')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 4 }?.toIntArray()
        set(value) = prefs.edit().putString(KEY_GAME_ZONE, value?.joinToString(",")).apply()

    /** Skanerlashdan keyin skrinshotni saqlash oynasi avtomatik ochilsinmi. */
    var askSaveScreenshot: Boolean
        get() = prefs.getBoolean(KEY_ASK_SAVE, true)
        set(value) = prefs.edit().putBoolean(KEY_ASK_SAVE, value).apply()

    /** Oxirgi saqlangan fayl — "Saqlash" oynasi keyingi safar shu papkada ochiladi. */
    var lastSaveUri: String?
        get() = prefs.getString(KEY_LAST_SAVE_URI, null)
        set(value) = prefs.edit().putString(KEY_LAST_SAVE_URI, value).apply()

    /** Uchish vaqti kalibrlash (jonli kadrlardan): 1 nuqta-qadam necha ms va otish kechikishi. 0 — noma'lum. */
    var flightMsPerStep: Float
        get() = prefs.getFloat(KEY_FLIGHT_MS_PER_STEP, 0f)
        set(value) = prefs.edit().putFloat(KEY_FLIGHT_MS_PER_STEP, value).apply()

    var flightLatencyMs: Float
        get() = prefs.getFloat(KEY_FLIGHT_LATENCY, 0f)
        set(value) = prefs.edit().putFloat(KEY_FLIGHT_LATENCY, value).apply()

    var testX: Float
        get() = prefs.getFloat(KEY_TEST_X, -1f)
        set(value) = prefs.edit().putFloat(KEY_TEST_X, value).apply()

    var testY: Float
        get() = prefs.getFloat(KEY_TEST_Y, -1f)
        set(value) = prefs.edit().putFloat(KEY_TEST_Y, value).apply()

    var testX2: Float
        get() = prefs.getFloat(KEY_TEST_X2, -1f)
        set(value) = prefs.edit().putFloat(KEY_TEST_X2, value).apply()

    var testY2: Float
        get() = prefs.getFloat(KEY_TEST_Y2, -1f)
        set(value) = prefs.edit().putFloat(KEY_TEST_Y2, value).apply()

    var testDelaySec: Int
        get() = prefs.getInt(KEY_TEST_DELAY, 5)
        set(value) = prefs.edit().putInt(KEY_TEST_DELAY, value).apply()

    var swipeDurationMs: Long
        get() = prefs.getLong(KEY_SWIPE_DURATION, 400L)
        set(value) = prefs.edit().putLong(KEY_SWIPE_DURATION, value).apply()

    var swipeHoldMs: Long
        get() = prefs.getLong(KEY_SWIPE_HOLD, 500L)
        set(value) = prefs.edit().putLong(KEY_SWIPE_HOLD, value).apply()

    companion object {
        private const val KEY_GAME_ZONE = "game_zone"
        private const val KEY_FLIGHT_MS_PER_STEP = "flight_ms_per_step"
        private const val KEY_FLIGHT_LATENCY = "flight_latency_ms"
        private const val KEY_ASK_SAVE = "ask_save_screenshot"
        private const val KEY_LAST_SAVE_URI = "last_save_uri"
        private const val KEY_TEST_X = "test_x"
        private const val KEY_TEST_Y = "test_y"
        private const val KEY_TEST_X2 = "test_x2"
        private const val KEY_TEST_Y2 = "test_y2"
        private const val KEY_TEST_DELAY = "test_delay_sec"
        private const val KEY_SWIPE_DURATION = "swipe_duration_ms"
        private const val KEY_SWIPE_HOLD = "swipe_hold_ms"
    }
}

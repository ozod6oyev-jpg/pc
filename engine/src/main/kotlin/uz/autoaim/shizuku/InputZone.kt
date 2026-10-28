package uz.autoaim.shizuku

/**
 * "Gorizontal chiziq" qoidasi (REJA 3.5): sharcha ostidan o'tgan chiziqdan TEPADA boshlangan
 * tegish — mo'ljal olish; chiziqdan PASTDA — o'yin qabul qilmaydi, biz ham yubormaymiz.
 *
 * Ekran koordinatalarida y pastga qarab o'sadi, shuning uchun "tepada" = y < lineY.
 */
data class InputZone(val lineY: Float) {

    @Suppress("UNUSED_PARAMETER") // x — keyinchalik yon chegaralar uchun
    fun contains(x: Float, y: Float): Boolean = y < lineY

    /** MOVE nuqtasi chiziqdan pastga tushib ketsa — chiziqning sal tepasiga qirqiladi. */
    fun clampY(y: Float): Float = if (y < lineY) y else lineY - 1f

    companion object {
        fun fromBall(ballCenterY: Float, ballRadius: Float, paddingPx: Float): InputZone =
            InputZone(ballCenterY + ballRadius + paddingPx)
    }
}

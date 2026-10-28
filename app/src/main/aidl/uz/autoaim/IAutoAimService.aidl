package uz.autoaim;

import android.os.ParcelFileDescriptor;

// Shizuku UserService interfeysi. Bu xizmat shell (uid 2000) yoki root jarayonida ishlaydi,
// shuning uchun INJECT_EVENTS huquqi bor va boshqa ilovaga (o'yinga) tegish yubora oladi.
interface IAutoAimService {
    // Shizuku talabi: xizmatni to'xtatish uchun maxsus tranzaksiya kodi.
    void destroy() = 16777114;

    // Shell buyrug'ini bajaradi; "exit=<kod>\n<chiqish>" qaytaradi (Variant B / sanity-test).
    String exec(String command) = 1;

    // Jarayon uid'i (2000 = adb/shell, 0 = root).
    int getUid() = 2;

    // Variant A: injectInputEvent orqali tegish. Hammasi true/false qaytaradi.
    boolean tap(float x, float y) = 3;

    // Barmoqni bosib turish: downTime ni qaytaradi (keyingi move/up uchun kerak), xato bo'lsa -1.
    long touchDown(float x, float y) = 4;
    boolean touchMove(long downTime, float x, float y) = 5;
    boolean touchUp(long downTime, float x, float y) = 6;

    // To'liq surish: (x1,y1) da bosish -> durationMs davomida (x2,y2) ga surish -> holdMs kutish -> qo'yib yuborish.
    boolean swipe(float x1, float y1, float x2, float y2, long durationMs, long holdMs) = 7;

    // Qaysi inject usuli ishlatilayotgani (diagnostika uchun).
    String injectorInfo() = 8;

    // Ekran rasmi (`screencap -p`, PNG) — quvur orqali, binder hajm chegarasiga urilmaslik uchun.
    // Sekin (~0.5–1 s): faqat o'yin zonasini skanerlash va debug uchun, asosiy sikl uchun emas.
    ParcelFileDescriptor screencap() = 9;

    // Ekranni videoga yozish (`screenrecord`, fonda). Jarayon raqamini qaytaradi, xato bo'lsa -1.
    int startScreenRecord(String path, int maxSeconds) = 10;

    // Yozuvni to'g'ri yakunlash (SIGINT — mp4 fayl yopiladi).
    void stopScreenRecord(int pid) = 11;
}

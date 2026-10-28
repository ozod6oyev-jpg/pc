# REJA — "AutoAim" (Shizuku orqali sharchani qutiga tushiruvchi Kotlin ilovasi)

> Bu fayl — **faqat reja**. Kod keyingi bosqichda yoziladi (`M0` dan boshlab).
> Holat: loyiha papkasida faqat 2 ta rasm bor (`ggg.jpg`, `jjg.jpg`), kod yo'q.

---

## 1. Maqsad

Android o'yinida (ekrandagi rasmda ko'rsatilgan offline o'yin) **pastdagi platformada turgan sharchani** tepadagi **qutining tepasidagi qora tirqish (slot) ichiga tushirish**.
Buni qo'lda emas, **avtomatik** qiluvchi Kotlin ilovasi yoziladi:

- Ekranni **o'zi ko'radi** (MediaProjection + rasm tahlili),
- sharchani **o'zi nishonga oladi** (drag bilan burchak/kuch),
- barmoq harakatini **Shizuku orqali** tizimga yuboradi (o'yinga oddiy tegish kabi ko'rinadi),
- **harakatlanuvchi quti** bilan ishlaydi. (To'siqlar hozircha rejadan chiqarildi — 3.7-band.)

Ilova — "aim bot" (avto-o'yinchi). O'yinning o'zi alohida ilova bo'lib qoladi; biz uni faqat boshqaramiz.

---

## 2. O'yin mexanikasi (rasmlardan olingan aniq tahlil)

`ggg.jpg` (1080×1394) va `jjg.jpg` (1080×1593) o'lchandi (numpy/PIL):

| Element | Ko'rinishi | O'lchangan qiymat | Izoh |
|---|---|---|---|
| Fon | deyarli qora, xira to'r chiziqlari | `RGB(15,15,17)`, to'r chizig'i `(16,16,18)` | fon chegarasi: `max(RGB) < 40` |
| Sharcha | yashil nurli (glow) sharcha, yadrosi oq | glow `RGB(125,211,181)`, yadro `(220,225,229)`, o'lcham ~51×46 px | yashil kanal dominant: `G > R+25 && G > B+15` |
| Platforma | sharcha ostidagi to'q kulrang "tava" | `RGB(41,44,48)` | pastdan tayanch |
| Quti | och kulrang, yumaloq, "karta/kitob" ko'rinishida, ikki qavatli (soya bilan) | `RGB(214,217,222)` | ekranning o'ng tomonida |
| **Nishon (slot)** | qutining **tepasidagi qora cho'zinchoq tirqish** | quti ichida `max(RGB) < 70` | sharcha shu yerga tushishi kerak |
| Traektoriya nuqtalari | kichik kulrang doiralar, yoy bo'ylab teng joylashgan | `RGB(148,155,153)`, ~10 px | **drag paytida** ko'rinadi |
| Yuqori panel | chapda `0`, o'ngda `THIS WEEK` + `15 pts` / `0 pts` | oq matn | ball hisoblagichi |

**Muhim xulosalar:**

1. Traektoriya yoyi **o'yinning o'zi tomonidan chiziladi** — drag qilganda. `ggg.jpg` da yoy baland chiqib qutining tepasiga yaqin tushgan, `jjg.jpg` da esa tekis chiqib nishonga yetmagan. Ya'ni bizda **o'yinning o'z "bashorati"** bor → undan foydalanamiz (3.3-band).
2. Nishon — **slot**: aniq nuqta, toleransi juda kichik ("1x1").
3. **Quti harakatlanadi** (bazida) — statik nishon emas.
4. ~~O'rtada to'siqlar paydo bo'ladi~~ — **hozircha hisobga olinmaydi**: foydalanuvchi 25 levelgacha o'ynadi, to'siq uchramadi → **P1 / keyingi bosqich** (3.7-band).
5. `ggg.jpg` da ko'rinadigan **asosiy xato turi**: yoy slotga emas, qutining **chap-yuqori burchagiga/devoriga** uriladi. Shuning uchun nishon — nuqta emas, **qutining butun geometriyasi** (3.4-band).

### Rasmlarni qayta tekshirish natijalari (tuzatishlar)
- **Slot koordinatasi tuzatildi** (`ggg.jpg`): quti x≈640–890, y≈508–666; slot teshigi **x≈710–822, y≈510–522** (avval x≈660–805, y≈555–585 deb yozilgan edi — noto'g'ri). `jjg.jpg`: quti x≈640–896, y≈636–796, slot y≈636–648.
- **`jjg.jpg` — devordan qaytish bor**: yoy quti **ostidan** o'tib, o'ng chetga (~x 978) urilib, chapga qaytadi ((901,917), (825,953)). Demak o'yinda devordan qaytish mavjud va preview uni ko'rsatadi → `DotDetector` nuqtalarni x bo'yicha emas, **yoy bo'ylab qo'shnilik** bo'yicha tartiblaydi va qaytish nuqtasida yoyni segmentlarga ajratadi.
- **`ggg.jpg` — ehtimol tushadi**: oxirgi nuqtalar (688,398)→(715,428)→(741,466) ni davom ettirsak, keyingisi ≈(768,512) — slot markazi, kirish burchagi ~60°. Preview yoyi **oxirigacha chizilmaydi** → tushish nuqtasi **parabola moslash (fit) bilan ekstrapolyatsiya** qilinadi.
- **Nuqtalar teng vaqt qadamida** (teng masofada emas): cho'qqida zichlashadi, `jjg` da gorizontal qadam doimiy (~77 px) → `t_flight ≈ n × dt` usuli o'rinli.
- **Uzoq nuqtalar xiralashadi** (`ggg` da juda xira) → `DotDetector` pastki yorqinlik chegarasi ~60 gacha tushiriladi.
- **O'yin maydoni — yumaloq ramka** (~x 42–1038), skrinshot o'lchamlari har xil (1394/1593) → koordinatalar **o'yin ramkasiga nisbatan** normallashtiriladi.
- **Chalg'ituvchilar**: sharcha platformasida ham qora tirqish bor; kartada chip bor → SlotDetector faqat **eng katta och-kulrang blob (quti)** ichida qidiradi.

### Ochiq (noma'lum) joylar
- **To'siq** — 25 levelgacha uchramagan, shuning uchun M0–M5 da **hisobga olinmaydi**; uchrasa, video bilan namuna olinadi (3.7-band, P1).
- **Qutining harakat qonuni noma'lum** — yo'nalishi/tezligi (gorizontal? vertikal? davriy?). Yozuv orqali o'lchanadi.
- "**1x1 qilib tushirish**" iborasi: rejada *"sharchani slotga aniq, xatosiz tushirish"* deb talqin qilindi (bir urinishda yoki minimal urinish bilan). Boshqa ma'no bo'lsa — aytilsin, rejaga tuzataman.

---

## 3. Texnik yechimlar (qarorlar)

### 3.1 Ekranni olish — MediaProjection + ImageReader ✅
- `MediaProjectionManager` + `VirtualDisplay` + `ImageReader` (`PixelFormat.RGBA_8888`), `maxImages = 2`.
- **Oxirgi kadr ustuvor** (latest-frame-wins): eski kadrlarni tashlab yuborish, bufer to'lib qolmasligi uchun.
- VirtualDisplay o'lchamini **ekran o'lchamidan kichikroq** qilib olish (masalan 1/2 yoki eng ko'pi 720p) → CV tezligi oshadi, aniqlik yetarli bo'ladi.
- Ruxsat dialogi Android 14+ da har sessiyada kerak. Buni kamaytirish uchun:
  - Foreground Service (`mediaProjection` turi) — Android 14+ majburiy, `FOREGROUND_SERVICE_MEDIA_PROJECTION` ruxsati;
  - **Eksperimental**: Shizuku orqali `appops set <pkg> PROJECT_MEDIA allow` → dialogni chetlab o'tish (eski versiyalarda ishlaydi, har qurilmada kafolat yo'q → zaxira: oddiy dialog).
- Zaxira (juda sekin, faqat debug uchun): Shizuku orqali `screencap -p` (~500–1000 ms/kadr — asosiy loopga yaroqsiz).

### 3.2 Kirish (barmoq harakati) — Shizuku + `injectInputEvent` ✅
Ikki variant bor, **ikkalasi ham rejaga kiradi**:

| Variant | Tezlik | Ishonchlilik | Qaror |
|---|---|---|---|
| **A.** `IInputManager.injectInputEvent()` — Shizuku `SystemServiceHelper` orqali `input` binderini olib, `ShizukuBinderWrapper` bilan o'ralgan holda chaqirish | ~5–20 ms | hidden API cheklovlariga e'tibor kerak | **birlamchi** |
| **B.** Shizuku `newProcess()` → `sh -c "input swipe x1 y1 x2 y2 ms"` | 200–600 ms | har qurilmada ishlaydi | **zaxira / sanity-test** |

**Amalda tanlangan yo'l (M0 da yozildi):** Shizuku **UserService** — ilovaning o'z klassi (`AutoAimUserService`) Shizuku tomonidan **shell (uid 2000)/root jarayonida** ishga tushiriladi; u yerda `INJECT_EVENTS` huquqi bor va `injectInputEvent` reflection orqali chaqiriladi (`InputManagerGlobal` → `InputManager` → `IInputManager` zaxira tartibida). Ilova u bilan o'z AIDL'i (`IAutoAimService`: `tap`, `touchDown/Move/Up`, `swipe`, `exec`) orqali gaplashadi. Variant B (`input tap`) ham shu xizmatdagi `exec` orqali.

Variant A uchun (dastlabki muqobil):
- AOSP'dan `IInputManager.aidl` (va `InputEvent` bilan bog'liq parcelable'lar) ilova ichiga kompilyatsiya qilinadi — reflection'dan ko'ra barqarorroq.
- Binder: `SystemServiceHelper.getSystemService("input")` → `ShizukuBinderWrapper`.
- `InputEvent` yaratish: `MotionEvent.obtain()` + `setSource(InputDevice.SOURCE_TOUCHSCREEN)`; down time / event time'ni `SystemClock.uptimeMillis()` asosida to'g'ri yuritish (`ACTION_DOWN`, `ACTION_MOVE`…, `ACTION_UP`).
- Android 9+ da hidden API ro'yxati muammo bo'lsa: `HiddenApiBypass` (yoki Shizuku'ning `SystemServiceHelper`) ishlatiladi.

**Nima uchun A shart:** o'yin drag paytida traektoriyani real vaqtda qayta chizadi — biz sekundiga bir necha MOVE yuborishimiz kerak, `input swipe` bilan bu imkonsiz (600 ms).

### 3.3 Nishonga olish — traektoriya nuqtalaridan foydalanish ✅ (tanlangan)
Fizikani taxmin qilish o'rniga **o'yinning o'z bashoratini o'qiymiz**:

1. Gorizontal chiziqdan **tepada** **DOWN** (barmoq bosib turiladi, 3.5-band).
2. Kadr olinadi → **nuqtalar (dots) topiladi** → yoy poliliniyasi quriladi.
3. **Maqsad funksiyasi** — nafaqat "qayerga tushadi", balki **qutining to'liq geometriyasi** hisobga olinadi (3.4-band):
   - **Qattiq shartlar (hard)** — biri bajarilmasa, bu drag umuman qabul qilinmaydi:
     1. tushish nuqtasi slot teshigi ichida (`± (slotTolerance − ballRadius)`) — "1x1" aniqlik;
     2. `yoy ∩ quti_tanasi = ∅` — yoy **qutining yon devorlari/tepa yuzasi/tagiga tegmaydi**;
     3. `kirish_burchagi ≥ entryAngleMinDeg` — slotga **tepadan tik** kirib tushadi;
     4. yoy ekran chegarasidan chiqmaydi va **quti ostidan o'tib ketmaydi**;
     5. *(P1 — hozircha o'chirilgan)* `yoy ∩ to'siq = ∅` — to'siq uchraganda yoqiladi (3.7-band).
   - **Yumshoq ball (soft)** — qanoatlantiruvchilar ichidan eng yaxshisini tanlash uchun:
     `score = w1·|tushish_x − slot_markaz_x| + w2·(devorga yaqinlik jarimasi) + w3·σ(kirish burchagi)`
     *(to'siq jarimasi — P1, keyinroq qo'shiladi)*
   - Yakuniy tanlov: qattiq shartlarni qanoatlantirgan drag'lar ichidan **eng kichik `score`**.
4. Drag vektori `(dx,dy)` shu maqsad funksiyasi bo'yicha **sozlanadi**:
   - **Raqamli Yakobian**: 3 ta sinov (baza, `+Δx`, `+Δy`) → `d(yoy)/d(drag)` ni chiziqli baholash → kerakli drag'ni **bir yo'la yechish** (5–10 urinish o'rniga 2–3).
   - Kerak bo'lsa keyin 1–2 marta mayda tuzatish (chiziqli model xato bersa).
5. **Barcha qattiq shartlar bajarilganda** va `score < ε` bo'lganda **UP** → sharcha uchadi.
6. Natija kadrlar bilan tekshiriladi (11-band) → xato bo'lsa qaytadan.

### 3.4 Nishon geometriyasi — **qutining yon tomonlari** hisobga olinadi ✅ (foydalanuvchi talabi)
Faqat "tushish nuqtasi" bilan cheklanmaymiz: yoy to'g'ri nuqtaga tushsa ham, sharcha **qutining yon devoriga urilib** qaytishi mumkin. Rasmda aynan shu holat ko'rinadi — `ggg.jpg` da yoyning oxirgi nuqtalari (~(695,455) → (750,510)) qutining **chap-yuqori burchagiga** borib uriladi; slot esa qutining **tepasida** (x≈710–822, y≈510–522 — 2-bo'limdagi tuzatishga qarang).

Demak nishon — **nuqta emas, geometrik obyekt**:

```
                        ┌─── slot (kirish teshigi) ───┐
   1) tepadan tik       │            ▼                │
      tushish kerak     │   ╔═════════════════════╗   │  ← quti TEPASI
                        │   ║     ███ slot ███    ║   │
   2) chap devordan ────┼──►║                     ║   │  ← chap/o'ng DEVORLAR
      o'tishi kerak     │   ║                     ║   │     (urilish taqiqlangan)
                        │   ╚═════════════════════╝   │  ← quti TAGI
   3) pastdan o'tish    │   ╳ (taqiqlangan)          │
      taqiqlanadi       └─────────────────────────────┘
```

**Qattiq shartlar (GeometryGate)** — yoy `P = [p0…pn]` poliliniyasi uchun:
1. **Teshikka tushish**: `slot_x0 + m ≤ tushish_x ≤ slot_x1 − m`, bu yerda `m = ballRadius + slotTolerancePx`.
2. **Tanaga urilmaslik**: `distance(P, quti_konturi) > ballRadius + wallClearancePx`; quti konturi — **yopiq poligon** (ikki qavatli "soya" birlashtiriladi), faqat slot teshigi ochiq.
3. **Burchakdan tepadan oshib o'tish**: `P` chap devor `x = box.left` ni kesganda `y ≤ box.top − (ballRadius + wallClearancePx)`.
4. **Pastdan o'tish yo'q**: slotga kirishdan **oldin** `y > box.bottom` va `box.left ≤ x ≤ box.right` sohasiga tushadigan nuqta bo'lmasin.
5. **Kirish burchagi**: slotga kirish lahzasi yoy bo'ylab **pastga qarab** bo'lishi kerak: `|dy| / |dx| ≥ tan(entryAngleMinDeg)` (default 55° ≈ 1.43) — yon tomondan "sirg'alib" kirmaydi.
6. **Ekran chegarasi**: yoy ekrandan chiqmaydi. *(to'siq sharti — P1, 3.7-band)*

**Yechish uchun evristika:** ballistik yoyda tik kirish uchun **cho'qqi (apex) slotning chap tomonida** bo'lishi kerak — `apex_x ≈ slot_x − k` (`k` quti balandligi va kirish burchagiga bog'liq, kalibrlanadi). Yakobian yechuvchisi shu evristikadan boshlanadi, keyin 1–2 mayda tuzatish bilan qattiq shartlarga keltiradi.

**Imkonsiz geometriya:** qanoatlantiruvchi drag topilmasa (masalan, quti vaqtincha noqulay holatda) — `waitWindowMs` davomida holat o'zgarishini kutamiz (quti harakatlanadi → yangi geometriya → qayta yechish).

### 3.5 "Gorizontal chiziq" qoidasi va mo'ljal olish usuli (foydalanuvchi talabi) ✅
O'yinning o'z boshqaruvi (foydalanuvchi tushuntirishi bo'yicha):
1. Sharchaning **pastidan** xayoliy gorizontal chiziq o'tkaziladi.
2. Chiziqdan **tepada** ekranning istalgan joyiga barmoq qo'yiladi va **bosib turiladi** (`DOWN`) — sharchaning o'ziga tegish shart emas.
3. Barmoq bosilgan holda **yuqoriga/pastga, o'ngga/chapga** suriladi (`MOVE`) — shu bilan yo'nalish va **uchish balandligi** boshqariladi; o'yin traektoriya nuqtalarini real vaqtda qayta chizadi.
4. Yoy nishonga mos kelganda barmoq **ekrandan uziladi** (`UP`) → sharcha uchadi.
5. Chiziqdan **pastda** boshlangan tegish ishlamaydi.

Kodda:
- `y_line = ball_center_y + ball_radius + pad` (masalan `pad = 40 px`; sozlanadi).
- **Chiziqdan tepada** boshlangan tegish = mo'ljal olish (aim). **Chiziqdan pastda** = e'tiborsiz (`ignored`, o'yinga umuman yuborilmaydi).
- Amalda: `DOWN` va barcha `MOVE` nuqtalari doim `y < y_line` zonada bo'ladi (MOVE chiziqdan pastga tushsa — `y_line − 1` ga qirqiladi).
- `DOWN` nuqtasi: chiziq ustidagi bo'sh joy, MOVE uchun har tomonga yetarli zaxira qoladigan qilib tanlanadi (default: sharcha ustida, `ball_y − 150 px`; `tapOriginOffsetPx` bilan sozlanadi).
- Barmoq siljishi ↔ yoy o'zgarishi bog'liqligi (yo'nalish teskarimi — "rogatka"mi, to'g'rimi; kuch siljish uzunligigami yoki barmoqning sharchaga nisbatan joyigami) **oldindan farazsiz** — Yakobian probe (3.3-band) buni har safar o'zi o'lchaydi.
- Foyda: pastdagi UI/tugmalar va tasodifiy tegishlar o'yinni buzmaydi; o'yin o'zi past zonani qabul qilmasa ham biz bekorga event yubormaymiz.
- Kodda: `AimZone.contains(x, y)` — bitta joyda, testlanadigan sof funksiya.

### 3.6 Harakatlanuvchi quti bilan ishlash (lead / oldindan olish) ✅
- Slot pozitsiyasi har kadrda topiladi → oxirgi 3–5 kadrdan `v_slot` (px/kadr) hisoblanadi.
- Uchish vaqti `t_flight`:
  - **usul 1 (arzon)**: `t_flight ≈ n_dots × dt`, bu yerda `dt` — nuqtalar orasidagi qadam (bir marta kalibrlanadi: haqiqiy otishni kadrlarda kuzatib, qo'nish vaqtini o'lchab).
  - **usul 2**: yoy uzunligi / tezlik (tezlik ham kalibrlanadi).
- Nishon sifatida **kelajakdagi** pozitsiya olinadi: `slot_pred = slot_now + v_slot × t_flight`.
- Yana qulay holat: quti chetga kelib to'xtagan/oyoq sekinlashgan paytni kutish (`release window`).

### 3.7 To'siqlar ⏸ — **P1, HOZIRCHA REJADAN CHIQARILDI**
> **Sabab:** foydalanuvchi 25 levelgacha o'ynadi va **hech qanday to'siqqa uchramadi**. Shu sababli M0–M5 ishlarida to'siq hisobga olinmaydi; bu bo'lim kelajak uchun "ilgak" sifatida saqlanadi (`obstacleEnabled = false`).
1. **Fon modeli**: statik fon (qora + to'r chiziqlari) bir marta yoki median bilan yig'iladi.
2. To'siq = fondan farq qiladigan, o'lchami yetarli bo'lgan bloklar (sharcha/quti/nuqtalardan tashqari).
3. `To'siq bbox` + yoy kesishuvi → **qattiq shart** (faqat P1 yoqilganda). Agar yoy hech qanday shaklda to'siqdan o'tmasa:
   - boshqa yoy oilasi sinaladi (yuqoriroq/tekisroq),
   - yoki quti/to'siq holati o'zgarishini kutish.
4. To'siq ko'rinishi qo'lga kirishi bilan **namuna (template)** sifatida saqlanadi va aniqroq moslashtirish ishlatiladi.

---

## 4. Arxitektura

```
┌──────────────────────────────── AutoAim (Kotlin, bitta modul) ────────────────────────────────┐
│                                                                                               │
│  MainActivity (UI)                                                                            │
│   ├─ Shizuku holati/ruxsat (Shizuku.requestPermission)                                        │
│   ├─ MediaProjection ruxsati + START/STOP                                                     │
│   ├─ Kalibrlash ekrani (chiziq balandligi, oldindan olish, koeffitsientlar)                    │
│   └─ Debug overlay (topilgan obyektlarni ekranda chizadi)                                     │
│                                                                                               │
│  AutoAimService (Foreground Service, type=mediaProjection)                                    │
│   ├─ ScreenSource    : VirtualDisplay + ImageReader ─► Frame (Bitmap + t)                     │
│   ├─ Vision          : BallDetector, SlotDetector, BoxDetector, DotDetector, ObstacleDetector  │
│   ├─ AimEngine       : holat mashinasi (8-band), probe/Yakobian/lead/ball bahosi              │
│   ├─ InputController : Shizuku + IInputManager (DOWN/MOVE/UP), AimZone qoidasi                │
│   └─ Recorder        : kadrlarni/hodisalarni log qilish (13-band)                             │
│                                                                                               │
│  core/: GameZone (ekrandan topilgan o'yin zonasi, koordinata masshtabi), Config (SharedPreferences), Logger  │
└───────────────────────────────────────────────────────────────────────────────────────────────┘
```

**Bog'lanish:** `ScreenSource → Vision → AimEngine → InputController → (o'yin) → yangi kadr → ...`
Bir aylanma (loop): **kadr olish → tahlil → drag yangilash → tekshirish**.

### Fayl tuzilishi (rejalashtirilgan)
```
app/src/main/java/uz/autoaim/
├── MainActivity.kt
├── AutoAimService.kt
├── shizuku/ShizukuManager.kt        // pingBinder, ruxsat, SystemServiceHelper
├── shizuku/InputController.kt       // injectInputEvent (Variant A), input-shell (Variant B)
├── shizuku/InputZone.kt             // gorizontal chiziq qoidasi (sof funksiya)
├── capture/ScreenSource.kt          // MediaProjection + ImageReader (latest frame)
├── vision/BallDetector.kt
├── vision/BoxDetector.kt            // quti konturi (4 devor) + SLOT teshigi
├── vision/DotDetector.kt            // traektoriya nuqtalari → poliliniya
├── vision/ObstacleDetector.kt       // (P1, ixtiyoriy) fon modeli + farq
├── vision/BackgroundModel.kt        // (P1, ixtiyoriy)
├── aim/AimEngine.kt                 // holat mashinasi
├── aim/JacobianSolver.kt            // raqamli Yakobian bilan drag hisoblash
├── aim/LeadPredictor.kt             // harakatlanuvchi nishon (lead)
├── aim/GeometryGate.kt              // qattiq shartlar: devor/teshik/burchak/chegara
├── aim/Scorer.kt                    // yumshoq ball funksiyasi
├── ui/OverlayView.kt                // debug chizish
├── core/Config.kt
├── core/Recorder.kt                 // kadr/log saqlash, sessiya papkalari
├── core/CsvWriter.kt                // CSV yozish (sof, testlanadi) — 9a
├── lab/LabActivity.kt               // Laboratoriya — 9b
├── lab/ResultsRepository.kt         // CSV → statistika
├── lab/{Stats,Replay,DetectorLab}Screen.kt, lab/{SweepExperiment,AbTest}.kt
├── lab/ZipExporter.kt               // CSV'lar → ZIP, SAF "Saqlash" oynasi (9a)
├── lab/SimActivity.kt               // maketni ekranda ko'rsatish (9b)
├── sim/{SimWorld,SimPhysics,SimRenderer,Scenario,SimFrameSource,SimInput,BatchRunner}.kt  // maket — sof Kotlin
└── app/src/main/aidl/android/hardware/input/IInputManager.aidl (+ parcelable'lar)
app/src/test/java/...                // sof funksiyalar uchun unit testlar (Vision, Scorer, InputZone)
docs/samples/{ggg.jpg,jjg.jpg}       // namuna kadrlar (ko'chiriladi)
docs/recordings/                     // yozuvlar, kadr namunalari
```

---

## 5. Detektorlar spetsifikatsiyasi (boshlang'ich chegaralar)

Barcha ishlov **kichraytirilgan kadrda** (masalan 1/2 masshtab) bajariladi; koordinatalar keyin real ekranga qayta masshtablanadi.

| Detektor | Algoritm (birinchi versiya) | Boshlang'ich chegaralar |
|---|---|---|
| **BackgroundModel** *(P1)* | median/persist kadrlar | fon `max(RGB)<40`; to'r chizig'i farqi juda kichik → e'tiborsiz |
| **BallDetector** | yashil glow: `G>R+25 && G>B+15 && G>120` → eng katta blob → markaz + radius | o'lcham 30–90 px (1080 kenglikda), `radius` glow bbox'idan |
| **BoxDetector** | och kulrang blob: `min(RGB)>170` → eng katta blob → **tashqi kontur (union)** va undan **4 devor** ajratiladi (chap/o'ng/tepa/tagi) | `RGB≈(214,217,222)`; ikki qavatli "soya" birlashtiriladi |
| **SlotDetector (NISHON)** | quti bbox'ining **yuqori 1/3** qismida `max(RGB)<70` bo'lgan eng katta yotiq blob → markazi + **kirish teshigi segmenti** (`[slot_x0, slot_x1]`, `y = slot_top`) | balandligi quti balandligining 5–15% |
| **DotDetector** | kulrang izotrop nuqtalar: `|R−G|<14 && |G−B|<14 && 60<R<235` → eng kichik bloblar (2–20 px) → markazlar ro'yxati → yoy bo'ylab qo'shnilik bilan tartiblash → devordan qaytishda segmentlarga bo'lish → parabola fit + ekstrapolyatsiya | `RGB≈(150,150,153)`, uzoqdagilari xiraroq |
| **ObstacleDetector** *(P1 — hozircha kerak emas)* | fon modelidan farq qiluvchi, sharcha/quti/nuqtalardan tashqari bloklar | piksel farqi > 25; blob maydoni > 400 px |

**Barqarorlik choralari:** kadrlar orasida iz qilish (tracking) → noto'g'ri bitta kadr natijani buzmasligi uchun; har detektor natijasi `confidence` bilan qaytariladi; `confidence < 0.6` bo'lsa kadr tashlanadi.

---

## 6. AimEngine holat mashinasi

```
IDLE ──(START)──► LOCATE ──(ball+slot topildi)──► PRESS (DOWN, chiziq ustida)
                    ▲                                   │
                    │                            (traektoriya nuqtalari ko'rindi)
                    │                                   ▼
             RESET kutish ◄── FAIL ── VERIFY ◄── RELEASE (UP) ◄── SOLVE/PROBE
                    │                        ▲                      │
                    │                        └── (score < ε) ───────┘
                    └──── SUCCESS (ball slotda) ──► IDLE (keyingi levelni kutish)
```
- **LOCATE**: sharcha qayta paydo bo'lishini kutish (level reset/respawn).
- **PRESS/PROBE**: DOWN + 2–3 sinov drag, Yakobian hisoblash.
- **SOLVE**: kerakli `(dx,dy)` yechiladi → MOVE'lar bilan shu holatga o'tiladi (o'yin preview'ni yangilaydi).
- **GEOMETRY-CHECK**: qattiq shartlar (3.4-band) tekshiriladi. Bajarilmasa — drag qayta sozlanadi; umuman imkoni bo'lmasa — quti/to'siq qulay holatga kelishini **kutish**.
- **TRACK**: quti harakati bo'lsa — yakuniy tuzatish + mos `release window` ni kutib UP.
- **VERIFY**: 0.5–2 s kadrlarni kuzatish → muvaffaqiyat mezonlari (11-band).
- **FAIL → RESET**: qayta urinish (maks. N marta; N = 5), keyin to'xtab foydalanuvchiga xabar.
- **Uzluksiz o'yin (level loop)**: foydalanuvchi 25+ levelga yetgan → level tugamaydi. Muvaffaqiyatdan keyin **yangi level** boshlanadi: sharcha va slot **qaytadan lokalizatsiya** qilinadi (eski koordinatalar ishlatilmaydi). Agar ekranda sharcha topilmasa (menyu/level-tugash oynasi) → `IDLE` da kutish + foydalanuvchiga xabar.
- **Xavfsizlik**: har doim `try/finally` ichida `ACTION_UP` yuborish — aks holda o'yin "barmoq bosib turibdi" holatida qolib ketadi.

---

## 7. Tezlik byudjeti (latency)

| Bosqich | Taxminiy vaqt |
|---|---|
| Kadr olish (MediaProjection, 1/2 masshtab) | 15–40 ms |
| Vision (5 detektor, kichraytirilgan kadr) | 5–15 ms |
| Drag yangilash + inject | 5–20 ms |
| **Bir aylanma jami** | **~40–90 ms** |
| Yakobian bilan otishga tayyorgarlik (3–5 aylanma) | **~0.3–0.6 s** |
| Lead bilan qo'shimcha tuzatish | +0.1–0.3 s |

Bu **sekin harakatlanuvchi qutiga** yetadi. Agar quti tez harakatlansa: (a) masshtabni kichraytirish, (b) Yakobian'ni tez-tez qayta hisoblamaslik, (c) "release window" (quti to'xtagan/o'zgargan payt) ni kutish.

---

## 8. Kalibrlash va sozlamalar (UI'dan boshqariladi)

`Config` (SharedPreferences + UI slayderlar):
- `linePaddingPx` — gorizontal chiziq balandligi (sharcha ostidan).
- `scale` — VirtualDisplay masshtabi (0.3–1.0), kadr va koordinata masshtabi.
- `ballRadiusPx`, `slotTolerancePx` (`ε` — "1x1" aniqligi).
- `wallClearancePx` — quti devoridan minimal masofa (default 6 px).
- `requireOverCorner` — chap-yuqori burchakdan **tepadan** oshib o'tish sharti (default `true`).
- `entryAngleMinDeg` — slotga kirish burchagining minimal tikligi (default ~55°).
- `waitWindowMs` — geometriya bajarilmasa kutish oynasi (quti harakatlanishi uchun).
- `probeStepPx`, `maxIterations`, `maxRetries`.
- `leadEnabled`, `flightTimePerDotMs` (kalibrlash natijasi).
- `obstacleEnabled` (default **`false`** — P1), `obstacleTemplate`.
- `tapOriginOffsetPx` — DOWN nuqtasini sharchadan siljitish (o'yin o'z qoidasiga mos).
- Debug: `drawOverlay`, `recordFrames`.

---

## 9. Debug va tekshirish (majburiy qism)

1. **Overlay** (SYSTEM_ALERT_WINDOW): topilgan sharcha, quti, slot, nuqtalar va gorizontal chiziq **o'yin ustida** ko'rsatiladi. Busiz "nega xato?" degan savolga javob topib bo'lmaydi.
2. **Recorder**: har urinish uchun kadrlar + log (`t, drag, score, detections`) → `docs/recordings/attempt_XX/`. CSV + bir necha PNG.
3. **Ofline rejim (test)**: yozib olingan kadrlarni Vision'ga berib, natijalarni **unit test**da tekshirish (qurilmasiz CD). Namuna: `ggg.jpg`/`jjg.jpg` → kutilgan sharcha/slot/dots koordinatalari.
4. **Sanity-test (Variant B)**: Shizuku orqali `input tap` bilan o'yinda umuman "tegish" ishlashini tasdiqlash.
5. Muvaffaqiyat mezoni (VERIFY): sharcha kadrda yo'qoladi va ~1.5 s ichida qayta paydo bo'lmaydi; **yoki** yuqoridagi `pts` matni o'zgaradi (o'zgargan piksel nisbati); **yoki** slot yonida vizual effekt/animatsiya.

---

## 9a. Natijalar CSV jurnali (foydalanuvchi talabi) ✅

Har bir otish, har bir sinov siljishi va har bir sessiya **CSV fayllarga** yozib boriladi — keyin tahlil (Excel/Sheets/Python) va laboratoriya (9b) shu fayllardan foydalanadi.

**Joylashuv — hammasi BITTA joyda (foydalanuvchi talabi):** barcha CSV fayllar bitta papkada — `Android/data/uz.autoaim/files/lab/` (sessiyalar bo'yicha papkalarga bo'linmaydi; har qatorda `session_id` ustuni bor, ajratish shu orqali). Kadrlar (PNG) — shu papkaning ichida `frames/`.

**ZIP:** barcha CSV'lar bitta ZIP faylga yig'iladi — `AutoAim_natijalar_<YYYY-MM-DD_HH-mm>.zip`:
- **sessiya tugaganda (STOP)** — ZIP tayyorlanadi va darhol tizimning **"Saqlash" oynasi (fayl tanlagich)** ochiladi: foydalanuvchi **papkani** (Yuklamalar, Documents, Drive, SD-karta …) va **fayl nomini** o'zi tanlaydi; nom maydonida tayyor taklif turadi — `AutoAim_natijalar_<YYYY-MM-DD_HH-mm>.zip` (o'zgartirsa bo'ladi);
  - texnik: Storage Access Framework — `ActivityResultContracts.CreateDocument("application/zip")` → tanlangan `Uri` ga `ZipOutputStream` bilan yoziladi (hech qanday xotira ruxsati kerak emas, Android 11+ da ham ishlaydi);
  - oxirgi tanlangan papka eslab qolinadi (keyingi safar oyna shu papkada ochiladi — `EXTRA_INITIAL_URI`);
  - saqlangandan keyin xabar: **"Saqlandi: <papka>/<nom>"** + **[Ochish]** (fayl menejerda ko'rsatish) va **[Ulashish]** (Telegram/Drive) tugmalari;
  - foydalanuvchi oynani **bekor qilsa** — ZIP yo'qolmaydi: ilova ichidagi `lab/zip/` da saqlanib qoladi, Laboratoriyada "Saqlanmagan ZIP'lar" ro'yxatidan keyin saqlash mumkin;
  - bot fon rejimida ishlab STOP bildirishnomadan bosilgan bo'lsa — oyna ochish uchun ilova oldinga chiqariladi (bildirishnoma: "Natijalarni saqlash");
- **qo'lda** — laboratoriyadagi **"ZIP qilish"** tugmasi (xuddi shu "Saqlash" oynasi ochiladi);
- ZIP ichida: `shots.csv`, `probes.csv`, `sessions.csv`, `configs.csv`, `errors.csv`, `sweep.csv`, `scenarios.csv` + `README.txt` (ustunlar izohi); kadrlar faqat "kadrlar bilan" belgisi qo'yilsa qo'shiladi (hajm katta bo'lmasligi uchun);
- Sozlamada `zipAskOnStop` (default `true`) — o'chirilsa, oyna ochilmaydi, ZIP oxirgi tanlangan papkaga avtomatik saqlanadi.

**Format qoidalari:** UTF-8, vergul ajratgich, birinchi qator — sarlavha; vaqt ISO-8601 (`2026-10-27T21:05:13.412`) + `t_ms` (uptime); o'nli kasr nuqta bilan (`Locale.ROOT`); koordinatalar **o'yin ramkasiga nisbatan** px va normallashgan (0–1); har qatordan keyin `flush()` (ilova qulasa ham ma'lumot yo'qolmaydi); fayllarga faqat qo'shib boriladi (append).

| Fayl | Bir qator = | Asosiy ustunlar |
|---|---|---|
| `shots.csv` | bitta otish | `shot_id, session_id, ts, level, attempt, mode(auto/manual/sweep), ball_x, ball_y, box_x0, box_y0, box_x1, box_y1, slot_x0, slot_x1, slot_y, box_vx, box_vy, down_x, down_y, drag_dx, drag_dy, pred_land_x, pred_land_y, entry_angle_deg, wall_clear_px, score, probes, solve_ms, total_ms, lead_used, t_flight_ms, result, conf_ball, conf_box, conf_dots, config_id` |
| `probes.csv` | bitta sinov siljishi (MOVE + kadr) | `shot_id, step, ts, finger_x, finger_y, drag_dx, drag_dy, n_dots, apex_x, apex_y, pred_land_x, pred_land_y, bounce(0/1), gate_ok, score, frame_lag_ms` |
| `sessions.csv` | bitta sessiya (yig'ma) | `session_id, start, end, app_version, device, shots, hits, hit_rate, avg_total_ms, misses_by_type, config_id` |
| `configs.csv` | sozlamalar to'plami (o'zgarganda) | `config_id, ts, linePaddingPx, scale, slotTolerancePx, wallClearancePx, entryAngleMinDeg, probeStepPx, leadEnabled, flightTimePerDotMs, …` |
| `errors.csv` | xato/ogohlantirish | `ts, session_id, shot_id, component, level, message` |

**`result` qiymatlari:** `HIT`, `MISS_WALL` (devorga urildi), `MISS_SHORT`, `MISS_LONG`, `MISS_UNDER` (quti ostidan o'tdi), `MISS_BOUNCE`, `NO_SOLUTION` (geometriya bajarilmadi), `TIMEOUT`, `ABORTED`, `UNKNOWN` (VERIFY aniqlay olmadi).

**Kadrlar (ixtiyoriy):** `recordFrames = true` bo'lsa, har otish uchun 3 ta PNG (`<shot_id>_aim.png` — UP oldidan, `_mid.png`, `_end.png`) — CSV dagi `shot_id` bilan bog'lanadi.

**Kodda:** `core/CsvWriter.kt` (sof, testlanadigan: ekranlash `"`, vergul, yangi qator), `core/Recorder.kt` (fayllarni ochadi/aylantiradi), `lab/ResultsRepository.kt` (CSV ni o'qib statistikaga aylantiradi).

---

## 9b. Laboratoriya — o'yin MAKETI (simulyator) (foydalanuvchi talabi) ✅

**Ma'nosi:** ilova ichida rasmlardagi o'yinning **maketi** (simulyatori) quriladi. Aim **haqiqiy o'yinda emas, avval maketda** — turli sharoit va vaziyatlarda tekshiriladi. Maketda har narsaning **aniq javobi (ground truth)** ma'lum: sharcha, quti va slot qayerda, sharcha aslida qayerga tushdi. Shuning uchun xato aniq o'lchanadi.

### 9b.1 Maket nimalardan iborat
| Qism | Haqiqiy o'yindagi kabi | Sozlanadi |
|---|---|---|
| **Sahna** | qora fon `RGB(15,15,17)` + xira gorizontal to'r chiziqlari, yumaloq ramka, yuqorida ball matni | ekran o'lchami / nisbati (1080×1394, 1080×1593, …) |
| **Sharcha + platforma** | yashil glow, oq yadro, to'q kulrang "tava" (tirqishli) | joyi, radiusi |
| **Quti (nishon)** | och kulrang, ikki qavatli, tepasida qora **slot** | joyi, o'lchami, slot kengligi |
| **Boshqaruv** | chiziqdan **tepada** bosib turish → surish → qo'yib yuborish (3.5) | drag → tezlik bog'liqligi (rogatka/to'g'ri, kuch koeffitsienti) |
| **Preview nuqtalari** | teng **vaqt** qadamida, uzoqlashgan sari **xiralashadi**, yoy oxirigacha chizilmaydi | nuqtalar soni, qadam, xiralashish |
| **Fizika** | ballistik yoy (gravitatsiya), ekran chetidan **qaytish**, quti devorlari/tepasiga urilish, slotga tushish | `g`, qaytish koeffitsienti, maks. tezlik |

Maket ranglari va o'lchamlari **5-bo'limdagi qiymatlarga** mos qilinadi — detektorlar maketda va haqiqiy o'yinda bir xil ishlashi uchun.

### 9b.2 Sharoitlar (ssenariylar)
Har ssenariy — parametrlar to'plami + `seed` (bir xil seed → aynan bir xil vaziyat, takrorlanadi):

| Guruh | Variantlar |
|---|---|
| **Joylashuv** | rasmdagidek (`ggg`, `jjg` presetlari), tasodifiy, quti chetga yaqin (faqat devordan qaytib tushadigan), quti juda baland/past |
| **Quti harakati** | yo'q; gorizontal tekis; sinusoidal (borib-kelish); vertikal; to'xtab-yuruvchi; tezlik: sekin / o'rta / tez |
| **Nishon qiyinligi** | slot keng / tor / juda tor ("1x1"), quti katta / kichik |
| **Kechikishlar** | kadr kechikishi 0–150 ms, input kechikishi 0–50 ms, kadr tashlab ketish (drop) %, FPS 15/30/60 |
| **Tasvir shovqini** | JPEG artefakt, yorqinlik o'zgarishi, xira nuqtalar, masshtab (0.5×) |
| **Fizika farqi** | `g` va kuch koeffitsientini ±10% o'zgartirish (bot buni Yakobian bilan o'zi moslashtira oladimi?) |
| **To'siqlar** *(P1)* | ilgak tayyor turadi, hozircha o'chiq |

### 9b.3 Ishlash rejimlari
1. **O'zim o'ynayman** — maketni qo'lda o'ynash (maket haqiqiy o'yinga o'xshashini his qilish uchun).
2. **Bot maketda (ichki sikl)** — maket kadrni to'g'ridan-to'g'ri `Vision` ga beradi, bot buyruqlari to'g'ridan-to'g'ri maketga boradi (Shizuku/MediaProjection kerak emas). Bot ekranda **real vaqtda** o'ynayotgani ko'rinadi, overlay bilan.
3. **Tezkor partiya (batch)** — ekransiz, vaqtni tezlashtirib, masalan **ssenariy × 100 otish** → natija jadvali. Bu rejim **sof Kotlin** — kompyuterda (shu sandbox'da) unit test sifatida ham ishlaydi → telefonsiz aim'ni tekshirish va yaxshilash mumkin.
4. **To'liq yo'l (end-to-end)** — maket alohida to'liq ekranli oynada ochiladi, bot uni **haqiqiy o'yindek** MediaProjection orqali ko'radi va Shizuku orqali bosadi → ekran olish + input yo'li ham tekshiriladi.

### 9b.4 Nimalar o'lchanadi (CSV ga — 9a)
- `shots.csv` ga `mode=sim`, `scenario_id`, `seed` va **haqiqat ustunlari**: `true_ball_x/y`, `true_slot_x0/x1/y`, `true_land_x/y`, `true_result`.
- **Detektor xatosi**: topilgan va haqiqiy koordinata farqi (px) — har detektor uchun.
- **Bashorat xatosi**: bot hisoblagan tushish nuqtasi va haqiqiy tushish nuqtasi farqi.
- **VERIFY to'g'riligi**: bot "tushdi" dedi ↔ aslida tushdimi.
- Yangi fayl `scenarios.csv` — har ssenariyning parametrlari; ZIP ga qo'shiladi.
- Lab ekranida **ssenariy × natija** jadvali: aniqlik %, o'rtacha vaqt, eng ko'p xato turi → bot qaysi sharoitda zaif ekani darhol ko'rinadi.

### 9b.5 Maketni haqiqiy o'yinga moslash (kalibrlash)
Maket qiymatlari (`g`, drag→tezlik, nuqta qadami) taxminiy. Haqiqiy o'yindan olingan **Supurish (sweep)** natijalari (bot otmasdan turli siljishlarda yoyni o'qiydi → `sweep.csv`) bilan maket fizikasi **moslashtiriladi** (eng kichik kvadratlar). Natija: maketdagi yoy ≈ haqiqiy yoy → maketdagi aniqlik haqiqiy o'yindagiga yaqin bo'ladi.

### 9b.6 Lab ekranining boshqa bo'limlari
| Bo'lim | Nima qiladi |
|---|---|
| **Statistika** | aniqlik %, xato turlari, ssenariy/config bo'yicha filtr (maket va haqiqiy o'yin alohida) |
| **Replay** | otishni tanlab, kadrlar ustida yoy, slot, probe qadamlarini qadamma-qadam ko'rish |
| **Detektor sinovi** | rasm/kadr yuklab overlay ko'rish, rang chegaralarini slayder bilan sozlash |
| **Supurish (haqiqiy o'yinda)** | 9b.5 uchun ma'lumot yig'ish |
| **A/B** | ikki config profilini bir xil ssenariy+seed larda solishtirish |
| **ZIP / tozalash** | barcha CSV'lar bitta ZIP ga (9a) |

### 9b.7 Kodda
Maket **Android'ga bog'liq emas** (sof Kotlin) — kompyuterda test qilish uchun:
- Umumiy interfeyslar: `FrameSource` (haqiqiy: `ScreenSource`; maket: `SimFrameSource`) va `InputSink` (haqiqiy: `InputController`; maket: `SimInput`). `AimEngine` qaysi biri ekanini bilmaydi.
- `Vision` `Bitmap` bilan emas, oddiy `Image(w, h, IntArray)` bilan ishlaydi → JVM testlarda ham ishlaydi.
- Fayllar: `sim/SimWorld.kt` (holat), `sim/SimPhysics.kt` (yoy, qaytish, quti to'qnashuvi, slot), `sim/SimRenderer.kt` (sahnani `Image` ga chizish), `sim/Scenario.kt` (+ presetlar), `sim/SimFrameSource.kt`, `sim/SimInput.kt`, `sim/BatchRunner.kt`, `lab/SimActivity.kt` (ekranda ko'rsatish), `lab/LabActivity.kt` (bo'limlar).

---

## 10. Ishlash bosqichlari (milestones) va "tayyor" mezonlari

| # | Bosqich | Tayyor mezoni (tekshiriladi) |
|---|---|---|
| **M0** | Loyiha skeleti: Kotlin + Gradle, `minSdk 26`, `targetSdk 34`; Shizuku API (`dev.rikka.shizuku:api` + `:provider`), Shizuku ruxsat oqimi; `input tap` bilan oddiy tegish sinovi | Telefonda APK o'rnatiladi; Shizuku holati ko'rinadi; ruxsat so'raladi; `input tap` o'yinda ishlaydi |
| **M1** | MediaProjection + Foreground Service + overlay | Ekran ustida jonli kadr/FPS ko'rinadi; 1/2 masshtabda barqaror |
| **M2** | Vision: Ball/Box/Slot/Dot + overlay chizish; `ggg.jpg`/`jjg.jpg` bo'yicha unit testlar | Overlay o'yindagi sharcha/slot/nuqtalarni to'g'ri belgilaydi |
| **M3** | `InputController` (Variant A) + `InputZone` (gorizontal chiziq) | DOWN→MOVE→UP ketma-ketligi o'yinda traektoriyani ko'rsatadi; chiziq ostidagi tegish hech narsa qilmaydi |
| **M4** | AimEngine v1: probe + Yakobian + **GeometryGate** + Scorer (quti **statik**, to'siqsiz) | Slotga tushadi (10 urinishda ≥8 marta); **10/10 otishda quti devoriga urilmaydi**, "pastdan o'tish" 0 marta |
| **M5** | LeadPredictor (quti **harakatlanadi**) | Harakatlanuvchi qutiga tushadi; `flightTimePerDotMs` kalibrlanadi; harakat paytida ham devor clearance saqlanadi |
| ~~**M6**~~ ⏸ | *(P1 — hozircha bekor)* ObstacleDetector + yoydan qochish | To'siq uchraganda: yoy to'siqdan o'tib, devorga urilmasdan slotga tushadi |
| **M7** | Barqarorlik: retry/reset, muvaffaqiyat aniqlash, log/recording, sozlash UI | Uzluksiz 10 daqiqa ishlaydi; xato holatlar logda ko'rinadi |
| **L1** *(M0 dan keyin, M1 bilan)* | `CsvWriter` + `Recorder` + `ZipExporter` (9a): CSV'lar bitta papkada; STOP → "Saqlash" oynasi | STOP bosilganda oyna ochiladi, tanlangan papkada nomlangan ZIP paydo bo'ladi; bekor qilinsa ZIP ilova ichida qoladi |
| **S1** *(M2 dan oldin)* | Maket: `SimWorld` + `SimPhysics` + `SimRenderer` + "O'zim o'ynayman" (9b.1, 9b.3-1) | maketni qo'lda o'ynab bo'ladi; maket kadri rasmlarga o'xshaydi |
| **S2** *(M2 bilan)* | Vision maketda va `ggg/jjg` da; detektor xatosi o'lchanadi (9b.4) | maket kadrlarida detektor xatosi < 3 px |
| **S3** *(M4 bilan)* | "Bot maketda" + **Tezkor partiya** + ssenariylar (9b.2, 9b.3-2,3); `scenarios.csv` | statik presetlarda ≥ 95% tushish; batch kompyuterda unit test sifatida ishlaydi |
| **S4** *(M5 bilan)* | Harakatlanuvchi quti, kechikish, shovqin ssenariylari | har ssenariy bo'yicha jadval; zaif joylar CSV da ko'rinadi |
| **S5** *(M7 bilan)* | Maketni haqiqiy o'yinga moslash (sweep, 9b.5), "To'liq yo'l" rejimi, A/B, Statistika, Replay | maket yoyi ↔ haqiqiy yoy farqi kichik; A/B natijasi yonma-yon |

---

## 11. Xatarlar va yumshatish choralari

| Xatar | Ta'sir | Yumshatish |
|---|---|---|
| MediaProjection ruxsati har safar so'raladi (Android 14+) | avtomatik ishga tushirish qiyin | Foreground Service'ni tirik tutish; `appops` trick (eksperimental); qayta ruxsat tugmasi |
| `injectInputEvent` hidden API cheklovi | input umuman ishlamaydi | AIDL'ni ilovaga kompilyatsiya qilish; `HiddenApiBypass`; Variant B zaxira |
| Lag/jitter: kadr inputdan kech keladi | nishon xato hisoblanadi | Vaqt belgilarini yuritish (event time vs frame time); "latest frame" strategiyasi; masshtabni kichraytirish |
| Quti **juda tez** harakatlanadi | lead xato beradi | Ekstremal holatda "to'xtashini kutish" strategiyasi; masshtabni pasaytirish; preview nuqtalaridan bevosita tekshirish |
| Sharcha qutining **yon devoriga** urilib qaytadi, yoki **ostidan o'tib** ketadi | otish muvaffaqiyatsiz, level qotib qoladi | `GeometryGate`: `y(box.left) ≤ box.top − (ballRadius+clearance)`, `yoy∩tana = ∅`, kirish burchagi ≥ `entryAngleMinDeg`, "pastdan o'tish" taqiqi (3.4-band) |
| To'siq uchrashi (hozircha uchramagan) | yozilgan yoy to'siqqa uriladi | `obstacleEnabled` bayrog'i va `ObstacleDetector` "ilgagi" tayyor turadi; o'yin yangilansa yoki yangi levelarda uchrasa — yoqiladi |
| O'yin o'z qoidasi bo'yicha chiziq ostidagi tegishni e'tiborsiz qoldiradi | bekorga eventlar | `InputZone` bilan doim chiziq **ustidan** boshlash |
| `ACTION_UP` yuborilmay qolishi | o'yin "qotib" qoladi | `try/finally` majburiy UP + keyingi aylanmada DOWN holatini tekshirish |
| Batareya/termal throttle, ekran o'chishi | loop sekinlashadi | `FLAG_KEEP_SCREEN_ON`, batareya optimizatsiyasidan chiqarish, FPS monitoring |

---

## 12. Kerakli resurslar / ruxsatlar

**Telefonda:**
- Shizuku o'rnatilgan va ishlab turibdi (ADB yoki root orqali ishga tushirilgan) — foydalanuvchi tasdiqladi ✅
- Ilovaga beriladi: **Shizuku ruxsati**, **Ekran yozib olish (MediaProjection)**, **Overlay (SYSTEM_ALERT_WINDOW)**, **Notification/foreground service**
- Android 11+ (tavsiya: 12–14), o'yin ilovasi o'rnatilgan
- Batareya optimizatsiyasidan chiqarish + ekran o'chmasligi

**Ishlab chiqish muhiti (bu sandbox) — o'rnatildi va tekshirildi ✅**

| Komponent | Holat | Joy |
|---|---|---|
| JDK | OpenJDK 17.0.20.1 ✅ | `/usr/lib/jvm/java-17-openjdk-amd64` |
| Android cmdline-tools | 19.0 (13114758) ✅ | `$HOME/Android/Sdk/cmdline-tools/latest` |
| Android SDK Platform | android-34 ✅ | `$HOME/Android/Sdk/platforms/android-34` |
| Build-Tools | 34.0.0 ✅ | `$HOME/Android/Sdk/build-tools/34.0.0` |
| Platform-Tools (adb) | 37.0.1 ✅ | `$HOME/Android/Sdk/platform-tools/adb` |
| Gradle | 8.9 (Kotlin 1.9.23) ✅ | `$HOME/gradle-8.9/bin/gradle` |
| Litsenziyalar | `sdkmanager --licenses` qabul qilindi ✅ | `$HOME/Android/Sdk/licenses` |

**Toolchain sinovi (haqiqiy qurilish):** vaqtinchalik loyihada (`/tmp/sdkcheck`, loyiha kodi emas) `AGP 8.5.2 + Gradle 8.9 + compileSdk 34 + JDK 17` bilan `gradle assembleDebug` → **BUILD SUCCESSFUL** (35 s), APK: `/tmp/sdkcheck/app/build/outputs/apk/debug/app-debug.apk` ✅
Ya'ni M0 dan boshlab shu muhitda to'g'ridan-to'g'ri APK qurish mumkin.

Har sessiyada ishlatiladigan buyruqlar:
```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/platform-tools:$HOME/gradle-8.9/bin:$PATH"
# loyiha ildizida bir marta: echo "sdk.dir=$ANDROID_HOME" > local.properties
gradle assembleDebug     # yoki bir marta: gradle wrapper --gradle-version 8.9
```
> Telefon bu muhitga USB bilan ulanmagan → APK'ni foydalanuvchi o'zi o'rnatadi (`adb install` yoki faylni ko'chirib).

---

## 13. Keyingi qadam (kod yozishga o'tish tartibi)

1. ~~Qurish muhitini hal qilish~~ → **bajarildi** (12-band: SDK + Gradle o'rnatildi, qurilish sinovi o'tdi ✅).
2. ~~`docs/samples/` ga rasmlarni ko'chirish; o'yin paketi~~ → bajarildi; paket kerak emas — zona ekrandan topiladi.
3. **Harakatlanuvchi quti** haqida qisqa video/yozuv (yoki 3–5 skrinshot) — `BoxDetector`, slot/predmet koordinatalari va lead kalibrlash shunga moslanadi. *(To'siq endi P1 — kutilmaydi.)*
4. `.gitignore`: `build/`, `.gradle/`, `local.properties`, `*.apk`.
5. M0 → M1 → M2 ketma-ketligida kod yozish (har bosqich oxirida mezon bo'yicha tekshirish).

### Holat (2026-10-27)
- **M0 — kod yozildi va qurildi** ✅: Gradle loyiha (AGP 8.5.2, Kotlin 1.9.24), Shizuku 13.1.5 ruxsat oqimi, `AutoAimUserService` (shell jarayonida inject), o'yinni ro'yxatdan tanlash, 3 ta sinov (`input tap`, inject tap, bosib-surish), `InputZone` + 4 unit test (o'tdi). APK: `AutoAim.apk`.
- **Telefonda tekshirish kutilmoqda**: M0 mezoni (Shizuku holati ko'rinadi, ruxsat so'raladi, o'yinda tegish ishlaydi).
- **O'yinni tanlash yo'q** (foydalanuvchi talabi): ilova **o'yin zonasini ekrandan o'zi topadi** — `GameZoneDetector` (pastga qarang). O'yin paketi umuman kerak emas.
- **`:engine` moduli** (sof Kotlin/JVM): `vision/` (`Image`, `Blob`, `GameZoneDetector`), `InputZone`. Kompyuterda test qilinadi; maket (sim) va aim ham shu yerga yoziladi.
- **Ilova ko'rinishi**: o'yin uslubidagi ikonka (sharcha → nuqtali yoy → kartadagi slot), to'q mavzu, kartochkali ekran.

### Yoy, bekor qilish va harakatlanuvchi qutilar (foydalanuvchi ma'lumoti, 2026-10-28) ✅
- **Yoy barmoqqa ergashadi** (bosib turib yuqoriga/pastga surilsa o'zgaradi) → bot otmasdan turib "qo'yib yuborsam qayerga tushadi" ni o'qiydi.
- **Yoy to'liq chizilmaydi**: to'g'ri yo'nalish, cho'qqi va tushishning boshlanishi; qutining og'zigacha emas → davomi **parabola bilan hisoblanadi**.
- **Bekor qilish ishlaydi**: kuch deyarli nol bo'lsa (sharcha tirqishdan chiqmaydigan darajada) qo'yib yuborish otmaydi → bot sinovdan keyin barmoqni **aynan DOWN nuqtasiga qaytarib**, yoy yo'qolganini tekshirib, keyin UP qiladi (yurak sarflanmaydi).
- **1–2 ta to'g'ri otishdan keyin** xuddi shu o'lchamdagi qutilar **gorizontal tebrana** boshlaydi → qutining tezligi/harakat qonuni va sharchaning uchish vaqti **to'liq fizika bilan** hisoblanadi (pastga qarang).

### Yoyni o'qish — `DotDetector` + `TrajectoryFitter` ✅
- Nuqtalar teng **vaqt** qadamida: gorizontal qadam doimiy (real/ggg 26.9 px, jjg 76.4 px), ikkinchi farq — gravitatsiya ≈ **8.3 px/qadam²** (har otishda bir xil; jjg rasmi ~4% kattalashtirilgan).
- Zanjir sharchadan boshlab **bashorat** bilan quriladi (oldingi qadam + gravitatsiya + devordan qaytish), yo'qolgan nuqtalar ustidan 3 qadamgacha sakraydi; devor = zona cheti ∓ sharcha radiusi (jjg: 1037 − 27 = 1010 — o'lchangan qaytish nuqtasi bilan bir xil).
- Eng kichik kvadratlar: `xu = x0 + vx·t`, `y = y0 + vy·t + g·t²/2`; xato **0.1–0.5 px**.
- Natijalar: real_01 → slot tepasida x = 768.6 (slot 711–820) ✅; ggg → 768.2 ✅; jjg → 1 qaytish, cho'qqi quti tagidan pastda → yetmaydi ❌.
- Ilovada: skanerlash rasmida nuqtalar, parabolaning davomi, "TUSHISH" belgisi va "TUSHADI / CHETIGA / TUSHMAYDI" xulosasi.

### GeometryGate — sharchaning butun yo'li radiusi bilan ✅
- O'yin yoyni **qutini e'tiborsiz** chizadi (yoy quti ichidan "o'tib" ketadi) → to'qnashuv o'zimiz hisoblanadi.
- Quti kesmalari: chap yon, o'ng yon, tag, slotning ikki yonidagi ust qismlar, slot qirralari. Sharcha (radius r) yo'l bo'ylab 0.02 qadam bilan kuzatiladi; devorlardan qaytish hisobga olinadi; `boxShiftX` — harakatlanuvchi quti uchun.
- Natijalar: TUSHADI, QIRRAGA TEGADI, QUTI USTIGA, CHAP/O'NG YONGA URILADI, TAGIGA URILADI, YETMAYDI, OSHIB KETADI, OSTIDAN O'TADI.
- 6 ta asl telefon skrinshoti (`docs/samples/real_*.png`) — hammasi to'g'ri: odatiy ×2 → TUSHADI (+3 px), tik → YETMAYDI, past → TAGIGA, kuchli → OSHIB KETADI, yonga → CHAP YONGA.
- Slotga sig'ish: sharcha diametri 52, slot 110 → markaz uchun ruxsat ±29 px.
- **Kuzatuv**: tegilmagan ("dam olish") yoyida gravitatsiya 8.30 px/qadam², barmoq bilan tortilganda **8.59** (4 rasmda bir xil) — bot doim tortilgan holatdagi yoyni ishlatadi; haqiqiy uchish vaqti alohida kalibrlanadi.

### Otmasdan mo'ljal olish — `AimController` + `AimRunner` ✅ (telefonda sinov kutilmoqda)
Paneldagi **«🎯 Mo'ljal olish (otmasdan)»**:
1. Kadr → sharcha, quti, slot. Sharchadan tepada (chiziq ustida, zona balandligining 11%) barmoq bosiladi.
2. **Yo'nalishni aniqlash**: (0,+100), (0,−100), qiya va yon tomonlar — yoy o'ngga-yuqoriga chiqqan birinchisi tanlanadi.
3. **2 ta sinov siljishi** (+30 px x va y bo'yicha) → drag → uchish tezligi chiziqli modeli (Yakobian, oxirgi 5 sinov, eng kichik kvadratlar).
4. **Maqsad tezlik**: uchish vaqti T bo'yicha qidiruv; GeometryGate ±8 px siljishda ham TUSHADI, kirish burchagi ≥ 50°, eng kichik T.
5. **Yopiq sikl**: drag → kadr → haqiqiy yoy → GeometryGate; |siljish| ≤ 4 px bo'lguncha (ko'pi bilan 4 aniqlashtirish).
6. **Bekor qilishdan oldin chizmalar bilan skrinshot** (foydalanuvchi talabi) → `Pictures/AutoAim/AutoAim_mojal_<vaqt>.png` (+ `_asl.png`); dialog ochilmaydi (barmoq bosilgan — o'yin ustida oyna ochilsa otilib ketishi mumkin).
7. **Bekor qilish**: barmoq aynan bosilgan nuqtaga sekin qaytadi → 300 ms → UP (xato bo'lsa ham UP majburiy). So'ng sharcha joyida qolgani tekshiriladi.
8. Har qadam CSV: `Download/AutoAim/AutoAim_mojal_<vaqt>.csv` (drag, sharcha, nuqtalar, vx, vy, g, rms, natija, siljish).
- Bu vaqtda panel ko'rinmaydi va tegishlarni o'tkazib yuboradi (bot tegishlari o'yinga borishi uchun).
- **1-telefon sinovi (04:10)**: bekor qilish ishladi, lekin **tegishlar o'yinga yetmadi** — yoy va sharcha tinch holatdagidek qoldi (16 nuqta, +3.4 px), model teskarilanmadi. Tuzatishlar:
  - inject hodisasi endi haqiqiy barmoqdek: `TOOL_TYPE_FINGER`, bosim/o'lcham 1, haqiqiy sensor ekran qurilmasi (o'yin — veb/Telegram Mini App ko'rinishida);
  - har kadr **tinch holatga solishtiriladi** (sharcha > 3 px siljidimi, yoy tezligi > 1.5 o'zgardimi); javobsiz kadrlar modelga kirmaydi;
  - 2 kadr javobsiz → bekor qilib, **ikkinchi usul**: `input touchscreen motionevent DOWN/MOVE/UP` (tizim buyrug'i); hisobotda qaysi usul ishlagani yoziladi.
- **2-telefon sinovi (04:16)** — tegish **ishladi** (sharcha tortilib siljidi, yoy o'zgardi). Yangi faktlar:
  - **Rogatka**: barmoq o'ngga-pastga → sharcha chapga-yuqoriga.
  - **Bosqichlar**: to'g'ri tushgandan keyin sharcha o'sha kartada qoladi (karta — yangi platforma), yangi nishon-karta boshqa joyda (chapda ham) paydo bo'ladi → yo'nalish nishon tomonga (chap/o'ng) tanlanadi.
  - **Ruxsat etilgan soha** (foydalanuvchi chizib ko'rsatdi): zona ichida, sharchaning **pastki chetidan tepada** — istalgan joyidan bosib mo'ljal olish mumkin. Avval chiziq sharchadan 40 px pastda edi → barmoq o'lik zonaga tushgan (y 1432 > 1403).
  - Tuzatish: chiziq = sharcha pastki cheti − 4 px; bosish nuqtasi — sohaning **o'rtasi** (har tomonga ~450 px tortish joyi); sinov masofasi 150 px, qadam 40 px, maks. drag ~450 px, 6 aniqlashtirish.
  - `real_07_chap_nishon.png` — testga qo'shildi (natija: O'NG YONGA URILADI — yoy past, tortish joyi yetmagan edi).
- **3-telefon sinovi (04:30) — ✅ BIRINCHI MUVAFFAQIYAT**: 8 qadamda MO'LJAL TAYYOR, TUSHADI, slot markazidan **+2.6 px** (ruxsat ±28), bekor qilish ✅ (`analizlar/`).
  - O'lchangan bog'liqlik (qora quti, CSV'dan): **vx ≈ −0.383·dx + 7.9, vy ≈ −0.337·dy** px/qadam — toza rogatka, o'qlar mustaqil; g = 8.58–8.59 har kadrda.
  - Yuqoriga tortish (dy < 0) — yoy yo'q; tik tortish (dx = 0) — 4 nuqtali buzuq yoy (g = −11.4) → model buzilgan (7-qadam xato).
  - Tuzatish: yoy ishonchliligi (≥ 5 nuqta, rms ≤ 1.5, g ∈ [6, 11]); yo'nalish sinovi rogatka taxminidan boshlanadi (nishonga teskari + pastga). Telefon modeliga kalibrlangan testda — **4 qadam**.
  - Har qadam ~1 s (screencap 0.5–0.77 s) — harakatlanuvchi quti uchun MediaProjection kerak.
- **4-telefon sinovi (04:39, yangi APK)**: **5 qadamda** MO'LJAL TAYYOR — TUSHADI **−0.8 px**, bekor qilish ✅. Yo'nalish birinchi urinishda topildi (rogatka taxmini); hisoblangan mo'ljal darhol TUSHADI (−11.6 px), 1 aniqlashtirish → −0.8 px. Kadr ~0.45–0.47 s.
- Soxta o'yin testlari: rogatka va teskari bog'liqlik, chiziqli bo'lmagan, sharcha tortganda siljiydi → 5–6 kadrda TUSHADI (siljish ≤ 4 px).

### Avtomatik o'yin — `AutoPlayRunner` (foydalanuvchi talabi) ✅ (telefonda sinov kutilmoqda)
Panel: **«🔥 O'ynash (haqiqiy otish)»** — ikki marta bosib tasdiqlanadi (yurak sarflanadi). Jarayon davomida ekran pastida holat va **⏹ To'xtatish** (zonadan tashqarida).
1. Har bosqich: 2 kadr (~0.5 s oraliq) — slot > 4 px siljisa, 3-kadr bilan tasdiqlanadi (sahna surilish animatsiyasi emasligi uchun).
2. **Harakatsiz** → `AimRunner(fire = true)`: mo'ljal → oxirgi kadr yana TUSHADI bo'lsa, barmoq **o'sha joyda qo'yib yuboriladi**. Xato/to'xtatish → bekor qilinadi.
3. Otishdan keyin 3 s kadrlar: yoy ustidagi sharcha → **1 nuqta-qadam = ? ms** (uchish vaqti kalibrlash); natija — sharcha eski slot x ida va nishon yangi joyda (to'g'ri tushganda sahna suriladi: karta platforma o'rniga tushadi) → TUSHDI; aks holda to'xtaydi.
4. **Harakatlanuvchi quti** → otish to'xtaydi, **`screenrecord` video** (`Movies/AutoAim/AutoAim_harakat_<vaqt>.mp4`, Shizuku orqali), 6 s slot markazini kuzatish (oraliq, tezlik, burilishlar, taxminiy davr), so'ng mo'ljal olib **bekor qilish** (pauza), video yakunlanadi.
5. CSV: `Download/AutoAim/AutoAim_oyin_<vaqt>.csv` (har kadr: vaqt, sharcha, slot, quti, nuqtalar; natija qatorlari) + har otishning mo'ljal rasmi/CSV'si.
6. To'xtaydi: harakatlanuvchi quti, tushmadi, 10 ta otish, mo'ljal olinmadi yoki foydalanuvchi.
- **1-sinov (04:58)**: darhol "zona / sharcha / slot topilmadi". Sabab: pastdagi To'xtatish oynasining **yashil ramkasi** (#2E8F6E) sharcha nuri shartiga mos va sharchadan katta → "sharcha" deb olingan. Tuzatish: oyna/panel ramkasi neytral kulrang; `BallDetector` faqat sharcha o'lchamidagi (ekran kengligining 1/60…1/10) va dumaloqqa yaqin (balandlik/kenglik 0.4…1.3) guruhni oladi; regressiya testi qo'shildi.

- **2-sinov**: harakatsiz qutilarda **4 bosqich ketma-ket to'g'ri** ✅; 5-bosqichda harakatlanuvchi quti → otish to'xtadi, pauza mo'ljali quti siljigani uchun yaqinlashmadi (kutilgan holat).

### Jonli rejim (M1) — `CaptureService` + `LiveFrames` ✅ (telefonda sinov kutilmoqda)
- Panel: **«📡 Jonli ko'rishni yoqish»** → tizim ruxsati (butun ekran) → foreground xizmat (mediaProjection) → VirtualDisplay → ImageReader RGBA_8888, **to'liq o'lcham** (koordinatalar screencap bilan bir xil), kadr vaqti = `uptimeMillis` soati.
- **Analizatorlar** — har kadrda, nusxasiz, buferdan: `BoxRowTracker` (quti tanasidan bitta qator → slot x; kompyuterda 0.014 ms/kadr), `BallFinder` (zona ichida yashil nur, qadam 2).
- **Snapshot** — to'liq kadr (`ZoneScanner.scan` avval jonli kadrni oladi, 250 ms ichida kelmasa — screencap).
- **Harakat qonuni** (`MotionFitter`): harakatsiz / sinusoida (davr — o'rta chiziqni kesishlardan + to'r, (c, a, b) chiziqli EKK) / doimiy tezlikda borib-kelish (tezlik — median, faza — qidiruv); eng kichik xato tanlanadi. Sun'iy testlar: davr 2400 → 2399 ms, tezlik 400 → 401 px/s.
- **Uchish vaqti** (`FlightCalibration`): otishdan keyingi jonli kadrlarda sharcha x → yoy vaqti t (qadam); `kadr vaqti = kechikish + t · ms/qadam` (EKK) → `Config.flightMsPerStep`, `flightLatencyMs`.
- `AutoPlayRunner`: jonli bo'lsa — har otishda uchish kalibrlanadi; harakatlanuvchi qutida 6 s jonli kuzatuv (~60 fps) + harakat modeli + video + pauza.

- **3-sinov (05:54) — jonli kuzatuv ishladi**: 6 s da 397 kadr (**66 fps**), tekshiruv kadri 29–59 ms (screencap 500 ms edi). Harakat: **sinusoida** — markaz 318, amplituda 68 px (x 250…386), **davr 8.8 s**, eng katta tezlik ~49 px/s, moslash xatosi **0.67 px**. Chekkada (t ≈ 2.2 s, x 249.5) tezlik 0. Pauza mo'ljali: TUSHADI +14.5 px.

### Harakatlanuvchi quti — otish (`LeadPlanner` + `shootMoving`) ✅ (telefonda sinov kutilmoqda)
1. Video (`AutoAim_harakat_<vaqt>_<n>.mp4`) + jonli kuzatuv (`RollingMotion`, 12 s oyna) 6 s → harakat qonuni.
2. **Nishon nuqtasi**: sinusoidada — quti ketayotgan **chekka** (u yerda deyarli to'xtaydi: ±0.5 s ichida ~4 px); doimiy tezlikda — oraliqning 25% nuqtasi.
3. Qutining shu nuqtadagi **virtual nusxasiga** mo'ljal (`AimRunner.fixedTarget`) — barmoq ushlab turiladi.
4. **Qo'yib yuborish darvozasi**: `kechikish + uchish_qadami × ms/qadam` dan keyin slot nishondan ≤ 4 px bo'ladigan oynaning **eng yaxshi** lahzasi (model har 250 ms qayta moslanadi); 1.2 davr ichida bo'lmasa — bekor qilish.
5. Uchish vaqti kalibrlanmagan bo'lsa — 33 ms/qadam, 50 ms kechikish (taxmin). Telefon modelida: uchish vaqti ±2 baravar xato bo'lsa ham slot xatosi ≤ 5 px (ruxsat ±28).
6. Natija — virtual slot bo'yicha; muvaffaqiyatda keyingi bosqichga o'tiladi.
- Telefon ma'lumoti: birinchi yarmidan (3 s) tuzilgan model keyingi 3 s ni 33 px xato bilan bashorat qiladi → mo'ljal va kutish davomida kuzatish davom etadi.

- **4-sinov (06:16) — ❌ xato topildi va tuzatildi**: jonli kuzatuv toza (826 kadr, sinusoida: x 245.6…383.6, davr ~8.7 s), lekin bot quti **o'ng tomonda** (x ≈ 378) bo'lganda "chap chekkada bo'ladi" deb otdi → sharcha qutining o'ng yoniga urildi.
  - Sabab: sinusoida davrini o'rta chiziqni kesishlardan baholash — x 0.5 px qadamli, ketma-ket ikki qiymat o'rtaga aynan teng → 0/0 = **NaN** → sinusoida moslash `null`; qolgan yagona model "borib-kelish" (xato 37–47 px!) qabul qilingan. Bundan tashqari shovqin bitta kesishni ikki marta sanagan.
  - Tuzatish: davr — to'g'ridan-to'g'ri qidiruv (log-to'r 0.4 s … 4×kuzatuv, so'ng ±10%); **model ishonchliligi** (xato ≤ max(3 px, amplitudaning 5%)) — ishonchsiz modelda otilmaydi; **jonli tekshiruv** — qo'yib yuborishdan oldin oxirgi kadrdagi haqiqiy slot model aytgan joydan ≤ 6 px bo'lishi shart.
  - Regressiya testi (`docs/samples/live_box_06-16.csv`): har oyna uzunligida sinusoida, otishdan 553 ms keyingi bashorat **362.2** (haqiqiy 361.4; avval 245.7).
  - **Uchish kalibrlandi**: 1 qadam = **53.8 ms**, kechikish **20 ms** (9 jonli kadr, xato 3.8 ms) → standart qiymatlar yangilandi (avval 33 ms / 50 ms taxmin).

### Harakatlanuvchi quti — fizika rejasi (dastlabki)
1. **Jonli kadrlar (M1)**: MediaProjection, vaqt belgisi bilan (screencap 0.55 s — bunga yaramaydi).
2. **Vaqt kalibrlash**: nuqta qadami Δ (ms) noma'lum → bitta haqiqiy otishda sharcha uchishini kadrlarda kuzatib, tiklangan yoyga moslab Δ topiladi (o'yin konstantasi, bir marta).
3. **Quti harakat modeli**: slot markazi x(t) ni 1–2 s kuzatib moslash — sinusoida `c + A·sin(ωt+φ)` yoki doimiy tezlikda chetlardan qaytish (uchburchak to'lqin); qaysi biri kichik xato bersa.
4. **Uchish vaqti**: `T = t_slot·Δ`, bu yerda `t_slot` — yoy slot balandligiga tushadigan vaqt (qadamlarda).
5. **Otish strategiyasi**: yoyni qutining **burilish nuqtasiga** (harakat chekkasi — u yerda quti eng sekin, vaqt xatosiga eng chidamli) to'g'rilab ushlab turish; quti o'sha nuqtaga `T + kechikish` dan keyin keladigan lahzada UP. Kechikish (inject → otish, kadr kechikishi) ham o'lchanadi.
6. Har otish CSV ga: bashorat qilingan va haqiqiy tushish, quti fazasi, Δ, kechikish → model aniqlashtiriladi.

### Qora quti (black-box) sharti
O'yin parametrlari (gravitatsiya, drag→tezlik, quti harakati) **berilmaydi** — bot nohalol o'yinchi bilan bir xil sharoitda, faqat ekrandan ko'rib o'rganadi (yoy nuqtalari, sinov siljishlari, Yakobian). Sinovlar real reytingga ta'sir qilmaydigan muhitda o'tkaziladi.

### Quti va slot — `BoxDetector` ✅ (piksel aniqligida)
- Quti — 3 qatlamli karta: orqa ~(148,152,161), o'rta ~(172,177,183), old ~(225,229,232); tana = neytral kulrang `min(RGB) > 95` eng katta guruh (avval faqat old karta topilardi — telefon skrinshotida slot ramkadan tashqarida qolgan edi).
- Slot — tananing yuqori 30% qismida, chetlarga tegmaydigan eng keng yotiq qora (`max(RGB) < 40`) guruh.
- `ggg`: quti 638,508–892,667, **slot 711,512–820,521**; `jjg`: quti 640,635–897,797, **slot 713,639–824,648** — qo'lda o'lchangan bilan 0 px farq.
- Tezlik: `Blobs` inline (piksel boshiga boxing yo'q) + **release APK** (debuggable emas); telefonda avval jami 4.5 s edi.

### O'yin zonasini topish — `GameZoneDetector` ✅
> Namuna rasmlar **kesilgan** (faqat o'yin zonasi). Haqiqiy ekranda zona **to'liq ekranga yoyilmaydi** — atrofida status bar, ilova sarlavhasi, tugmalar bor.
1. **Tayanch — yashil sharcha** (ekranda boshqa bunday rang yo'q).
2. **Chap/o'ng chegara**: sharchaning ikki tomonidagi eng uzun vertikal chegara — ingichka yorqin chiziq (zona ramkasi, yorqinlik ~32–40, fon ~11–13) yoki keskin pog'ona; zona tomoni to'q bo'lishi shart, chiziq sharcha qatorini qamrashi shart.
3. **Tepa/past**: chiziq tugagan joydan (yumaloq burchak) tashqariga — qator rangi zona tashqarisidagi rangga tenglashgan joy.
4. **Tasdiqlash**: zona ichida quti (och kulrang) bor → ishonch 100%.
5. Testlar (8 ta, hammasi o'tadi): kesilgan `ggg`/`jjg`, soxta to'liq ekran (to'q va oq fon, status bar/sarlavha/tugmalar bilan), 1/2 masshtab — xato **0–2 px**; o'yinsiz ekranda zona topilmaydi.
6. Ilovada (foydalanuvchi talabi — kechikish emas, **qalqib turuvchi panel**): **"O'yin zonasini skanerlash"** → ekranda surib yuriladigan panel ochiladi, ilova orqaga ketadi → foydalanuvchi o'yinni ochadi → paneldagi **«Skanerlash»** → panel bir lahzaga yashirinadi → Shizuku `screencap` → detektor → natija panelda + **o'yin ustida 3 s chizib ko'rsatiladi** → «Ilovaga qaytish» → asosiy ekranda rasm, zona saqlanadi, sinov koordinatalari sharchaga moslanadi. Overlay ruxsati avval Shizuku (`appops set … SYSTEM_ALERT_WINDOW allow`) orqali, bo'lmasa sozlamalar oynasi.

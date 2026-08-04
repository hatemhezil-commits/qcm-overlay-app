# QCM Overlay — تطبيق تذكير أسئلة QCM

## طريقة البناء من الهاتف (بدون Android Studio) — عبر Termux + GitHub Actions

1. **في Termux** (نفس بيئتك الحالية لبوت التلغرام):
   ```bash
   pkg install git
   ```
2. أنشئ مستودعًا (repo) جديدًا **فارغًا** على GitHub من متصفح الهاتف (اسمه مثلاً `qcm-overlay-app`)، بدون أي ملفات.
3. أنشئ Personal Access Token من إعدادات GitHub (Settings → Developer settings → Personal access tokens) بصلاحية `repo`، واحفظه.
4. من داخل مجلد المشروع (بعد فك ضغط الـ zip الذي أرسلته لك) في Termux:
   ```bash
   cd QcmOverlayApp
   git init
   git add .
   git commit -m "init"
   git branch -M main
   git remote add origin https://<USERNAME>:<TOKEN>@github.com/<USERNAME>/qcm-overlay-app.git
   git push -u origin main
   ```
5. بعد الـ push، اذهب لتبويب **Actions** في صفحة المستودع على GitHub — ستجد عملية بناء تعمل تلقائيًا (تأخذ 2-4 دقائق).
6. عند انتهائها، افتحها واسحب لأسفل لتجد **Artifacts → qcm-overlay-debug-apk** — حمّله (ملف zip يحتوي `app-debug.apk`).
7. فك الضغط وثبّت `app-debug.apk` مباشرة على هاتفك (فعّل "تثبيت من مصادر غير معروفة" إذا طُلب).

بعد هذا، أي تعديل تريده على الكود مستقبلًا: تعدّل الملف في Termux أو تطلب مني تعديله، تعمل `git add . && git commit -m "update" && git push`، وستحصل على APK جديد تلقائيًا من Actions — دون الحاجة لحاسوب أبدًا.

## طريقة التشغيل (بعد التثبيت)
1. افتح التطبيق على هاتفك.
2. اضغط **"منح صلاحية الظهور فوق التطبيقات"** ووافق.
3. اضغط **"تشغيل التذكير اليومي"**.
4. سيظهر سؤال فوق الشاشة عشوائيًا كل 2 إلى 5 ساعات (قابل للتعديل).

## (اختياري) البناء عبر Android Studio على حاسوب
1. افتح المجلد `QcmOverlayApp` في **Android Studio** (File → Open).
2. اتركه يقوم بـ Gradle Sync.
3. شغّل التطبيق على هاتفك أو محاكي (minSdk 26 / أندرويد 8+).

## إضافة ملفات أسئلة أخرى (مثل الهيماتولوجيا، الجهاز الهضمي...)
- ضع أي ملف JSON بنفس الصيغة (`module`, `lessons`, `question`, `options`,
  `correct_option_ids`, `explanation`) داخل:
  `app/src/main/assets/qcm/`
- كل ملف = module مستقل. التطبيق يقرأ **كل** الملفات الموجودة في هذا المجلد
  ويدمج أسئلتها معًا تلقائيًا — لا حاجة لتعديل أي كود.
- تم وضع ملف الأطفال (pédiartie.json) بالفعل هنا باسم `pediatrie.json`.

## تعديل تواتر الظهور
في ملف `QuestionScheduler.kt`:
```kotlin
private const val MIN_DELAY_HOURS = 2L
private const val MAX_DELAY_HOURS = 5L
```
غيّر هذه القيم لتقليل أو زيادة عدد مرات الظهور يوميًا.

## ملاحظات مهمة
- الأسئلة متعددة الإجابات الصحيحة (`correct_option_ids` تحتوي أكثر من رقم)
  تُعرض تلقائيًا كـ checkboxes، والأسئلة ذات جواب واحد تُعرض كـ radio buttons.
- التطبيق يتجنب تكرار نفس الأسئلة قبل عرض 30 سؤالًا مختلفًا على الأقل.
- عند إعادة تشغيل الهاتف، الجدولة تتوقف (لم يُضف BroadcastReceiver لـ
  BOOT_COMPLETED بعد) — يجب فتح التطبيق مرة والضغط على "تشغيل" مجددًا.
  أخبرني إذا تريد أن أضيف هذا لاحقًا.

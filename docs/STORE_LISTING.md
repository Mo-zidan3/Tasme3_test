# Play Store listing draft — v0.1.0 (Phase 4 prep; UNREVIEWED until Mo signs off)

## App name
اقرأ — متعلم التلاوة (Iqraa)

## Short description (ar)
متابعة تلاوتك حرفًا حرفًا أثناء القراءة — يعمل دون اتصال بالإنترنت.

## Short description (en)
Follow your Quran recitation word-by-word as you read — fully offline.

## Full description (ar) — CONSTRAINTS §12.6
يساعدك التطبيق على متابعة تلاوتك أثناء القراءة: يعرض الكلمات ويبرز حالتها
(صحيحة / قريبة من النص) أثناء التلاوة، مع ملخص في نهاية الجلسة.

قيود مهمة (مطلوبة من السياسة):
- هذا التطبيق ليس أداة لتعليم التجويد ولا يقدّم أحكامًا أو مخارجًا؛ هو مساعد متابعة فقط.
- لا يعد التطبيق بكشف الأخطاء: في هذا الإصدار تعرض الكلمات المشكوك فيها كـ«قريبة من النص»
  بدون تمييز الخطأ (القيمة الافتراضية المحافظة حتى إتممة قياس العتبة على الهاتف).
- قيود التعرف: قد تتشابه كلمات متقاربة في الحروف (مثل «حيث/حصْت») — راجع دائمًا.
- يعمل دون اتصال بالإنترنت بالكامل؛ لا يجمع أي بيانات؛ لا يسجل الصوت ولا يحفظه.

## Limitations paragraph (en, letters-only collisions — V-024)
Iqraa is a recitation-following aid, not a tajweed teacher: it makes no tajweed or
makhraj claims, and in this version it makes no mistake-detection promise — suspect words
render as "close to the text" without distinguishing errors. The underlying recognition
can confuse words that share the same letters (Arabic diacritics are not modeled), so
always double-check flagged words. Fully offline; no audio is recorded or stored; no data
is collected.

## Privacy policy URL content (host by Mo)
Iqraa collects no personal data. All recognition runs on-device and offline; audio is
processed in memory only and never written to storage or transmitted. The app requests
the microphone permission solely for live recitation following. No analytics, no ads, no
network access (the manifest declares no INTERNET permission).

## Play "Data safety" answers (must match the merged manifest)
- Data collected: NONE (no identifiers, no audio, no usage data).
- Data shared: NONE. Data encrypted in transit: N/A (no network).
- Deletion request: N/A (nothing stored).
- Permissions declared in the manifest: RECORD_AUDIO (runtime) — and nothing else.

## Screenshots plan (Mo)
Onboarding (rationale), follow-along screen mid-recitation (§10.2 glyphs), summary screen
(«صحيحة: X من Y»), About/licenses. Phone hardware only — emulator screenshots are not
store-grade.

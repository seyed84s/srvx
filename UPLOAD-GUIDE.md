# چطور این پروژه رو روی گیت‌هاب بذاری

این پروژه ۴۶۹ فایل داره، پس آپلود دستی (drag & drop) کار نمی‌کنه — گیت‌هاب سقف ۱۰۰ فایل داره.
راه درست: با git از کامپیوترت پوش کنی. دو راه داری:

## راه A — با GitHub Desktop (ساده‌ترین، بدون دستور)
1. برنامه‌ی GitHub Desktop رو نصب کن: https://desktop.github.com
2. با اکانت seyed84s واردش شو.
3. یه مخزن خالی روی سایت گیت‌هاب بساز (Private، اسم دلخواه مثلا srvx-app، بدون README).
4. در GitHub Desktop: File → Clone repository → همون مخزن خالی رو clone کن روی کامپیوتر.
5. این zip رو extract کن و *محتوای* پوشه‌ی v2rayNG-2.2.3 رو کپی کن توی پوشه‌ی clone‌شده.
6. توی GitHub Desktop تغییرات رو می‌بینی → پایین یه پیام بنویس → "Commit to main" → بعد "Push origin".
تمام. حالا کل پروژه روی گیت‌هابه و Actions خودش build رو شروع می‌کنه.

## راه B — با خط فرمان git
```bash
# مخزن خالی روی گیت‌هاب بساز، بعد:
cd مسیر/پوشه‌ی/v2rayNG-2.2.3
git init
git add .
git commit -m "SRVX on v2rayNG 2.2.3"
git branch -M main
git remote add origin https://github.com/seyed84s/اسم-مخزن.git
git push -u origin main
```

بعد از پوش: تب Actions → build شروع می‌شه → APK از Artifacts.

# PriceView (Minecraft 26.3, Fabric)

نمایش قیمت بازار اسکای‌بلاک و قابل‌فروش بودن آیتم در تولتیپ.

## نیازها
- JDK 25
- Fabric Loader 0.19.5+ و Fabric API 0.161.0+26.3 (روی کلاینت)

## بیلد
ساده‌ترین راه: از https://fabricmc.net/develop/template/ یک پروژه برای 26.3 بگیر،
بعد پوشه `src/` و فایل `src/main/resources/fabric.mod.json` این پروژه رو جایگزین کن
(و اگه ورژن‌ها فرق داشت `gradle.properties` رو با نسخه‌های تمپلیت هماهنگ کن).
بعد:

    ./gradlew build

فایل jar داخل `build/libs/` ساخته می‌شه (فایل بدون `-sources`) و می‌ره توی پوشه `mods`.

## استفاده
روی هر آیتم اسکای‌بلاک موس رو نگه دار:
- در بازار نیست: `Sellable: No` (بدون قیمت)
- در بازار هست: `Sellable: Yes` + قیمت Bazaar (Buy/Sell) یا Lowest BIN

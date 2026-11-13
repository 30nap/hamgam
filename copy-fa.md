# Landing copy (Persian) - use verbatim

Every Persian line in this first part must appear word for word on the site.
`CopyVerbatimTest` fails the build if one of them is changed in a template but not here.

## Headline

دلار رفت بالا؟ دیگه لازم نیست دونه‌دونه قیمت‌ها رو عوض کنی.

## Subheadline

«همگام» قیمت محصولاتت رو با نرخ دلار و سودی که خودت می‌خوای، خودکار به‌روز می‌کنه. هنوز داریم می‌سازیمش. الان فقط پیش‌ثبت‌نام می‌گیریم و کاملاً رایگانه.

## Benefits

۱. دیگه با قیمت دیروز نفروش

هر محصول رو یه بار به دلار، درهم یا طلا وصل کن. نرخ که عوض شد، قیمتت خودش درست می‌شه.

۲. اختیارش دست خودته

خودت تعیین می‌کنی قیمت تا کجا بالا و پایین بره، چطور رُند بشه، و اگه خواستی قبل از اعمال تأییدش کنی.

۳. لیست قیمت هر روز آماده‌ست

هر روز یه لیست قیمت مرتب می‌گیری که مستقیم بفرستی تو کانال بله و ایتا یا برای مشتری‌های عمده‌ت.

## Offer

پیش‌ثبت‌نام رایگانه و هیچ پولی ازت نمی‌گیریم. پیش‌ثبت‌نامی‌ها اولین نفرایی‌ان که همگام رو امتحان می‌کنن.

## Form

### Intro

شماره‌ت رو بذار و بگو کجا می‌فروشی. نسخهٔ اول رو برای جایی می‌سازیم که بیشترین درخواست رو داشته باشه، و آماده که شد خبرت می‌کنیم.

### Phone field label

شماره موبایل

### Platform question (multi-select)

کجا می‌فروشی؟

* ووکامرس
* باسلام
* دیجی‌کالا
* کانال بله
* کانال ایتا
* یه جای دیگه

### Price question (optional, single choice, nothing selected by default)

اگه همگام ماهی حدود ۳۰۰ هزار تومن باشه، ازش استفاده می‌کنی؟

* آره، حتماً
* شاید
* نه، گرونه

### Button

پیش‌ثبت‌نام رایگان

## OTP step

کدی که برات پیامک شد رو بزن.

## Success message

ثبت شد! همگام که آماده شد، اول از همه به تو خبر می‌دیم.

## Footer

همگام هنوز منتشر نشده. شماره‌ت فقط برای خبر دادن زمان انتشار استفاده می‌شه.

<!-- added-ui-strings -->

# Added UI strings (NOT part of the original copy)

The original copy has no text for these. They were added so the form can work, in the same
tone, and promise nothing about the product. Edit them freely in the templates
(`src/main/resources/templates/`) and keep this list in sync.

| Where | Text |
|---|---|
| Browser tab title | همگام \| پیش‌ثبت‌نام |
| Phone field placeholder | ۰۹۱۲۳۴۵۶۷۸۹ |
| Invalid phone number | شماره موبایل درست نیست. مثل ۰۹۱۲۳۴۵۶۷۸۹ واردش کن. |
| Code requested again too soon | همین الان برات کد فرستادیم. یه دقیقه دیگه دوباره امتحان کن. |
| Too many code requests | تعداد درخواست‌ها زیاد شد. چند ساعت دیگه دوباره امتحان کن. |
| SMS could not be sent | نتونستیم پیامک بفرستیم. چند دقیقه دیگه دوباره امتحان کن. |
| Code field label | کد تأیید |
| Confirm button | تأیید |
| Wrong code | کد درست نیست. دوباره امتحان کن. |
| Expired code | مهلت این کد تموم شد. یه کد جدید بگیر. |
| Resend button | ارسال دوباره کد |
| Back to the form | تغییر شماره |
| Suggested SMS template (set in the Kavenegar panel) | کد تأیید همگام: %token |
| Hint under "کجا می‌فروشی؟" | هر چندتا که هست رو انتخاب کن. |
| Hint under the price question | جواب دادنش اختیاریه. |
| Under the form button, before the footer sentence | بعدش یه کد تأیید برات پیامک می‌شه. |
| Code page, under the heading (number is masked) | کد رو به ۰۹۱۲ ••• ۶۷۸۹ فرستادیم. |
| Thank-you page, invitation to pass the link on | همکاری داری که اون هم هر روز قیمت عوض می‌کنه؟ لینک همگام رو براش بفرست. |
| Thank-you page, button where the phone can share | فرستادن لینک |
| Thank-you page, button where it can only copy | کپی لینک |
| Thank-you page, after copying | لینک کپی شد. |

## Price demo (illustration, NOT part of the original copy)

The block under the first button shows the headline with made-up numbers: a dollar rate the
visitor can drag, and three example products whose prices follow it. It only illustrates
what the copy already says (price = dollar rate + your margin, rounded). It is labelled as
hypothetical twice, and `PageContentTest` fails if those labels are removed. If you change
a product, change its `data-usd` / `data-margin` and the numbers next to it in
`landing.html` together (price = dollars x rate x (1 + margin), rounded to 1,000 toman;
yesterday's rate is 100,000 and the starting rate is 104,000).

| Where | Text |
|---|---|
| Rate label | نرخ دلار (فرضی) |
| Unit | تومن |
| Change since yesterday | ۴٪ بالاتر از دیروز |
| Hint under the slider | نرخ رو جابه‌جا کن و ببین قیمت‌ها چی می‌شن. |
| Example products | قاب گوشی، هندزفری بلوتوث، پاوربانک |
| How a price is built | ۵ دلار + ۲۰٪ سود |
| Yesterday's price | دیروز ۶۰۰٬۰۰۰ |
| Note under the block | این فقط یه نمونه‌ست و عددهاش واقعی نیستن. |

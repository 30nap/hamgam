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
| Tag next to the price question (it may be skipped) | اختیاری |
| Example card under the headline: its tag | مثال |
| Example card: rate row, label and change | دلار، ۲٪ |
| Example card: price row, label, the two prices and the unit | قیمت محصولت، ۱٬۲۵۰٬۰۰۰، ۱٬۲۷۵٬۰۰۰، تومن |
| Example card: badge | خودکار به‌روز شد |
| Example card: description read out by screen readers | مثال: دلار ۲٪ بالا می‌ره و قیمت محصولت خودکار از ۱٬۲۵۰٬۰۰۰ به ۱٬۲۷۵٬۰۰۰ تومن به‌روز می‌شه. |

The example card is a picture of the headline: the rate moves, the price follows. It shows a
percentage instead of a real exchange rate so it cannot go out of date, and it is tagged
«مثال» so nobody reads it as a live price. It describes nothing the original copy does not.

The page description and the link-preview title and text (`<meta>` tags in `fragments.html`)
reuse the headline and subheadline word for word.

# Kaasu — Smart SMS Expense Tracker (Android)

Privacy-first expense tracker that reads bank transaction SMS **on the phone**, and turns them into a
monthly dashboard (income, expenses, UPI, ATM, refunds, transfers, balances). No backend, no AI API,
no cloud — works fully offline.

## Get the APK (no Android Studio needed) — GitHub Actions

1. Create a new **private** repository on github.com (e.g. `kaasu`).
2. Upload everything in this folder (keep the folder structure, including the hidden `.github` folder).
   Easiest from a PC:
   ```
   cd kaasu
   git init && git add . && git commit -m "Kaasu v1"
   git branch -M main
   git remote add origin https://github.com/<you>/kaasu.git
   git push -u origin main
   ```
3. Open the repo → **Actions** tab → "Build APK" runs automatically (~6–8 min).
4. Open the finished run → download the **kaasu-apk** artifact (zip) → inside are:
   - `app-release.apk` — smaller, optimised (recommended)
   - `app-debug.apk` — debug build
5. Copy the APK to your phone, open it, allow "Install unknown apps" for your file manager.

## Or build with Android Studio
Open this folder in Android Studio (Koala or newer) → wait for Gradle sync →
**Build › Build App Bundle(s) / APK(s) › Build APK(s)**.
Output: `app/build/outputs/apk/debug/app-debug.apk`.

## First run
Welcome screens → choose how far back to scan (this month / 3 / 6 / 12 months) → allow SMS →
"X messages scanned, Y transactions found, Z need review" → dashboard.
New transaction SMS are picked up automatically after that.

## What's inside
| Area | File |
|---|---|
| SMS parser (amounts, balance, account, UPI, ref, direction, status) | `sms/SmsParser.kt` |
| Bank detection (HDFC, SBI, ICICI, Axis, Kotak, Canara, IOB, Indian Bank, KVB, CUB, TMB…) | `sms/SmsParser.kt` → `BankProfiles` |
| Merchant + category recognition (extendable list) | `sms/MerchantCatalog.kt` |
| Duplicate detection, transfer pairing, refund linking, learned rules | `data/Repo.kt` |
| Monthly / yearly consolidation, bank-reported vs calculated balance | `data/Stats.kt` |
| Local SQLite storage | `data/Db.kt` |
| Live SMS listener | `sms/SmsReceiver.kt` |
| UI (Home, Transactions, Accounts, Analytics, Settings, detail sheet) | `ui/` |

### Money logic
- Only SUCCESS, non-duplicate, confirmed transactions count toward totals.
- Same money reported by two SMS (bank + UPI app) → linked as one group, counted once.
- Debit on one of your accounts + same credit on another within an hour → **Transfer**, not expense.
- Credit-card bill payments → **Card payment**, not a second expense.
- Refunds reduce expenses (Gross − Refunds = Net), and are not counted as income.
- ATM withdrawals are tracked separately from merchant spending.
- Low-confidence messages go to **Needs review** and stay out of totals until you confirm them.
  Your choice is remembered for similar future SMS.
- Bank balance is only what the bank SMS says ("bank-reported"); the app's own figure is shown
  as "calculated", and any difference is flagged as a mismatch.

## Play Store note
READ_SMS / RECEIVE_SMS are restricted permissions on Google Play. Sideloading the APK works fine.
To publish, you must complete Play's Permissions Declaration (category: SMS-based money management),
add a privacy policy URL, and sign with your own keystore instead of the debug key used in
`app/build.gradle.kts`.

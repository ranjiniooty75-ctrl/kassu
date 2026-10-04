package com.kaasu.tracker.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Telephony
import com.kaasu.tracker.sms.SmsParser
import com.kaasu.tracker.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

object Repo {
    private lateinit var db: Db
    private lateinit var prefs: SharedPreferences
    private val lock = Any()

    /** Bumped on every write so the UI reloads (also when SMS arrive in the background). */
    val version = MutableStateFlow(0)

    fun init(context: Context) {
        if (::db.isInitialized) return
        synchronized(lock) {
            if (::db.isInitialized) return
            db = Db(context.applicationContext)
            prefs = context.applicationContext.getSharedPreferences("kaasu", Context.MODE_PRIVATE)
        }
    }

    private fun bump() { version.value = version.value + 1 }

    // ---- preferences ----
    fun onboarded() = prefs.getBoolean("onboarded", false)
    fun setOnboarded() = prefs.edit().putBoolean("onboarded", true).apply()
    fun cash() = prefs.getFloat("cash", 0f).toDouble()
    fun setCash(v: Double) { prefs.edit().putFloat("cash", v.toFloat()).apply(); bump() }
    fun threshold() = prefs.getInt("threshold", 70)
    fun setThreshold(v: Int) = prefs.edit().putInt("threshold", v).apply()

    // ---- reads ----
    fun all(): List<Txn> = db.all()
    fun budgets(): Map<String, Double> = db.budgets()

    // ---- writes ----
    fun update(t: Txn) { synchronized(lock) { db.update(t) }; bump() }
    fun delete(t: Txn) { synchronized(lock) { db.delete(t.id) }; bump() }
    fun insertManual(t: Txn) { synchronized(lock) { db.insert(t) }; bump() }
    fun setBudget(category: String, amount: Double) { db.setBudget(category, amount); bump() }
    fun deleteAll() { synchronized(lock) { db.deleteAll() }; prefs.edit().putFloat("cash", 0f).apply(); bump() }

    /** Remember a user's correction so similar SMS are classified the same way next time. */
    fun learn(t: Txn) {
        val pattern = (t.upi ?: t.rawMerchant)?.lowercase()?.trim() ?: return
        if (pattern.length < 3) return
        db.putRule(Rule(pattern, t.merchant, t.category, t.type))
    }

    private fun sha(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun isCreditType(type: TxType, fallback: Boolean) = when (type) {
        TxType.INCOME, TxType.REFUND -> true
        TxType.EXPENSE, TxType.ATM_WITHDRAWAL -> false
        else -> fallback
    }

    /**
     * Parse one SMS and store it. Returns the stored transaction, or null when the SMS
     * is not a financial transaction or was already processed.
     */
    fun ingest(sender: String, body: String, ts: Long, notify: Boolean = true): Txn? = ingestLocked(sender, body, ts, notify)

    private fun ingestLocked(sender: String, body: String, ts: Long, notify: Boolean): Txn? {
        synchronized(lock) {
        val hash = sha(body.trim())
        if (db.hashExists(hash)) return null
        val p = SmsParser.parse(sender, body) ?: return null

        var t = Txn(
            ts = ts, amount = p.amount, credit = p.credit, type = p.type, category = p.category,
            merchant = p.merchant, rawMerchant = p.rawMerchant, mode = p.mode, bank = p.bank,
            last4 = p.last4, reference = p.reference, upi = p.upi, sender = sender, body = body,
            hash = hash, balance = p.balance, status = p.status, confidence = p.confidence
        )

        // User-taught rules win over the built-in guesses.
        val lower = body.lowercase()
        db.rules().firstOrNull { lower.contains(it.pattern) }?.let { r ->
            val type = r.type ?: t.type
            t = t.copy(
                merchant = r.merchant ?: t.merchant, category = r.category ?: t.category,
                type = type, credit = isCreditType(type, t.credit), confidence = maxOf(t.confidence, 92)
            )
        }

        t = t.copy(needsReview = t.status != TxStatus.FAILED && (t.confidence < threshold() || t.type == TxType.UNKNOWN))

        // ---- duplicate detection (same money movement reported by two SMS) ----
        val window = 15 * 60_000L
        val near = db.between(ts - window, ts + window)
        val byRef = t.reference?.let { db.byRef(it) } ?: emptyList()
        val original = (byRef + near).firstOrNull { e ->
            !e.isDuplicate && !e.manual && e.amount == t.amount && e.credit == t.credit &&
                (
                    (t.reference != null && e.reference == t.reference) ||
                        (abs(e.ts - t.ts) <= 10 * 60_000L && e.sender != t.sender &&
                            (e.last4 == null || t.last4 == null || e.last4 == t.last4))
                    )
        }
        if (original != null) {
            val group = original.groupId ?: ("TXN-" + Fmt.csvDate(original.ts).replace("-", "") + "-" + original.id)
            // Enrich the original with anything the second SMS knows better.
            var o = original.copy(groupId = group)
            if (o.balance == null && t.balance != null) o = o.copy(balance = t.balance)
            if (o.last4 == null && t.last4 != null) o = o.copy(last4 = t.last4, bank = o.bank ?: t.bank)
            if ((o.merchant == "Unknown" || o.needsReview) && t.merchant != "Unknown" && !t.needsReview) {
                o = o.copy(merchant = t.merchant, category = t.category, needsReview = false, confidence = t.confidence)
            }
            db.update(o)
            t = t.copy(isDuplicate = true, groupId = group, needsReview = false)
            t = t.copy(id = db.insert(t))
            if (notify) bump()
            return t
        }

        // ---- internal transfer: debit on one own account + same credit on another ----
        val movable = setOf(TxType.INCOME, TxType.EXPENSE, TxType.TRANSFER)
        if (t.type in movable && t.last4 != null && t.status == TxStatus.SUCCESS) {
            val hour = 60 * 60_000L
            val pair = db.between(ts - hour, ts + hour).firstOrNull { e ->
                !e.isDuplicate && !e.manual && e.amount == t.amount && e.last4 != null && e.last4 != t.last4 &&
                    e.credit != t.credit && e.type in movable &&
                    !(e.type == TxType.TRANSFER && t.type == TxType.TRANSFER)
            }
            if (pair != null) {
                db.update(pair.copy(type = TxType.TRANSFER, category = "Transfer", needsReview = false))
                t = t.copy(type = TxType.TRANSFER, category = "Transfer", needsReview = false)
            }
        }

        // ---- refund: link to the original purchase ----
        if (t.type == TxType.REFUND && (t.category == "Refunds" || t.merchant == "Refund")) {
            val since = ts - 60L * 24 * 60 * 60_000L
            db.between(since, ts).lastOrNull { e -> e.type == TxType.EXPENSE && e.amount == t.amount && !e.isDuplicate }
                ?.let { e -> t = t.copy(merchant = e.merchant, category = e.category) }
        }

        t = t.copy(id = db.insert(t))
        if (notify) bump()
        return t
        }
    }

    /** Reads only inbox SMS from the chosen period; nothing is uploaded anywhere. */
    suspend fun scanInbox(context: Context, months: Int): ScanResult = withContext(Dispatchers.IO) {
        val since = YearMonth.now().minusMonths((months - 1).toLong()).atDay(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        var scanned = 0
        var found = 0
        var review = 0
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} ASC"
        )?.use { c ->
            val ia = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val ib = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val id = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                scanned++
                val t = ingest(c.getString(ia) ?: "", c.getString(ib) ?: "", c.getLong(id), notify = false)
                if (t != null && !t.isDuplicate) {
                    found++
                    if (t.needsReview) review++
                }
            }
        }
        bump()
        ScanResult(scanned, found, review)
    }

    fun csv(list: List<Txn>): String = buildString {
        fun esc(v: Any?): String = "\"" + (v?.toString() ?: "").replace("\"", "\"\"") + "\""
        append('\uFEFF')
        appendLine("Date,Time,Merchant,Amount,Type,Category,Payment Mode,Bank,Account,UPI ID,Reference,Status,Available Balance,Duplicate")
        list.sortedBy { it.ts }.forEach { t ->
            appendLine(
                listOf(
                    Fmt.csvDate(t.ts), Fmt.csvTime(t.ts), t.merchant, String.format(Locale.US, "%.2f", t.amount), t.type.label,
                    t.category, t.mode.label, t.bank, t.last4, t.upi, t.reference, t.status.label,
                    t.balance?.let { String.format(Locale.US, "%.2f", it) }, if (t.isDuplicate) "yes" else ""
                ).joinToString(",") { esc(it) }
            )
        }
    }

    suspend fun exportCsv(context: Context, uri: Uri, list: List<Txn>) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { it.write(csv(list).toByteArray(Charsets.UTF_8)) }
    }
}

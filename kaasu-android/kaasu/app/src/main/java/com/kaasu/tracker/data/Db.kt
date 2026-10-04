package com.kaasu.tracker.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Plain SQLite storage — everything stays on the device. */
class Db(context: Context) : SQLiteOpenHelper(context, "kaasu.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE txns(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts INTEGER NOT NULL, amount REAL NOT NULL, credit INTEGER NOT NULL,
                type TEXT NOT NULL, category TEXT NOT NULL, merchant TEXT NOT NULL, raw_merchant TEXT,
                mode TEXT NOT NULL, bank TEXT, last4 TEXT, ref TEXT, upi TEXT,
                sender TEXT NOT NULL, body TEXT NOT NULL, hash TEXT NOT NULL, bal REAL,
                status TEXT NOT NULL, confidence INTEGER NOT NULL, review INTEGER NOT NULL,
                dup INTEGER NOT NULL, ignored INTEGER NOT NULL, grp TEXT, manual INTEGER NOT NULL, notes TEXT
            )"""
        )
        db.execSQL("CREATE INDEX ix_txns_ts ON txns(ts)")
        db.execSQL("CREATE INDEX ix_txns_hash ON txns(hash)")
        db.execSQL("CREATE INDEX ix_txns_ref ON txns(ref)")
        db.execSQL("CREATE TABLE rules(pattern TEXT PRIMARY KEY, merchant TEXT, category TEXT, type TEXT)")
        db.execSQL("CREATE TABLE budgets(category TEXT PRIMARY KEY, amount REAL NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    private fun values(t: Txn) = ContentValues().apply {
        put("ts", t.ts); put("amount", t.amount); put("credit", if (t.credit) 1 else 0)
        put("type", t.type.name); put("category", t.category); put("merchant", t.merchant)
        put("raw_merchant", t.rawMerchant); put("mode", t.mode.name); put("bank", t.bank)
        put("last4", t.last4); put("ref", t.reference); put("upi", t.upi); put("sender", t.sender)
        put("body", t.body); put("hash", t.hash); put("bal", t.balance); put("status", t.status.name)
        put("confidence", t.confidence); put("review", if (t.needsReview) 1 else 0)
        put("dup", if (t.isDuplicate) 1 else 0); put("ignored", if (t.ignored) 1 else 0)
        put("grp", t.groupId); put("manual", if (t.manual) 1 else 0); put("notes", t.notes)
    }

    private fun Cursor.s(c: String): String? { val i = getColumnIndexOrThrow(c); return if (isNull(i)) null else getString(i) }
    private fun Cursor.d(c: String): Double? { val i = getColumnIndexOrThrow(c); return if (isNull(i)) null else getDouble(i) }
    private fun Cursor.l(c: String): Long = getLong(getColumnIndexOrThrow(c))
    private fun Cursor.i(c: String): Int = getInt(getColumnIndexOrThrow(c))

    private fun Cursor.toTxn() = Txn(
        id = l("id"), ts = l("ts"), amount = d("amount") ?: 0.0, credit = i("credit") == 1,
        type = runCatching { TxType.valueOf(s("type")!!) }.getOrDefault(TxType.UNKNOWN),
        category = s("category") ?: "Other", merchant = s("merchant") ?: "Unknown",
        rawMerchant = s("raw_merchant"),
        mode = runCatching { PayMode.valueOf(s("mode")!!) }.getOrDefault(PayMode.OTHER),
        bank = s("bank"), last4 = s("last4"), reference = s("ref"), upi = s("upi"),
        sender = s("sender") ?: "", body = s("body") ?: "", hash = s("hash") ?: "", balance = d("bal"),
        status = runCatching { TxStatus.valueOf(s("status")!!) }.getOrDefault(TxStatus.SUCCESS),
        confidence = i("confidence"), needsReview = i("review") == 1, isDuplicate = i("dup") == 1,
        ignored = i("ignored") == 1, groupId = s("grp"), manual = i("manual") == 1, notes = s("notes")
    )

    private fun query(sql: String, args: Array<String> = emptyArray()): List<Txn> =
        readableDatabase.rawQuery(sql, args).use { c ->
            val out = ArrayList<Txn>(c.count)
            while (c.moveToNext()) out.add(c.toTxn())
            out
        }

    fun insert(t: Txn): Long = writableDatabase.insert("txns", null, values(t))
    fun update(t: Txn) { writableDatabase.update("txns", values(t), "id = ?", arrayOf(t.id.toString())) }
    fun delete(id: Long) { writableDatabase.delete("txns", "id = ?", arrayOf(id.toString())) }

    fun all(): List<Txn> = query("SELECT * FROM txns ORDER BY ts DESC")
    fun between(from: Long, to: Long): List<Txn> =
        query("SELECT * FROM txns WHERE ts BETWEEN ? AND ? ORDER BY ts", arrayOf(from.toString(), to.toString()))
    fun byRef(ref: String): List<Txn> = query("SELECT * FROM txns WHERE ref = ?", arrayOf(ref))
    fun hashExists(hash: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM txns WHERE hash = ? LIMIT 1", arrayOf(hash)).use { it.moveToFirst() }

    fun rules(): List<Rule> = readableDatabase.rawQuery("SELECT * FROM rules", null).use { c ->
        val out = ArrayList<Rule>()
        while (c.moveToNext()) out.add(
            Rule(c.s("pattern")!!, c.s("merchant"), c.s("category"), c.s("type")?.let { runCatching { TxType.valueOf(it) }.getOrNull() })
        )
        out
    }

    fun putRule(r: Rule) {
        writableDatabase.insertWithOnConflict("rules", null, ContentValues().apply {
            put("pattern", r.pattern); put("merchant", r.merchant); put("category", r.category); put("type", r.type?.name)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun budgets(): Map<String, Double> = readableDatabase.rawQuery("SELECT * FROM budgets", null).use { c ->
        val out = HashMap<String, Double>()
        while (c.moveToNext()) out[c.s("category")!!] = c.d("amount") ?: 0.0
        out
    }

    fun setBudget(category: String, amount: Double) {
        if (amount <= 0) writableDatabase.delete("budgets", "category = ?", arrayOf(category))
        else writableDatabase.insertWithOnConflict("budgets", null, ContentValues().apply {
            put("category", category); put("amount", amount)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteAll() {
        writableDatabase.delete("txns", null, null)
        writableDatabase.delete("rules", null, null)
        writableDatabase.delete("budgets", null, null)
    }
}

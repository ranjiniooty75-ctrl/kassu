package com.kaasu.tracker.data

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs

data class MerchantSum(val name: String, val total: Double, val count: Int)

data class MonthStats(
    val income: Double,
    val gross: Double,
    val refunds: Double,
    val net: Double,
    val atm: Double,
    val transfers: Double,
    val savings: Double,
    val categories: List<Pair<String, Double>>,
    val merchants: List<MerchantSum>,
    val modes: List<Pair<PayMode, Double>>,
    val upiSpend: Double,
    val upiCount: Int,
    val upiCredits: Double,
    val upiMerchants: List<MerchantSum>,
    val incomeByCategory: List<Pair<String, Double>>,
    val atmList: List<Txn>,
    val pendingCount: Int,
    val reviewCount: Int
)

data class Account(
    val key: String,
    val bank: String,
    val last4: String?,
    val isCard: Boolean,
    val reported: Double?,
    val reportedAt: Long?,
    val calculated: Double?,
    val count: Int
) {
    val mismatch: Double?
        get() = if (reported != null && calculated != null && abs(reported - calculated) > 1.0) reported - calculated else null
}

data class BalanceSummary(val amount: Double?, val bankAccounts: Int)

data class YearRow(val month: YearMonth, val income: Double, val expense: Double)

object Stats {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun monthOf(ts: Long): YearMonth = YearMonth.from(Instant.ofEpochMilli(ts).atZone(zone))

    fun inMonth(txns: List<Txn>, m: YearMonth): List<Txn> = txns.filter { monthOf(it.ts) == m }

    private fun merchantSums(list: List<Txn>): List<MerchantSum> =
        list.groupBy { it.merchant }
            .map { (k, v) -> MerchantSum(k, v.sumOf { it.amount }, v.size) }
            .sortedByDescending { it.total }

    fun month(all: List<Txn>, m: YearMonth): MonthStats {
        val list = inMonth(all, m)
        val c = list.filter { it.counted }
        val expenses = c.filter { it.type == TxType.EXPENSE }
        val refunds = c.filter { it.type == TxType.REFUND }
        val incomes = c.filter { it.type == TxType.INCOME }
        val atm = c.filter { it.type == TxType.ATM_WITHDRAWAL }
        val gross = expenses.sumOf { it.amount }
        val refundSum = refunds.sumOf { it.amount }
        val net = (gross - refundSum).coerceAtLeast(0.0)
        val income = incomes.sumOf { it.amount }
        val atmSum = atm.sumOf { it.amount }

        val cat = HashMap<String, Double>()
        expenses.forEach { cat[it.category] = (cat[it.category] ?: 0.0) + it.amount }
        refunds.forEach { r -> if (cat.containsKey(r.category)) cat[r.category] = (cat[r.category]!! - r.amount).coerceAtLeast(0.0) }

        val upiExp = expenses.filter { it.mode == PayMode.UPI }
        val modes = (expenses + atm).groupBy { it.mode }
            .map { (k, v) -> k to v.sumOf { it.amount } }
            .sortedByDescending { it.second }

        return MonthStats(
            income = income,
            gross = gross,
            refunds = refundSum,
            net = net,
            atm = atmSum,
            transfers = c.filter { it.type == TxType.TRANSFER || it.type == TxType.CREDIT_CARD_PAYMENT }.sumOf { it.amount } / 1.0,
            savings = income - net - atmSum,
            categories = cat.filter { it.value > 0.0 }.toList().sortedByDescending { it.second },
            merchants = merchantSums(expenses),
            modes = modes,
            upiSpend = upiExp.sumOf { it.amount },
            upiCount = upiExp.size,
            upiCredits = incomes.filter { it.mode == PayMode.UPI }.sumOf { it.amount },
            upiMerchants = merchantSums(upiExp),
            incomeByCategory = (incomes + refunds).groupBy { if (it.type == TxType.REFUND) "Refunds" else it.category }
                .map { (k, v) -> k to v.sumOf { it.amount } }.sortedByDescending { it.second },
            atmList = atm.sortedBy { it.ts },
            pendingCount = list.count { it.status == TxStatus.PENDING && !it.isDuplicate && !it.ignored },
            reviewCount = list.count { it.needsReview && !it.isDuplicate && !it.ignored }
        )
    }

    fun year(all: List<Txn>, year: Int): List<YearRow> = (1..12).map { mo ->
        val m = YearMonth.of(year, mo)
        val s = month(all, m)
        YearRow(m, s.income, s.net)
    }

    /**
     * Bank-reported balance = latest balance quoted in an SMS for that account.
     * Calculated balance = first quoted balance + every later counted credit/debit.
     * The two are never mixed up; a difference is reported as a mismatch.
     */
    fun accounts(all: List<Txn>): List<Account> {
        val sms = all.filter { !it.manual && !it.isDuplicate && !it.ignored && (it.last4 != null || it.bank != null) }
        return sms.groupBy { it.accountKey }.map { (key, list) ->
            val sorted = list.sortedBy { it.ts }
            val isCard = list.count { it.mode == PayMode.CREDIT_CARD } * 2 > list.size
            val snaps = sorted.filter { it.balance != null }
            val latest = snaps.lastOrNull()
            var calc: Double? = null
            if (!isCard && snaps.isNotEmpty()) {
                val first = snaps.first()
                calc = first.balance!! + sorted
                    .filter { it.ts > first.ts && it.status == TxStatus.SUCCESS }
                    .sumOf { if (it.credit) it.amount else -it.amount }
            }
            Account(key, list.first().bank ?: "Bank", list.first().last4, isCard, latest?.balance, latest?.ts, calc, list.size)
        }.sortedWith(compareBy<Account> { it.isCard }.thenByDescending { it.count })
    }

    fun totalBalance(accounts: List<Account>, cash: Double): BalanceSummary {
        val banks = accounts.filter { !it.isCard && it.reported != null }
        if (banks.isEmpty() && cash <= 0.0) return BalanceSummary(null, 0)
        return BalanceSummary(banks.sumOf { it.reported!! } + cash, banks.size)
    }

    fun accountMonth(all: List<Txn>, key: String, m: YearMonth): Pair<Double, Double> {
        val l = inMonth(all, m).filter { it.accountKey == key && it.status == TxStatus.SUCCESS && !it.isDuplicate && !it.ignored }
        return l.filter { it.credit }.sumOf { it.amount } to l.filter { !it.credit }.sumOf { it.amount }
    }

    /** Returns (category, spent, budget) for budgets at or above 80 %. */
    fun budgetAlerts(s: MonthStats, budgets: Map<String, Double>): List<Triple<String, Double, Double>> {
        val spent = s.categories.toMap()
        return budgets.filter { it.value > 0 }.mapNotNull { (cat, b) ->
            val sp = spent[cat] ?: 0.0
            if (sp >= b * 0.8) Triple(cat, sp, b) else null
        }.sortedByDescending { it.second / it.third }
    }
}

package com.kaasu.tracker.data

enum class TxType(val label: String) {
    INCOME("Income"),
    EXPENSE("Expense"),
    ATM_WITHDRAWAL("ATM withdrawal"),
    REFUND("Refund"),
    TRANSFER("Transfer"),
    CREDIT_CARD_PAYMENT("Card payment"),
    UNKNOWN("Unknown")
}

enum class PayMode(val label: String) {
    UPI("UPI"),
    DEBIT_CARD("Debit card"),
    CREDIT_CARD("Credit card"),
    ATM("ATM"),
    BANK_TRANSFER("Bank transfer"),
    NET_BANKING("Net banking"),
    CASH("Cash"),
    OTHER("Bank")
}

enum class TxStatus(val label: String) {
    SUCCESS("Success"),
    PENDING("Pending"),
    FAILED("Failed"),
    REVERSED("Reversed")
}

data class Txn(
    val id: Long = 0,
    val ts: Long,
    val amount: Double,
    val credit: Boolean,
    val type: TxType,
    val category: String,
    val merchant: String,
    val rawMerchant: String? = null,
    val mode: PayMode,
    val bank: String? = null,
    val last4: String? = null,
    val reference: String? = null,
    val upi: String? = null,
    val sender: String = "",
    val body: String = "",
    val hash: String = "",
    val balance: Double? = null,
    val status: TxStatus = TxStatus.SUCCESS,
    val confidence: Int = 100,
    val needsReview: Boolean = false,
    val isDuplicate: Boolean = false,
    val ignored: Boolean = false,
    val groupId: String? = null,
    val manual: Boolean = false,
    val notes: String? = null
) {
    /** Counts toward monthly totals only when it is real, confirmed and not a copy. */
    val counted: Boolean
        get() = status == TxStatus.SUCCESS && !isDuplicate && !ignored && !needsReview

    val accountKey: String
        get() = (bank ?: "Bank") + "|" + (last4 ?: "")
}

data class Rule(val pattern: String, val merchant: String?, val category: String?, val type: TxType?)

data class ScanResult(val scanned: Int, val found: Int, val review: Int)

object Categories {
    val expense = listOf(
        "Food", "Groceries", "Shopping", "Fuel", "Bills", "Travel",
        "Entertainment", "Medical", "Education", "Rent", "Investments", "Other"
    )
    val income = listOf("Salary", "Transfers", "Refunds", "Interest", "Other income")
    val all = expense + income + listOf("Cash", "Transfer", "Card payment")
}

package com.kaasu.tracker.sms

import com.kaasu.tracker.data.PayMode
import com.kaasu.tracker.data.TxStatus
import com.kaasu.tracker.data.TxType

/**
 * Local, rule-based SMS parser. Nothing leaves the phone.
 *
 * Bank-specific behaviour lives in [BankProfiles] (sender codes + names); the
 * extraction rules below are generic so that any bank's wording is handled,
 * and the user's corrections are layered on top as rules by the repository.
 */
object SmsParser {

    data class Parsed(
        val amount: Double,
        val credit: Boolean,
        val type: TxType,
        val status: TxStatus,
        val mode: PayMode,
        val bank: String?,
        val last4: String?,
        val reference: String?,
        val upi: String?,
        val balance: Double?,
        val merchant: String,
        val rawMerchant: String?,
        val category: String,
        val confidence: Int,
        val knownMerchant: Boolean
    )

    private val I = RegexOption.IGNORE_CASE
    private const val NUM = """([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""

    private val AMT = Regex("""(?:rs\.?|inr|₹)\s*$NUM""", I)
    private val AMT_FALLBACK = Regex("""\b(?:debited|credited|spent|paid|withdrawn|sent|received)\s+(?:by|for|with|of)?\s*([0-9][0-9,]*\.[0-9]{1,2})""", I)
    private val BAL = Regex("""\b(?:avl\.?\s*|available\s+|avail\.?\s*|a/c\s+|clear\s+|closing\s+|total\s+)?bal(?:ance)?\b(?:[^0-9]{0,22}?(?:rs\.?|inr|₹)\s*|\s*(?:is|:|-)?\s*)$NUM""", I)
    private val LIMIT = Regex("""\b(?:avl\.?|available|avail\.?)\s*(?:lmt|limit|credit\s+limit)\b(?:[^0-9]{0,22}?(?:rs\.?|inr|₹)\s*|\s*(?:is|:|-)?\s*)$NUM""", I)

    private val SENDER_PHONE = Regex("""^\+?[0-9 ]{8,}$""")
    private val OTP = Regex("""\b(otp|one[\s-]?time\s+password|verification\s+code|passcode)\b""", I)
    private val STRONG = Regex("""\b(debited|credited|spent|withdrawn)\b""", I)
    private val NOT_TXN = Regex("""\b(has requested|collect request|requested money|will be debited|is due|due on|due date|minimum amount due|min\.? amt due|total amount due|statement is generated|statement generated)\b""", I)
    private val PROMO = Regex("""(pre[\s-]?approved|apply now|click here|offer valid|limited period|congratulations|you have won|eligible for|loan of up to|get up to|t&c apply|exclusive offer)""", I)

    private val DEBIT = Regex("""\b(debited|debit|spent|withdrawn|withdrawal|paid|sent|purchase|purchased|charged|used|deducted|dr|txn of|transferred to|money transfer|payment of|payment to)\b""", I)
    private val CREDIT = Regex("""\b(credited|credit|received|deposited|added|cr)\b""", I)
    private val REFUND = Regex("""\b(refund|refunded|reversed|reversal|chargeback)\b""", I)
    private val FAILED = Regex("""\b(failed|declined|unsuccessful|not successful|could not be processed|rejected)\b""", I)
    private val PENDING = Regex("""\b(pending|under process|being processed|in process)\b""", I)

    private val ATM = Regex("""\b(atm|cash withdrawal|cash wdl|withdrawn at|nfs|cash withdrawn)\b""", I)
    private val SELF = Regex("""\b(to self|self transfer|own a/?c|own account|wallet load|added to (?:your )?wallet|amazon pay balance|to your own)\b""", I)
    private val SALARY = Regex("""\b(salary|sal|payroll|wages|stipend)\b""", I)
    private val INTEREST = Regex("""\b(interest|int\.? pd|int\.? credit)\b""", I)
    private val CC = Regex("""(credit\s+card|\bcc\b|creditcard)""", I)
    private val CC_PAY = Regex("""(payment\s+(?:of\s+.{0,25}?\s+)?(?:received|towards)|towards\s+(?:your\s+)?(?:credit\s+)?card|credit\s+card\s+(?:bill|payment|dues)|paid\s+towards|card\s+bill)""", I)

    private val UPI_ID = Regex("""([a-z0-9._-]{2,}@[a-z]{2,})(?!\.?[a-z])""", I)
    private val UPI_WORD = Regex("""\b(upi|vpa|bhim)\b""", I)
    private val DCARD = Regex("""\b(debit\s+card|card)\b""", I)
    private val BANKXFER = Regex("""\b(neft|rtgs|imps)\b""", I)
    private val NETBANK = Regex("""\b(net\s?banking|inb|internet banking)\b""", I)

    private val MASK = Regex("""[x*•]{2,}\s?([0-9]{3,6})\b""", I)
    private val ACCT = Regex("""\b(?:account|acct|a/c|ac|card)\.?\s*(?:no\.?\s*)?(?:ending\s*(?:with|in)?\s*)?[:\s]*[x*•.\-]*\s*([0-9]{3,6})\b""", I)
    private val REF = Regex("""\b(?:upi\s*ref(?:erence)?(?:\s*no)?|refno|ref(?:erence)?(?:\s*no|\s*number|\s*id)?|utr(?:\s*no)?|rrn|txn\s*(?:id|no)|transaction\s*(?:id|no)|upi)\s*[:.#-]?\s*(?:no\.?\s*)?([a-z0-9]{6,25})""", I)

    private val MERCH = Regex("""\b(?:to|at|towards|for|via|from|by|info)\b\s*[:\-]?\s*(?:vpa\s+)?([a-z0-9][a-z0-9 &._@'-]{1,40}?)(?=\s+(?:on|ref|refno|upi|via|avl|avail|at|dated|thru|using|from|is|has|txn|in|for|not|and|with|of|failed|successful|was)\b|[.,;:()](?=\s|$)|\s*-\s|\s*$)""", I)
    private val SEMI_CREDITED = Regex(""";\s*([a-z0-9][a-z0-9 &._@'-]{1,40}?)\s+credited""", I)
    private val STOP_START = Regex("""^(a/?c|ac\b|acct|account|your|you\b|card|x+\d*|\*+|rs\b|rs\.|inr|the\b|a\b|an\b|self|mobile|beneficiary|upi\b|vpa|transaction|txn|payment|net ?banking|neft|imps|rtgs|nach|ach\b|\d)""", I)
    private val STOP_ANY = Regex("""(a/c|\bbank\b|\bbal\b|balance|\brs\b|\binr\b|₹|xx\d|\bdebit|\bcredit|\bdate\b)""", I)

    private fun num(s: String): Double? = s.replace(",", "").toDoubleOrNull()

    fun parse(sender: String, body: String): Parsed? {
        val addr = sender.trim()
        // Banks use alphanumeric sender IDs; personal numbers and promo headers are skipped.
        if (SENDER_PHONE.matches(addr)) return null
        if (addr.uppercase().endsWith("-P")) return null

        val l = body.lowercase()
        if (NOT_TXN.containsMatchIn(l)) return null
        if (OTP.containsMatchIn(l) && !STRONG.containsMatchIn(l)) return null

        val failed = FAILED.containsMatchIn(l)
        val isRefund = REFUND.containsMatchIn(l) && !failed
        val l2 = l.replace("credit card", "ccard").replace("debit card", "dcard")
        val dIdx = DEBIT.find(l2)?.range?.first ?: Int.MAX_VALUE
        val cIdx = CREDIT.find(l2)?.range?.first ?: Int.MAX_VALUE
        if (dIdx == Int.MAX_VALUE && cIdx == Int.MAX_VALUE && !isRefund) return null

        // Amount = first currency amount that is not part of a balance / limit phrase.
        val balRanges = (BAL.findAll(body) + LIMIT.findAll(body)).map { it.range }.toList()
        val amtMatch = AMT.findAll(body).firstOrNull { m ->
            balRanges.none { r -> r.first <= m.range.last && m.range.first <= r.last }
        }
        val amount = (amtMatch?.groupValues?.get(1) ?: AMT_FALLBACK.find(body)?.groupValues?.get(1))
            ?.let { num(it) } ?: return null
        if (amount <= 0.0) return null
        val balance = (BAL.find(body) ?: LIMIT.find(body))?.groupValues?.get(1)?.let { num(it) }

        val last4 = (MASK.find(body)?.groupValues?.get(1) ?: ACCT.find(body)?.groupValues?.get(1))?.takeLast(4)
        val bank = BankProfiles.bankOf(addr, l)
        val upi = UPI_ID.find(body)?.groupValues?.get(1)?.lowercase()
        val upiMention = upi != null || UPI_WORD.containsMatchIn(l)

        // Must look like it came from a bank / card / UPI context.
        if (bank == null && last4 == null && !upiMention) return null
        if (PROMO.containsMatchIn(l) && last4 == null) return null

        val credit = isRefund || cIdx < dIdx
        val bothDirections = dIdx != Int.MAX_VALUE && cIdx != Int.MAX_VALUE

        val status = when {
            failed -> TxStatus.FAILED
            PENDING.containsMatchIn(l) -> TxStatus.PENDING
            else -> TxStatus.SUCCESS
        }

        val hasLimit = LIMIT.containsMatchIn(body)
        val ccMention = CC.containsMatchIn(l) || hasLimit
        // Ignore UPI handle domains (@paytm, @ybl …) so payment apps are not mistaken for merchants.
        val catalog = MerchantCatalog.find(l.replace(Regex("@[a-z]+"), " "))

        val type = when {
            isRefund -> TxType.REFUND
            ccMention && CC_PAY.containsMatchIn(l) -> TxType.CREDIT_CARD_PAYMENT
            catalog?.name == "CRED" -> TxType.CREDIT_CARD_PAYMENT
            SELF.containsMatchIn(l) -> TxType.TRANSFER
            !credit && (ATM.containsMatchIn(l) || l.contains("withdrawn")) -> TxType.ATM_WITHDRAWAL
            credit -> TxType.INCOME
            else -> TxType.EXPENSE
        }

        val mode = when {
            type == TxType.ATM_WITHDRAWAL -> PayMode.ATM
            ccMention -> PayMode.CREDIT_CARD
            upiMention -> PayMode.UPI
            DCARD.containsMatchIn(l) -> PayMode.DEBIT_CARD
            BANKXFER.containsMatchIn(l) -> PayMode.BANK_TRANSFER
            NETBANK.containsMatchIn(l) -> PayMode.NET_BANKING
            else -> PayMode.OTHER
        }

        val reference = REF.findAll(body).map { it.groupValues[1] }
            .firstOrNull { s -> s.count { it.isDigit() } >= 6 }

        val isSalary = credit && SALARY.containsMatchIn(l)
        val isInterest = credit && INTEREST.containsMatchIn(l)

        // Merchant
        val candidate = merchantCandidate(body, bank)
        val rawMerchant: String?
        var merchant: String
        if (catalog != null) {
            merchant = catalog.name
            rawMerchant = candidate?.lowercase() ?: upi
        } else {
            rawMerchant = candidate?.lowercase() ?: upi
            merchant = when {
                candidate != null && candidate.contains("@") -> vpaName(candidate)
                candidate != null -> titleCase(candidate)
                upi != null -> vpaName(upi)
                else -> ""
            }
        }
        if (isSalary) merchant = "Salary"
        if (merchant.isBlank()) merchant = when (type) {
            TxType.ATM_WITHDRAWAL -> "ATM Withdrawal"
            TxType.CREDIT_CARD_PAYMENT -> "Credit card payment"
            TxType.TRANSFER -> "Self transfer"
            TxType.REFUND -> "Refund"
            TxType.INCOME -> if (isInterest) "Interest" else "Money received"
            else -> "Unknown"
        }
        if (type == TxType.ATM_WITHDRAWAL) merchant = "ATM Withdrawal"
        if (type == TxType.CREDIT_CARD_PAYMENT && catalog?.name != "CRED") merchant = "Credit card payment"
        if (type == TxType.TRANSFER && SELF.containsMatchIn(l)) merchant = "Self transfer"

        val category = when (type) {
            TxType.ATM_WITHDRAWAL -> "Cash"
            TxType.TRANSFER -> "Transfer"
            TxType.CREDIT_CARD_PAYMENT -> "Card payment"
            TxType.INCOME -> when {
                isSalary -> "Salary"
                isInterest -> "Interest"
                else -> "Transfers"
            }
            TxType.REFUND -> catalog?.category ?: "Refunds"
            else -> catalog?.category ?: MerchantCatalog.categoryFor(merchant.lowercase()) ?: "Other"
        }

        // Confidence
        var c = 50
        c += if (bothDirections) 8 else 15
        if (last4 != null) c += 10
        if (bank != null) c += 10
        if (reference != null) c += 5
        when {
            type == TxType.INCOME || type == TxType.ATM_WITHDRAWAL || type == TxType.TRANSFER ||
                type == TxType.CREDIT_CARD_PAYMENT || type == TxType.REFUND -> c += 10
            catalog != null -> c += 15
            merchant == "Unknown" -> c -= 25
        }
        c = c.coerceIn(5, 99)

        return Parsed(
            amount = amount, credit = credit, type = type, status = status, mode = mode,
            bank = bank, last4 = last4, reference = reference, upi = upi, balance = balance,
            merchant = merchant, rawMerchant = rawMerchant, category = category,
            confidence = c, knownMerchant = catalog != null
        )
    }

    private fun merchantCandidate(body: String, bank: String?): String? {
        val all = (SEMI_CREDITED.findAll(body) + MERCH.findAll(body)).map { it.groupValues[1].trim().trimEnd('.', '-', '\'') }
        return all.firstOrNull { c ->
            c.length >= 2 &&
                !STOP_START.containsMatchIn(c) &&
                !STOP_ANY.containsMatchIn(c) &&
                c.any { it.isLetter() } &&
                (bank == null || !c.equals(bank, ignoreCase = true))
        }
    }

    private val QR_PREFIX = Regex("""^(paytmqr|paytm-|bharatpe|q[0-9]|mab|yespay|gpay-|ombk|stk-|pos)""", I)

    fun vpaName(vpa: String): String {
        val local = vpa.substringBefore("@")
        if (QR_PREFIX.containsMatchIn(local)) return "Local merchant"
        if (local.count { it.isDigit() } >= 8) return "UPI transfer"
        val cleaned = local.replace(Regex("[._-]"), " ").replace(Regex("[0-9]+"), "").trim()
        return if (cleaned.length < 2) "UPI transfer" else titleCase(cleaned)
    }

    fun titleCase(s: String): String =
        s.trim().replace(Regex("\\s+"), " ").take(30).split(" ").joinToString(" ") { w ->
            w.lowercase().replaceFirstChar { it.titlecase() }
        }
}

/** Bank identification from DLT sender headers (e.g. "VM-HDFCBK-S") or message text. */
object BankProfiles {
    private val senderCodes = listOf(
        "SBICRD" to "SBI Card", "SBICARD" to "SBI Card",
        "HDFC" to "HDFC Bank", "ICICI" to "ICICI Bank", "AXIS" to "Axis Bank",
        "KOTAK" to "Kotak Bank", "IDFC" to "IDFC First", "INDUS" to "IndusInd",
        "FEDBNK" to "Federal Bank", "FEDERAL" to "Federal Bank",
        "CANBNK" to "Canara Bank", "CANARA" to "Canara Bank",
        "BOBTXN" to "Bank of Baroda", "BOBSMS" to "Bank of Baroda", "BARODA" to "Bank of Baroda",
        "UNIONB" to "Union Bank", "UBOI" to "Union Bank", "PNB" to "PNB",
        "YESBNK" to "Yes Bank", "YESBK" to "Yes Bank", "IOB" to "Indian Overseas Bank",
        "INDBNK" to "Indian Bank", "CENTBK" to "Central Bank", "BOIIND" to "Bank of India",
        "AUBANK" to "AU Bank", "SCBANK" to "Standard Chartered", "HSBC" to "HSBC",
        "AMEX" to "Amex", "PAYTM" to "Paytm", "PYTM" to "Paytm", "AIRBNK" to "Airtel Payments",
        "KVB" to "Karur Vysya", "CUBANK" to "City Union", "CUB" to "City Union",
        "TMBANK" to "TMB", "TMB" to "TMB", "SIBSMS" to "South Indian Bank",
        "SBI" to "SBI"
    )
    private val bodyNames = listOf(
        "hdfc bank" to "HDFC Bank", "icici bank" to "ICICI Bank", "axis bank" to "Axis Bank",
        "kotak" to "Kotak Bank", "idfc first" to "IDFC First", "indusind" to "IndusInd",
        "federal bank" to "Federal Bank", "canara bank" to "Canara Bank",
        "bank of baroda" to "Bank of Baroda", "union bank" to "Union Bank",
        "punjab national" to "PNB", "icici" to "ICICI Bank", "hdfc" to "HDFC Bank", "axis" to "Axis Bank", "yes bank" to "Yes Bank", "indian overseas" to "Indian Overseas Bank",
        "indian bank" to "Indian Bank", "karur vysya" to "Karur Vysya", "city union" to "City Union",
        "tamilnad mercantile" to "TMB", "south indian bank" to "South Indian Bank",
        "au small finance" to "AU Bank", "state bank" to "SBI", "sbi" to "SBI"
    )

    private val bodyRegex = bodyNames.map { Regex("\\b" + Regex.escape(it.first) + "\\b") to it.second }

    /** Earliest bank named in the text wins (the account the money moved in); else the sender ID. */
    fun bankOf(sender: String, lowerBody: String): String? {
        bodyRegex.mapNotNull { (re, name) -> re.find(lowerBody)?.let { it.range.first to name } }
            .minByOrNull { it.first }?.let { return it.second }
        val s = sender.uppercase()
        return senderCodes.firstOrNull { s.contains(it.first) }?.second
    }
}

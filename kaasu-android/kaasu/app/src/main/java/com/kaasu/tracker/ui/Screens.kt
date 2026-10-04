package com.kaasu.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaasu.tracker.data.Account
import com.kaasu.tracker.data.Categories
import com.kaasu.tracker.data.PayMode
import com.kaasu.tracker.data.Stats
import com.kaasu.tracker.data.TxType
import com.kaasu.tracker.data.Txn
import com.kaasu.tracker.util.Fmt
import com.kaasu.tracker.util.Money
import java.time.YearMonth

// ---------------------------------------------------------------- Transactions

@Composable
fun TransactionsScreen(vm: AppViewModel, onOpen: (Txn) -> Unit) {
    val txns by vm.txns.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("All", "Expenses", "Income", "UPI", "ATM", "Transfers", "Review")

    val list = remember(txns, month, query, tab) {
        Stats.inMonth(txns, month)
            .filter { !it.ignored }
            .filter { t ->
                when (tab) {
                    1 -> t.type == TxType.EXPENSE
                    2 -> t.type == TxType.INCOME || t.type == TxType.REFUND
                    3 -> t.mode == PayMode.UPI
                    4 -> t.type == TxType.ATM_WITHDRAWAL
                    5 -> t.type == TxType.TRANSFER || t.type == TxType.CREDIT_CARD_PAYMENT
                    6 -> t.needsReview && !t.isDuplicate
                    else -> true
                }
            }
            .filter { t ->
                val q = query.trim()
                q.isEmpty() || listOf(t.merchant, t.category, t.mode.label, t.bank ?: "", t.last4 ?: "", t.upi ?: "", t.type.label)
                    .any { it.contains(q, ignoreCase = true) } ||
                    (q.toDoubleOrNull()?.let { it == t.amount } ?: false)
            }
            .sortedByDescending { it.ts }
    }
    val review = if (tab == 6) list else list.filter { it.needsReview && !it.isDuplicate }
    val rest = if (tab == 6) emptyList() else list.filter { !(it.needsReview && !it.isDuplicate) }
    val days = rest.groupBy { Fmt.dayHeader(it.ts) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
        item {
            GradientHeader {
                Text("Transactions", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                MonthSwitcher(month, vm::prevMonth, vm::nextMonth, dark = false)
                Spacer(Modifier.height(12.dp))
                val shape = RoundedCornerShape(16.dp)
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(shape).background(Color.White.copy(alpha = 0.22f))
                        .border(1.dp, Color.White.copy(alpha = 0.45f), shape).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Search, null, tint = Color.White)
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                        cursorBrush = SolidColor(Color.White), modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            Box {
                                if (query.isEmpty()) Text("Search Amazon, UPI, HDFC, 450…", color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp)
                                inner()
                            }
                        }
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tabs.forEachIndexed { i, label -> Pill(label, tab == i, { tab = i }, onDark = true) }
                }
            }
        }
        items(review, key = { "r" + it.id }) { t -> ReviewCard(t, vm, onOpen) }
        if (list.isEmpty()) item {
            WhiteCard(Modifier.padding(top = 16.dp)) { Text("Nothing here for ${Fmt.month(month)}.", color = K.Muted) }
        }
        days.forEach { (day, dayTxns) ->
            item(key = "h$day") {
                Text(day, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = K.Muted, modifier = Modifier.padding(start = 22.dp, top = 18.dp, bottom = 4.dp))
            }
            items(dayTxns, key = { it.id }) { t -> TxnRow(t) { onOpen(t) } }
        }
    }
}

@Composable
fun ReviewCard(t: Txn, vm: AppViewModel, onOpen: (Txn) -> Unit) {
    AmberCard(Modifier.padding(top = 12.dp).clickable { onOpen(t) }) {
        Row {
            Text("NEEDS REVIEW · ${t.confidence}% confidence", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = K.AmberInk, modifier = Modifier.weight(1f))
            Text(Fmt.dayTime(t.ts), fontSize = 12.5.sp, color = K.AmberInk)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t.merchant, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = K.Ink, modifier = Modifier.weight(1f))
            Text(Money.inr(t.amount), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = K.Ink)
        }
        Text(t.body, fontSize = 12.sp, color = K.AmberInk, maxLines = 2)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Expense" to TxType.EXPENSE, "Income" to TxType.INCOME, "Transfer" to TxType.TRANSFER).forEach { (label, type) ->
                ReviewButton(label, Modifier.weight(1f)) { vm.resolve(t, type) }
            }
            ReviewButton("Ignore", Modifier.weight(1f)) { vm.ignore(t) }
        }
    }
}

@Composable
private fun ReviewButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.height(40.dp).clip(shape).background(Color.White).border(1.dp, K.AmberBorder, shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 13.sp, color = Color(0xFF7C2D12)) }
}

// ---------------------------------------------------------------- Accounts

@Composable
fun AccountsScreen(vm: AppViewModel, onOpen: (Txn) -> Unit) {
    val txns by vm.txns.collectAsStateWithLifecycle()
    val cash by vm.cash.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val accounts = remember(txns) { Stats.accounts(txns) }
    val total = Stats.totalBalance(accounts, cash)
    var editCash by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
        item {
            GradientHeader {
                Text("Accounts", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(14.dp))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total available balance", color = Color.White, fontSize = 14.5.sp)
                    Text(total.amount?.let { Money.inr(it) } ?: "Balance unavailable", color = Color.White, fontSize = if (total.amount != null) 40.sp else 26.sp, fontWeight = FontWeight.SemiBold)
                    Text("Bank-reported balances + cash. Card dues are shown separately.", color = Color.White, fontSize = 12.5.sp)
                }
            }
        }
        if (accounts.isEmpty()) item {
            WhiteCard(Modifier.padding(top = 16.dp)) { Text("No bank accounts detected yet. Scan your SMS from the Home screen.", color = K.Muted) }
        }
        items(accounts, key = { it.key }) { a -> AccountCard(a, Stats.accountMonth(txns, a.key, month), month) }
        item {
            WhiteCard(Modifier.padding(top = 10.dp).clickable { editCash = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFD7F5E3)), contentAlignment = Alignment.Center) {
                        Text("₹", color = K.Green, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Cash in hand", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = K.Ink)
                        Text("Entered by you · tap to update", fontSize = 12.5.sp, color = K.Muted)
                    }
                    Text(Money.inr(cash), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = K.Ink)
                }
            }
        }
    }
    if (editCash) AmountDialog("Cash in hand", cash, { editCash = false }) { vm.setCash(it) }
}

@Composable
fun AccountCard(a: Account, monthFlow: Pair<Double, Double>, month: YearMonth) {
    WhiteCard(Modifier.padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(if (a.isCard) Color(0xFFFFE8D2) else Color(0xFFDCE8FF)),
                contentAlignment = Alignment.Center
            ) {
                Text(a.bank.take(4).uppercase(), color = if (a.isCard) Color(0xFF9A3412) else Color(0xFF1E3A8A), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(a.bank + (if (a.isCard) " Card" else "") + (a.last4?.let { " ••$it" } ?: ""), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = K.Ink)
                val sub = when {
                    a.reported == null -> "Balance unavailable"
                    a.isCard -> "Available limit · ${Fmt.dayTime(a.reportedAt!!)}"
                    else -> "Bank-reported · ${Fmt.dayTime(a.reportedAt!!)}"
                }
                Text(sub, fontSize = 12.5.sp, color = if (a.reported != null && !a.isCard) K.Green else K.Muted)
            }
            Text(a.reported?.let { Money.inr(it) } ?: "—", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = K.Ink)
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = K.Line)
        LabelValue("${Fmt.monthShort(month)} credits", "+" + Money.inr(monthFlow.first), K.Green)
        LabelValue("${Fmt.monthShort(month)} debits", "−" + Money.inr(monthFlow.second))
        if (a.calculated != null) LabelValue("Calculated balance", Money.inr(a.calculated))
        a.mismatch?.let { diff ->
            Spacer(Modifier.height(6.dp))
            Text(
                "Balance mismatch: bank says ${Money.inr(a.reported!!)}, app calculated ${Money.inr(a.calculated!!)} (${Money.inr(kotlin.math.abs(diff))} difference). Some transactions may be missing SMS.",
                fontSize = 12.5.sp, color = K.AmberInk,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(K.Amber).padding(10.dp)
            )
        }
    }
}

// ---------------------------------------------------------------- Analytics

@Composable
fun AnalyticsScreen(vm: AppViewModel) {
    val txns by vm.txns.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val s = remember(txns, month) { Stats.month(txns, month) }
    val year = remember(txns, month) { Stats.year(txns, month.year) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
        item {
            GradientHeader {
                Text("Analytics", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                MonthSwitcher(month, vm::prevMonth, vm::nextMonth, dark = false)
            }
        }
        item {
            WhiteCard(Modifier.padding(top = 16.dp)) {
                CardTitle("Where your money went")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Donut(s.categories.map { categoryColor(it.first) to it.second }, "Net spent", Money.short(s.net))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (s.categories.isEmpty()) Text("No expenses yet", color = K.Muted, fontSize = 13.sp)
                        s.categories.take(8).forEach { (cat, v) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(categoryColor(cat)))
                                Spacer(Modifier.width(6.dp))
                                Text(cat, fontSize = 13.sp, color = K.Ink, modifier = Modifier.weight(1f), maxLines = 1)
                                Text(Money.inr(v), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = K.Ink)
                            }
                        }
                    }
                }
                if (s.refunds > 0) {
                    Spacer(Modifier.height(10.dp))
                    LabelValue("Gross expenses", Money.inr(s.gross))
                    LabelValue("Refunds", "−" + Money.inr(s.refunds), K.Green)
                    LabelValue("Net expenses", Money.inr(s.net))
                }
            }
        }
        item {
            WhiteCard(Modifier.padding(top = 12.dp)) {
                CardTitle("Budgets")
                val spent = s.categories.toMap()
                val active = budgets.filter { it.value > 0 }
                if (active.isEmpty()) Text("No budgets yet. Set them in Settings.", color = K.Muted, fontSize = 13.sp)
                active.forEach { (cat, b) ->
                    val sp = spent[cat] ?: 0.0
                    val f = (sp / b).toFloat()
                    Row(Modifier.fillMaxWidth()) {
                        Text(cat, fontSize = 14.sp, color = K.Ink, modifier = Modifier.weight(1f))
                        Text("${Money.inr(sp)} / ${Money.inr(b)}", fontSize = 14.sp, color = if (f >= 0.8f) K.Orange else K.Ink)
                    }
                    Spacer(Modifier.height(6.dp))
                    ProgressBar(f, if (f >= 1f) K.Orange else if (f >= 0.8f) Color(0xFFF97316) else K.Blue)
                    if (f >= 0.8f) Text(if (f >= 1f) "Budget exceeded" else "${(f * 100).toInt()}% used", fontSize = 12.sp, color = K.AmberInk)
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
        item {
            WhiteCard(Modifier.padding(top = 12.dp)) {
                CardTitle("Payment methods")
                if (s.modes.isEmpty()) Text("No spending yet", color = K.Muted, fontSize = 13.sp)
                s.modes.forEach { (m, v) -> LabelValue(m.label, Money.inr(v)) }
            }
        }
        item {
            WhiteCard(Modifier.padding(top = 12.dp)) {
                CardTitle("UPI — ${Fmt.month(month)}")
                LabelValue("UPI spending", Money.inr(s.upiSpend))
                LabelValue("UPI transactions", s.upiCount.toString())
                LabelValue("UPI received", Money.inr(s.upiCredits), K.Green)
                if (s.upiMerchants.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Top UPI merchants", fontSize = 13.sp, color = K.Muted)
                    s.upiMerchants.take(5).forEach { m -> LabelValue(m.name, Money.inr(m.total)) }
                }
            }
        }
        item {
            WhiteCard(Modifier.padding(top = 12.dp)) {
                CardTitle("Top merchants")
                if (s.merchants.isEmpty()) Text("No merchants yet", color = K.Muted, fontSize = 13.sp)
                s.merchants.take(8).forEach { m -> LabelValue("${m.name} · ${m.count}", Money.inr(m.total)) }
            }
        }
        item {
            WhiteCard(Modifier.padding(top = 12.dp)) {
                CardTitle("ATM withdrawals · ${Money.inr(s.atm)}")
                if (s.atmList.isEmpty()) Text("No withdrawals this month", color = K.Muted, fontSize = 13.sp)
                s.atmList.forEach { t -> LabelValue(Fmt.dayTime(t.ts) + (t.last4?.let { " · ••$it" } ?: ""), Money.inr(t.amount)) }
            }
        }
        item {
            WhiteCard(Modifier.padding(top = 12.dp)) {
                CardTitle("Income · ${Money.inr(s.income)}")
                if (s.incomeByCategory.isEmpty()) Text("No income recorded", color = K.Muted, fontSize = 13.sp)
                s.incomeByCategory.forEach { (c, v) -> LabelValue(c, Money.inr(v), K.Green) }
            }
        }
        item {
            WhiteCard(Modifier.padding(top = 12.dp)) {
                CardTitle("${Fmt.month(month)} summary")
                LabelValue("Income", Money.inr(s.income), K.Green)
                LabelValue("Expenses", Money.inr(s.net))
                LabelValue("Withdrawals", Money.inr(s.atm))
                LabelValue("Refunds", Money.inr(s.refunds))
                LabelValue("Savings", Money.inr(s.savings), if (s.savings >= 0) K.TransferBlue else K.Orange)
                s.categories.firstOrNull()?.let { LabelValue("Highest category", "${it.first} — ${Money.inr(it.second)}") }
                s.merchants.firstOrNull()?.let { LabelValue("Highest merchant", "${it.name} — ${Money.inr(it.total)}") }
                s.modes.firstOrNull()?.let { LabelValue("Most used method", it.first.label) }
            }
        }
        item {
            WhiteCard(Modifier.padding(top = 12.dp)) {
                CardTitle("Yearly summary · ${month.year}")
                val active = year.filter { it.income > 0 || it.expense > 0 }
                year.forEach { r ->
                    if (r.income > 0 || r.expense > 0) Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(Fmt.monthShort(r.month), fontSize = 14.sp, color = K.Ink, modifier = Modifier.width(44.dp))
                        Text("In " + Money.short(r.income), fontSize = 14.sp, color = K.Green, modifier = Modifier.weight(1f))
                        Text("Out " + Money.short(r.expense), fontSize = 14.sp, color = K.Ink)
                    }
                }
                if (active.isEmpty()) {
                    Text("No data for ${month.year} yet", color = K.Muted, fontSize = 13.sp)
                } else {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = K.Line)
                    val ti = active.sumOf { it.income }
                    val te = active.sumOf { it.expense }
                    LabelValue("Total income", Money.inr(ti), K.Green)
                    LabelValue("Total expenses", Money.inr(te))
                    LabelValue("Average monthly expense", Money.inr(te / active.size))
                    active.maxByOrNull { it.expense }?.let { LabelValue("Highest spending month", Fmt.month(it.month)) }
                    active.minByOrNull { it.expense }?.let { LabelValue("Lowest spending month", Fmt.month(it.month)) }
                    LabelValue("Total savings", Money.inr(ti - te), K.TransferBlue)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Settings

@Composable
fun SettingsScreen(vm: AppViewModel, requestScan: (Int) -> Unit, onExportAll: () -> Unit) {
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val threshold by vm.threshold.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    var months by rememberSaveable { mutableIntStateOf(3) }
    var editBudget by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
        item {
            GradientHeader { Text("Settings", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium) }
        }
        item {
            WhiteCard(Modifier.padding(top = 16.dp)) {
                CardTitle("SMS scanning")
                Text("Read bank SMS from:", fontSize = 13.sp, color = K.Muted)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1 to "This month", 3 to "3 months", 6 to "6 months", 12 to "12 months").forEach { (m, l) -> Pill(l, months == m, { months = m }) }
                }
                Button(onClick = { requestScan(months) }, enabled = !scanning, modifier = Modifier.fillMaxWidth()) {
                    Text(if (scanning) "Scanning…" else "Scan now")
                }
                Text("Already-imported messages are skipped automatically.", fontSize = 12.sp, color = K.Muted, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            WhiteCard(Modifier.padding(top = 12.dp)) {
                CardTitle("Review threshold")
                Text("Transactions parsed with lower confidence go to \"Needs review\" and stay out of totals.", fontSize = 13.sp, color = K.Muted)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(60, 70, 80, 90).forEach { v -> Pill("$v%", threshold == v, { vm.setThreshold(v) }) }
                }
            }
        }
        item {
            WhiteCard(Modifier.padding(top = 12.dp)) {
                CardTitle("Monthly budgets")
                Text("Alerts appear on Home at 80% and 100%.", fontSize = 13.sp, color = K.Muted)
                Spacer(Modifier.height(6.dp))
                Categories.expense.forEach { c ->
                    Row(Modifier.fillMaxWidth().clickable { editBudget = c }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(categoryColor(c)))
                        Spacer(Modifier.width(10.dp))
                        Text(c, fontSize = 15.sp, color = K.Ink, modifier = Modifier.weight(1f))
                        Text(budgets[c]?.let { Money.inr(it) } ?: "Set", fontSize = 15.sp, color = if (budgets[c] != null) K.Ink else K.Blue)
                    }
                }
            }
        }
        item {
            WhiteCard(Modifier.padding(top = 12.dp)) {
                CardTitle("Export")
                OutlinedButton(onClick = onExportAll, modifier = Modifier.fillMaxWidth()) { Text("Export all transactions (CSV)") }
                Text("Opens in Excel or Google Sheets. Use Export on Home for a single month.", fontSize = 12.sp, color = K.Muted, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            WhiteCard(Modifier.padding(top = 12.dp)) {
                CardTitle("Privacy & data")
                Text(
                    "• SMS are read and parsed only on this phone.\n• No server, no cloud database, no analytics, no AI service receives your messages.\n• Only bank-style messages are processed; OTPs, promotions and personal SMS are skipped.\n• App backup is disabled so data does not leave the device.",
                    fontSize = 13.sp, color = K.Ink, lineHeight = 20.sp
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB42318))
                ) { Text("Delete all data") }
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            Text("Kaasu 1.0 · offline expense tracker", fontSize = 12.sp, color = K.Muted, modifier = Modifier.fillMaxWidth().padding(16.dp))
        }
    }

    editBudget?.let { c ->
        AmountDialog("$c budget", budgets[c] ?: 0.0, { editBudget = null }) { vm.setBudget(c, it) }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete all data?") },
        text = { Text("This permanently removes every stored transaction, budget and learned rule from this phone. Your SMS inbox is not touched.") },
        confirmButton = { TextButton(onClick = { vm.deleteAll(); confirmDelete = false }) { Text("Delete", color = Color(0xFFB42318)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
    )
}

// ---------------------------------------------------------------- Detail sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxnDetailSheet(t: Txn, vm: AppViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var merchant by remember(t.id) { mutableStateOf(t.merchant) }
    var category by remember(t.id) { mutableStateOf(t.category) }
    var type by remember(t.id) { mutableStateOf(t.type) }
    var confirmDelete by remember { mutableStateOf(false) }
    val types = listOf(TxType.EXPENSE, TxType.INCOME, TxType.REFUND, TxType.TRANSFER, TxType.ATM_WITHDRAWAL, TxType.CREDIT_CARD_PAYMENT)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(t, 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.merchant, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = K.Ink)
                    Text("${t.type.label} · ${t.category}", fontSize = 14.sp, color = K.Muted)
                }
                Text(amountText(t), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = amountColor(t))
            }
            Spacer(Modifier.height(14.dp))
            LabelValue("Date", Fmt.full(t.ts))
            LabelValue("Payment", t.mode.label)
            if (t.bank != null || t.last4 != null) LabelValue("Account", listOfNotNull(t.bank, t.last4?.let { "XXXX$it" }).joinToString(" "))
            t.upi?.let { LabelValue("UPI", it) }
            t.reference?.let { LabelValue("Reference", it) }
            t.balance?.let { LabelValue(if (t.mode == PayMode.CREDIT_CARD) "Available limit after" else "Balance after transaction", Money.inr(it)) }
            LabelValue("Status", t.status.label)
            if (!t.manual) LabelValue("Confidence", "${t.confidence}%")
            t.groupId?.let { LabelValue("Group", it) }
            if (t.isDuplicate) LabelValue("Duplicate", "Not counted (same money as another SMS)", K.Muted)
            t.notes?.let { LabelValue("Notes", it) }

            if (t.body.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text("Original SMS · ${t.sender}", fontSize = 13.sp, color = K.Muted)
                Spacer(Modifier.height(4.dp))
                Text(
                    t.body, fontSize = 13.sp, color = K.Ink, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(K.Bg).padding(12.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = K.Line)
            Spacer(Modifier.height(12.dp))
            Text("Edit", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = K.Ink)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = merchant, onValueChange = { merchant = it }, label = { Text("Merchant") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Text("Type", fontSize = 13.sp, color = K.Muted)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                types.forEach { ty -> Pill(ty.label, type == ty, { type = ty }) }
            }
            Text("Category", fontSize = 13.sp, color = K.Muted)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Categories.all.forEach { c -> Pill(c, category == c, { category = c }) }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val credit = type == TxType.INCOME || type == TxType.REFUND
                    vm.save(
                        t.copy(merchant = merchant.trim().ifBlank { t.merchant }, category = category, type = type, credit = if (type == TxType.TRANSFER || type == TxType.CREDIT_CARD_PAYMENT) t.credit else credit, needsReview = false, confidence = if (t.manual) t.confidence else 100),
                        learn = merchant != t.merchant || category != t.category || type != t.type
                    )
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Save changes") }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.save(t.copy(isDuplicate = !t.isDuplicate, needsReview = false), learn = false); onDismiss() }, modifier = Modifier.weight(1f)) {
                    Text(if (t.isDuplicate) "Not a duplicate" else "Mark duplicate")
                }
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) {
                    Text("Delete", color = Color(0xFFB42318))
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this transaction?") },
        text = { Text("It will be removed from Kaasu. Your SMS is not deleted.") },
        confirmButton = { TextButton(onClick = { vm.delete(t); confirmDelete = false; onDismiss() }) { Text("Delete", color = Color(0xFFB42318)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
    )
}

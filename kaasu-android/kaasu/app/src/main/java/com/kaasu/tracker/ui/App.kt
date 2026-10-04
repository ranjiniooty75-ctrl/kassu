package com.kaasu.tracker.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kaasu.tracker.data.Categories
import com.kaasu.tracker.data.MonthStats
import com.kaasu.tracker.data.PayMode
import com.kaasu.tracker.data.Stats
import com.kaasu.tracker.data.TxType
import com.kaasu.tracker.data.Txn
import com.kaasu.tracker.util.Fmt
import com.kaasu.tracker.util.Money
import java.time.YearMonth

fun hasSmsPermission(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

@Composable
fun App(vm: AppViewModel = viewModel()) {
    val ctx = LocalContext.current
    val onboarded by vm.onboarded.collectAsStateWithLifecycle()
    val scanResult by vm.scanResult.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()

    var pendingMonths by remember { mutableIntStateOf(1) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.READ_SMS] == true) vm.scan(pendingMonths)
        else vm.message.value = "Without SMS access you can still add transactions manually."
    }
    val requestScan: (Int) -> Unit = { m ->
        pendingMonths = m
        if (hasSmsPermission(ctx)) vm.scan(m)
        else permLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
    }

    var exportMonth by remember { mutableStateOf<YearMonth?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.export(uri, exportMonth)
    }
    val startExport: (YearMonth?) -> Unit = { m ->
        exportMonth = m
        exportLauncher.launch(if (m != null) "kaasu-${m}.csv" else "kaasu-all.csv")
    }

    if (!onboarded) {
        Onboarding(vm, requestScan)
        return
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Txn?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(K.Bg)) {
        when (tab) {
            0 -> HomeScreen(
                vm, onOpen = { detail = it }, onSeeAll = { tab = 1 },
                onScan = { requestScan(1) }, onAdd = { showAdd = true },
                onBudgets = { tab = 4 }, onExport = { startExport(month) }
            )
            1 -> TransactionsScreen(vm, onOpen = { detail = it })
            2 -> AccountsScreen(vm, onOpen = { detail = it })
            3 -> AnalyticsScreen(vm)
            else -> SettingsScreen(vm, requestScan = requestScan, onExportAll = { startExport(null) })
        }
        BottomPill(tab, { tab = it }, Modifier.align(Alignment.BottomCenter))
    }

    detail?.let { d ->
        val fresh = vm.txns.value.firstOrNull { it.id == d.id } ?: d
        TxnDetailSheet(fresh, vm, onDismiss = { detail = null })
    }
    if (showAdd) AddTxnDialog(vm, onDismiss = { showAdd = false })
    scanResult?.let { r ->
        AlertDialog(
            onDismissRequest = { vm.dismissScanResult() },
            confirmButton = { TextButton(onClick = { vm.dismissScanResult() }) { Text("Open dashboard") } },
            title = { Text("Scan complete") },
            text = {
                Column {
                    Text("${r.scanned} messages scanned")
                    Text("${r.found} financial transactions found")
                    Text("${r.review} transactions need review")
                }
            }
        )
    }
    message?.let { m ->
        AlertDialog(
            onDismissRequest = { vm.dismissMessage() },
            confirmButton = { TextButton(onClick = { vm.dismissMessage() }) { Text("OK") } },
            text = { Text(m) }
        )
    }
}

// ---------------------------------------------------------------- Onboarding

@Composable
fun Onboarding(vm: AppViewModel, requestScan: (Int) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var months by rememberSaveable { mutableIntStateOf(1) }
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val result by vm.scanResult.collectAsStateWithLifecycle()

    val titles = listOf(
        "Welcome to Kaasu",
        "Automatically organize your money",
        "Your SMS stays on your device",
        "Allow transaction SMS access"
    )
    val bodies = listOf(
        "A smart expense tracker that turns your bank SMS into a clear monthly picture.",
        "Income, expenses, UPI, ATM withdrawals, refunds and transfers — consolidated month by month, without typing anything.",
        "Messages are read and parsed on this phone only. Nothing is uploaded, sold or sent to any server or AI service.",
        "Kaasu needs access to transaction SMS messages to automatically identify your income, expenses, UPI payments and withdrawals. OTPs, promotions and personal messages are skipped."
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(HeaderBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Logo(84.dp)
        Spacer(Modifier.height(28.dp))
        Text(titles[step], color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(bodies[step], color = Color.White.copy(alpha = 0.95f), fontSize = 16.sp, textAlign = TextAlign.Center, lineHeight = 23.sp)

        if (step == 3 && result == null) {
            Spacer(Modifier.height(22.dp))
            Text("Scan history from", color = Color.White, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to "This month", 3 to "3 months", 6 to "6 months", 12 to "12 months").forEach { (m, label) ->
                    Pill(label, months == m, { months = m }, onDark = true)
                }
            }
        }
        result?.let { r ->
            Spacer(Modifier.height(22.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.2f)).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("${r.scanned} messages scanned", color = Color.White, fontSize = 16.sp)
                Text("${r.found} financial transactions found", color = Color.White, fontSize = 16.sp)
                Text("${r.review} transactions need review", color = Color.White, fontSize = 16.sp)
            }
        }

        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) { i ->
                Box(Modifier.size(if (i == step) 22.dp else 8.dp, 8.dp).clip(CircleShape).background(Color.White.copy(alpha = if (i == step) 1f else 0.45f)))
            }
        }
        Spacer(Modifier.height(22.dp))
        val buttonColors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = K.Blue)
        when {
            step < 3 -> Button(onClick = { step++ }, colors = buttonColors, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Text("Next", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            scanning -> CircularProgressIndicator(color = Color.White)
            result != null -> Button(
                onClick = { vm.dismissScanResult(); vm.finishOnboarding() },
                colors = buttonColors, modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text("Open dashboard", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            else -> Button(onClick = { requestScan(months) }, colors = buttonColors, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Text("Allow SMS access & scan", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (step == 3 && result == null && !scanning) {
            TextButton(onClick = { vm.finishOnboarding() }) {
                Text("Skip — I'll add transactions manually", color = Color.White)
            }
        }
    }
}

// ---------------------------------------------------------------- Home

@Composable
fun HomeScreen(
    vm: AppViewModel,
    onOpen: (Txn) -> Unit,
    onSeeAll: () -> Unit,
    onScan: () -> Unit,
    onAdd: () -> Unit,
    onBudgets: () -> Unit,
    onExport: () -> Unit
) {
    val txns by vm.txns.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val cash by vm.cash.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()

    val stats = remember(txns, month) { Stats.month(txns, month) }
    val accounts = remember(txns) { Stats.accounts(txns) }
    val total = Stats.totalBalance(accounts, cash)
    val recent = remember(txns, month) {
        Stats.inMonth(txns, month).filter { !it.isDuplicate && !it.ignored }.sortedByDescending { it.ts }.take(8)
    }
    val alerts = remember(stats, budgets) { Stats.budgetAlerts(stats, budgets) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
        item {
            GradientHeader {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Logo()
                    Spacer(Modifier.width(10.dp))
                    Text("Kaasu", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    GlassIconButton(Icons.Outlined.Notifications, "Needs review", onSeeAll)
                    Spacer(Modifier.width(10.dp))
                    GlassIconButton(Icons.Outlined.Shield, "Privacy", { vm.message.value = "All your transaction data is stored only on this phone. Nothing is uploaded." })
                }
                Spacer(Modifier.height(26.dp))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (total.bankAccounts > 0) "Total balance · bank-reported" else "Total balance", color = Color.White, fontSize = 15.sp)
                    if (total.amount != null) {
                        Text(Money.inr(total.amount), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Balance unavailable", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(6.dp))
                    val chip = when {
                        total.bankAccounts > 0 -> "${total.bankAccounts} account${if (total.bankAccounts > 1) "s" else ""}" + (if (cash > 0) " + cash" else "")
                        else -> "No balance SMS yet"
                    }
                    Text(
                        chip, color = Color.White, fontSize = 12.5.sp,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0x400F286E)).padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    GlassAction(Icons.Outlined.Sync, if (scanning) "Scanning…" else "Scan SMS", onScan)
                    GlassAction(Icons.Outlined.Add, "Add", onAdd)
                    GlassAction(Icons.Outlined.TrackChanges, "Budgets", onBudgets)
                    GlassAction(Icons.Outlined.Download, "Export", onExport)
                }
            }
        }
        item { MonthCard(month, stats, vm::prevMonth, vm::nextMonth) }

        if (stats.reviewCount > 0) item {
            AmberCard(Modifier.padding(top = 12.dp).clickable(onClick = onSeeAll)) {
                Text("${stats.reviewCount} transaction${if (stats.reviewCount > 1) "s" else ""} need review", color = K.AmberInk, fontWeight = FontWeight.SemiBold)
                Text("They are kept out of your totals until you confirm them.", color = K.AmberInk, fontSize = 13.sp)
            }
        }
        items(alerts) { (cat, spent, budget) ->
            AmberCard(Modifier.padding(top = 10.dp)) {
                val pct = (spent / budget * 100).toInt()
                Text(
                    if (pct >= 100) "$cat budget exceeded" else "$cat budget is $pct% used",
                    color = K.AmberInk, fontWeight = FontWeight.SemiBold
                )
                Text("${Money.inr(spent)} of ${Money.inr(budget)}", color = K.AmberInk, fontSize = 13.sp)
            }
        }

        if (stats.merchants.isNotEmpty()) {
            item { SectionHeader("Top Merchants") }
            item {
                WhiteCard {
                    stats.merchants.take(4).forEach { m ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(m.name, fontSize = 15.sp, color = K.Ink, modifier = Modifier.weight(1f))
                            Text("${m.count} txn", fontSize = 12.5.sp, color = K.Muted)
                            Spacer(Modifier.width(12.dp))
                            Text(Money.inr(m.total), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = K.Ink)
                        }
                    }
                }
            }
        }

        item { SectionHeader("Recent Transactions", "See all", onSeeAll) }
        if (recent.isEmpty()) item {
            WhiteCard {
                Text("No transactions for ${Fmt.month(month)} yet.", color = K.Ink, fontSize = 15.sp)
                Text("Tap Scan SMS to read bank messages, or Add to enter one manually.", color = K.Muted, fontSize = 13.sp)
            }
        }
        items(recent, key = { it.id }) { t -> TxnRow(t) { onOpen(t) } }
    }
}

@Composable
fun MonthCard(month: YearMonth, s: MonthStats, onPrev: () -> Unit, onNext: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .padding(start = 18.dp, end = 18.dp, top = 14.dp)
            .fillMaxWidth()
            .shadow(10.dp, shape, ambientColor = K.Blue, spotColor = K.Blue)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFFE4F0FF), Color(0xFFC9E0FF))))
            .padding(14.dp)
    ) {
        MonthSwitcher(month, onPrev, onNext)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Income", Money.inr(s.income), K.Green, Modifier.weight(1f))
            StatTile("Expenses", Money.inr(s.net), K.Orange, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("ATM withdrawn", Money.inr(s.atm), K.Ink, Modifier.weight(1f))
            StatTile("Saved", Money.inr(s.savings), if (s.savings >= 0) K.TransferBlue else K.Orange, Modifier.weight(1f))
        }
        if (s.refunds > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Gross ${Money.inr(s.gross)} − refunds ${Money.inr(s.refunds)} = net ${Money.inr(s.net)}",
                fontSize = 12.5.sp, color = Color(0xFF1E3A8A), modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        if (s.pendingCount > 0) {
            Text("${s.pendingCount} pending — not counted yet", fontSize = 12.5.sp, color = K.AmberInk, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
        }
    }
}

// ---------------------------------------------------------------- Dialogs

@Composable
fun AmountDialog(title: String, initial: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by remember { mutableStateOf(if (initial > 0) initial.toLong().toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' } },
                label = { Text("Amount (₹)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.toDoubleOrNull() ?: 0.0); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddTxnDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var mode by remember { mutableStateOf(PayMode.CASH) }
    var category by remember { mutableStateOf("Food") }
    val types = listOf(TxType.EXPENSE, TxType.INCOME, TxType.TRANSFER, TxType.ATM_WITHDRAWAL, TxType.REFUND)
    val modes = listOf(PayMode.CASH, PayMode.UPI, PayMode.DEBIT_CARD, PayMode.CREDIT_CARD, PayMode.BANK_TRANSFER)
    val cats = if (type == TxType.INCOME) Categories.income else Categories.expense

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add transaction") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = amount, onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' } },
                    label = { Text("Amount (₹)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = merchant, onValueChange = { merchant = it }, label = { Text("Merchant / description") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Text("Type", fontSize = 13.sp, color = K.Muted)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    types.forEach { t ->
                        Pill(t.label, type == t, {
                            type = t
                            category = when (t) {
                                TxType.INCOME -> "Salary"; TxType.TRANSFER -> "Transfer"; TxType.ATM_WITHDRAWAL -> "Cash"; else -> "Food"
                            }
                        })
                    }
                }
                if (type == TxType.EXPENSE || type == TxType.INCOME || type == TxType.REFUND) {
                    Text("Category", fontSize = 13.sp, color = K.Muted)
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        cats.forEach { c -> Pill(c, category == c, { category = c }) }
                    }
                }
                Text("Paid with", fontSize = 13.sp, color = K.Muted)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    modes.forEach { m -> Pill(m.label, mode == m, { mode = m }) }
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = amount.toDoubleOrNull()
                if (a != null && a > 0) {
                    vm.addManual(a, merchant.trim(), category, type, mode, notes.trim())
                    onDismiss()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

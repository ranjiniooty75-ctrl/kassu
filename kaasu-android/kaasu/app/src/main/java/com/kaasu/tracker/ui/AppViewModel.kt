package com.kaasu.tracker.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaasu.tracker.data.PayMode
import com.kaasu.tracker.data.Repo
import com.kaasu.tracker.data.ScanResult
import com.kaasu.tracker.data.Stats
import com.kaasu.tracker.data.Txn
import com.kaasu.tracker.data.TxStatus
import com.kaasu.tracker.data.TxType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()

    val txns = MutableStateFlow<List<Txn>>(emptyList())
    val budgets = MutableStateFlow<Map<String, Double>>(emptyMap())
    val month = MutableStateFlow(YearMonth.now())
    val cash = MutableStateFlow(0.0)
    val threshold = MutableStateFlow(70)
    val onboarded = MutableStateFlow(false)
    val scanning = MutableStateFlow(false)
    val scanResult = MutableStateFlow<ScanResult?>(null)
    val message = MutableStateFlow<String?>(null)

    init {
        Repo.init(app)
        cash.value = Repo.cash()
        threshold.value = Repo.threshold()
        onboarded.value = Repo.onboarded()
        viewModelScope.launch { Repo.version.collect { reload() } }
    }

    private suspend fun reload() {
        val (t, b) = withContext(Dispatchers.IO) { Repo.all() to Repo.budgets() }
        txns.value = t
        budgets.value = b
        cash.value = Repo.cash()
    }

    private fun io(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { block() }
    }

    fun prevMonth() { month.value = month.value.minusMonths(1) }
    fun nextMonth() { month.value = month.value.plusMonths(1) }

    fun scan(months: Int) {
        if (scanning.value) return
        viewModelScope.launch {
            scanning.value = true
            try {
                scanResult.value = Repo.scanInbox(ctx, months)
            } catch (e: SecurityException) {
                message.value = "SMS permission was not granted."
            } catch (e: Exception) {
                message.value = "Scan failed: ${e.message}"
            } finally {
                scanning.value = false
            }
        }
    }

    fun dismissScanResult() { scanResult.value = null }
    fun dismissMessage() { message.value = null }

    fun finishOnboarding() {
        Repo.setOnboarded()
        onboarded.value = true
    }

    /** Saves an edit and learns from it so similar SMS are handled the same way. */
    fun save(t: Txn, learn: Boolean) = io {
        Repo.update(t)
        if (learn && !t.manual) Repo.learn(t)
    }

    fun resolve(t: Txn, type: TxType) {
        val credit = type == TxType.INCOME || type == TxType.REFUND
        val category = when (type) {
            TxType.TRANSFER -> "Transfer"
            TxType.INCOME -> if (t.category in listOf("Other", "Transfers")) "Transfers" else t.category
            else -> if (t.category in listOf("Transfers", "Transfer")) "Other" else t.category
        }
        save(t.copy(type = type, credit = credit, category = category, needsReview = false, confidence = 100), learn = true)
    }

    fun ignore(t: Txn) = io { Repo.update(t.copy(ignored = true, needsReview = false)) }
    fun delete(t: Txn) = io { Repo.delete(t) }

    fun addManual(amount: Double, merchant: String, category: String, type: TxType, mode: PayMode, notes: String) = io {
        val credit = type == TxType.INCOME || type == TxType.REFUND
        Repo.insertManual(
            Txn(
                ts = System.currentTimeMillis(), amount = amount, credit = credit, type = type,
                category = category, merchant = merchant.ifBlank { type.label }, mode = mode,
                bank = if (mode == PayMode.CASH) "Cash" else null, status = TxStatus.SUCCESS,
                confidence = 100, manual = true, notes = notes.ifBlank { null },
                hash = "manual-" + System.nanoTime()
            )
        )
    }

    fun setBudget(category: String, amount: Double) = io { Repo.setBudget(category, amount) }
    fun setCash(v: Double) = io { Repo.setCash(v) }
    fun setThreshold(v: Int) { threshold.value = v; Repo.setThreshold(v) }
    fun deleteAll() = io { Repo.deleteAll() }

    fun export(uri: Uri, onlyMonth: YearMonth?) = viewModelScope.launch {
        val list = txns.value.filter { onlyMonth == null || Stats.monthOf(it.ts) == onlyMonth }
        try {
            Repo.exportCsv(ctx, uri, list)
            message.value = "Exported ${list.size} transactions."
        } catch (e: Exception) {
            message.value = "Export failed: ${e.message}"
        }
    }
}

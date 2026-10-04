package com.kaasu.tracker.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.kaasu.tracker.data.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Picks up new transaction SMS as they arrive and updates the dashboard instantly. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return
        val grouped = messages.filterNotNull().groupBy { it.originatingAddress ?: "" }
        Repo.init(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                grouped.forEach { (address, parts) ->
                    val body = parts.joinToString("") { it.messageBody ?: "" }
                    Repo.ingest(address, body, System.currentTimeMillis())
                }
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}

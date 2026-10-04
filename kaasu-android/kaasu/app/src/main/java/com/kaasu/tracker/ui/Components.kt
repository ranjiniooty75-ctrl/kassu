package com.kaasu.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaasu.tracker.data.TxStatus
import com.kaasu.tracker.data.TxType
import com.kaasu.tracker.data.Txn
import com.kaasu.tracker.util.Fmt
import com.kaasu.tracker.util.Money
import java.time.YearMonth

object K {
    val Bg = Color(0xFFF3F6FB)
    val Blue = Color(0xFF1E63E0)
    val BlueMid = Color(0xFF4C8DF6)
    val BlueLight = Color(0xFFA9D2FF)
    val Ink = Color(0xFF0F172A)
    val Muted = Color(0xFF64748B)
    val Slate = Color(0xFF475569)
    val Green = Color(0xFF0B7A42)
    val Orange = Color(0xFFC2410C)
    val TransferBlue = Color(0xFF1E4FD8)
    val Chip = Color(0xFFEEF2F8)
    val Line = Color(0xFFEEF2F7)
    val Amber = Color(0xFFFFF6E8)
    val AmberBorder = Color(0xFFF5C77E)
    val AmberInk = Color(0xFF92400E)
}

val HeaderBrush = Brush.verticalGradient(listOf(K.BlueLight, K.BlueMid, K.Blue))

private val categoryColors = mapOf(
    "Shopping" to Color(0xFF1E4FD8), "Food" to Color(0xFFF97316), "Other" to Color(0xFF94A3B8),
    "Bills" to Color(0xFF38BDF8), "Fuel" to Color(0xFFFBBF24), "Travel" to Color(0xFF4338CA),
    "Entertainment" to Color(0xFFDB2777), "Medical" to Color(0xFF0F766E), "Groceries" to Color(0xFF65A30D),
    "Education" to Color(0xFF7C3AED), "Rent" to Color(0xFF92400E), "Investments" to Color(0xFF0E7490)
)

fun categoryColor(c: String): Color = categoryColors[c] ?: Color(0xFF64748B)

@Composable
fun KaasuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = K.Blue, onPrimary = Color.White, background = K.Bg, surface = Color.White,
            onSurface = K.Ink, secondaryContainer = Color(0xFFDCE8FF)
        ),
        content = content
    )
}

@Composable
fun GradientHeader(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 34.dp, bottomEnd = 34.dp))
            .background(HeaderBrush)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 22.dp),
        content = content
    )
}

@Composable
fun GlassIconButton(icon: ImageVector, label: String, onClick: () -> Unit, size: Dp = 46.dp, round: Boolean = true) {
    val shape = if (round) CircleShape else RoundedCornerShape(18.dp)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.18f))
            .border(1.dp, Color.White.copy(alpha = 0.45f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = Color.White)
    }
}

@Composable
fun GlassAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlassIconButton(icon, label, onClick, size = 62.dp, round = false)
        Spacer(Modifier.height(7.dp))
        Text(label, color = Color.White, fontSize = 13.5.sp)
    }
}

@Composable
fun Logo(size: Dp = 36.dp) {
    Box(
        Modifier
            .size(size)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color.White, Color(0xFFCFE4FF), Color(0xFF2F6FE4))))
    )
}

@Composable
fun WhiteCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .padding(horizontal = 18.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .padding(16.dp),
        content = content
    )
}

@Composable
fun AmberCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .padding(horizontal = 18.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(K.Amber)
            .border(1.dp, K.AmberBorder, shape)
            .padding(14.dp),
        content = content
    )
}

@Composable
fun CardTitle(text: String) {
    Text(text, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = K.Ink)
    Spacer(Modifier.height(10.dp))
}

@Composable
fun SectionHeader(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 21.sp, fontWeight = FontWeight.Medium, color = K.Ink, modifier = Modifier.weight(1f))
        if (action != null) Text(action, fontSize = 14.5.sp, color = K.Slate, modifier = Modifier.clickable(onClick = onAction).padding(6.dp))
    }
}

@Composable
fun StatTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.78f))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(label, fontSize = 12.5.sp, color = K.Slate)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

@Composable
fun MonthSwitcher(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit, dark: Boolean = true) {
    val fg = if (dark) Color(0xFF0F2A6B) else Color.White
    val btnBg = if (dark) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.2f)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(btnBg).clickable(onClick = onPrev), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.ChevronLeft, "Previous month", tint = fg)
        }
        Text(Fmt.month(month), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = fg, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Box(Modifier.size(38.dp).clip(CircleShape).background(btnBg).clickable(onClick = onNext), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.ChevronRight, "Next month", tint = fg)
        }
    }
}

private val avatarPalette = listOf(
    Color(0xFFFFE8D2) to Color(0xFF9A3412), Color(0xFFFFE2DC) to Color(0xFFB42318),
    Color(0xFFDCE8FF) to Color(0xFF1E3A8A), Color(0xFFE9E3FF) to Color(0xFF4C1D95),
    Color(0xFFDDF4F1) to Color(0xFF115E59), Color(0xFFFCE7F3) to Color(0xFF9D174D)
)

@Composable
fun Avatar(t: Txn, size: Dp = 46.dp) {
    val icon: ImageVector?
    val bg: Color
    val fg: Color
    var label = ""
    when (t.type) {
        TxType.TRANSFER, TxType.CREDIT_CARD_PAYMENT -> {
            icon = if (t.type == TxType.TRANSFER) Icons.Outlined.SwapHoriz else Icons.Outlined.CreditCard
            bg = Color(0xFFDCE8FF); fg = K.TransferBlue
        }
        TxType.INCOME, TxType.REFUND -> { icon = Icons.Outlined.ArrowDownward; bg = Color(0xFFD7F5E3); fg = K.Green }
        TxType.ATM_WITHDRAWAL -> { icon = null; bg = Color(0xFFE2E8F0); fg = Color(0xFF334155); label = "ATM" }
        else -> {
            icon = null
            val p = avatarPalette[(t.merchant.hashCode() and 0x7fffffff) % avatarPalette.size]
            bg = p.first; fg = p.second
            label = t.merchant.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
        }
    }
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
        else Text(label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = if (label.length > 1) 12.sp else 18.sp)
    }
}

fun amountText(t: Txn): String = when (t.type) {
    TxType.INCOME, TxType.REFUND -> "+" + Money.inr(t.amount)
    TxType.TRANSFER, TxType.CREDIT_CARD_PAYMENT -> Money.inr(t.amount)
    TxType.UNKNOWN -> Money.inr(t.amount)
    else -> "−" + Money.inr(t.amount)
}

fun amountColor(t: Txn): Color = when (t.type) {
    TxType.INCOME, TxType.REFUND -> K.Green
    TxType.TRANSFER, TxType.CREDIT_CARD_PAYMENT -> K.TransferBlue
    else -> K.Ink
}

fun subLabel(t: Txn): Pair<String, Color> = when {
    t.isDuplicate -> "Duplicate" to K.Muted
    t.status == TxStatus.FAILED -> "Failed" to K.Muted
    t.status == TxStatus.PENDING -> "Pending" to K.AmberInk
    t.needsReview -> "Needs review" to K.AmberInk
    t.type == TxType.TRANSFER -> "Not counted" to K.TransferBlue
    t.type == TxType.CREDIT_CARD_PAYMENT -> "Not counted" to K.TransferBlue
    t.type == TxType.INCOME || t.type == TxType.REFUND -> t.category to K.Green
    t.type == TxType.ATM_WITHDRAWAL -> "Cash" to K.Slate
    else -> t.category to K.Orange
}

@Composable
fun TxnRow(t: Txn, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 18.dp, vertical = 5.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(t)
        Spacer(Modifier.width(12.dp))
        TxnTexts(t)
    }
}

@Composable
fun RowScope.TxnTexts(t: Txn) {
    Column(Modifier.weight(1f)) {
        Text(t.merchant, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = K.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val acct = listOfNotNull(t.mode.label, t.last4?.let { "••$it" }).joinToString(" ")
        Text("$acct · ${Fmt.dayTime(t.ts)}", fontSize = 12.5.sp, color = K.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.width(8.dp))
    Column(horizontalAlignment = Alignment.End) {
        val faded = t.isDuplicate || t.status == TxStatus.FAILED
        Text(amountText(t), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (faded) K.Muted else amountColor(t))
        val (label, color) = subLabel(t)
        Text(label, fontSize = 12.5.sp, color = color, maxLines = 1)
    }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, onDark: Boolean = false) {
    val shape = RoundedCornerShape(17.dp)
    val bg = when {
        selected && onDark -> Color.White
        selected -> K.Blue
        onDark -> Color.White.copy(alpha = 0.15f)
        else -> K.Chip
    }
    val fg = when {
        selected && onDark -> Color(0xFF1E3A8A)
        selected -> Color.White
        onDark -> Color.White
        else -> K.Slate
    }
    Box(
        Modifier
            .height(36.dp)
            .clip(shape)
            .background(bg)
            .then(if (onDark && !selected) Modifier.border(1.dp, Color.White.copy(alpha = 0.5f), shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
fun BottomPill(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val items = listOf(
        Icons.Outlined.Home to "Home",
        Icons.Outlined.ReceiptLong to "Transactions",
        Icons.Outlined.AccountBalanceWallet to "Accounts",
        Icons.Outlined.BarChart to "Analytics",
        Icons.Outlined.Settings to "Settings"
    )
    val shape = RoundedCornerShape(40.dp)
    Row(
        modifier
            .navigationBarsPadding()
            .padding(bottom = 14.dp)
            .shadow(18.dp, shape, ambientColor = Color(0xFF1E3C78), spotColor = Color(0xFF1E3C78))
            .clip(shape)
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), shape)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEachIndexed { i, (icon, label) ->
            val active = i == selected
            Box(
                Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(if (active) K.Blue else K.Chip)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, label, tint = if (active) Color.White else K.Slate)
            }
        }
    }
}

@Composable
fun Donut(slices: List<Pair<Color, Double>>, centerTop: String, centerValue: String, size: Dp = 150.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 20.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val total = slices.sumOf { it.second }
            if (total <= 0.0) {
                drawArc(K.Chip, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            } else {
                var start = -90f
                slices.forEach { (c, v) ->
                    val sweep = (v / total * 360.0).toFloat()
                    drawArc(c, start, sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    start += sweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTop, fontSize = 12.sp, color = K.Muted)
            Text(centerValue, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = K.Ink)
        }
    }
}

@Composable
fun ProgressBar(fraction: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(K.Line)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
    }
}

@Composable
fun LabelValue(label: String, value: String, valueColor: Color = K.Ink) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, fontSize = 14.sp, color = K.Muted, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, color = valueColor, fontWeight = FontWeight.Medium)
    }
}

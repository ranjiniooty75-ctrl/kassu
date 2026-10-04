package com.kaasu.tracker.util

import java.text.SimpleDateFormat
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object Money {
    /** Indian digit grouping: 1,23,150 */
    fun group(n: Long): String {
        val s = abs(n).toString()
        if (s.length <= 3) return (if (n < 0) "-" else "") + s
        val last3 = s.takeLast(3)
        var rest = s.dropLast(3)
        val parts = ArrayList<String>()
        while (rest.length > 2) {
            parts.add(0, rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        if (rest.isNotEmpty()) parts.add(0, rest)
        return (if (n < 0) "-" else "") + parts.joinToString(",") + "," + last3
    }

    fun inr(v: Double): String = "₹" + group(v.roundToLong())

    fun short(v: Double): String = when {
        abs(v) >= 1_00_00_000 -> "₹" + String.format(Locale.US, "%.1fCr", v / 1e7)
        abs(v) >= 1_00_000 -> "₹" + String.format(Locale.US, "%.1fL", v / 1e5)
        abs(v) >= 1000 -> "₹" + String.format(Locale.US, "%.0fK", v / 1e3)
        else -> inr(v)
    }
}

object Fmt {
    private val dayTime = SimpleDateFormat("dd MMM, h:mm a", Locale.ENGLISH)
    private val full = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.ENGLISH)
    private val dayHeader = SimpleDateFormat("dd MMM", Locale.ENGLISH)
    private val csvDate = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
    private val csvTime = SimpleDateFormat("HH:mm", Locale.ENGLISH)

    fun dayTime(ts: Long): String = dayTime.format(Date(ts))
    fun full(ts: Long): String = full.format(Date(ts))
    fun dayHeader(ts: Long): String = dayHeader.format(Date(ts)).uppercase()
    fun csvDate(ts: Long): String = csvDate.format(Date(ts))
    fun csvTime(ts: Long): String = csvTime.format(Date(ts))
    fun month(m: YearMonth): String = m.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + m.year
    fun monthShort(m: YearMonth): String = m.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
}

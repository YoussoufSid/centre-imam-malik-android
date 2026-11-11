package ml.centre.imammalik

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// =====================================================================
// Memorization & review follow-up: periods (day … academic year), per-student summaries
// and the suggested next position for fast entry. Pure functions (unit-tested).
// =====================================================================

enum class LogPeriod(val label: String) { DAY("يومي"), WEEK("أسبوعي"), MONTH("شهري"), TERM("فصلي"), YEAR("سنوي") }

data class PeriodRange(val from: String, val to: String, val label: String)

private fun fmt() = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private fun cal(date: String): Calendar = Calendar.getInstance().apply {
    time = runCatching { fmt().parse(date) }.getOrNull() ?: time
    set(Calendar.HOUR_OF_DAY, 12)
}
private fun Calendar.key() = fmt().format(time)
private fun dm(d: String) = if (d.length == 10) "${d.substring(8, 10)}/${d.substring(5, 7)}" else d

/** Academic terms: 1st Sep–Dec, 2nd Jan–Mar, 3rd Apr–Aug. Returns 1..3. */
fun termOf(month: Int): Int = when (month) { in 9..12 -> 1; in 1..3 -> 2; else -> 3 }
private val TermNames = listOf("الفصل الأول", "الفصل الثاني", "الفصل الثالث")

/** Date range and Arabic label of the period containing [anchor] (yyyy-MM-dd). Weeks run Monday–Sunday. */
fun periodRange(p: LogPeriod, anchor: String): PeriodRange {
    val c = cal(anchor)
    return when (p) {
        LogPeriod.DAY -> PeriodRange(c.key(), c.key(), ltr(c.key()))
        LogPeriod.WEEK -> {
            val back = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7   // Monday = 0
            c.add(Calendar.DAY_OF_MONTH, -back); val from = c.key()
            c.add(Calendar.DAY_OF_MONTH, 6); val to = c.key()
            PeriodRange(from, to, "أسبوع ${ltr(dm(from))} – ${ltr(dm(to))}")
        }
        LogPeriod.MONTH -> {
            val m = anchor.take(7)
            PeriodRange("$m-01", "$m-31", monthName(m))
        }
        LogPeriod.TERM -> {
            val y = c.get(Calendar.YEAR); val mo = c.get(Calendar.MONTH) + 1
            val start = if (mo >= 9) y else y - 1     // academic year start
            val (from, to) = when (termOf(mo)) {
                1 -> "$start-09-01" to "$start-12-31"
                2 -> "${start + 1}-01-01" to "${start + 1}-03-31"
                else -> "${start + 1}-04-01" to "${start + 1}-08-31"
            }
            PeriodRange(from, to, "${TermNames[termOf(mo) - 1]} ${ltr("$start / ${start + 1}")}")
        }
        LogPeriod.YEAR -> {
            val y = c.get(Calendar.YEAR); val mo = c.get(Calendar.MONTH) + 1
            val start = if (mo >= 9) y else y - 1
            PeriodRange("$start-09-01", "${start + 1}-08-31", "العام الدراسي ${ltr("$start / ${start + 1}")}")
        }
    }
}

/** Moves the anchor date one period forward (+1) or back (−1). */
fun shiftPeriod(p: LogPeriod, anchor: String, delta: Int): String {
    val c = cal(anchor)
    when (p) {
        LogPeriod.DAY -> c.add(Calendar.DAY_OF_MONTH, delta)
        LogPeriod.WEEK -> c.add(Calendar.DAY_OF_MONTH, 7 * delta)
        LogPeriod.MONTH -> { c.set(Calendar.DAY_OF_MONTH, 1); c.add(Calendar.MONTH, delta) }
        LogPeriod.TERM -> {   // jump to the first day of the next/previous term
            val r = periodRange(p, c.key())
            val edge = cal(if (delta > 0) r.to else r.from)
            edge.add(Calendar.DAY_OF_MONTH, if (delta > 0) 1 else -1)
            return edge.key()
        }
        LogPeriod.YEAR -> c.add(Calendar.YEAR, delta)
    }
    return c.key()
}

/** The date [n] days before [date]. */
fun daysBefore(date: String, n: Int): String = cal(date).apply { add(Calendar.DAY_OF_MONTH, -n) }.key()

/** Number of verses in a log (0 when the range is not given). */
fun ayatCount(l: QuranLog): Int = if (l.fromAyah > 0 && l.toAyah >= l.fromAyah) l.toAyah - l.fromAyah + 1 else 0

/** Grade → score 5..1 (ممتاز … يحتاج إعادة), null when not graded. */
fun gradeScore(g: String): Int? = Grades.indexOf(g).takeIf { it >= 0 }?.let { 5 - it }

/** Average score → nearest grade label. */
fun gradeFromScore(avg: Double): String = Grades[(5 - Math.round(avg).toInt()).coerceIn(0, Grades.size - 1)]

data class MemoRow(
    val student: Student,
    val newSessions: Int, val reviewSessions: Int,
    val newAyat: Int, val reviewAyat: Int,
    val surahs: List<String>,         // surahs memorized in the period (new), in order of first appearance
    val avgGrade: Double?,            // 1..5
    val days: Int,                    // distinct days with at least one record
    val lastNew: QuranLog?, val lastReview: QuranLog?
) { val sessions get() = newSessions + reviewSessions }

/** Per-student summary of [logs] (already limited to the period) for [students]. */
fun summarizeMemorization(students: List<Student>, logs: List<QuranLog>): List<MemoRow> {
    val by = logs.groupBy { it.studentId }
    return students.map { s ->
        val l = by[s.id].orEmpty().sortedWith(compareBy<QuranLog> { it.date }.thenBy { it.createdAt })
        val news = l.filter { it.type == LogTypes.NEW }; val revs = l.filter { it.type == LogTypes.REVIEW }
        val scores = l.mapNotNull { gradeScore(it.grade) }
        MemoRow(s, news.size, revs.size, news.sumOf(::ayatCount), revs.sumOf(::ayatCount),
            news.map { it.surah }.filter { it.isNotBlank() }.distinct(),
            scores.takeIf { it.isNotEmpty() }?.average(), l.map { it.date }.distinct().size,
            news.lastOrNull(), revs.lastOrNull())
    }
}

data class Position(val surah: String, val from: Int, val to: Int)

/**
 * Suggested next entry after [last] (same type): continue in the same surah after the last verse,
 * with the same number of verses; once a surah is finished, move to the previous surah in the mushaf
 * (memorization usually goes from An-Nas towards Al-Baqara). Null when there is no history.
 */
fun nextPosition(last: QuranLog?): Position? {
    if (last == null || last.surah.isBlank()) return null
    val idx = Surahs.indexOfFirst { it.first == last.surah }
    if (idx < 0) return Position(last.surah, 0, 0)
    val max = Surahs[idx].second
    val size = ayatCount(last).coerceAtLeast(1)
    if (last.toAyah in 1 until max) {
        val from = last.toAyah + 1
        return Position(last.surah, from, minOf(max, from + size - 1))
    }
    if (last.toAyah <= 0) return Position(last.surah, 0, 0)
    val prev = if (idx > 0) Surahs[idx - 1] else Surahs[idx]
    return Position(prev.first, 1, minOf(prev.second, size))
}

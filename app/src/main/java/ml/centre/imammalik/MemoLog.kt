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

/** Academic year start (September) of a date. */
fun academicStart(date: String): Int { val y = date.take(4).toInt(); val m = date.substring(5, 7).toInt(); return if (m >= 9) y else y - 1 }

/** A term of the academic calendar (dates yyyy-MM-dd, inclusive). */
data class Term(val name: String, val from: String, val to: String)

/** Calendar event: review period, exams, holiday or other, set by the administration. */
data class CalEvent(val id: String, val type: String, val title: String, val from: String, val to: String)

object EventTypes {
    const val REVIEW = "مراجعة"; const val EXAM = "اختبارات"; const val HOLIDAY = "عطلة"; const val OTHER = "مناسبة"
    val all = listOf(REVIEW, EXAM, HOLIDAY, OTHER)
    fun icon(t: String) = when (t) { REVIEW -> "🔁"; EXAM -> "📝"; HOLIDAY -> "🌴"; else -> "📌" }
}

/** Academic calendar of one year (start = September's year). */
data class AcademicCalendar(val start: Int, val terms: List<Term>, val events: List<CalEvent>)

val TermNames = listOf("الفصل الأول", "الفصل الثاني", "الفصل الثالث", "الفصل الرابع")

/** Four default terms (used until the administration sets the real dates). */
fun defaultTerms(start: Int): List<Term> = listOf(
    Term(TermNames[0], "$start-09-01", "$start-11-30"), Term(TermNames[1], "$start-12-01", "${start + 1}-02-28"),
    Term(TermNames[2], "${start + 1}-03-01", "${start + 1}-05-31"), Term(TermNames[3], "${start + 1}-06-01", "${start + 1}-08-31"))

/** The term containing [date]; between two terms (holidays) the previous one; before the first, the first. */
fun termIndexAt(terms: List<Term>, date: String): Int {
    val i = terms.indexOfFirst { date >= it.from && date <= it.to }
    if (i >= 0) return i
    return terms.indexOfLast { it.from <= date }.takeIf { it >= 0 } ?: 0
}

/** Events touching [date]. */
fun eventsOn(events: List<CalEvent>, date: String) = events.filter { date >= it.from && date <= it.to.ifBlank { it.from } }

/** Events that are ongoing or start within [days] days after [today], soonest first. */
fun upcomingEvents(events: List<CalEvent>, today: String, days: Int = 30): List<CalEvent> {
    val limit = cal(today).apply { add(Calendar.DAY_OF_MONTH, days) }.key()
    return events.filter { it.to.ifBlank { it.from } >= today && it.from <= limit }.sortedBy { it.from }
}

/** Date range and Arabic label of the period containing [anchor] (yyyy-MM-dd). Weeks run Monday–Sunday. */
fun periodRange(p: LogPeriod, anchor: String, termsFor: (Int) -> List<Term> = ::defaultTerms): PeriodRange {
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
            val start = academicStart(anchor); val terms = termsFor(start).ifEmpty { defaultTerms(start) }
            val t = terms[termIndexAt(terms, anchor)]
            PeriodRange(t.from, t.to, "${t.name} ${ltr("$start / ${start + 1}")}")
        }
        LogPeriod.YEAR -> {
            val y = c.get(Calendar.YEAR); val mo = c.get(Calendar.MONTH) + 1
            val start = if (mo >= 9) y else y - 1
            PeriodRange("$start-09-01", "${start + 1}-08-31", "العام الدراسي ${ltr("$start / ${start + 1}")}")
        }
    }
}

/** Moves the anchor date one period forward (+1) or back (−1). */
fun shiftPeriod(p: LogPeriod, anchor: String, delta: Int, termsFor: (Int) -> List<Term> = ::defaultTerms): String {
    val c = cal(anchor)
    when (p) {
        LogPeriod.DAY -> c.add(Calendar.DAY_OF_MONTH, delta)
        LogPeriod.WEEK -> c.add(Calendar.DAY_OF_MONTH, 7 * delta)
        LogPeriod.MONTH -> { c.set(Calendar.DAY_OF_MONTH, 1); c.add(Calendar.MONTH, delta) }
        LogPeriod.TERM -> {   // first day of the next / previous term (crossing into the next or previous year)
            val start = academicStart(anchor); val terms = termsFor(start).ifEmpty { defaultTerms(start) }
            val k = termIndexAt(terms, anchor) + delta
            return when {
                k < 0 -> termsFor(start - 1).ifEmpty { defaultTerms(start - 1) }.last().from
                k >= terms.size -> termsFor(start + 1).ifEmpty { defaultTerms(start + 1) }.first().from
                else -> terms[k].from
            }
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

/** Summary of one halaqa for the all-halaqat follow-up register. */
data class HalaqaMemo(val group: String, val students: Int, val recorded: Int, val newAyat: Int, val reviewAyat: Int,
                      val sessions: Int, val avgGrade: Double?)

/** Groups student rows by halaqa (students without a halaqa under «بدون حلقة»), halaqas in name order. */
fun summarizeByHalaqa(rows: List<MemoRow>, logs: List<QuranLog>): List<HalaqaMemo> =
    rows.groupBy { it.student.group.ifBlank { "بدون حلقة" } }.toSortedMap().map { (g, rs) ->
        val ids = rs.map { it.student.id }.toSet()
        val scores = logs.filter { it.studentId in ids }.mapNotNull { gradeScore(it.grade) }
        HalaqaMemo(g, rs.size, rs.count { it.sessions > 0 }, rs.sumOf { it.newAyat }, rs.sumOf { it.reviewAyat },
            rs.sumOf { it.sessions }, scores.takeIf { it.isNotEmpty() }?.average())
    }

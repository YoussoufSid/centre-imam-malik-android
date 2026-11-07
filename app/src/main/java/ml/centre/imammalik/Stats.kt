package ml.centre.imammalik

/** Pure analytics (no Android / Firebase), so it can be unit-tested. */

data class GroupStat(val group: String, val students: Int, val attendanceRate: Int?, val avgHizbs: Double)

data class AtRisk(val student: Student, val reason: String)

data class MonthStats(
    val activeStudents: Int,
    val newEnrollments: Int,
    val attendanceRate: Int?,
    val recordedDays: Int,
    val newSessions: Int,
    val reviewSessions: Int,
    val avgHizbs: Double,
    val khatmCount: Int,
    val groups: List<GroupStat>,
    /** date (yyyy-MM-dd) to number of present students that day. */
    val daily: List<Pair<String, Int>>,
    val hizbBuckets: List<Pair<String, Int>>,
    val topReciters: List<Pair<Student, Int>>,
    val atRisk: List<AtRisk>
)

private fun rate(records: List<AttendanceRecord>): Int? =
    if (records.isEmpty()) null else records.count { AttendanceStatus.isPresent(it.status) } * 100 / records.size

/** Number of consecutive absences at the end of the student's records (most recent first). */
fun absenceStreak(records: List<AttendanceRecord>): Int =
    records.sortedByDescending { it.date }.takeWhile { it.status == AttendanceStatus.ABSENT }.size

val HizbBucketLabels = listOf("أقل من 5", "5–14", "15–29", "30–59", "خاتم (60)")

fun hizbBucket(h: Int): Int = when {
    h >= TOTAL_HIZBS -> 4
    h >= 30 -> 3
    h >= 15 -> 2
    h >= 5 -> 1
    else -> 0
}

fun computeMonthStats(
    students: List<Student>, month: String, attendance: List<AttendanceRecord>, logs: List<QuranLog>
): MonthStats {
    val active = students.filter { it.status == "نشط" }
    val att = attendance.filter { it.date.startsWith(month) }
    val ls = logs.filter { it.date.startsWith(month) }
    val byStudent = att.groupBy { it.studentId }

    val groups = active.groupBy { it.group.ifBlank { "بدون حلقة" } }.map { (g, list) ->
        val ids = list.map { it.id }.toSet()
        GroupStat(g, list.size, rate(att.filter { it.studentId in ids }), list.sumOf { it.hizbs }.toDouble() / list.size)
    }.sortedBy { it.group }

    val daily = att.groupBy { it.date }.map { (d, r) -> d to r.count { AttendanceStatus.isPresent(it.status) } }.sortedBy { it.first }

    val buckets = IntArray(HizbBucketLabels.size).also { b -> active.forEach { b[hizbBucket(it.hizbs)]++ } }

    val logCount = ls.groupingBy { it.studentId }.eachCount()
    val top = active.mapNotNull { s -> logCount[s.id]?.let { s to it } }.sortedByDescending { it.second }.take(5)

    val atRisk = active.mapNotNull { s ->
        val r = byStudent[s.id].orEmpty()
        val streak = absenceStreak(r)
        val pr = rate(r)
        when {
            streak >= 3 -> AtRisk(s, "غائب آخر $streak حصص متتالية")
            pr != null && r.size >= 4 && pr < 60 -> AtRisk(s, "نسبة الحضور $pr٪ فقط")
            r.isEmpty() && att.isNotEmpty() -> AtRisk(s, "لم يُسجَّل له أي حضور هذا الشهر")
            else -> null
        }
    }

    return MonthStats(
        activeStudents = active.size,
        newEnrollments = students.count { it.enrollmentDate.startsWith(month) },
        attendanceRate = rate(att.filter { a -> active.any { it.id == a.studentId } }),
        recordedDays = att.map { it.date }.distinct().size,
        newSessions = ls.count { it.type == LogTypes.NEW },
        reviewSessions = ls.count { it.type == LogTypes.REVIEW },
        avgHizbs = if (active.isEmpty()) 0.0 else active.sumOf { it.hizbs }.toDouble() / active.size,
        khatmCount = active.count { it.hizbs >= TOTAL_HIZBS },
        groups = groups,
        daily = daily,
        hizbBuckets = HizbBucketLabels.mapIndexed { i, l -> l to buckets[i] },
        topReciters = top,
        atRisk = atRisk
    )
}

package ml.centre.imammalik

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {

    // ---------- Dates ----------

    @Test fun shiftDay_crossesMonthAndYear() {
        assertEquals("2026-12-01", shiftDay("2026-11-30", 1))
        assertEquals("2027-01-01", shiftDay("2026-12-31", 1))
        assertEquals("2024-02-29", shiftDay("2024-03-01", -1))
    }

    @Test fun shiftMonth_crossesYear() {
        assertEquals("2027-01", shiftMonth("2026-12", 1))
        assertEquals("2025-12", shiftMonth("2026-01", -1))
    }

    @Test fun monthName_isArabic() {
        assertEquals("نوفمبر 2026", monthName("2026-11"))
        assertEquals("يناير 2027", monthName("2027-01"))
    }

    @Test fun ltr_wrapsWithIsolates() {
        assertEquals("⁦2026-11-07⁩", ltr("2026-11-07"))
    }

    // ---------- Money & grades ----------

    @Test fun money_groupsThousands() {
        assertEquals("5 000 فرنك", money(5000))
        assertEquals("1 250 000 فرنك", money(1250000))
        assertEquals("0 فرنك", money(0))
    }

    @Test fun centre_contactLineAndCurrency() {
        val saved = Triple(Centre.address, Centre.phone, Centre.currency)
        try {
            Centre.address = "باماكو"; Centre.phone = "76123456"; Centre.currency = "FCFA"
            assertEquals("باماكو • هاتف: 76123456", Centre.contactLine())
            assertEquals("2 500 FCFA", money(2500))
            Centre.address = ""; Centre.phone = ""
            assertEquals("", Centre.contactLine())
        } finally {
            Centre.address = saved.first; Centre.phone = saved.second; Centre.currency = saved.third
        }
    }

    @Test fun scoreText_dropsTrailingZero() {
        assertEquals("15", scoreText(15.0))
        assertEquals("12.5", scoreText(12.5))
    }

    @Test fun mention_thresholds() {
        assertEquals("ممتاز", mention(16.0))
        assertEquals("جيد جدًا", mention(14.0))
        assertEquals("جيد", mention(12.0))
        assertEquals("مقبول", mention(10.0))
        assertEquals("ضعيف", mention(9.99))
    }

    @Test fun gradesReportLines_onlyThatMonth() {
        val g = listOf(
            Grade(subject = "الفقه", score = 16.0, date = "2026-11-02"),
            Grade(subject = "الفقه", score = 12.0, date = "2026-11-20"),
            Grade(subject = "العقيدة", score = 18.0, date = "2026-10-01")
        )
        val lines = gradesReportLines(g, "2026-11")
        assertTrue(lines.contains("الفقه: 14/20"))
        assertFalse(lines.any { it.startsWith("العقيدة") })
        assertTrue(gradesReportLines(g, "2026-09").isEmpty())
    }

    // ---------- WhatsApp numbers ----------

    @Test fun whatsappNumber_addsMaliCodeForLocalNumbers() {
        assertEquals("22376123456", whatsappNumber("76 12 34 56"))
        assertEquals("22376123456", whatsappNumber("+223 76 12 34 56"))
        assertEquals("22376123456", whatsappNumber("0022376123456"))
        assertNull(whatsappNumber(""))
        assertNull(whatsappNumber("123"))
    }

    // ---------- Roles & audience ----------

    @Test fun audience_visibility() {
        val admin = AppUser(role = Roles.ADMIN)
        val teacher = AppUser(role = Roles.TEACHER)
        val parent = AppUser(role = Roles.PARENT)
        assertTrue(Audience.visibleTo(Audience.TEACHERS, admin))
        assertTrue(Audience.visibleTo(Audience.TEACHERS, teacher))
        assertFalse(Audience.visibleTo(Audience.TEACHERS, parent))
        assertTrue(Audience.visibleTo(Audience.PARENTS, parent))
        assertFalse(Audience.visibleTo(Audience.PARENTS, teacher))
        assertTrue(Audience.visibleTo(Audience.ALL, parent))
    }

    @Test fun attendance_presentIncludesLate() {
        assertTrue(AttendanceStatus.isPresent(AttendanceStatus.PRESENT))
        assertTrue(AttendanceStatus.isPresent(AttendanceStatus.LATE))
        assertFalse(AttendanceStatus.isPresent(AttendanceStatus.ABSENT))
        assertFalse(AttendanceStatus.isPresent(AttendanceStatus.EXCUSED))
    }

    // ---------- Monthly report ----------

    @Test fun monthlyReport_countsOnlyTheMonth() {
        val s = Student(id = "s1", name = "أحمد", group = "الحلقة أ", hizbs = 10)
        val att = listOf(
            AttendanceRecord(studentId = "s1", date = "2026-11-01", status = AttendanceStatus.PRESENT),
            AttendanceRecord(studentId = "s1", date = "2026-11-02", status = AttendanceStatus.LATE),
            AttendanceRecord(studentId = "s1", date = "2026-11-03", status = AttendanceStatus.ABSENT),
            AttendanceRecord(studentId = "s1", date = "2026-11-04", status = AttendanceStatus.PRESENT),
            AttendanceRecord(studentId = "s1", date = "2026-10-30", status = AttendanceStatus.ABSENT)
        )
        val logs = listOf(
            QuranLog(studentId = "s1", date = "2026-11-02", type = LogTypes.NEW, surah = "الملك", grade = "ممتاز"),
            QuranLog(studentId = "s1", date = "2026-11-05", type = LogTypes.REVIEW, surah = "النبأ", grade = "ممتاز")
        )
        val r = buildMonthlyReport(s, "2026-11", att, logs)
        assertTrue(r, r.contains("الطالب: أحمد"))
        assertTrue(r, r.contains("حاضر: 2 • متأخر: 1 • غائب: 1 • معذور: 0"))
        assertTrue(r, r.contains("نسبة الحضور: 75٪"))
        assertTrue(r, r.contains("جلسات الحفظ الجديد: 1 • جلسات المراجعة: 1"))
        assertTrue(r, r.contains("السور التي حفظ منها: الملك"))
        assertTrue(r, r.contains("10 من 60 حزبًا"))
    }

    @Test fun hizbLabel_completion() {
        assertEquals("خاتم للقرآن الكريم", hizbLabel(60))
        assertEquals("1 من 60 حزبًا", hizbLabel(1))
        assertEquals("3 من 60 حزبًا (≈ 1 جزءًا)", hizbLabel(3))
        assertEquals("20 من 60 حزبًا (≈ 10 جزءًا)", hizbLabel(20))
    }

    // ---------- Bulk import ----------

    @Test fun parseStudentLines_handlesPasteExcelAndCsv() {
        val text = """
            الاسم	الهاتف
            1- أحمد  محمد سيسي ; 76 12 34 56
            ٢. فاطمة علي
            3	عمر تراوري	66554433
            "مريم كوليبالي",70112233

            76543210
            أحمد محمد سيسي
        """.trimIndent()
        val r = parseStudentLines(text)
        assertEquals(listOf("أحمد محمد سيسي", "فاطمة علي", "عمر تراوري", "مريم كوليبالي"), r.map { it.name })
        assertEquals("76 12 34 56", r[0].phone)
        assertEquals("", r[1].phone)
        assertEquals("66554433", r[2].phone)
        assertEquals("70112233", r[3].phone)
    }

    @Test fun parseStudentLines_ignoresBomAndBlank() {
        assertEquals(listOf("علي"), parseStudentLines("\uFEFFعلي\n\n   \n").map { it.name })
        assertTrue(parseStudentLines("").isEmpty())
    }

    // ---------- Payroll & petty cash ----------

    @Test fun academicYear_runsSeptemberToAugust() {
        val nov = academicYearMonths("2026-11")
        assertEquals("2026-09", nov.first()); assertEquals("2027-08", nov.last()); assertEquals(12, nov.size)
        assertEquals(nov, academicYearMonths("2027-03"))
        assertEquals("2027-09", academicYearMonths("2027-09").first())
        assertEquals("2026 / 2027", academicYearLabel("2027-01"))
    }

    @Test fun payroll_netAndDeposit() {
        val sep = PayrollEntry(month = "2026-09", base = 100000, advance = 20000, received = 50000) // net 80k, leaves 30k
        val oct = PayrollEntry(month = "2026-10", base = 100000, advance = 0, received = 120000)   // withdraws 20k
        assertEquals(80000, sep.net); assertEquals(30000, sep.depositChange)
        assertEquals(-20000, oct.depositChange)
        assertEquals(10000, depositBalance(listOf(sep, oct)))
        assertEquals(0, depositBalance(emptyList()))
    }

    @Test fun advance_remainingOrDeficit() {
        val a = CashAdvance(amount = 50000)
        assertEquals(12500, advanceBalance(a, 37500))   // reste
        assertEquals(-5000, advanceBalance(a, 55000))   // déficit
    }

    // ---------- Backup ----------

    @Test fun backupCodec_roundTripKeepsDatesListsAndMaps() {
        val date = java.util.Date(1_762_000_000_000L)
        val original = mapOf(
            "name" to "أحمد", "hizbs" to 12, "score" to 15.5, "active" to true, "none" to null,
            "createdAt" to date, "studentIds" to listOf("a", "b"), "nested" to mapOf("x" to 1)
        )
        val json = org.json.JSONObject(BackupCodec.encode(original).toString())
        @Suppress("UNCHECKED_CAST")
        val back = BackupCodec.decode(json) as Map<String, Any?>
        assertEquals("أحمد", back["name"])
        assertEquals(12, (back["hizbs"] as Number).toInt())
        assertEquals(15.5, (back["score"] as Number).toDouble(), 0.0001)
        assertEquals(true, back["active"])
        assertNull(back["none"])
        assertEquals(date, back["createdAt"])
        assertEquals(listOf("a", "b"), back["studentIds"])
        assertEquals(1, ((back["nested"] as Map<*, *>)["x"] as Number).toInt())
    }

    // ---------- Analytics ----------

    @Test fun absenceStreak_countsLatestConsecutive() {
        val r = listOf(
            AttendanceRecord(date = "2026-11-01", status = AttendanceStatus.PRESENT),
            AttendanceRecord(date = "2026-11-02", status = AttendanceStatus.ABSENT),
            AttendanceRecord(date = "2026-11-03", status = AttendanceStatus.ABSENT),
            AttendanceRecord(date = "2026-11-04", status = AttendanceStatus.ABSENT)
        )
        assertEquals(3, absenceStreak(r))
        assertEquals(0, absenceStreak(r + AttendanceRecord(date = "2026-11-05", status = AttendanceStatus.PRESENT)))
    }

    @Test fun hizbBucket_boundaries() {
        assertEquals(0, hizbBucket(4)); assertEquals(1, hizbBucket(5)); assertEquals(2, hizbBucket(15))
        assertEquals(3, hizbBucket(59)); assertEquals(4, hizbBucket(60))
    }

    @Test fun monthStats_groupsAndAtRisk() {
        val a = Student(id = "a", name = "علي", group = "أ", hizbs = 60, enrollmentDate = "2026-11-03")
        val b = Student(id = "b", name = "بكر", group = "أ", hizbs = 10)
        val c = Student(id = "c", name = "خالد", group = "ب", hizbs = 2)
        val stopped = Student(id = "d", name = "متوقف", group = "ب", status = "متوقف")
        fun rec(id: String, day: Int, st: String) = AttendanceRecord(studentId = id, date = "2026-11-%02d".format(day), status = st)
        val att = listOf(
            rec("a", 1, AttendanceStatus.PRESENT), rec("a", 2, AttendanceStatus.PRESENT),
            rec("b", 1, AttendanceStatus.ABSENT), rec("b", 2, AttendanceStatus.ABSENT), rec("b", 3, AttendanceStatus.ABSENT)
        )
        val logs = listOf(QuranLog(studentId = "a", date = "2026-11-02", type = LogTypes.NEW))
        val s = computeMonthStats(listOf(a, b, c, stopped), "2026-11", att, logs)

        assertEquals(3, s.activeStudents)
        assertEquals(1, s.newEnrollments)
        assertEquals(40, s.attendanceRate)          // 2 present of 5 records
        assertEquals(1, s.khatmCount)
        assertEquals(1, s.newSessions)
        assertEquals(listOf("أ", "ب"), s.groups.map { it.group })
        assertEquals(40, s.groups.first { it.group == "أ" }.attendanceRate)
        assertNull(s.groups.first { it.group == "ب" }.attendanceRate)
        assertEquals(listOf("2026-11-01" to 1, "2026-11-02" to 1, "2026-11-03" to 0), s.daily)
        // b: 3 absences in a row; c: no record at all this month
        assertEquals(setOf("b", "c"), s.atRisk.map { it.student.id }.toSet())
        assertEquals(a, s.topReciters.single().first)
    }
}

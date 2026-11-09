package ml.centre.imammalik

import java.text.SimpleDateFormat
import java.util.*

const val ADMIN_EMAIL = "sidibey487@gmail.com"

const val DEFAULT_CENTRE_NAME = "مركز الإمام مالك لتحفيظ القرآن الكريم والدراسات الإسلامية"

/** Centre identity, editable by the admin (settings/centre) and used in documents and messages. */
object Centre {
    var name = DEFAULT_CENTRE_NAME
    var director = ""
    var phone = ""
    var address = ""
    var academicYear = ""
    var currency = "فرنك"

    /** "Centre name • address • phone" footer for documents. */
    fun contactLine(): String = listOf(address, phone.takeIf { it.isNotBlank() }?.let { "هاتف: $it" } ?: "")
        .filter { it.isNotBlank() }.joinToString(" • ")
}

object Roles {
    const val ADMIN = "admin"
    const val TEACHER = "teacher"
    const val PARENT = "parent"
    const val STUDENT = "student"

    val all = listOf(ADMIN, TEACHER, PARENT, STUDENT)

    fun label(role: String) = when (role) {
        ADMIN -> "الإدارة"
        TEACHER -> "معلم"
        PARENT -> "ولي أمر"
        else -> "طالب"
    }
}

data class AppUser(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = Roles.STUDENT,
    val studentIds: List<String> = emptyList(),
    /** For teachers: the groups (halaqat) they teach. Empty = sees all students. */
    val groups: List<String> = emptyList()
) {
    val isAdmin get() = role == Roles.ADMIN
    val isStaff get() = role == Roles.ADMIN || role == Roles.TEACHER
}

data class Student(
    val id: String = "",
    val name: String = "",
    val gender: String = "",
    val birthDate: String = "",
    val level: String = "",
    val group: String = "",
    val teacher: String = "",
    val phone: String = "",
    val address: String = "",
    val enrollmentDate: String = "",
    val status: String = "نشط",
    /** Number of hizbs memorized (0..60). */
    val hizbs: Int = 0,
    /** Monthly fee in FCFA (0 = exempt / not set). */
    val monthlyFee: Int = 0,
    /** One-time registration fee in FCFA (0 = none). */
    val registrationFee: Int = 0
)

// ---------- Teachers ----------

/** A staff member: teacher, supervisor or worker. */
data class Teacher(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    /** Base monthly salary. */
    val salary: Int = 0,
    val hireDate: String = "",
    val status: String = "نشط",
    val notes: String = "",
    val category: String = StaffCategory.TEACHER,
    /** Job title, e.g. "مدرس الحلقة ونائب المدير". */
    val function: String = "",
    val cessationDate: String = "",
    val paymentMode: String = ""
)

object StaffCategory {
    const val TEACHER = "مدرس"
    const val SUPERVISOR = "مراقب"
    const val WORKER = "عامل"
    val all = listOf(TEACHER, SUPERVISOR, WORKER)
    fun plural(c: String) = when (c) { SUPERVISOR -> "المراقبون"; WORKER -> "العمال"; else -> "المدرسون" }
    fun french(c: String) = when (c) { SUPERVISOR -> "Surveillants"; WORKER -> "Ouvriers"; else -> "Enseignants" }
}

val PaymentModes = listOf("نقدًا", "Orange Money", "Moov Money", "تحويل بنكي")

/**
 * One month of salary for one staff member.
 * net = base − advance; the part of net not taken is left in deposit with the administration
 * (received > net means the employee withdrew from an earlier deposit).
 */
data class PayrollEntry(
    val id: String = "",
    val staffId: String = "",
    val name: String = "",
    /** yyyy-MM */
    val month: String = "",
    val base: Int = 0,
    val advance: Int = 0,
    val received: Int = 0,
    val receivedDate: String = "",
    val paymentMode: String = ""
) {
    val net get() = base - advance
    /** + left in deposit / − withdrawn from deposit. */
    val depositChange get() = net - received
}

/** Academic year months, September to August, containing [month] (yyyy-MM). */
fun academicYearMonths(month: String): List<String> {
    val y = month.take(4).toIntOrNull() ?: 2026
    val m = month.drop(5).toIntOrNull() ?: 9
    val start = if (m >= 9) y else y - 1
    return (9..12).map { "%d-%02d".format(Locale.US, start, it) } + (1..8).map { "%d-%02d".format(Locale.US, start + 1, it) }
}

fun academicYearLabel(month: String): String = academicYearMonths(month).let { "${it.first().take(4)} / ${it.last().take(4)}" }

/** Deposit balance held by the administration = Σ net − Σ received. */
fun depositBalance(entries: List<PayrollEntry>): Int = entries.sumOf { it.depositChange }

const val SALARY_CATEGORY = "رواتب المعلمين"

data class AuditEntry(val action: String = "", val details: String = "", val by: String = "", val at: Long = 0)

// ---------- Halaqat (groups) ----------

data class Halaqa(
    val id: String = "",
    val name: String = "",
    val teacher: String = "",
    /** Free text, e.g. "السبت–الخميس، 16:00–18:00". */
    val schedule: String = "",
    val notes: String = ""
)

/** A student line parsed from pasted text or a CSV file. */
data class ParsedStudent(val name: String, val phone: String = "")

private val HeaderWords = setOf("الاسم", "اسم الطالب", "الطالب", "الاسم الكامل", "name", "nom", "nom complet", "prénom et nom")

/**
 * Parses one student per line. Columns may be separated by tab (Excel copy), ';' or ',':
 * first column = name, second (optional) = phone. Header rows, numbering and blank lines are ignored.
 */
fun parseStudentLines(text: String): List<ParsedStudent> = text.removePrefix("\uFEFF").lines().mapNotNull { raw ->
    // Drop a leading numbering column (Excel "N°" column) such as "1" or "١٢".
    val cols = raw.split('\t', ';', ',').map { it.trim().trim('"').trim() }
        .dropWhile { it.isNotEmpty() && it.length <= 4 && it.all { c -> c.isDigit() } }
    val name = cols.firstOrNull().orEmpty()
        .replace(Regex("^\\s*[0-9٠-٩]+\\s*[-.)–]\\s*"), "")   // "1- أحمد" / "٢. علي"
        .replace(Regex("\\s+"), " ").trim()
    if (name.isBlank() || cols.any { it.lowercase() in HeaderWords }) return@mapNotNull null  // header row
    if (name.all { it.isDigit() || it == '+' || it == ' ' }) return@mapNotNull null
    val phone = cols.drop(1).firstOrNull { c -> c.count { it.isDigit() } >= 6 }.orEmpty()
    ParsedStudent(name, phone)
}.distinctBy { it.name }

object Audience {
    const val ALL = "all"
    const val PARENTS = "parents"
    const val TEACHERS = "teachers"
    val all = listOf(ALL, PARENTS, TEACHERS)
    fun label(a: String) = when (a) { PARENTS -> "أولياء الأمور"; TEACHERS -> "المعلمون"; else -> "الجميع" }
    fun visibleTo(a: String, user: AppUser) = user.isAdmin || a == ALL ||
        (a == TEACHERS && user.isStaff) || (a == PARENTS && !user.isStaff)
}

data class Announcement(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val audience: String = Audience.ALL,
    val author: String = "",
    val createdAt: Long = 0
)

data class Payment(
    val id: String = "",
    val studentId: String = "",
    val studentName: String = "",
    /** yyyy-MM */
    val month: String = "",
    val amount: Int = 0,
    val paidOn: String = "",
    val recordedBy: String = "",
    /** "monthly" or "registration". */
    val type: String = PaymentType.MONTHLY
)

object PaymentType {
    const val MONTHLY = "monthly"
    const val REGISTRATION = "registration"
}

val ExpenseCategories = listOf("رواتب المعلمين", "الإيجار", "الكهرباء والماء", "المصاحف والكتب",
    "الأدوات واللوازم", "الصيانة", "النقل", "مصروفات يومية", "أخرى")

data class Expense(
    val id: String = "",
    val category: String = "",
    val amount: Int = 0,
    val date: String = "",
    /** yyyy-MM */
    val month: String = "",
    val note: String = "",
    val recordedBy: String = "",
    /** Set when the expense is a line of a petty-cash register (عهدة). */
    val registerId: String = "",
    val observation: String = ""
)

const val DAILY_EXPENSES_CATEGORY = "مصروفات يومية"

/** Petty-cash advance (عهدة) handed by the administration and justified by daily expense lines. */
data class CashAdvance(
    val id: String = "",
    /** yyyy-MM */
    val month: String = "",
    val week: String = "",
    val receivedDate: String = "",
    val amount: Int = 0,
    val remisPar: String = "",
    val recuPar: String = "",
    val settlementAmount: Int = 0,
    val settlementDate: String = "",
    val closed: Boolean = false
)

/** Positive = remaining with the beneficiary, negative = deficit owed to them. */
fun advanceBalance(a: CashAdvance, spent: Int): Int = a.amount - spent

// ---------- Islamic studies ----------

val Subjects = listOf("القرآن والتجويد", "الفقه", "العقيدة", "الحديث", "السيرة النبوية", "اللغة العربية", "الأخلاق والآداب")
val Terms = listOf("الفصل الأول", "الفصل الثاني", "الفصل الثالث")
const val MAX_SCORE = 20

data class Grade(
    val id: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val subject: String = "",
    val term: String = "",
    val score: Double = 0.0,
    val date: String = "",
    val notes: String = "",
    val teacherName: String = ""
)

fun scoreText(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(Locale.US, v)

fun mention(avg: Double): String = when {
    avg >= 16 -> "ممتاز"
    avg >= 14 -> "جيد جدًا"
    avg >= 12 -> "جيد"
    avg >= 10 -> "مقبول"
    else -> "ضعيف"
}

/** Today's date in the Hijri (Umm al-Qura) calendar, e.g. "16 جمادى الأولى 1448 هـ". */
fun hijriToday(): String = runCatching {
    val locale = android.icu.util.ULocale("ar@calendar=islamic-umalqura;numbers=latn")
    val cal = android.icu.util.Calendar.getInstance(locale)
    val fmt = android.icu.text.DateFormat.getDateInstance(android.icu.text.DateFormat.LONG, locale)
    fmt.calendar = cal
    fmt.format(Date())
}.getOrDefault("")

fun currentMonth(): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

fun shiftMonth(month: String, delta: Int): String {
    val f = SimpleDateFormat("yyyy-MM", Locale.US)
    val cal = Calendar.getInstance()
    runCatching { f.parse(month) }.getOrNull()?.let { cal.time = it }
    cal.add(Calendar.MONTH, delta)
    return f.format(cal.time)
}

private val ArabicMonthNames = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
    "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")

fun monthName(key: String): String {
    val parts = key.split("-")
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return key
    return "${ArabicMonthNames.getOrElse(m - 1) { key }} ${parts[0]}"
}

fun money(amount: Int): String = "%,d".format(Locale.US, amount).replace(',', ' ') + " " + Centre.currency

const val TOTAL_HIZBS = 60

object AttendanceStatus {
    const val PRESENT = "حاضر"
    const val ABSENT = "غائب"
    const val LATE = "متأخر"
    const val EXCUSED = "معذور"
    val all = listOf(PRESENT, LATE, ABSENT, EXCUSED)
    fun isPresent(status: String) = status == PRESENT || status == LATE
}

data class AttendanceRecord(
    val id: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val date: String = "",
    val status: String = AttendanceStatus.PRESENT
)

object LogTypes {
    const val NEW = "حفظ جديد"
    const val REVIEW = "مراجعة"
    val all = listOf(NEW, REVIEW)
}

val Grades = listOf("ممتاز", "جيد جدًا", "جيد", "مقبول", "يحتاج إعادة")

data class QuranLog(
    val id: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val date: String = "",
    val type: String = LogTypes.NEW,
    val surah: String = "",
    val fromAyah: Int = 0,
    val toAyah: Int = 0,
    val grade: String = "",
    val notes: String = "",
    val teacherName: String = "",
    val createdAt: Long = 0
) {
    val range get() = if (fromAyah > 0 && toAyah > 0) "الآيات $fromAyah–$toAyah" else ""
}

private val dayFormat get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

/** Keeps dates like 2026-11-07 in their natural order inside right-to-left Arabic text. */
fun ltr(text: String): String = "\u2066$text\u2069"

fun today(): String = dayFormat.format(Date())

fun shiftDay(day: String, delta: Int): String {
    val cal = Calendar.getInstance()
    runCatching { dayFormat.parse(day) }.getOrNull()?.let { cal.time = it }
    cal.add(Calendar.DAY_OF_MONTH, delta)
    return dayFormat.format(cal.time)
}

/** Surah names with their verse counts (Hafs/Kufi count, shown as a hint only). */
val Surahs: List<Pair<String, Int>> = listOf(
    "الفاتحة" to 7, "البقرة" to 286, "آل عمران" to 200, "النساء" to 176, "المائدة" to 120,
    "الأنعام" to 165, "الأعراف" to 206, "الأنفال" to 75, "التوبة" to 129, "يونس" to 109,
    "هود" to 123, "يوسف" to 111, "الرعد" to 43, "إبراهيم" to 52, "الحجر" to 99,
    "النحل" to 128, "الإسراء" to 111, "الكهف" to 110, "مريم" to 98, "طه" to 135,
    "الأنبياء" to 112, "الحج" to 78, "المؤمنون" to 118, "النور" to 64, "الفرقان" to 77,
    "الشعراء" to 227, "النمل" to 93, "القصص" to 88, "العنكبوت" to 69, "الروم" to 60,
    "لقمان" to 34, "السجدة" to 30, "الأحزاب" to 73, "سبأ" to 54, "فاطر" to 45,
    "يس" to 83, "الصافات" to 182, "ص" to 88, "الزمر" to 75, "غافر" to 85,
    "فصلت" to 54, "الشورى" to 53, "الزخرف" to 89, "الدخان" to 59, "الجاثية" to 37,
    "الأحقاف" to 35, "محمد" to 38, "الفتح" to 29, "الحجرات" to 18, "ق" to 45,
    "الذاريات" to 60, "الطور" to 49, "النجم" to 62, "القمر" to 55, "الرحمن" to 78,
    "الواقعة" to 96, "الحديد" to 29, "المجادلة" to 22, "الحشر" to 24, "الممتحنة" to 13,
    "الصف" to 14, "الجمعة" to 11, "المنافقون" to 11, "التغابن" to 18, "الطلاق" to 12,
    "التحريم" to 12, "الملك" to 30, "القلم" to 52, "الحاقة" to 52, "المعارج" to 44,
    "نوح" to 28, "الجن" to 28, "المزمل" to 20, "المدثر" to 56, "القيامة" to 40,
    "الإنسان" to 31, "المرسلات" to 50, "النبأ" to 40, "النازعات" to 46, "عبس" to 42,
    "التكوير" to 29, "الانفطار" to 19, "المطففين" to 36, "الانشقاق" to 25, "البروج" to 22,
    "الطارق" to 17, "الأعلى" to 19, "الغاشية" to 26, "الفجر" to 30, "البلد" to 20,
    "الشمس" to 15, "الليل" to 21, "الضحى" to 11, "الشرح" to 8, "التين" to 8,
    "العلق" to 19, "القدر" to 5, "البينة" to 8, "الزلزلة" to 8, "العاديات" to 11,
    "القارعة" to 11, "التكاثر" to 8, "العصر" to 3, "الهمزة" to 9, "الفيل" to 5,
    "قريش" to 4, "الماعون" to 7, "الكوثر" to 3, "الكافرون" to 6, "النصر" to 3,
    "المسد" to 5, "الإخلاص" to 4, "الفلق" to 5, "الناس" to 6
)

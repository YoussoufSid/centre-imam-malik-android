package ml.centre.imammalik

import java.text.SimpleDateFormat
import java.util.*

const val ADMIN_EMAIL = "sidibey487@gmail.com"

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
    val studentIds: List<String> = emptyList()
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
    val hizbs: Int = 0
)

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

package ml.centre.imammalik

import java.util.Locale

// =====================================================================
// Fee arrears and parent reminders. A month's fee becomes overdue from the due day (default the 25th)
// of that month. Pure functions (unit-tested); the web dashboard uses the same rules.
// =====================================================================

const val DEFAULT_FEE_DUE_DAY = 25

/** Last day for paying a month's fee, as written in reminders («قبل 30 من الشهر»). Arrears start counting from [DEFAULT_FEE_DUE_DAY]. */
const val DEFAULT_FEE_PAY_DAY = 30

val FrenchMonthNames = listOf("Janvier", "Février", "Mars", "Avril", "Mai", "Juin", "Juillet", "Août",
    "Septembre", "Octobre", "Novembre", "Décembre")

fun frenchMonthName(key: String): String {
    val parts = key.split("-")
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return key
    return "${FrenchMonthNames.getOrElse(m - 1) { key }} ${parts[0]}"
}

/** True when the fee of [month] (yyyy-MM) is overdue on [today] (yyyy-MM-dd): past months, or this month from [dueDay]. */
fun isMonthDue(month: String, today: String, dueDay: Int = DEFAULT_FEE_DUE_DAY): Boolean {
    val current = today.take(7)
    if (month < current) return true
    if (month > current) return false
    return (today.substring(8, 10).toIntOrNull() ?: 1) >= dueDay
}

/** Arrears of one student: the due months with no payment, and the amount still owed (fee × due months − paid, never negative). */
data class FeeArrears(val student: Student, val months: List<String>, val amount: Int)

fun feeArrears(student: Student, yearMonths: List<String>, payments: List<Payment>, today: String, dueDay: Int = DEFAULT_FEE_DUE_DAY): FeeArrears {
    val mine = payments.filter { it.type == PaymentType.MONTHLY && it.studentId == student.id }
    val due = yearMonths.filter { isMonthDue(it, today, dueDay) }
    val unpaid = due.filter { m -> mine.none { it.month == m } }
    val owed = (student.monthlyFee * due.size - mine.sumOf { it.amount }).coerceAtLeast(0)
    return FeeArrears(student, unpaid, owed)
}

/** Students with arrears, in halaqa then name order. */
fun studentsWithArrears(students: List<Student>, yearMonths: List<String>, payments: List<Payment>, today: String, dueDay: Int): List<FeeArrears> =
    students.filter { it.status == "نشط" && it.monthlyFee > 0 }
        .map { feeArrears(it, yearMonths, payments, today, dueDay) }
        .filter { it.amount > 0 }
        .sortedWith(compareBy({ it.student.group.ifBlank { "￿" } }, { it.student.name }))

/** Home «تنبيهات» card (admin): students in arrears, with a button to open the reminders. */
@androidx.compose.runtime.Composable
fun ArrearsAlertCard(state: AppState, openReminders: () -> Unit) {
    val now = today(); val months = academicYearMonths(now)
    var payments by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<Payment>?>(null) }
    androidx.compose.runtime.LaunchedEffect(state.monthPayments, state.students.size) { state.loadMonths(months.first(), months.last()) { p, _ -> payments = p } }
    val late = studentsWithArrears(state.students, months, payments.orEmpty(), now, Centre.feeDueDay)
    if (payments == null || late.isEmpty()) return
    androidx.compose.material3.Card(androidx.compose.ui.Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = CardBg)) {
        androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.padding(14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.weight(1f)) {
                androidx.compose.material3.Text("تنبيهات", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Green)
                androidx.compose.material3.Text("${late.size} طالبًا متأخرون عن الدفع — ${money(late.sumOf { it.amount })} للتحصيل",
                    color = androidx.compose.ui.graphics.Color(0xFFC53030))
            }
            androidx.compose.material3.FilledTonalButton(onClick = openReminders) { androidx.compose.material3.Text("عرض") }
        }
    }
}

/** "1 520 000" without direction marks (for WhatsApp / plain text). */
fun plainAmount(n: Int): String = "%,d".format(Locale.US, n).replace(',', ' ')

/** Arabic + French WhatsApp message for a guardian. */
fun reminderMessage(a: FeeArrears, centreName: String, phone: String, payDay: Int = DEFAULT_FEE_PAY_DAY): String = buildString {
    appendLine("السلام عليكم ورحمة الله وبركاته،")
    appendLine("نُذكّركم بأن على الطالب/ة ${a.student.name} رسومًا دراسية متأخرة" +
        (if (a.months.isNotEmpty()) " عن: ${a.months.joinToString("، ") { monthName(it) }}" else "") + ".")
    appendLine("المبلغ المتأخر: ${plainAmount(a.amount)} ${Centre.currency}")
    appendLine("(تُستحق الرسوم قبل يوم $payDay من كل شهر). نرجو التسوية في أقرب وقت، جزاكم الله خيرًا.")
    appendLine()
    appendLine("Assalamou alaykoum wa rahmatullah,")
    appendLine("Nous vous rappelons que l'élève ${a.student.frenchName} a des frais de scolarité impayés" +
        (if (a.months.isNotEmpty()) " pour : ${a.months.joinToString(", ") { frenchMonthName(it) }}" else "") + ".")
    appendLine("Montant dû : ${plainAmount(a.amount)} FCFA")
    appendLine("(Les frais sont exigibles avant le $payDay de chaque mois.) Merci de régulariser dès que possible. Qu'Allah vous récompense.")
    appendLine()
    append(centreName + (if (phone.isNotBlank()) " • $phone" else ""))
}

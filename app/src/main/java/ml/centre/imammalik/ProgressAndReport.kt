package ml.centre.imammalik

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

// ================= Hizb progress =================

fun hizbLabel(hizbs: Int): String {
    if (hizbs >= TOTAL_HIZBS) return "خاتم للقرآن الكريم"
    val juz = hizbs / 2
    return "$hizbs من $TOTAL_HIZBS حزبًا" + if (juz > 0) " (≈ $juz جزءًا)" else ""
}

@Composable
fun HizbProgressBar(hizbs: Int, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { hizbs.coerceIn(0, TOTAL_HIZBS) / TOTAL_HIZBS.toFloat() },
        modifier = modifier.fillMaxWidth().height(8.dp),
        color = if (hizbs >= TOTAL_HIZBS) Gold else Green,
        trackColor = Green.copy(alpha = .12f)
    )
}

@Composable
fun HizbProgressCard(state: AppState, me: AppUser, student: Student) {
    var editing by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (student.hizbs >= TOTAL_HIZBS) Icons.Default.EmojiEvents else Icons.Default.AutoStories, null, tint = Gold)
                Spacer(Modifier.width(8.dp))
                Text("التقدم في حفظ القرآن", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${student.hizbs * 100 / TOTAL_HIZBS}٪", color = Green, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            HizbProgressBar(student.hizbs)
            Spacer(Modifier.height(6.dp))
            Text(hizbLabel(student.hizbs), color = Color.DarkGray, fontSize = 14.sp)
            if (me.isStaff) {
                if (!editing) TextButton(onClick = { editing = true }) {
                    Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("تحديث عدد الأحزاب")
                } else {
                    var value by remember(student.hizbs) { mutableFloatStateOf(student.hizbs.toFloat()) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { value = (value - 1).coerceAtLeast(0f) }) { Icon(Icons.Default.Remove, "إنقاص") }
                        Slider(value = value, onValueChange = { value = it }, valueRange = 0f..TOTAL_HIZBS.toFloat(),
                            steps = TOTAL_HIZBS - 1, modifier = Modifier.weight(1f))
                        IconButton(onClick = { value = (value + 1).coerceAtMost(TOTAL_HIZBS.toFloat()) }) { Icon(Icons.Default.Add, "زيادة") }
                    }
                    Text("${value.toInt()} حزبًا", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { editing = false }) { Text("إلغاء") }
                        Button(onClick = { state.setHizbs(student, value.toInt()); editing = false }) { Text("حفظ") }
                    }
                }
            }
        }
    }
}

// ================= Monthly report =================

private fun monthKey(offset: Int): String {
    val cal = Calendar.getInstance()
    cal.add(Calendar.MONTH, -offset)
    return SimpleDateFormat("yyyy-MM", Locale.US).format(cal.time)
}

private val ArabicMonths = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
    "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")

private fun monthName(key: String): String {
    val parts = key.split("-")
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return key
    return "${ArabicMonths.getOrElse(m - 1) { key }} ${parts[0]}"
}

fun buildMonthlyReport(student: Student, month: String, attendance: List<AttendanceRecord>, logs: List<QuranLog>): String {
    val att = attendance.filter { it.date.startsWith(month) }
    val ls = logs.filter { it.date.startsWith(month) }
    val present = att.count { it.status == AttendanceStatus.PRESENT }
    val late = att.count { it.status == AttendanceStatus.LATE }
    val absent = att.count { it.status == AttendanceStatus.ABSENT }
    val excused = att.count { it.status == AttendanceStatus.EXCUSED }
    val rate = if (att.isEmpty()) "—" else "${(present + late) * 100 / att.size}٪"
    val newLogs = ls.filter { it.type == LogTypes.NEW }
    val reviewLogs = ls.filter { it.type == LogTypes.REVIEW }
    val surahs = newLogs.map { it.surah }.distinct()
    val topGrade = ls.groupingBy { it.grade }.eachCount().filterKeys { it.isNotBlank() }.maxByOrNull { it.value }?.key
    val notes = ls.filter { it.notes.isNotBlank() }.take(3)

    return buildString {
        appendLine("مركز الإمام مالك لتحفيظ القرآن الكريم والدراسات الإسلامية")
        appendLine("التقرير الشهري — ${monthName(month)}")
        appendLine()
        appendLine("الطالب: ${student.name}")
        if (student.group.isNotBlank()) appendLine("الحلقة: ${student.group}")
        if (student.teacher.isNotBlank()) appendLine("المعلم: ${student.teacher}")
        appendLine()
        appendLine("📅 الحضور")
        if (att.isEmpty()) appendLine("لا يوجد حضور مسجل هذا الشهر")
        else {
            appendLine("حاضر: $present • متأخر: $late • غائب: $absent • معذور: $excused")
            appendLine("نسبة الحضور: $rate")
        }
        appendLine()
        appendLine("📖 الحفظ والمراجعة")
        appendLine("جلسات الحفظ الجديد: ${newLogs.size} • جلسات المراجعة: ${reviewLogs.size}")
        if (surahs.isNotEmpty()) appendLine("السور التي حفظ منها: ${surahs.joinToString("، ")}")
        if (topGrade != null) appendLine("التقدير الغالب: $topGrade")
        appendLine("التقدم الإجمالي: ${hizbLabel(student.hizbs)}")
        if (notes.isNotEmpty()) {
            appendLine()
            appendLine("📝 ملاحظات المعلم")
            notes.forEach { appendLine("• ${ltr(it.date)}: ${it.notes}") }
        }
        appendLine()
        append("جزاكم الله خيرًا على متابعتكم.")
    }
}

/** Mali numbers are 8 digits; add the country code when it is missing. */
private fun whatsappNumber(phone: String): String? {
    var d = phone.filter { it.isDigit() }
    if (d.startsWith("00")) d = d.drop(2)
    if (d.length == 8) d = "223$d"
    return d.takeIf { it.length >= 10 }
}

@Composable
fun MonthlyReportDialog(student: Student, attendance: List<AttendanceRecord>, logs: List<QuranLog>, close: () -> Unit) {
    var offset by remember { mutableIntStateOf(0) }
    val month = monthKey(offset)
    val report = buildMonthlyReport(student, month, attendance, logs)
    val context = LocalContext.current
    val wa = whatsappNumber(student.phone)

    AlertDialog(
        onDismissRequest = close,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { offset++ }, enabled = offset < 11) { Icon(Icons.Default.ChevronRight, "الشهر السابق") }
                Text(monthName(month), Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 18.sp)
                IconButton(onClick = { offset-- }, enabled = offset > 0) { Icon(Icons.Default.ChevronLeft, "الشهر التالي") }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(report, fontSize = 14.sp, lineHeight = 22.sp)
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (wa != null) Button(onClick = {
                    val uri = Uri.parse("https://wa.me/$wa?text=" + Uri.encode(report))
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                }) { Icon(Icons.Default.Send, null); Spacer(Modifier.width(6.dp)); Text("إرسال لولي الأمر (واتساب)") }
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, report) }
                    context.startActivity(Intent.createChooser(send, "مشاركة التقرير"))
                }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("مشاركة") }
                TextButton(onClick = close) { Text("إغلاق") }
            }
        }
    )
}

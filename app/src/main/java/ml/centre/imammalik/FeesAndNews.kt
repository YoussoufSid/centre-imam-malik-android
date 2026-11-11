package ml.centre.imammalik

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

// ================= Announcements =================

@Composable
fun AnnouncementCard(a: Announcement, canDelete: Boolean, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEA))) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Campaign, null, tint = Gold)
                Spacer(Modifier.width(8.dp))
                Text(a.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (canDelete) IconButton(onClick = { confirm = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, "حذف", tint = Color.Gray)
                }
            }
            if (a.body.isNotBlank()) Text(a.body, Modifier.padding(top = 6.dp), fontSize = 14.sp)
            val date = if (a.createdAt > 0) SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(a.createdAt)) else ""
            Text(listOf(a.author, ltr(date), if (canDelete) "إلى: ${Audience.label(a.audience)}" else "")
                .filter { it.isNotBlank() }.joinToString(" • "),
                Modifier.padding(top = 6.dp), fontSize = 12.sp, color = Color.Gray)
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("حذف الإعلان") }, text = { Text("هل تريد حذف «${a.title}»؟") },
        confirmButton = { Button(onClick = { confirm = false; onDelete() },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("حذف") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("تراجع") } }
    )
}

@Composable
fun AnnouncementDialog(state: AppState, close: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var audience by remember { mutableStateOf(Audience.ALL) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("إعلان جديد") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AppField(title, { title = it }, "العنوان (مثال: عطلة يوم الجمعة)")
                OutlinedTextField(body, { body = it }, label = { Text("نص الإعلان") },
                    modifier = Modifier.fillMaxWidth(), minLines = 3)
                Spacer(Modifier.height(10.dp))
                Text("موجّه إلى", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Audience.all.forEach { a -> FilterChip(audience == a, { audience = a }, { Text(Audience.label(a)) }) }
                }
            }
        },
        confirmButton = { Button(onClick = { state.postAnnouncement(title, body, audience, close) }) { Text("نشر") } },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } }
    )
}

// ================= Fees =================

@Composable
fun FeesScreen(state: AppState, openProfile: (String) -> Unit) {
    val month = state.feeMonth
    val isCurrent = month == currentMonth()
    val paid = state.monthPayments.associateBy { it.studentId }
    val billable = state.students.filter { it.status == "نشط" && it.monthlyFee > 0 }
    val unpaid = billable.filter { it.id !in paid }
    val expected = billable.sumOf { it.monthlyFee }
    val collected = state.monthPayments.sumOf { it.amount }
    var paying by remember { mutableStateOf<Student?>(null) }
    var filterUnpaid by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { state.changeFeeMonth(-1) }) { Icon(Icons.Default.ChevronRight, "الشهر السابق") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("رسوم ${monthName(month)}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = { state.changeFeeMonth(1) }, enabled = !isCurrent) { Icon(Icons.Default.ChevronLeft, "الشهر التالي") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("المحصَّل", money(collected), Icons.Default.Payments, Modifier.weight(1f), Green)
            StatCard("المتوقع", money(expected), Icons.Default.AccountBalanceWallet, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(!filterUnpaid, { filterUnpaid = false }, { Text("الكل (${billable.size})") })
            FilterChip(filterUnpaid, { filterUnpaid = true }, { Text("لم يدفعوا (${unpaid.size})") })
        }
        val noFee = state.students.count { it.status == "نشط" && it.monthlyFee <= 0 }
        if (noFee > 0) Text("$noFee طالب بلا رسوم محددة (حدّدها من «تعديل ملف الطالب»).",
            fontSize = 12.sp, color = Color.Gray)
        val shown = if (filterUnpaid) unpaid else billable
        if (shown.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (billable.isEmpty()) "لم تُحدَّد رسوم لأي طالب بعد" else "جميع الطلاب دفعوا هذا الشهر 👍",
                color = Color.Gray, textAlign = TextAlign.Center)
        } else LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { s ->
                val p = paid[s.id]
                Card(Modifier.fillMaxWidth().clickable { openProfile(s.id) }, colors = CardDefaults.cardColors(containerColor = CardBg)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, fontWeight = FontWeight.Bold)
                            Text(if (p != null) "دُفع ${money(p.amount)} • ${ltr(p.paidOn)}" else "المطلوب: ${money(s.monthlyFee)}",
                                fontSize = 13.sp, color = if (p != null) Green else Color(0xFFC53030))
                        }
                        if (p != null) Icon(Icons.Default.CheckCircle, "مدفوع", tint = Green)
                        else {
                            val wa = whatsappNumber(s.phone)
                            if (wa != null) IconButton(onClick = {
                                val msg = "السلام عليكم ورحمة الله،\nنذكّركم بأن رسوم شهر ${monthName(month)} للطالب ${s.name} " +
                                    "(${money(s.monthlyFee)}) لم تُسدَّد بعد.\nجزاكم الله خيرًا.\n${Centre.name}"
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$wa?text=" + Uri.encode(msg)))) }
                            }) { Icon(Icons.Default.NotificationsActive, "تذكير عبر واتساب", tint = Gold) }
                            FilledTonalButton(onClick = { paying = s }) { Text("تسجيل الدفع") }
                        }
                    }
                }
            }
        }
    }
    paying?.let { s -> PaymentDialog(state, s, month) { paying = null } }
}

@Composable
fun PaymentDialog(state: AppState, student: Student, month: String, close: () -> Unit) {
    var amount by remember { mutableStateOf(if (student.monthlyFee > 0) student.monthlyFee.toString() else "") }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("تسجيل دفع") },
        text = {
            Column {
                Text("${student.name} — ${monthName(month)}", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(amount, { v -> amount = v.filter { it.isDigit() }.take(7) },
                    label = { Text("المبلغ (فرنك)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { state.savePayment(student, month, amount.toIntOrNull() ?: 0, close) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } }
    )
}

/** Payments section shown in the student profile (admin and linked parents). */
@Composable
fun StudentFeesCard(state: AppState, me: AppUser, student: Student, payments: List<Payment>) {
    var paying by remember { mutableStateOf<String?>(null) }
    val months = (0 until 6).map { shiftMonth(currentMonth(), -it) }
    val byMonth = payments.associateBy { it.month }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardBg)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Payments, null, tint = Gold); Spacer(Modifier.width(8.dp))
                Text("الرسوم الشهرية", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (student.monthlyFee > 0) money(student.monthlyFee) else "غير محددة", color = SubText, fontSize = 13.sp)
            }
            months.forEach { m ->
                val p = byMonth[m]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(monthName(m), Modifier.weight(1f))
                    when {
                        p != null -> {
                            Text("مدفوع ${money(p.amount)}", color = Green, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            if (me.isAdmin) IconButton(onClick = { state.deletePayment(p) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Undo, "إلغاء الدفع", tint = Color.Gray, modifier = Modifier.size(18.dp))
                            }
                        }
                        me.isAdmin -> TextButton(onClick = { paying = m }) { Text("تسجيل الدفع") }
                        student.monthlyFee > 0 -> Text("غير مدفوع", color = Color(0xFFC53030), fontSize = 13.sp)
                        else -> Text("—", color = Color.Gray)
                    }
                }
            }
        }
    }
    paying?.let { m -> PaymentDialog(state, student, m) { paying = null } }
}

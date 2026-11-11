package ml.centre.imammalik

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.ListenerRegistration

@Composable
fun StudentRow(s: Student, open: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = open), colors = CardDefaults.cardColors(containerColor = CardBg)) {
        ListItem(
            headlineContent = { Text(s.name, fontWeight = FontWeight.Bold) },
            supportingContent = {
                Column {
                    Text("المستوى: ${s.level.ifBlank { "غير محدد" }} • الحلقة: ${s.group.ifBlank { "غير محددة" }}")
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HizbProgressBar(s.hizbs, Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Text("${s.hizbs}/$TOTAL_HIZBS", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            },
            leadingContent = { StudentAvatar(s) },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (s.status != "نشط") Text(s.status, color = Color.Gray, fontSize = 12.sp)
                    Icon(Icons.Default.ChevronLeft, "فتح الملف")
                }
            },
            colors = ListItemDefaults.colors(containerColor = CardBg)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentsScreen(state: AppState, me: AppUser, onGradeEntry: () -> Unit = {}, onBulkAdd: () -> Unit = {}, open: (Student) -> Unit) {
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<String?>(null) }
    val groups = state.students.map { it.group }.filter { it.isNotBlank() }.distinct().sorted()
    val shown = state.students.filter { s ->
        (query.isBlank() || s.name.contains(query.trim(), true) || s.phone.contains(query.trim())) &&
            (group == null || s.group == group)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        if (me.isStaff) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (me.isAdmin) OutlinedButton(onClick = onBulkAdd, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.GroupAdd, null); Spacer(Modifier.width(4.dp)); Text("إضافة جماعية", maxLines = 1)
                }
                if (state.students.isNotEmpty()) OutlinedButton(onClick = onGradeEntry, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.School, null); Spacer(Modifier.width(4.dp)); Text("إدخال درجات", maxLines = 1)
                }
            }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(top = 10.dp),
                placeholder = { Text("بحث بالاسم أو الهاتف") }, singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) })
            if (groups.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(group == null, { group = null }, { Text("الكل (${state.students.size})") })
                groups.forEach { g -> FilterChip(group == g, { group = if (group == g) null else g }, { Text(g) }) }
            }
        }
        if (shown.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                when {
                    !me.isStaff -> "لم يُربط حسابك بأي طالب بعد"
                    state.students.isEmpty() -> "لا يوجد طلاب بعد\nاضغط زر الإضافة"
                    else -> "لا توجد نتائج"
                }, color = Color.Gray
            )
        } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { s -> StudentRow(s) { open(s) } }
        }
    }
}

@Composable
fun StudentProfileScreen(state: AppState, me: AppUser, student: Student) {
    var attendance by remember(student.id) { mutableStateOf(emptyList<AttendanceRecord>()) }
    var logs by remember(student.id) { mutableStateOf(emptyList<QuranLog>()) }
    var addLog by remember { mutableStateOf(false) }
    var reportOpen by remember { mutableStateOf(false) }
    var cardOpen by remember { mutableStateOf(false) }
    var payments by remember(student.id) { mutableStateOf(emptyList<Payment>()) }
    var grades by remember(student.id) { mutableStateOf(emptyList<Grade>()) }
    DisposableEffect(student.id) {
        val reg = state.listenStudentGrades(student.id) { grades = it }
        onDispose { reg.remove() }
    }
    val seesFees = me.isAdmin || !me.isStaff
    DisposableEffect(student.id, seesFees) {
        val reg = if (seesFees) state.listenStudentPayments(student.id) { payments = it } else null
        onDispose { reg?.remove() }
    }
    DisposableEffect(student.id) {
        val regs: List<ListenerRegistration> = state.listenStudentHistory(student.id, { attendance = it }, { logs = it })
        onDispose { regs.forEach { it.remove() } }
    }
    val context = LocalContext.current
    val presentCount = attendance.count { AttendanceStatus.isPresent(it.status) }
    val rate = if (attendance.isEmpty()) "—" else "${presentCount * 100 / attendance.size}٪"
    val memorizedSurahs = logs.filter { it.type == LogTypes.NEW }.map { it.surah }.distinct()

    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardBg)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StudentAvatar(student, 64.dp); Spacer(Modifier.width(12.dp))
                        Text(student.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Green)
                    }
                    InfoLine("الحالة", student.status)
                    InfoLine("الجنس", student.gender)
                    InfoLine("تاريخ الميلاد", student.birthDate)
                    InfoLine("المستوى", student.level)
                    InfoLine("الحلقة", student.group)
                    InfoLine("المعلم", student.teacher)
                    InfoLine("العنوان", student.address)
                    InfoLine("تاريخ التسجيل", student.enrollmentDate)
                    if (student.phone.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                        InfoLine("هاتف ولي الأمر", student.phone, Modifier.weight(1f))
                        IconButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${student.phone}")))
                        }) { Icon(Icons.Default.Phone, "اتصال", tint = Green) }
                    }
                }
            }
        }
        item { HizbProgressCard(state, me, student) }
        item { StudentGradesCard(state, me, student, grades) }
        if (seesFees) item { StudentFeesCard(state, me, student, payments) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { reportOpen = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Assessment, null); Spacer(Modifier.width(4.dp)); Text("التقرير الشهري", maxLines = 1)
                }
                OutlinedButton(onClick = { cardOpen = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(4.dp)); Text("كشف النتائج", maxLines = 1)
                }
            }
        }
        if (me.isStaff) item {
            OutlinedButton(onClick = { shareCertificate(context, student) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.WorkspacePremium, null, tint = Gold); Spacer(Modifier.width(6.dp))
                Text(if (student.hizbs >= TOTAL_HIZBS) "شهادة ختم القرآن (PDF)" else "شهادة تقدير بالحفظ (PDF)")
            }
        }
        if (me.isStaff) item {
            OutlinedButton(onClick = { shareStudentCard(context, student) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Badge, null, tint = Green); Spacer(Modifier.width(6.dp)); Text("بطاقة الطالب (PDF)")
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("نسبة الحضور", rate, Icons.Default.FactCheck, Modifier.weight(1f), Green)
                StatCard("سور محفوظة", memorizedSurahs.size.toString(), Icons.Default.MenuBook, Modifier.weight(1f))
                StatCard("جلسات تسميع", logs.size.toString(), Icons.Default.Star, Modifier.weight(1f))
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("سجل الحفظ والمراجعة", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (me.isStaff) TextButton(onClick = { addLog = true }) {
                    Icon(Icons.Default.Add, null); Text("تسميع جديد")
                }
            }
        }
        if (logs.isEmpty()) item { Text("لا يوجد تسميع مسجل بعد", color = Color.Gray) }
        items(logs.take(30), key = { "log_" + it.id }) { log ->
            LogCard(log, showStudent = false, onDelete = if (me.isAdmin) ({ state.deleteLog(log) }) else null)
        }
        item { Text("سجل الحضور", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        if (attendance.isEmpty()) item { Text("لا يوجد حضور مسجل بعد", color = Color.Gray) }
        items(attendance.take(30), key = { "att_" + it.id }) { r ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardBg)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(ltr(r.date), Modifier.weight(1f))
                    StatusBadge(r.status)
                }
            }
        }
    }
    if (addLog) LogDialog(state, initialStudent = student) { addLog = false }
    if (cardOpen) {
        val terms = Terms.filter { t -> grades.any { it.term == t } }
        AlertDialog(
            onDismissRequest = { cardOpen = false },
            title = { Text("كشف النتائج (PDF)") },
            text = {
                Column {
                    Text("اختر الفترة:", fontWeight = FontWeight.Bold)
                    (listOf<String?>(null) + terms).forEach { t ->
                        TextButton(onClick = { cardOpen = false; shareReportCard(context, student, t, grades, attendance) },
                            modifier = Modifier.fillMaxWidth()) { Text(t ?: "جميع الفصول") }
                    }
                    if (terms.isEmpty()) Text("لا توجد درجات بعد؛ سيتضمن الكشف الحفظ والحضور فقط.", fontSize = 12.sp, color = Color.Gray)
                }
            },
            confirmButton = { TextButton(onClick = { cardOpen = false }) { Text("إغلاق") } }
        )
    }
    if (reportOpen) MonthlyReportDialog(student, attendance, logs, grades) { reportOpen = false }
}

@Composable
private fun InfoLine(label: String, value: String, modifier: Modifier = Modifier) {
    if (value.isBlank()) return
    Row(modifier) {
        Text("$label: ", fontWeight = FontWeight.Bold, color = SubText)
        Text(value)
    }
}

@Composable
fun StatusBadge(status: String) {
    val color = when (status) {
        AttendanceStatus.PRESENT -> Green
        AttendanceStatus.LATE -> Color(0xFFB7791F)
        AttendanceStatus.EXCUSED -> Color(0xFF2B6CB0)
        else -> Color(0xFFC53030)
    }
    Surface(color = color.copy(alpha = .12f), shape = MaterialTheme.shapes.small) {
        Text(status, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StudentDialog(state: AppState, existing: Student?, onDeleted: () -> Unit = {}, close: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var gender by remember { mutableStateOf(existing?.gender ?: "") }
    var birthDate by remember { mutableStateOf(existing?.birthDate ?: "") }
    var level by remember { mutableStateOf(existing?.level ?: "") }
    var group by remember { mutableStateOf(existing?.group ?: "") }
    var teacher by remember { mutableStateOf(existing?.teacher ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var address by remember { mutableStateOf(existing?.address ?: "") }
    var enrollmentDate by remember { mutableStateOf(existing?.enrollmentDate ?: today()) }
    var status by remember { mutableStateOf(existing?.status ?: "نشط") }
    var fee by remember { mutableStateOf(existing?.monthlyFee?.takeIf { it > 0 }?.toString() ?: "") }
    var regFee by remember { mutableStateOf(existing?.registrationFee?.takeIf { it > 0 }?.toString() ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf(existing?.photo ?: "") }
    val student = Student(existing?.id ?: "", name, gender, birthDate, level, group, teacher, phone, address, enrollmentDate, status,
        hizbs = existing?.hizbs ?: 0, monthlyFee = fee.toIntOrNull() ?: 0, registrationFee = regFee.toIntOrNull() ?: 0, photo = photo)

    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (existing == null) "إضافة طالب" else "تعديل ملف الطالب") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PhotoPicker(photo) { photo = it }
                AppField(name, { name = it }, "الاسم الكامل")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("الجنس:", fontWeight = FontWeight.Bold)
                    listOf("ذكر", "أنثى").forEach { g ->
                        RadioButton(selected = gender == g, onClick = { gender = g }); Text(g)
                    }
                }
                DateField(birthDate, { birthDate = it }, "تاريخ الميلاد", optional = true)
                AppField(level, { level = it }, "المستوى (مثال: الحزب 5)")
                GroupField(state, group, { group = it }) { t -> if (teacher.isBlank()) teacher = t }
                AppField(teacher, { teacher = it }, "المعلم أو المعلمة")
                AppField(phone, { phone = it }, "هاتف ولي الأمر", KeyboardType.Phone)
                AppField(address, { address = it }, "العنوان")
                DateField(enrollmentDate, { enrollmentDate = it }, "تاريخ التسجيل")
                if (state.profile?.isAdmin == true) AppField(fee, { v -> fee = v.filter { it.isDigit() }.take(7) },
                    "الرسوم الشهرية بالفرنك (اتركها فارغة للإعفاء)", KeyboardType.Number)
                if (state.profile?.isAdmin == true) AppField(regFee, { v -> regFee = v.filter { it.isDigit() }.take(7) },
                    "رسوم التسجيل بالفرنك (مرة واحدة)", KeyboardType.Number)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("الحالة:", fontWeight = FontWeight.Bold)
                    listOf("نشط", "متوقف").forEach { st ->
                        RadioButton(selected = status == st, onClick = { status = st }); Text(st)
                    }
                }
                if (existing != null && state.profile?.isAdmin == true) TextButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("حذف الطالب") }
            }
        },
        confirmButton = { Button(onClick = { state.saveStudent(student, close) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } }
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text("تأكيد الحذف") },
        text = { Text("هل تريد حذف ملف ${existing?.name}؟") },
        confirmButton = {
            Button(onClick = { existing?.let { state.deleteStudent(it) { close(); onDeleted() } } },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("حذف") }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("تراجع") } }
    )
}

@Composable
fun NumberField(value: String, change: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(value, { v -> change(v.filter { it.isDigit() }.take(3)) }, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = modifier)
}

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
import androidx.compose.runtime.saveable.rememberSaveable
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
import java.util.Date
import java.util.Locale

private val Red = Color(0xFFC53030)

@Composable
fun TeachersScreen(state: AppState) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab, containerColor = Color.White, contentColor = Green) {
            listOf("المعلمون", "الحضور", "الرواتب").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
            }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> TeacherListTab(state)
                1 -> TeacherAttendanceTab(state)
                else -> SalariesTab(state)
            }
        }
    }
}

// ---------------- List ----------------

@Composable
private fun TeacherListTab(state: AppState) {
    var editing by remember { mutableStateOf<Teacher?>(null) }
    val context = LocalContext.current
    val known = state.teachers.map { it.name.trim() }.toSet()
    // Teacher accounts or halaqa teachers that have no teacher file yet.
    val suggestions = (state.users.filter { it.role == Roles.TEACHER }.map { it.name } + state.halaqat.map { it.teacher })
        .map { it.trim() }.filter { it.isNotBlank() && it !in known }.distinct()

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 10.dp)) {
        item {
            Button(onClick = { editing = Teacher(hireDate = today()) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(6.dp)); Text("إضافة معلم")
            }
        }
        if (suggestions.isNotEmpty()) item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEA))) {
                Column(Modifier.padding(12.dp)) {
                    Text("معلمون في الحسابات أو الحلقات بلا ملف:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    suggestions.forEach { n ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(n, Modifier.weight(1f))
                            TextButton(onClick = { editing = Teacher(name = n, hireDate = today()) }) { Text("إنشاء ملف") }
                        }
                    }
                }
            }
        }
        if (state.teachers.isEmpty() && suggestions.isEmpty()) item { EmptyState(Icons.Default.CoPresent, "لا يوجد معلمون مسجلون بعد") }
        items(state.teachers, key = { it.id }) { t ->
            val groups = state.halaqat.filter { it.teacher.trim() == t.name.trim() }
            val count = state.students.count { s -> groups.any { it.name == s.group } && s.status == "نشط" }
            Card(Modifier.fillMaxWidth().clickable { editing = t }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(t.name, 44.dp); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t.name, fontWeight = FontWeight.Bold)
                            if (t.status != "نشط") Text("  (${t.status})", fontSize = 12.sp, color = Color.Gray)
                        }
                        Text(if (groups.isEmpty()) "لا توجد حلقة" else groups.joinToString("، ") { it.name } + " • $count طالب",
                            fontSize = 13.sp, color = Color.DarkGray)
                        if (t.salary > 0) Text("الراتب: ${money(t.salary)}", fontSize = 12.sp, color = Color.Gray)
                    }
                    if (t.phone.isNotBlank()) IconButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${t.phone}"))) }
                    }) { Icon(Icons.Default.Phone, "اتصال", tint = Green) }
                }
            }
        }
    }
    editing?.let { t -> TeacherDialog(state, t) { editing = null } }
}

@Composable
private fun TeacherDialog(state: AppState, initial: Teacher, close: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var phone by remember { mutableStateOf(initial.phone) }
    var salary by remember { mutableStateOf(initial.salary.takeIf { it > 0 }?.toString() ?: "") }
    var hireDate by remember { mutableStateOf(initial.hireDate) }
    var status by remember { mutableStateOf(initial.status) }
    var notes by remember { mutableStateOf(initial.notes) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (initial.id.isBlank()) "معلم جديد" else "ملف المعلم") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AppField(name, { name = it }, "الاسم الكامل")
                AppField(phone, { phone = it }, "الهاتف", KeyboardType.Phone)
                AppField(salary, { v -> salary = v.filter { it.isDigit() }.take(8) }, "الراتب الشهري (${Centre.currency})", KeyboardType.Number)
                DateField(hireDate, { hireDate = it }, "تاريخ بدء العمل", optional = true)
                AppField(notes, { notes = it }, "ملاحظات (المؤهل، التخصص...)")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("الحالة:", fontWeight = FontWeight.Bold)
                    listOf("نشط", "متوقف").forEach { st -> RadioButton(status == st, { status = st }); Text(st) }
                }
                Text("تُحدَّد حلقات المعلم من شاشة «الحلقات».", fontSize = 12.sp, color = Color.Gray)
                if (initial.id.isNotBlank()) TextButton(onClick = { confirmDelete = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("حذف المعلم") }
            }
        },
        confirmButton = { Button(onClick = {
            state.saveTeacher(initial.copy(name = name, phone = phone, salary = salary.toIntOrNull() ?: 0,
                hireDate = hireDate, status = status, notes = notes), close)
        }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } }
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text("حذف المعلم") },
        text = { Text("هل تريد حذف ملف «${initial.name}»؟ تبقى رواتبه المسجلة في المصروفات.") },
        confirmButton = { Button(onClick = { state.deleteTeacher(initial); confirmDelete = false; close() },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("حذف") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("تراجع") } }
    )
}

// ---------------- Attendance ----------------

@Composable
private fun TeacherAttendanceTab(state: AppState) {
    val active = state.teachers.filter { it.status == "نشط" }
    val byTeacher = state.teacherDayAttendance.associateBy { it.studentId }
    val month = state.teacherDate.take(7)
    var monthData by remember(month) { mutableStateOf<Map<String, List<String>>?>(null) }
    LaunchedEffect(month, state.teacherDayAttendance) { state.loadTeacherMonth(month) { monthData = it } }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 12.dp)) {
        item {
            MonthSwitcher(if (state.teacherDate == today()) "حضور اليوم • ${ltr(state.teacherDate)}" else ltr(state.teacherDate),
                state.teacherDate < today(), { state.changeTeacherDate(-1) }, { state.changeTeacherDate(1) })
        }
        if (active.isEmpty()) item { EmptyState(Icons.Default.CoPresent, "أضف المعلمين أولًا من تبويب «المعلمون»") }
        items(active, key = { it.id }) { t ->
            val current = byTeacher[t.id]?.status
            val m = monthData?.get(t.id).orEmpty()
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        if (m.isNotEmpty()) Text("هذا الشهر: حضور ${m.count { AttendanceStatus.isPresent(it) }} • غياب ${m.count { it == AttendanceStatus.ABSENT }}",
                            fontSize = 12.sp, color = Color.Gray)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AttendanceStatus.all.forEach { st ->
                            FilterChip(current == st, { state.setTeacherAttendance(t, st) }, { Text(st, fontSize = 13.sp) }, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Salaries ----------------

@Composable
private fun SalariesTab(state: AppState) {
    val month = state.salaryMonth
    // Salary expenses use the id "salary_<month>_<teacherId>".
    val paid = state.salaryExpenses.associateBy { it.id.removePrefix("salary_${month}_") }
    val active = state.teachers.filter { it.status == "نشط" || it.id in paid }
    val totalDue = active.filter { it.id !in paid }.sumOf { it.salary }
    val totalPaid = state.salaryExpenses.sumOf { it.amount }
    var paying by remember { mutableStateOf<Teacher?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 12.dp)) {
        item {
            MonthSwitcher("رواتب ${monthName(month)}", month < currentMonth(), { state.changeSalaryMonth(-1) }, { state.changeSalaryMonth(1) })
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("المدفوع", money(totalPaid), Icons.Default.Payments, Modifier.weight(1f), Green)
                StatCard("المتبقي", money(totalDue), Icons.Default.PendingActions, Modifier.weight(1f), Red)
            }
        }
        item { Text("تُسجَّل الرواتب تلقائيًا في المصروفات (رواتب المعلمين).", fontSize = 12.sp, color = Color.Gray) }
        if (active.isEmpty()) item { EmptyState(Icons.Default.Payments, "لا يوجد معلمون بعد") }
        items(active, key = { it.id }) { t ->
            val e = paid[t.id]
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(t.name, 40.dp); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.name, fontWeight = FontWeight.Bold)
                        Text(if (e != null) "دُفع ${money(e.amount)} • ${ltr(e.date)}" else "الراتب: ${money(t.salary)}",
                            fontSize = 13.sp, color = if (e != null) Green else Red)
                    }
                    if (e != null) IconButton(onClick = { state.cancelSalary(e) }) { Icon(Icons.Default.Undo, "إلغاء", tint = Color.Gray) }
                    else FilledTonalButton(onClick = { paying = t }) { Text("دفع الراتب") }
                }
            }
        }
    }
    paying?.let { t ->
        var amount by remember(t.id) { mutableStateOf(t.salary.takeIf { it > 0 }?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { paying = null },
            title = { Text("راتب ${t.name} — ${monthName(month)}") },
            text = {
                OutlinedTextField(amount, { v -> amount = v.filter { it.isDigit() }.take(8) }, singleLine = true,
                    label = { Text("المبلغ (${Centre.currency})") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            },
            confirmButton = { Button(onClick = { state.paySalary(t, month, amount.toIntOrNull() ?: 0) { paying = null } }) { Text("تأكيد الدفع") } },
            dismissButton = { TextButton(onClick = { paying = null }) { Text("إلغاء") } }
        )
    }
}

// ---------------- Audit log ----------------

@Composable
fun AuditScreen(state: AppState) {
    var entries by remember { mutableStateOf<List<AuditEntry>?>(null) }
    var filter by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { state.loadAudit { entries = it } }
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    val shown = entries.orEmpty().filter { filter.isBlank() || it.action.contains(filter) || it.details.contains(filter) || it.by.contains(filter) }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        OutlinedTextField(filter, { filter = it }, Modifier.fillMaxWidth().padding(top = 10.dp), singleLine = true,
            placeholder = { Text("بحث في السجل (اسم، عملية...)") }, leadingIcon = { Icon(Icons.Default.Search, null) })
        Text("آخر 300 عملية حساسة. لا يمكن تعديل السجل أو حذفه.", fontSize = 12.sp, color = Color.Gray,
            modifier = Modifier.padding(vertical = 6.dp))
        when {
            entries == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            shown.isEmpty() -> EmptyState(Icons.Default.History, "لا توجد عمليات مسجلة")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                items(shown) { e ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(10.dp)) {
                            Row {
                                Text(e.action, Modifier.weight(1f), fontWeight = FontWeight.Bold,
                                    color = if (e.action.startsWith("حذف") || e.action.startsWith("إلغاء")) Red else Green)
                                Text(ltr(fmt.format(Date(e.at))), fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.End)
                            }
                            if (e.details.isNotBlank()) Text(e.details, fontSize = 13.sp)
                            Text("بواسطة: ${e.by}", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

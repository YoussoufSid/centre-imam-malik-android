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
        TabRow(selectedTabIndex = tab, containerColor = CardBg, contentColor = Green) {
            listOf("الموظفون", "الحضور", "الرواتب").forEachIndexed { i, t ->
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

@Composable
private fun AmountInput(value: String, change: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(value, { v -> change(v.filter { it.isDigit() }.take(8)) }, modifier, singleLine = true,
        label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}

// ---------------- Staff list ----------------

@Composable
private fun TeacherListTab(state: AppState) {
    var editing by remember { mutableStateOf<Teacher?>(null) }
    val context = LocalContext.current
    val known = state.teachers.map { it.name.trim() }.toSet()
    val suggestions = (state.users.filter { it.role == Roles.TEACHER }.map { it.name } + state.halaqat.map { it.teacher })
        .map { it.trim() }.filter { it.isNotBlank() && it !in known }.distinct()

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 10.dp)) {
        item {
            Button(onClick = { editing = Teacher(hireDate = today()) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(6.dp)); Text("إضافة موظف")
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
        if (state.teachers.isEmpty() && suggestions.isEmpty()) item { EmptyState(Icons.Default.CoPresent, "لا يوجد موظفون مسجلون بعد") }
        StaffCategory.all.forEach { cat ->
            val list = state.teachers.filter { it.category == cat }
            if (list.isNotEmpty()) {
                item(key = "h_$cat") {
                    Text("${StaffCategory.plural(cat)} (${list.size}) • ${StaffCategory.french(cat)}", fontWeight = FontWeight.Bold,
                        color = Green, modifier = Modifier.padding(top = 6.dp))
                }
                items(list, key = { it.id }) { t ->
                    val groups = state.halaqat.filter { it.teacher.trim() == t.name.trim() }
                    val count = state.students.count { s -> groups.any { it.name == s.group } && s.status == "نشط" }
                    Card(Modifier.fillMaxWidth().clickable { editing = t }, colors = CardDefaults.cardColors(containerColor = CardBg)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            PhotoAvatar(t.name, t.photo, 44.dp); Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(t.name, fontWeight = FontWeight.Bold)
                                    if (t.status != "نشط") Text("  (${t.status})", fontSize = 12.sp, color = Color.Gray)
                                }
                                if (t.function.isNotBlank()) Text(t.function, fontSize = 13.sp, color = SubText)
                                if (groups.isNotEmpty()) Text(groups.joinToString("، ") { it.name } + " • $count طالب",
                                    fontSize = 12.sp, color = Color.Gray)
                                Text("الراتب: ${money(t.salary)}" + if (t.hireDate.isNotBlank()) " • منذ ${ltr(t.hireDate)}" else "",
                                    fontSize = 12.sp, color = Color.Gray)
                            }
                            if (t.phone.isNotBlank()) IconButton(onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${t.phone}"))) }
                            }) { Icon(Icons.Default.Phone, "اتصال", tint = Green) }
                        }
                    }
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
    var cessation by remember { mutableStateOf(initial.cessationDate) }
    var status by remember { mutableStateOf(initial.status) }
    var notes by remember { mutableStateOf(initial.notes) }
    var category by remember { mutableStateOf(initial.category) }
    var function by remember { mutableStateOf(initial.function) }
    var mode by remember { mutableStateOf(initial.paymentMode) }
    var photo by remember { mutableStateOf(initial.photo) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (initial.id.isBlank()) "موظف جديد" else "ملف الموظف") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PhotoPicker(photo) { photo = it }
                AppField(name, { name = it }, "الاسم الكامل")
                Text("الفئة", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StaffCategory.all.forEach { c -> FilterChip(category == c, { category = c }, { Text(c) }) }
                }
                AppField(function, { function = it }, "الوظيفة (مثال: مدرس الحلقة)")
                AppField(phone, { phone = it }, "الهاتف", KeyboardType.Phone)
                AppField(salary, { v -> salary = v.filter { it.isDigit() }.take(8) }, "الراتب الأساسي (${Centre.currency})", KeyboardType.Number)
                Text("طريقة الدفع", fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PaymentModes.forEach { m -> FilterChip(mode == m, { mode = if (mode == m) "" else m }, { Text(m) }) }
                }
                DateField(hireDate, { hireDate = it }, "تاريخ التعيين", optional = true)
                DateField(cessation, { cessation = it; if (it.isNotBlank()) status = "متوقف" }, "تاريخ التوقف عن العمل", optional = true)
                AppField(notes, { notes = it }, "ملاحظات (المؤهل، التخصص...)")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("الحالة:", fontWeight = FontWeight.Bold)
                    listOf("نشط", "متوقف").forEach { st -> RadioButton(status == st, { status = st }); Text(st) }
                }
                if (category == StaffCategory.TEACHER) Text("تُحدَّد حلقات المدرس من شاشة «الحلقات».", fontSize = 12.sp, color = Color.Gray)
                if (initial.id.isNotBlank()) OutlinedButton(onClick = {
                    shareStaffCard(context, initial.copy(name = name, phone = phone, hireDate = hireDate, category = category,
                        function = function, photo = photo))
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Badge, null, tint = Green); Spacer(Modifier.width(6.dp)); Text("بطاقة الموظف (PDF)")
                }
                if (initial.id.isNotBlank()) TextButton(onClick = { confirmDelete = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("حذف الموظف") }
            }
        },
        confirmButton = { Button(onClick = {
            state.saveTeacher(initial.copy(name = name, phone = phone, salary = salary.toIntOrNull() ?: 0, hireDate = hireDate,
                cessationDate = cessation, status = status, notes = notes, category = category, function = function,
                paymentMode = mode, photo = photo), close)
        }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } }
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text("حذف الموظف") },
        text = { Text("هل تريد حذف ملف «${initial.name}»؟ تبقى رواتبه المسجلة. يُفضَّل بدلًا من الحذف تحديد «تاريخ التوقف عن العمل».") },
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
        if (active.isEmpty()) item { EmptyState(Icons.Default.CoPresent, "أضف الموظفين أولًا من تبويب «الموظفون»") }
        items(active, key = { it.id }) { t ->
            val current = byTeacher[t.id]?.status
            val m = monthData?.get(t.id).orEmpty()
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardBg)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.name, fontWeight = FontWeight.Bold)
                            Text(t.function.ifBlank { t.category }, fontSize = 12.sp, color = Color.Gray)
                        }
                        if (m.isNotEmpty()) Text("الشهر: حضور ${m.count { AttendanceStatus.isPresent(it) }} • غياب ${m.count { it == AttendanceStatus.ABSENT }}",
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

// ---------------- Payroll ----------------

@Composable
private fun SalariesTab(state: AppState) {
    val context = LocalContext.current
    val month = state.salaryMonth
    val entries = state.monthPayroll.associateBy { it.staffId }
    val staff = state.teachers.filter { (it.status == "نشط" && (it.hireDate.isBlank() || it.hireDate.take(7) <= month)) || it.id in entries }
    var year by remember(month) { mutableStateOf<List<PayrollEntry>?>(null) }
    LaunchedEffect(month, state.monthPayroll) { state.loadPayrollYear(month) { year = it } }
    var editing by remember { mutableStateOf<Teacher?>(null) }
    val list = entries.values.toList()

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            MonthSwitcher("رواتب ${monthName(month)}", month < currentMonth(), { state.changeSalaryMonth(-1) }, { state.changeSalaryMonth(1) })
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("الرواتب الأساسية", money(staff.sumOf { entries[it.id]?.base ?: it.salary }), Icons.Default.Payments, Modifier.weight(1f))
                StatCard("السلف", money(list.sumOf { it.advance }), Icons.Default.RequestQuote, Modifier.weight(1f), Gold)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("المستلم فعلًا", money(list.sumOf { it.received }), Icons.Default.CheckCircle, Modifier.weight(1f), Green)
                StatCard("لم يُسجَّل بعد", "${staff.count { it.id !in entries }}", Icons.Default.PendingActions, Modifier.weight(1f), Red)
            }
        }
        item {
            OutlinedButton(onClick = { sharePayrollStatement(context, month, staff, list, year.orEmpty()) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(6.dp)); Text("كشف رواتب الشهر (PDF)")
            }
        }
        if (staff.isEmpty()) item { EmptyState(Icons.Default.Payments, "لا يوجد موظفون بعد") }
        StaffCategory.all.forEach { cat ->
            val group = staff.filter { it.category == cat }
            if (group.isNotEmpty()) {
                item(key = "h_$cat") { Text(StaffCategory.plural(cat), fontWeight = FontWeight.Bold, color = Green) }
                items(group, key = { it.id }) { t ->
                    val e = entries[t.id]
                    Card(Modifier.fillMaxWidth().clickable { editing = t }, colors = CardDefaults.cardColors(containerColor = CardBg)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            PhotoAvatar(t.name, t.photo, 40.dp); Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.name, fontWeight = FontWeight.Bold)
                                if (e == null) Text("الراتب: ${money(t.salary)} • لم يُسجَّل", fontSize = 13.sp, color = Red)
                                else {
                                    Text("صافي ${money(e.net)}" + (if (e.advance > 0) " (سلفة ${money(e.advance)})" else "") +
                                        " • مستلم ${money(e.received)}", fontSize = 13.sp, color = Green)
                                    if (e.depositChange != 0) Text(if (e.depositChange > 0) "متروك أمانة: ${money(e.depositChange)}"
                                        else "مسحوب من الأمانة: ${money(-e.depositChange)}", fontSize = 12.sp, color = Gold)
                                }
                            }
                            IconButton(onClick = { shareSalaryCard(context, t, year.orEmpty().filter { it.staffId == t.id }, month) }) {
                                Icon(Icons.Default.Badge, "بطاقة الاستلام", tint = Color.Gray)
                            }
                            Icon(if (e != null) Icons.Default.CheckCircle else Icons.Default.ChevronLeft, null,
                                tint = if (e != null) Green else Color.Gray)
                        }
                    }
                }
            }
        }
        // Salaries left in deposit with the administration (academic year)
        val y = year
        if (y != null && state.teachers.isNotEmpty()) item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEA))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("الرواتب المتروكة أمانةً لدى الإدارة — ${academicYearLabel(month)}", fontWeight = FontWeight.Bold, color = Green)
                    state.teachers.forEach { t ->
                        val mine = y.filter { it.staffId == t.id }
                        val bal = depositBalance(mine)
                        Row {
                            Text(t.name, Modifier.weight(1f), fontSize = 13.sp)
                            Text("مستحق ${money(mine.sumOf { it.net })} • مستلم ${money(mine.sumOf { it.received })}",
                                fontSize = 11.sp, color = Color.Gray)
                        }
                        Text("الرصيد: ${money(bal)}", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = if (bal > 0) Gold else Color.Gray, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                    }
                    HorizontalDivider()
                    Text("مجموع الأمانات: ${money(depositBalance(y))}", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    editing?.let { t -> PayrollDialog(state, t, month, entries[t.id]) { editing = null } }
}

@Composable
private fun PayrollDialog(state: AppState, t: Teacher, month: String, existing: PayrollEntry?, close: () -> Unit) {
    var base by remember { mutableStateOf((existing?.base ?: t.salary).takeIf { it > 0 }?.toString() ?: "") }
    var advance by remember { mutableStateOf(existing?.advance?.takeIf { it > 0 }?.toString() ?: "") }
    var received by remember { mutableStateOf(existing?.received?.toString() ?: "") }
    var date by remember { mutableStateOf(existing?.receivedDate ?: today()) }
    var mode by remember { mutableStateOf(existing?.paymentMode?.ifBlank { null } ?: t.paymentMode) }
    val b = base.toIntOrNull() ?: 0; val a = advance.toIntOrNull() ?: 0
    val net = b - a
    val r = received.toIntOrNull() ?: net
    AlertDialog(
        onDismissRequest = close,
        title = { Text("راتب ${t.name} — ${monthName(month)}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountInput(base, { base = it }, "الراتب الأساسي")
                AmountInput(advance, { advance = it }, "السلفة (تُخصم من الراتب)")
                Text("صافي الراتب: ${money(net)}", fontWeight = FontWeight.Bold, color = Green)
                AmountInput(received, { received = it }, "المبلغ المستلم فعلًا (فارغ = الصافي كاملًا)")
                val change = net - r
                if (change != 0) Text(if (change > 0) "سيُترك أمانةً لدى الإدارة: ${money(change)}"
                    else "سيُسحب من الأمانة: ${money(-change)}", color = Gold, fontSize = 13.sp)
                DateField(date, { date = it }, "تاريخ الاستلام")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PaymentModes.forEach { m -> FilterChip(mode == m, { mode = if (mode == m) "" else m }, { Text(m) }) }
                }
                if (existing != null) TextButton(onClick = { state.deletePayroll(existing); close() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("حذف سجل هذا الشهر") }
            }
        },
        confirmButton = { Button(onClick = {
            state.savePayroll(t, month, b, a, received.toIntOrNull() ?: net.coerceAtLeast(0), date, mode, close)
        }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } }
    )
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
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardBg)) {
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

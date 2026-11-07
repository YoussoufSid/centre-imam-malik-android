package ml.centre.imammalik

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

// ================= Attendance =================

@Composable
fun AttendanceScreen(state: AppState) {
    val active = state.students.filter { it.status == "نشط" }
    val byStudent = state.dayAttendance.associateBy { it.studentId }
    val isToday = state.attendanceDate == today()
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { state.changeAttendanceDate(-1) }) { Icon(Icons.Default.ChevronRight, "اليوم السابق") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (isToday) "حضور اليوم" else "حضور يوم سابق", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(ltr(state.attendanceDate), color = Color.DarkGray)
            }
            IconButton(onClick = { state.changeAttendanceDate(1) }, enabled = !isToday) { Icon(Icons.Default.ChevronLeft, "اليوم التالي") }
        }
        val counts = AttendanceStatus.all.associateWith { st -> active.count { byStudent[it.id]?.status == st } }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            AttendanceStatus.all.forEach { st -> Text("$st: ${counts[st]}", fontSize = 13.sp) }
            Text("بلا تسجيل: ${active.count { it.id !in byStudent }}", fontSize = 13.sp, color = Color.Gray)
        }
        if (active.isNotEmpty()) OutlinedButton(onClick = { state.markAllPresent(active) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.DoneAll, null); Spacer(Modifier.width(6.dp)); Text("تسجيل حضور الجميع غير المسجلين")
        }
        if (active.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لا يوجد طلاب نشطون", color = Color.Gray)
        } else LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(active, key = { it.id }) { s ->
                val current = byStudent[s.id]?.status
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            if (s.group.isNotBlank()) Text(s.group, color = Color.Gray, fontSize = 12.sp)
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AttendanceStatus.all.forEach { st ->
                                FilterChip(selected = current == st, onClick = { state.setAttendance(s, st) },
                                    label = { Text(st, fontSize = 13.sp) }, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ================= Quran memorization =================

@Composable
fun LogCard(log: QuranLog, showStudent: Boolean, onDelete: (() -> Unit)? = null, onClick: (() -> Unit)? = null) {
    val gradeColor = when (log.grade) {
        "ممتاز", "جيد جدًا" -> Green
        "يحتاج إعادة" -> Color(0xFFC53030)
        else -> Color(0xFFB7791F)
    }
    Card(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (log.type == LogTypes.NEW) Icons.Default.MenuBook else Icons.Default.Replay, null,
                tint = if (log.type == LogTypes.NEW) Green else Gold)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                if (showStudent) Text(log.studentName, fontWeight = FontWeight.Bold)
                Text("${log.type} • سورة ${log.surah} ${log.range}".trim(), fontWeight = if (showStudent) FontWeight.Normal else FontWeight.Bold)
                Text(listOf(ltr(log.date), log.teacherName).filter { it.isNotBlank() }.joinToString(" • "), fontSize = 12.sp, color = Color.Gray)
                if (log.notes.isNotBlank()) Text(log.notes, fontSize = 13.sp, color = Color.DarkGray)
            }
            if (log.grade.isNotBlank()) Text(log.grade, color = gradeColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "حذف", tint = Color.Gray) }
        }
    }
}

@Composable
fun QuranScreen(state: AppState, openProfile: (String) -> Unit) {
    var addOpen by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf<String?>(null) }
    val logs = state.recentLogs.filter { filter == null || it.type == filter }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Button(onClick = { addOpen = true }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(50.dp)) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("تسجيل تسميع جديد")
        }
        Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(filter == null, { filter = null }, { Text("الكل") })
            LogTypes.all.forEach { t -> FilterChip(filter == t, { filter = if (filter == t) null else t }, { Text(t) }) }
        }
        if (logs.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لا يوجد تسميع مسجل بعد", color = Color.Gray)
        } else LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(logs, key = { it.id }) { log -> LogCard(log, showStudent = true, onClick = { openProfile(log.studentId) }) }
        }
    }
    if (addOpen) LogDialog(state, null) { addOpen = false }
}

@Composable
fun LogDialog(state: AppState, initialStudent: Student?, close: () -> Unit) {
    var student by remember { mutableStateOf(initialStudent) }
    var type by remember { mutableStateOf(LogTypes.NEW) }
    var surah by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf(Grades.first()) }
    var notes by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today()) }
    var pickStudent by remember { mutableStateOf(false) }
    var pickSurah by remember { mutableStateOf(false) }
    val maxAyah = Surahs.firstOrNull { it.first == surah }?.second

    AlertDialog(
        onDismissRequest = close,
        title = { Text("تسجيل تسميع") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { if (initialStudent == null) pickStudent = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Person, null); Spacer(Modifier.width(6.dp))
                    Text(student?.name ?: "اختر الطالب")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LogTypes.all.forEach { t -> FilterChip(type == t, { type = t }, { Text(t) }) }
                }
                OutlinedButton(onClick = { pickSurah = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.MenuBook, null); Spacer(Modifier.width(6.dp))
                    Text(if (surah.isBlank()) "اختر السورة" else "سورة $surah")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(from, { from = it }, "من آية", Modifier.weight(1f))
                    NumberField(to, { to = it }, "إلى آية", Modifier.weight(1f))
                }
                if (maxAyah != null) Text("عدد آيات السورة: $maxAyah", fontSize = 12.sp, color = Color.Gray)
                Text("التقدير", fontWeight = FontWeight.Bold)
                Column {
                    Grades.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { g -> FilterChip(grade == g, { grade = g }, { Text(g, fontSize = 13.sp) }) }
                        }
                    }
                }
                AppField(date, { date = it }, "التاريخ")
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات المعلم") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = {
            Button(onClick = {
                val s = student
                state.saveLog(QuranLog(
                    studentId = s?.id ?: "", studentName = s?.name ?: "", date = date.trim().ifBlank { today() },
                    type = type, surah = surah, fromAyah = from.toIntOrNull() ?: 0, toAyah = to.toIntOrNull() ?: 0,
                    grade = grade, notes = notes
                ), close)
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } }
    )
    if (pickStudent) PickerDialog("اختر الطالب", state.students.filter { it.status == "نشط" }.map { it.name to it }, { pickStudent = false }) {
        student = it; pickStudent = false
    }
    if (pickSurah) PickerDialog("اختر السورة", Surahs.mapIndexed { i, s -> "${i + 1}. ${s.first}" to s.first }, { pickSurah = false }) {
        surah = it; pickSurah = false
    }
}

@Composable
fun <T> PickerDialog(title: String, options: List<Pair<String, T>>, dismiss: () -> Unit, pick: (T) -> Unit) {
    var q by remember { mutableStateOf("") }
    Dialog(onDismissRequest = dismiss) {
        Card(Modifier.fillMaxWidth().heightIn(max = 520.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    placeholder = { Text("بحث") }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) })
                val shown = options.filter { q.isBlank() || it.first.contains(q.trim()) }
                if (shown.isEmpty()) Text("لا توجد نتائج", color = Color.Gray, modifier = Modifier.padding(12.dp))
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(shown.size) { i ->
                        Text(shown[i].first, Modifier.fillMaxWidth().clickable { pick(shown[i].second) }.padding(vertical = 12.dp, horizontal = 8.dp))
                        HorizontalDivider()
                    }
                }
                TextButton(onClick = dismiss, modifier = Modifier.align(Alignment.End)) { Text("إغلاق") }
            }
        }
    }
}

// ================= Users / accounts (admin) =================

@Composable
fun UsersScreen(state: AppState) {
    var editing by remember { mutableStateOf<AppUser?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Text("حسابات المستخدمين", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 10.dp))
        Text("حدد صفة كل حساب، واربط حسابات أولياء الأمور والطلاب بملفات الطلاب ليتمكنوا من متابعة الحفظ والحضور.",
            fontSize = 13.sp, color = Color.DarkGray)
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.users, key = { it.uid }) { u ->
                Card(Modifier.fillMaxWidth().clickable(enabled = !u.email.equals(ADMIN_EMAIL, true)) { editing = u },
                    colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    ListItem(
                        headlineContent = { Text(u.name.ifBlank { "بدون اسم" }, fontWeight = FontWeight.Bold) },
                        supportingContent = {
                            val linked = u.studentIds.mapNotNull { id -> state.students.firstOrNull { it.id == id }?.name }
                            Text(u.email + (if (linked.isNotEmpty()) "\nمرتبط بـ: ${linked.joinToString("، ")}" else "") +
                                (if (u.role == Roles.TEACHER && u.groups.isNotEmpty()) "\nالحلقات: ${u.groups.joinToString("، ")}" else ""))
                        },
                        leadingContent = { Icon(Icons.Default.AccountCircle, null, tint = Green) },
                        trailingContent = { Text(Roles.label(u.role), color = Gold, fontWeight = FontWeight.Bold) },
                        colors = ListItemDefaults.colors(containerColor = Color.White)
                    )
                }
            }
        }
    }
    editing?.let { u -> UserDialog(state, u) { editing = null } }
}

@Composable
private fun UserDialog(state: AppState, user: AppUser, close: () -> Unit) {
    var role by remember { mutableStateOf(user.role) }
    var linked by remember { mutableStateOf(user.studentIds.toSet()) }
    var groups by remember { mutableStateOf(user.groups.toSet()) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(user.name.ifBlank { user.email }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("الصفة", fontWeight = FontWeight.Bold)
                Roles.all.filter { it != Roles.ADMIN }.forEach { r ->
                    Row(Modifier.fillMaxWidth().clickable { role = r }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = role == r, onClick = { role = r }); Text(Roles.label(r))
                    }
                }
                if (role == Roles.TEACHER) {
                    Spacer(Modifier.height(8.dp))
                    Text("الحلقات التي يدرّسها", fontWeight = FontWeight.Bold)
                    Text("إن لم تُحدَّد حلقة يرى المعلم جميع الطلاب.", fontSize = 12.sp, color = Color.Gray)
                    if (state.allGroups.isEmpty()) Text("لا توجد حلقات بعد (أضف اسم الحلقة في ملفات الطلاب)", color = Color.Gray, fontSize = 13.sp)
                    state.allGroups.forEach { g ->
                        Row(Modifier.fillMaxWidth().clickable { groups = if (g in groups) groups - g else groups + g },
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = g in groups, onCheckedChange = { c -> groups = if (c) groups + g else groups - g })
                            Text(g)
                        }
                    }
                }
                if (role == Roles.PARENT || role == Roles.STUDENT) {
                    Spacer(Modifier.height(8.dp))
                    Text("ملفات الطلاب المرتبطة", fontWeight = FontWeight.Bold)
                    if (state.students.isEmpty()) Text("لا يوجد طلاب", color = Color.Gray)
                    state.students.forEach { s ->
                        Row(Modifier.fillMaxWidth().clickable { linked = if (s.id in linked) linked - s.id else linked + s.id },
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = s.id in linked, onCheckedChange = { c -> linked = if (c) linked + s.id else linked - s.id })
                            Text(s.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val ids = if (role == Roles.PARENT || role == Roles.STUDENT) linked.toList() else emptyList()
                state.updateUser(user, role, ids, if (role == Roles.TEACHER) groups.toList() else emptyList()); close()
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } }
    )
}

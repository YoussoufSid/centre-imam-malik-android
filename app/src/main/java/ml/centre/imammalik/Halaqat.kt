package ml.centre.imammalik

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val NO_GROUP = "بدون حلقة"

// ---------------- Halaqat list ----------------

@Composable
fun HalaqatScreen(state: AppState, me: AppUser, onBulk: () -> Unit, openProfile: (String) -> Unit) {
    var editing by remember { mutableStateOf<Halaqa?>(null) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val byGroup = state.students.groupBy { it.group }
    val registered = state.halaqat.map { it.name }.toSet()
    // Groups typed in student files but not created as halaqat yet.
    val unregistered = byGroup.keys.filter { it.isNotBlank() && it !in registered }.sorted()
    val ungrouped = byGroup[""].orEmpty()

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 10.dp)) {
        if (me.isAdmin) item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { editing = Halaqa() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("حلقة جديدة")
                }
                OutlinedButton(onClick = onBulk, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.GroupAdd, null); Spacer(Modifier.width(4.dp)); Text("إضافة طلاب", maxLines = 1)
                }
            }
        }
        if (state.halaqat.isEmpty() && unregistered.isEmpty()) item {
            EmptyState(Icons.Default.Diversity3, if (me.isAdmin) "لا توجد حلقات بعد\nأنشئ أول حلقة بزر «حلقة جديدة»" else "لا توجد حلقات بعد")
        }
        items(state.halaqat, key = { it.id }) { h ->
            val members = byGroup[h.name].orEmpty().sortedBy { it.name }
            HalaqaCard(h.name, h.teacher, h.schedule, members, expanded == h.name,
                onToggle = { expanded = if (expanded == h.name) null else h.name },
                onEdit = if (me.isAdmin) ({ editing = h }) else null, openProfile = openProfile)
        }
        if (unregistered.isNotEmpty()) {
            item { Text("حلقات مكتوبة في ملفات الطلاب ولم تُسجَّل بعد", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                color = Color.Gray, modifier = Modifier.padding(top = 8.dp)) }
            items(unregistered, key = { "u_$it" }) { g ->
                val members = byGroup[g].orEmpty().sortedBy { it.name }
                HalaqaCard(g, members.firstOrNull { it.teacher.isNotBlank() }?.teacher ?: "", "", members, expanded == g,
                    onToggle = { expanded = if (expanded == g) null else g },
                    onEdit = if (me.isAdmin) ({ editing = Halaqa(name = g, teacher = members.firstOrNull()?.teacher ?: "") }) else null,
                    editLabel = "تسجيل", openProfile = openProfile)
            }
        }
        if (ungrouped.isNotEmpty()) item {
            HalaqaCard(NO_GROUP, "", "حدّد الحلقة من «تعديل ملف الطالب»", ungrouped.sortedBy { it.name }, expanded == NO_GROUP,
                onToggle = { expanded = if (expanded == NO_GROUP) null else NO_GROUP }, onEdit = null, openProfile = openProfile)
        }
    }
    editing?.let { h -> HalaqaDialog(state, h) { editing = null } }
}

@Composable
private fun HalaqaCard(
    name: String, teacher: String, schedule: String, members: List<Student>, expanded: Boolean,
    onToggle: () -> Unit, onEdit: (() -> Unit)?, editLabel: String? = null, openProfile: (String) -> Unit
) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardBg)) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(name, 44.dp); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.Bold)
                    if (teacher.isNotBlank()) Text("المعلم: $teacher", fontSize = 13.sp, color = SubText)
                    if (schedule.isNotBlank()) Text(schedule, fontSize = 12.sp, color = Color.Gray)
                }
                Text("${members.size} طالب", color = Green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (onEdit != null) {
                    if (editLabel != null) TextButton(onClick = onEdit) { Text(editLabel) }
                    else IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "تعديل", tint = Color.Gray) }
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Color.Gray)
            }
            if (expanded) {
                HorizontalDivider()
                if (members.isEmpty()) Text("لا يوجد طلاب في هذه الحلقة", Modifier.padding(12.dp), color = Color.Gray)
                members.forEach { s ->
                    Row(Modifier.fillMaxWidth().clickable { openProfile(s.id) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Avatar(s.name, 30.dp); Spacer(Modifier.width(8.dp))
                        Text(s.name, Modifier.weight(1f), fontSize = 14.sp)
                        Text("${s.hizbs}/$TOTAL_HIZBS", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
private fun HalaqaDialog(state: AppState, initial: Halaqa, close: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var teacher by remember { mutableStateOf(initial.teacher) }
    var schedule by remember { mutableStateOf(initial.schedule) }
    var notes by remember { mutableStateOf(initial.notes) }
    var confirmDelete by remember { mutableStateOf(false) }
    val teachers = state.users.filter { it.role == Roles.TEACHER }.map { it.name }.filter { it.isNotBlank() }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (initial.id.isBlank()) "حلقة جديدة" else "تعديل الحلقة") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AppField(name, { name = it }, "اسم الحلقة (مثال: حلقة أبي بكر)")
                AppField(teacher, { teacher = it }, "المعلم أو المعلمة")
                if (teachers.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    teachers.forEach { t -> FilterChip(teacher == t, { teacher = t }, { Text(t) }) }
                }
                AppField(schedule, { schedule = it }, "المواعيد (مثال: السبت–الأربعاء 16:00–18:00)")
                AppField(notes, { notes = it }, "ملاحظات (المستوى، القاعة...)")
                if (initial.id.isNotBlank() && initial.name != name.trim() && name.isNotBlank())
                    Text("سيُحدَّث اسم الحلقة في ملفات طلابها تلقائيًا.", fontSize = 12.sp, color = Gold)
                if (initial.id.isNotBlank()) TextButton(onClick = { confirmDelete = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("حذف الحلقة") }
            }
        },
        confirmButton = { Button(onClick = { state.saveHalaqa(initial.copy(name = name, teacher = teacher, schedule = schedule, notes = notes), close) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } }
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text("حذف الحلقة") },
        text = { Text("هل تريد حذف «${initial.name}»؟ لن يُحذف أي طالب، وتبقى ملفاتهم كما هي.") },
        confirmButton = { Button(onClick = { state.deleteHalaqa(initial); confirmDelete = false; close() },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("حذف") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("تراجع") } }
    )
}

/** Halaqa field for the student form: pick from the list, or type a new name. */
@Composable
fun GroupField(state: AppState, value: String, onChange: (String) -> Unit, onTeacher: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    OutlinedTextField(value, onChange, label = { Text("الحلقة") }, singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        trailingIcon = { IconButton(onClick = { picking = true }) { Icon(Icons.Default.ArrowDropDown, "اختيار الحلقة", tint = Green) } })
    if (picking) PickerDialog("اختر الحلقة", state.allGroups.map { it to it } + (NO_GROUP to ""), { picking = false }) { g ->
        onChange(g); picking = false
        state.halaqat.firstOrNull { it.name == g }?.teacher?.takeIf { it.isNotBlank() }?.let(onTeacher)
    }
}

// ---------------- Bulk add ----------------

@Composable
fun BulkAddScreen(state: AppState, close: () -> Unit) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf(state.halaqat.firstOrNull()?.name ?: "") }
    var gender by rememberSaveable { mutableStateOf("") }
    var fee by rememberSaveable { mutableStateOf("") }
    var regFee by rememberSaveable { mutableStateOf("") }
    val parsed = remember(text) { parseStudentLines(text) }
    val existing = state.students.filter { it.group == group }.map { it.name.trim() }.toSet()
    val newOnes = parsed.filter { it.name !in existing }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()?.let { content -> text = (text.trimEnd() + "\n" + content).trim() }
            ?: run { state.message = "تعذّرت قراءة الملف" }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("1. اختر الحلقة", fontWeight = FontWeight.Bold, color = Green)
        val groups = state.allGroups
        if (groups.isEmpty()) Text("لا توجد حلقات؛ أنشئ حلقة أولًا من شاشة «الحلقات» أو اترك الطلاب بلا حلقة.", fontSize = 13.sp, color = Color.Gray)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            groups.forEach { g -> FilterChip(group == g, { group = g }, { Text(g) }) }
            FilterChip(group.isBlank(), { group = "" }, { Text(NO_GROUP) })
        }

        Text("2. الصق الأسماء (اسم في كل سطر)", fontWeight = FontWeight.Bold, color = Green)
        Text("يمكن نسخ عمودَي «الاسم» و«الهاتف» من Excel ولصقهما مباشرة، أو كتابة: الاسم ; الهاتف", fontSize = 12.sp, color = Color.Gray)
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), minLines = 6, maxLines = 14,
            placeholder = { Text("أحمد محمد سيسي ; 76123456\nفاطمة علي تراوري\n...") })
        OutlinedButton(onClick = { picker.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "application/csv", "*/*")) },
            modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.UploadFile, null); Spacer(Modifier.width(6.dp)); Text("أو اختر ملف CSV")
        }

        Text("3. بيانات مشتركة (اختياري)", fontWeight = FontWeight.Bold, color = Green)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("الجنس:")
            listOf("ذكر", "أنثى").forEach { g -> RadioButton(gender == g, { gender = if (gender == g) "" else g }); Text(g) }
        }
        if (state.profile?.isAdmin == true) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(fee, { v -> fee = v.filter { it.isDigit() }.take(7) }, Modifier.weight(1f), singleLine = true,
                label = { Text("الرسوم الشهرية") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(regFee, { v -> regFee = v.filter { it.isDigit() }.take(7) }, Modifier.weight(1f), singleLine = true,
                label = { Text("رسوم التسجيل") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }

        if (parsed.isNotEmpty()) Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardBg)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("معاينة: ${newOnes.size} طالب جديد" + if (parsed.size > newOnes.size) " • ${parsed.size - newOnes.size} موجود مسبقًا (سيُتخطّى)" else "",
                    fontWeight = FontWeight.Bold)
                parsed.take(12).forEach { p ->
                    val dup = p.name in existing
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (dup) Icons.Default.Block else Icons.Default.CheckCircle, null,
                            tint = if (dup) Color.Gray else Green, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(p.name, Modifier.weight(1f), fontSize = 14.sp, color = if (dup) Color.Gray else Color.Unspecified)
                        if (p.phone.isNotBlank()) Text(ltr(p.phone), fontSize = 12.sp, color = Color.Gray)
                    }
                }
                if (parsed.size > 12) Text("... و${parsed.size - 12} آخرون", fontSize = 12.sp, color = Color.Gray)
            }
        }
        Button(onClick = {
            state.bulkAddStudents(parsed, group, gender, fee.toIntOrNull() ?: 0, regFee.toIntOrNull() ?: 0) { close() }
        }, enabled = newOnes.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Icon(Icons.Default.GroupAdd, null); Spacer(Modifier.width(6.dp))
            Text(if (newOnes.isEmpty()) "أدخل الأسماء أولًا" else "إضافة ${newOnes.size} طالب" + if (group.isNotBlank()) " إلى «$group»" else "")
        }
    }
}

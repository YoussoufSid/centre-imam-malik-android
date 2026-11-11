package ml.centre.imammalik

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =====================================================================
// «الحفظ» tab: fast daily entry by halaqa, the log list, and period reports
// (daily / weekly / monthly / term / yearly).
// =====================================================================

@Composable
fun QuranScreen(state: AppState, openProfile: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    if (state.memoRequest != null && tab != 2) tab = 2   // opened from the home follow-up card
    val tabs = listOf("تسجيل سريع", "السجل", "التقارير")
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab, containerColor = CardBg) {
            tabs.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t, fontSize = 14.sp) }) }
        }
        when (tab) {
            0 -> QuickSheet(state, openProfile)
            1 -> LogListTab(state, openProfile)
            else -> MemoReportTab(state, openProfile)
        }
    }
}

private fun activeGroups(state: AppState) =
    state.students.filter { it.status == "نشط" }.map { it.group }.filter { it.isNotBlank() }.distinct().sorted()

@Composable
private fun GroupChips(groups: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected == "", { onSelect("") }, { Text("كل الحلقات") })
        groups.forEach { g -> FilterChip(selected == g, { onSelect(g) }, { Text(g, maxLines = 1) }) }
    }
}

private fun describe(l: QuranLog?): String = l?.let {
    "سورة ${it.surah}" + (if (it.fromAyah > 0) " ${ltr("${it.fromAyah}–${it.toAyah}")}" else "") + " • ${ltr(it.date.takeLast(5))}"
} ?: "—"

// ---------------- Fast daily entry ----------------

@Composable
private fun QuickSheet(state: AppState, openProfile: (String) -> Unit) {
    val groups = activeGroups(state)
    var group by rememberSaveable { mutableStateOf(groups.firstOrNull() ?: "") }
    var date by rememberSaveable { mutableStateOf(today()) }
    var history by remember { mutableStateOf(emptyList<QuranLog>()) }
    LaunchedEffect(Unit) { state.loadLogs(daysBefore(today(), 365), today()) { history = it } }
    val all = (state.recentLogs + history).distinctBy { it.id }
    val order = compareBy<QuranLog> { it.date }.thenBy { it.createdAt }
    val lastNew = all.filter { it.type == LogTypes.NEW }.groupBy { it.studentId }.mapValues { (_, l) -> l.maxWith(order) }
    val lastRev = all.filter { it.type == LogTypes.REVIEW }.groupBy { it.studentId }.mapValues { (_, l) -> l.maxWith(order) }
    val onDate = all.filter { it.date == date }.groupBy { it.studentId }
    val students = state.students.filter { it.status == "نشط" && (group == "" || it.group == group) }.sortedBy { it.name }
    var entry by remember { mutableStateOf<Pair<Student, String>?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        GroupChips(groups, group) { group = it }
        DateField(date, { date = it }, "تاريخ التسميع")
        val done = students.count { onDate[it.id] != null }
        Text("سُجّل لـ $done من ${students.size} طالب • اضغط «حفظ» أو «مراجعة» ثم التقدير",
            fontSize = 13.sp, color = SubText, modifier = Modifier.padding(vertical = 4.dp))
        LinearProgressIndicator(progress = { if (students.isEmpty()) 0f else done.toFloat() / students.size },
            modifier = Modifier.fillMaxWidth(), color = Green)
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(students, key = { it.id }) { s ->
                val today = onDate[s.id].orEmpty()
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardBg)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StudentAvatar(s, 36.dp); Spacer(Modifier.width(8.dp))
                            Text(s.name, Modifier.weight(1f).clickable { openProfile(s.id) }, fontWeight = FontWeight.Bold)
                            if (today.isNotEmpty()) Icon(Icons.Default.CheckCircle, "سُجّل", tint = Green)
                        }
                        Text("آخر حفظ: ${describe(lastNew[s.id])}", fontSize = 12.sp, color = SubText)
                        Text("آخر مراجعة: ${describe(lastRev[s.id])}", fontSize = 12.sp, color = SubText)
                        today.forEach { l ->
                            Text("✓ ${l.type}: سورة ${l.surah}" + (if (l.fromAyah > 0) " ${ltr("${l.fromAyah}–${l.toAyah}")}" else "") +
                                (if (l.grade.isNotBlank()) " — ${l.grade}" else ""), fontSize = 12.sp, color = Green)
                        }
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { entry = s to LogTypes.NEW }, Modifier.weight(1f)) {
                                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Text(" حفظ")
                            }
                            OutlinedButton(onClick = { entry = s to LogTypes.REVIEW }, Modifier.weight(1f)) {
                                Icon(Icons.Default.Replay, null, Modifier.size(18.dp)); Text(" مراجعة")
                            }
                        }
                    }
                }
            }
        }
    }
    entry?.let { (s, type) ->
        QuickEntryDialog(state, s, type, date, nextPosition(if (type == LogTypes.NEW) lastNew[s.id] else lastRev[s.id])) { entry = null }
    }
}

/** Two taps: the position is pre-filled from the last record; tapping a grade saves. */
@Composable
private fun QuickEntryDialog(state: AppState, s: Student, type: String, date: String, suggestion: Position?, close: () -> Unit) {
    var surah by remember { mutableStateOf(suggestion?.surah ?: "") }
    var from by remember { mutableStateOf(suggestion?.from?.takeIf { it > 0 }?.toString() ?: "") }
    var to by remember { mutableStateOf(suggestion?.to?.takeIf { it > 0 }?.toString() ?: "") }
    var notes by remember { mutableStateOf("") }
    var pickSurah by remember { mutableStateOf(false) }
    val max = Surahs.firstOrNull { it.first == surah }?.second
    fun save(grade: String) = state.saveLog(QuranLog(studentId = s.id, studentName = s.name, date = date, type = type, surah = surah,
        fromAyah = from.toIntOrNull() ?: 0, toAyah = to.toIntOrNull() ?: 0, grade = grade, notes = notes), close)
    AlertDialog(
        onDismissRequest = close,
        title = { Text("$type — ${s.name}", fontSize = 18.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickSurah = true }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.MenuBook, null); Spacer(Modifier.width(6.dp))
                    Text(if (surah.isBlank()) "اختر السورة" else "سورة $surah" + (max?.let { " ($it آية)" } ?: ""))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumberField(from, { from = it }, "من آية", Modifier.weight(1f))
                    NumberField(to, { to = it }, "إلى آية", Modifier.weight(1f))
                }
                if (max != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip({ from = "1"; to = max.toString() }, { Text("السورة كاملة") })
                    AssistChip({ val t = (to.toIntOrNull() ?: 0) + 1; if (t <= max) to = t.toString() }, { Text("+1 آية") })
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظة (اختياري)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("اضغط التقدير للحفظ:", fontWeight = FontWeight.Bold)
                Grades.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { g ->
                            Button(onClick = { save(g) }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(
                                containerColor = when (gradeScore(g)) { 5, 4 -> Green; 3 -> Color(0xFF3B7DD8); 2 -> Color(0xFFB7791F); else -> Color(0xFFC53030) })
                            ) { Text(g, maxLines = 1) }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } }
    )
    if (pickSurah) PickerDialog("اختر السورة", Surahs.mapIndexed { i, x -> "${i + 1}. ${x.first} (${x.second})" to x.first }, { pickSurah = false }) {
        surah = it; from = "1"; to = ""; pickSurah = false
    }
}

// ---------------- Log list (previous screen) ----------------

@Composable
private fun LogListTab(state: AppState, openProfile: (String) -> Unit) {
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

// ---------------- Period reports ----------------

@Composable
private fun MemoReportTab(state: AppState, openProfile: (String) -> Unit) {
    val context = LocalContext.current
    val groups = activeGroups(state)
    var period by rememberSaveable { mutableStateOf(LogPeriod.WEEK) }
    var anchor by rememberSaveable { mutableStateOf(today()) }
    var group by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.memoRequest) { state.memoRequest?.let { p -> period = p; anchor = today(); group = ""; state.memoRequest = null } }
    val range = periodRange(period, anchor, state::termsFor)
    var logs by remember { mutableStateOf<List<QuranLog>?>(null) }
    LaunchedEffect(range.from, range.to, state.recentLogs.size) { logs = null; state.loadLogs(range.from, range.to) { logs = it } }
    val students = state.students.filter { it.status == "نشط" && (group == "" || it.group == group) }
    val ids = students.map { it.id }.toSet()
    val inScope = logs.orEmpty().filter { it.studentId in ids }
    val rows = summarizeMemorization(students, inScope).sortedWith(compareBy<MemoRow> { if (group == "") it.student.group.ifBlank { "\uFFFF" } else "" }
        .thenByDescending { it.newAyat }.thenByDescending { it.sessions })
    val byStudent = inScope.groupBy { it.studentId }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LogPeriod.values().forEach { p -> FilterChip(period == p, { period = p }, { Text(p.label) }) }
            }
        }
        item { GroupChips(groups, group) { group = it } }
        item { MonthSwitcher(range.label, range.to < today(), { anchor = shiftPeriod(period, anchor, -1, state::termsFor) }, { anchor = shiftPeriod(period, anchor, 1, state::termsFor) }) }
        if (logs == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (period == LogPeriod.DAY) eventsOn(state.eventsFor(academicStart(anchor)), anchor).forEach { e ->
            item { Text("${EventTypes.icon(e.type)} ${e.type}: ${e.title}", color = Color(0xFFB7791F), fontWeight = FontWeight.Bold) }
        }
        val halaqat = summarizeByHalaqa(rows, inScope)
        if (group == "" && halaqat.size > 1) {
            item { SectionHeading("ملخص جميع الحلقات") }
            items(halaqat, key = { "h_" + it.group }) { h ->
                Card(Modifier.fillMaxWidth().clickable { group = if (h.group == "بدون حلقة") "" else h.group },
                    colors = CardDefaults.cardColors(containerColor = CardBg)) {
                    Column(Modifier.padding(12.dp)) {
                        Row { Text(h.group, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Green)
                            h.avgGrade?.let { Text(gradeFromScore(it), fontWeight = FontWeight.Bold, color = Green, fontSize = 13.sp) } }
                        Text("سُجّل لـ ${h.recorded} من ${h.students} طالب • حفظ ${h.newAyat} آية • مراجعة ${h.reviewAyat} آية • ${h.sessions} جلسة",
                            fontSize = 13.sp, color = SubText)
                        LinearProgressIndicator(progress = { if (h.students == 0) 0f else h.recorded.toFloat() / h.students },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp), color = Green)
                    }
                }
            }
            item { SectionHeading("تفاصيل الطلاب") }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("آيات الحفظ", rows.sumOf { it.newAyat }.toString(), Icons.Default.MenuBook, Modifier.weight(1f), Green)
                StatCard("آيات المراجعة", rows.sumOf { it.reviewAyat }.toString(), Icons.Default.Replay, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("جلسات التسميع", rows.sumOf { it.sessions }.toString(), Icons.Default.EventNote, Modifier.weight(1f))
                StatCard("طلاب بلا تسجيل", rows.count { it.sessions == 0 }.toString(), Icons.Default.PersonOff, Modifier.weight(1f), Color(0xFFC53030))
            }
        }
        item {
            Button(onClick = { shareMemoReport(context, period, range, group, rows, if (period == LogPeriod.DAY) inScope else emptyList(), summarizeByHalaqa(rows, inScope)) },
                Modifier.fillMaxWidth()) { Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(6.dp)); Text("تقرير PDF للطباعة أو الإرسال") }
        }
        items(rows, key = { it.student.id }) { r ->
            Card(Modifier.fillMaxWidth().clickable { openProfile(r.student.id) }, colors = CardDefaults.cardColors(containerColor = CardBg)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StudentAvatar(r.student, 32.dp); Spacer(Modifier.width(8.dp))
                        Text(r.student.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        r.avgGrade?.let { Text(gradeFromScore(it), color = Green, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    }
                    if (r.sessions == 0) Text("لم يُسجَّل حفظ ولا مراجعة في هذه الفترة", fontSize = 13.sp, color = Color(0xFFC53030))
                    else {
                        Text("حفظ جديد: ${r.newSessions} جلسة • ${r.newAyat} آية" +
                            (if (r.surahs.isNotEmpty()) " • ${r.surahs.joinToString("، ")}" else ""), fontSize = 13.sp)
                        Text("مراجعة: ${r.reviewSessions} جلسة • ${r.reviewAyat} آية • أيام الحضور للتسميع: ${r.days}", fontSize = 13.sp, color = SubText)
                        if (period == LogPeriod.DAY) byStudent[r.student.id].orEmpty().forEach { l ->
                            Text("• ${l.type}: سورة ${l.surah}" + (if (l.fromAyah > 0) " ${ltr("${l.fromAyah}–${l.toAyah}")}" else "") +
                                (if (l.grade.isNotBlank()) " — ${l.grade}" else "") + (if (l.notes.isNotBlank()) " (${l.notes})" else ""),
                                fontSize = 12.sp, color = Green)
                        }
                    }
                }
            }
        }
        if (rows.isEmpty()) item { Text("لا يوجد طلاب في هذه الحلقة", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Color.Gray) }
    }
}

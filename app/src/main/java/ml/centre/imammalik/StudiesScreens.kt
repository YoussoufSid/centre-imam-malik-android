package ml.centre.imammalik

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o -> FilterChip(selected == o, { onSelect(o) }, { Text(o) }) }
    }
}

@Composable
private fun ScoreField(value: String, change: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value, { v -> change(v.filter { it.isDigit() || it == '.' || it == ',' }.take(5)) },
        modifier = modifier, singleLine = true, placeholder = { Text("/$MAX_SCORE") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

/** Full-screen entry of one subject's grades for a whole group at once (staff). */
@Composable
fun GradeEntryScreen(state: AppState, close: () -> Unit) {
    var subject by rememberSaveableString(Subjects.first())
    var term by rememberSaveableString(Terms.first())
    var date by rememberSaveableString(today())
    var group by remember { mutableStateOf<String?>(null) }
    val scores = remember { mutableStateMapOf<String, String>() }
    val groups = state.students.map { it.group }.filter { it.isNotBlank() }.distinct().sorted()
    val shown = state.students.filter { it.status == "نشط" && (group == null || it.group == group) }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Text("المادة", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        ChipRow(Subjects, subject) { subject = it }
        Text("الفصل", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        ChipRow(Terms, term) { term = it }
        if (groups.isNotEmpty()) {
            Text("الحلقة", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(group == null, { group = null }, { Text("الكل") })
                groups.forEach { g -> FilterChip(group == g, { group = g }, { Text(g) }) }
            }
        }
        DateField(date, { date = it }, "تاريخ الاختبار", modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
        Text("اكتب درجة كل طالب من $MAX_SCORE، واترك الخانة فارغة لمن لم يُختبر.", fontSize = 12.sp, color = Color.Gray)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(shown, key = { it.id }) { s ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        ScoreField(scores[s.id] ?: "", { scores[s.id] = it }, Modifier.width(96.dp))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = close, modifier = Modifier.weight(1f)) { Text("إلغاء") }
            Button(onClick = {
                state.saveGrades(subject, term, date.trim().ifBlank { today() },
                    shown.map { it to (scores[it.id] ?: "") }.filter { it.second.isNotBlank() }, done = close)
            }, modifier = Modifier.weight(2f)) {
                Icon(Icons.Default.Save, null); Spacer(Modifier.width(6.dp))
                Text("حفظ الدرجات (${shown.count { !scores[it.id].isNullOrBlank() }})")
            }
        }
    }
}

@Composable
private fun rememberSaveableString(initial: String) =
    androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initial) }

/** Islamic studies results in the student profile. */
@Composable
fun StudentGradesCard(state: AppState, me: AppUser, student: Student, grades: List<Grade>) {
    var adding by remember { mutableStateOf(false) }
    var term by remember { mutableStateOf<String?>(null) }
    val shown = grades.filter { term == null || it.term == term }
    val avg = if (shown.isEmpty()) null else shown.sumOf { it.score } / shown.size
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.School, null, tint = Gold); Spacer(Modifier.width(8.dp))
                Text("الدراسات الإسلامية", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (avg != null) Text("المعدل ${scoreText(avg)}/$MAX_SCORE • ${mention(avg)}", color = Green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            val terms = grades.map { it.term }.distinct()
            if (terms.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(term == null, { term = null }, { Text("كل الفصول") })
                Terms.filter { it in terms }.forEach { t -> FilterChip(term == t, { term = t }, { Text(t) }) }
            }
            if (shown.isEmpty()) Text("لا توجد درجات مسجلة بعد", color = Color.Gray)
            Subjects.filter { s -> shown.any { it.subject == s } }.forEach { subject ->
                val list = shown.filter { it.subject == subject }
                val a = list.sumOf { it.score } / list.size
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(subject, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text("${scoreText(a)}/$MAX_SCORE", color = if (a >= 10) Green else Color(0xFFC53030), fontWeight = FontWeight.Bold)
                }
                list.forEach { g ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${g.term} • ${ltr(g.date)}" + if (g.notes.isNotBlank()) " • ${g.notes}" else "",
                            Modifier.weight(1f), fontSize = 12.sp, color = Color.Gray)
                        Text(scoreText(g.score), fontSize = 13.sp)
                        if (me.isAdmin) IconButton(onClick = { state.deleteGrade(g) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, "حذف", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            if (me.isStaff) TextButton(onClick = { adding = true }) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("إضافة درجة")
            }
        }
    }
    if (adding) GradeDialog(state, student) { adding = false }
}

@Composable
private fun GradeDialog(state: AppState, student: Student, close: () -> Unit) {
    var subject by remember { mutableStateOf(Subjects.first()) }
    var term by remember { mutableStateOf(Terms.first()) }
    var score by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today()) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("درجة جديدة — ${student.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("المادة", fontWeight = FontWeight.Bold); ChipRow(Subjects, subject) { subject = it }
                Text("الفصل", fontWeight = FontWeight.Bold); ChipRow(Terms, term) { term = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("الدرجة من $MAX_SCORE", Modifier.weight(1f))
                    ScoreField(score, { score = it }, Modifier.width(110.dp))
                }
                DateField(date, { date = it }, "التاريخ")
                AppField(notes, { notes = it }, "ملاحظة (اختياري)")
            }
        },
        confirmButton = { Button(onClick = {
            if (score.isBlank()) state.message = "أدخل الدرجة"
            else state.saveGrades(subject, term, date.trim().ifBlank { today() }, listOf(student to score), notes, close)
        }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } }
    )
}

/** Lines added to the monthly report for grades recorded in that month. */
fun gradesReportLines(grades: List<Grade>, month: String): List<String> {
    val g = grades.filter { it.date.startsWith(month) }
    if (g.isEmpty()) return emptyList()
    val avg = g.sumOf { it.score } / g.size
    return listOf("🎓 الدراسات الإسلامية") +
        g.groupBy { it.subject }.map { (s, l) -> "$s: ${scoreText(l.sumOf { it.score } / l.size)}/$MAX_SCORE" } +
        "المعدل: ${scoreText(avg)}/$MAX_SCORE (${mention(avg)})"
}

package ml.centre.imammalik

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Red = Color(0xFFC53030)

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeading(title)
            content()
        }
    }
}

/** Horizontal bar with label and value, value in 0..max. */
@Composable
private fun HBar(label: String, value: Float, max: Float, valueText: String, color: Color = Green) {
    Column {
        Row { Text(label, Modifier.weight(1f), fontSize = 13.sp); Text(valueText, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(color.copy(alpha = .12f))) {
            val f = if (max <= 0f) 0f else (value / max).coerceIn(0f, 1f)
            if (f > 0f) Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(color))
        }
    }
}

/** Simple vertical bar chart (one bar per recorded day). */
@Composable
private fun DailyChart(daily: List<Pair<String, Int>>) {
    val max = daily.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        daily.forEach { (_, v) ->
            Box(Modifier.weight(1f).fillMaxHeight(v / max.toFloat()).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                .background(Green))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Text(daily.firstOrNull()?.first?.takeLast(2)?.let { "يوم $it" } ?: "", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.weight(1f))
        Text("الأعلى: $max", fontSize = 11.sp, color = Color.Gray)
        Text(daily.lastOrNull()?.first?.takeLast(2)?.let { "يوم $it" } ?: "", fontSize = 11.sp, color = Color.Gray,
            modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
}

@Composable
fun StatsScreen(state: AppState, openProfile: (String) -> Unit) {
    var month by rememberSaveable { mutableStateOf(currentMonth()) }
    var data by remember { mutableStateOf<Pair<List<AttendanceRecord>, List<QuranLog>>?>(null) }
    LaunchedEffect(month, state.students.size) {
        data = null
        state.loadMonthStats(month) { a, l -> data = a to l }
    }
    val stats = data?.let { (a, l) -> computeMonthStats(state.students, month, a, l) }
    val context = LocalContext.current

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            MonthSwitcher("إحصائيات ${monthName(month)}", month < currentMonth(),
                { month = shiftMonth(month, -1) }, { month = shiftMonth(month, 1) })
        }
        if (stats == null) { item { LinearProgressIndicator(Modifier.fillMaxWidth()) }; return@LazyColumn }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("نسبة الحضور", stats.attendanceRate?.let { "$it٪" } ?: "—", Icons.Default.FactCheck, Modifier.weight(1f), Green)
                StatCard("طلاب نشطون", stats.activeStudents.toString(), Icons.Default.Groups, Modifier.weight(1f))
                StatCard("مسجّلون جدد", stats.newEnrollments.toString(), Icons.Default.PersonAdd, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("حفظ جديد", stats.newSessions.toString(), Icons.Default.MenuBook, Modifier.weight(1f), Green)
                StatCard("مراجعة", stats.reviewSessions.toString(), Icons.Default.Replay, Modifier.weight(1f))
                StatCard("متوسط الأحزاب", scoreText(stats.avgHizbs), Icons.Default.AutoStories, Modifier.weight(1f))
            }
        }

        if (stats.atRisk.isNotEmpty()) item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F0))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = Red); Spacer(Modifier.width(8.dp))
                        Text("طلاب يحتاجون متابعة (${stats.atRisk.size})", fontWeight = FontWeight.Bold, color = Red)
                    }
                    stats.atRisk.take(15).forEach { r ->
                        Row(Modifier.fillMaxWidth().clickable { openProfile(r.student.id) }, verticalAlignment = Alignment.CenterVertically) {
                            Avatar(r.student.name, 32.dp); Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.student.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text(r.reason, fontSize = 12.sp, color = Red)
                            }
                            val wa = whatsappNumber(r.student.phone)
                            if (wa != null) IconButton(onClick = {
                                val msg = "السلام عليكم ورحمة الله،\nنودّ إعلامكم بأن الطالب ${r.student.name}: ${r.reason}.\n" +
                                    "نرجو التواصل مع المركز للمتابعة، جزاكم الله خيرًا.\n${Centre.name}"
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$wa?text=" + Uri.encode(msg)))) }
                            }) { Icon(Icons.Default.Chat, "مراسلة ولي الأمر", tint = Green) }
                        }
                    }
                }
            }
        }

        if (stats.daily.isNotEmpty()) item {
            SectionCard("الحضور اليومي (${stats.recordedDays} يومًا مسجلًا)") { DailyChart(stats.daily) }
        }
        if (stats.groups.isNotEmpty()) item {
            SectionCard("الحضور حسب الحلقة") {
                stats.groups.forEach { g ->
                    HBar("${g.group} (${g.students})", (g.attendanceRate ?: 0).toFloat(), 100f,
                        g.attendanceRate?.let { "$it٪" } ?: "—", if ((g.attendanceRate ?: 100) < 60) Red else Green)
                }
            }
        }
        if (stats.groups.isNotEmpty()) item {
            SectionCard("متوسط الأحزاب المحفوظة حسب الحلقة") {
                stats.groups.forEach { g -> HBar(g.group, g.avgHizbs.toFloat(), TOTAL_HIZBS.toFloat(), scoreText(g.avgHizbs), Gold) }
            }
        }
        item {
            SectionCard("توزيع الطلاب حسب مقدار الحفظ" + if (stats.khatmCount > 0) " • ${stats.khatmCount} خاتم 🎉" else "") {
                val max = stats.hizbBuckets.maxOfOrNull { it.second }?.toFloat() ?: 0f
                stats.hizbBuckets.forEach { (l, n) -> HBar("$l حزبًا", n.toFloat(), max, n.toString(), Gold) }
            }
        }
        if (stats.topReciters.isNotEmpty()) item {
            SectionCard("الأكثر تسميعًا هذا الشهر") {
                stats.topReciters.forEachIndexed { i, (s, n) ->
                    Row(Modifier.fillMaxWidth().clickable { openProfile(s.id) }, verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", Modifier.width(24.dp), fontWeight = FontWeight.Bold, color = Gold)
                        Avatar(s.name, 30.dp); Spacer(Modifier.width(8.dp))
                        Text(s.name, Modifier.weight(1f))
                        Text("$n جلسة", color = Green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
        if (stats.daily.isEmpty() && stats.newSessions + stats.reviewSessions == 0) item {
            EmptyState(Icons.Default.Insights, "لا توجد بيانات حضور أو تسميع لهذا الشهر بعد")
        }
    }
}

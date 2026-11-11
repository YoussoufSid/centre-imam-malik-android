package ml.centre.imammalik

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Academic calendar: 4 terms (start/end set by the administration), review periods, exams and holidays. */
@Composable
fun CalendarScreen(state: AppState, me: AppUser) {
    var start by rememberSaveable { mutableIntStateOf(academicStart(today())) }
    val saved = state.calendars[start]
    val terms = remember(start, saved) { mutableStateListOf(*state.termsFor(start).toTypedArray()) }
    val events = remember(start, saved) { mutableStateListOf(*state.eventsFor(start).toTypedArray()) }
    val offDays = remember(start, saved) { mutableStateListOf(*state.offDaysFor(start).toTypedArray()) }
    var editing by remember { mutableStateOf<CalEvent?>(null) }
    val admin = me.isAdmin
    val now = today()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MonthSwitcher("العام الدراسي ${ltr("$start / ${start + 1}")}", true, { start-- }, { start++ })
        if (saved == null) Text("لم تُحدِّد الإدارة تواريخ الفصول لهذا العام بعد؛ تظهر تواريخ افتراضية." +
            (if (admin) " عدّلها ثم اضغط «حفظ التقويم»." else ""), fontSize = 13.sp, color = Color(0xFFB7791F))
        SectionHeading("أيام العطلة الأسبوعية")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WeekDayNames.forEachIndexed { i, d -> val iso = i + 1
                FilterChip(iso in offDays, { if (admin) { if (iso in offDays) offDays.remove(iso) else offDays.add(iso) } }, { Text(d) }) }
        }
        Text("أيام الدراسة: " + WeekDayNames.filterIndexed { i, _ -> (i + 1) !in offDays }.joinToString("، "), fontSize = 13.sp, color = SubText)
        SectionHeading("الفصول الدراسية الأربعة")
        terms.forEachIndexed { i, t ->
            val current = now >= t.from && now <= t.to
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (current) Green.copy(alpha = .12f) else CardBg)) {
                Column(Modifier.padding(12.dp)) {
                    Text(t.name + if (current) "  • الفصل الحالي" else "", fontWeight = FontWeight.Bold, color = Green)
                    if (admin) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { DateField(t.from, { terms[i] = t.copy(from = it) }, "البداية") }
                        Box(Modifier.weight(1f)) { DateField(t.to, { terms[i] = t.copy(to = it) }, "النهاية") }
                    } else Text("من ${ltr(t.from)} إلى ${ltr(t.to)}", fontSize = 14.sp)
                }
            }
        }
        SectionHeading("المراجعة والاختبارات والعطل")
        if (events.isEmpty()) Text("لا توجد مواعيد مسجلة", color = Color.Gray, fontSize = 13.sp)
        events.sortedBy { it.from }.forEach { e ->
            val active = now >= e.from && now <= e.to.ifBlank { e.from }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                containerColor = if (e.type == EventTypes.HOLIDAY) Gold.copy(alpha = .15f) else CardBg)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(EventTypes.icon(e.type), fontSize = 22.sp); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.title.ifBlank { e.type }, fontWeight = FontWeight.Bold)
                        Text("${e.type} • " + (if (e.to.isBlank() || e.to == e.from) ltr(e.from) else "من ${ltr(e.from)} إلى ${ltr(e.to)}") +
                            if (active) " • جارٍ الآن" else "", fontSize = 13.sp, color = SubText)
                    }
                    if (admin) IconButton(onClick = { editing = e }) { Icon(Icons.Default.Edit, "تعديل", tint = Green) }
                }
            }
        }
        if (admin) {
            OutlinedButton(onClick = { editing = CalEvent("", EventTypes.REVIEW, "", now, now) }, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null); Text(" إضافة موعد (مراجعة / اختبارات / عطلة)")
            }
            Button(onClick = { state.saveCalendar(start, terms.toList(), events.toList(), offDays.toList()) }, Modifier.fillMaxWidth().height(50.dp)) {
                Icon(Icons.Default.Save, null); Spacer(Modifier.width(6.dp)); Text("حفظ التقويم")
            }
        }
    }
    editing?.let { e ->
        EventDialog(e, onDelete = if (e.id.isBlank()) null else ({ events.removeAll { it.id == e.id }; editing = null }),
            close = { editing = null }) { ne ->
            val withId = if (ne.id.isBlank()) ne.copy(id = System.currentTimeMillis().toString(36)) else ne
            val i = events.indexOfFirst { it.id == withId.id }
            if (i >= 0) events[i] = withId else events.add(withId)
            editing = null
        }
    }
}

@Composable
private fun EventDialog(initial: CalEvent, onDelete: (() -> Unit)?, close: () -> Unit, save: (CalEvent) -> Unit) {
    var type by remember { mutableStateOf(initial.type) }
    var title by remember { mutableStateOf(initial.title) }
    var from by remember { mutableStateOf(initial.from) }
    var to by remember { mutableStateOf(initial.to) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (initial.id.isBlank()) "موعد جديد" else "تعديل الموعد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EventTypes.all.forEach { t -> FilterChip(type == t, { type = t }, { Text("${EventTypes.icon(t)} $t", fontSize = 12.sp) }) }
                }
                OutlinedTextField(title, { title = it }, label = { Text("العنوان (مثال: اختبارات الفصل الأول)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                DateField(from, { from = it; if (to < it) to = it }, "من تاريخ")
                DateField(to, { to = it }, "إلى تاريخ")
                onDelete?.let { TextButton(onClick = it, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("حذف الموعد") } }
            }
        },
        confirmButton = { Button(onClick = { save(CalEvent(initial.id, type, title, from, if (to < from) from else to)) }) { Text("موافق") } },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } }
    )
}

/** Home card: current term, holidays today, upcoming events, and one-tap follow-up registers for all halaqat. */
@Composable
fun FollowUpHomeCard(state: AppState, openReport: (LogPeriod) -> Unit, openCalendar: () -> Unit) {
    val now = today(); val start = academicStart(now)
    val terms = state.termsFor(start)
    val term = terms.firstOrNull { now >= it.from && now <= it.to }
    val events = state.eventsFor(start) + state.eventsFor(start + 1)
    val todayEvents = eventsOn(events, now)
    val upcoming = upcomingEvents(events, now, 30).filter { it !in todayEvents }.take(3)
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardBg)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📅 " + (term?.let { "${it.name} • حتى ${ltr(it.to)}" } ?: "خارج الفصول الدراسية"),
                    Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Green)
                TextButton(onClick = openCalendar) { Text("التقويم") }
            }
            if (isoDayOfWeek(now) in state.offDaysFor(start))
                Text("🕌 اليوم ${WeekDayNames[isoDayOfWeek(now) - 1]}: عطلة أسبوعية", fontWeight = FontWeight.Bold, color = Color(0xFFB7791F))
            todayEvents.forEach { e ->
                Text("${EventTypes.icon(e.type)} اليوم: ${e.title.ifBlank { e.type }}", fontWeight = FontWeight.Bold,
                    color = if (e.type == EventTypes.HOLIDAY) Color(0xFFB7791F) else Green)
            }
            upcoming.forEach { e -> Text("${EventTypes.icon(e.type)} ${e.title.ifBlank { e.type }} — ${ltr(e.from)}", fontSize = 13.sp, color = SubText) }
            HorizontalDivider()
            Text("📋 سجل متابعة الحفظ لجميع الحلقات", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(LogPeriod.DAY to "اليوم", LogPeriod.WEEK to "الأسبوع", LogPeriod.MONTH to "الشهر").forEach { (p, t) ->
                    FilledTonalButton(onClick = { openReport(p) }, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text(t, fontSize = 13.sp) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(LogPeriod.TERM to "الفصل", LogPeriod.YEAR to "العام الدراسي").forEach { (p, t) ->
                    FilledTonalButton(onClick = { openReport(p) }, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text(t, fontSize = 13.sp) }
                }
            }
        }
    }
}

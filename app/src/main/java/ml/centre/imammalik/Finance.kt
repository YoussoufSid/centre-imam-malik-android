package ml.centre.imammalik

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.Locale

private val Red = Color(0xFFC53030)

/** Finance hub for the admin: monthly fees, registration fees, expenses and summary. */
@Composable
fun FinanceScreen(state: AppState, openProfile: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("الشهرية", "التسجيل", "المصروفات", "العُهَد", "الملخص")
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tab, containerColor = Color.White, contentColor = Green, edgePadding = 8.dp) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, maxLines = 1, fontSize = 14.sp) })
            }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> FeesScreen(state, openProfile)
                1 -> RegistrationTab(state, openProfile)
                2 -> ExpensesTab(state)
                3 -> AdvancesTab(state)
                else -> SummaryTab(state)
            }
        }
    }
}

@Composable
fun MonthSwitcher(title: String, canNext: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.Default.ChevronRight, "السابق") }
        Text(title, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        IconButton(onClick = onNext, enabled = canNext) { Icon(Icons.Default.ChevronLeft, "التالي") }
    }
}

@Composable
private fun AmountField(value: String, change: (String) -> Unit, label: String) {
    OutlinedTextField(value, { v -> change(v.filter { it.isDigit() }.take(8)) }, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
}

// ---------------- Registration fees ----------------

@Composable
private fun RegistrationTab(state: AppState, openProfile: (String) -> Unit) {
    val paid = state.registrationPayments.associateBy { it.studentId }
    val billable = state.students.filter { it.registrationFee > 0 || it.id in paid }
    val unpaid = billable.filter { it.id !in paid }
    var onlyUnpaid by remember { mutableStateOf(true) }
    var paying by remember { mutableStateOf<Student?>(null) }
    val shown = if (onlyUnpaid) unpaid else billable
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("المحصَّل", money(state.registrationPayments.sumOf { it.amount }), Icons.Default.HowToReg, Modifier.weight(1f), Green)
            StatCard("المتبقي", money(unpaid.sumOf { it.registrationFee }), Icons.Default.PendingActions, Modifier.weight(1f), Red)
        }
        Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(onlyUnpaid, { onlyUnpaid = true }, { Text("لم يدفعوا (${unpaid.size})") })
            FilterChip(!onlyUnpaid, { onlyUnpaid = false }, { Text("الكل (${billable.size})") })
        }
        Text("حدّد رسوم التسجيل لكل طالب من «تعديل ملف الطالب».", fontSize = 12.sp, color = Color.Gray)
        if (shown.isEmpty()) EmptyState(Icons.Default.HowToReg,
            if (billable.isEmpty()) "لم تُحدَّد رسوم تسجيل لأي طالب بعد" else "دفع جميع الطلاب رسوم التسجيل 👍")
        else LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { s ->
                val p = paid[s.id]
                Card(Modifier.fillMaxWidth().clickable { openProfile(s.id) }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(s.name)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.name, fontWeight = FontWeight.Bold)
                            Text(if (p != null) "دُفع ${money(p.amount)} • ${ltr(p.paidOn)}" else "المطلوب: ${money(s.registrationFee)}",
                                fontSize = 13.sp, color = if (p != null) Green else Red)
                        }
                        if (p != null) IconButton(onClick = { state.deletePayment(p) }) { Icon(Icons.Default.Undo, "إلغاء", tint = Color.Gray) }
                        else FilledTonalButton(onClick = { paying = s }) { Text("تسجيل الدفع") }
                    }
                }
            }
        }
    }
    paying?.let { s ->
        var amount by remember(s.id) { mutableStateOf(s.registrationFee.takeIf { it > 0 }?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { paying = null },
            title = { Text("رسوم التسجيل — ${s.name}") },
            text = { AmountField(amount, { amount = it }, "المبلغ (فرنك)") },
            confirmButton = { Button(onClick = { state.saveRegistrationPayment(s, amount.toIntOrNull() ?: 0) { paying = null } }) { Text("حفظ") } },
            dismissButton = { TextButton(onClick = { paying = null }) { Text("إلغاء") } }
        )
    }
}

// ---------------- Expenses ----------------

@Composable
private fun ExpensesTab(state: AppState) {
    var editing by remember { mutableStateOf<Expense?>(null) }
    val list = state.monthExpenses
    val byCat = list.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amount } }.toList().sortedByDescending { it.second }
    val total = list.sumOf { it.amount }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        MonthSwitcher("مصروفات ${monthName(state.expenseMonth)}", state.expenseMonth < currentMonth(),
            { state.changeExpenseMonth(-1) }, { state.changeExpenseMonth(1) })
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("مجموع المصروفات", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(money(total), color = Red, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                byCat.forEach { (cat, sum) ->
                    Column {
                        Row { Text(cat, Modifier.weight(1f), fontSize = 13.sp); Text(money(sum), fontSize = 13.sp) }
                        LinearProgressIndicator(progress = { if (total == 0) 0f else sum / total.toFloat() },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = Gold, trackColor = Gold.copy(alpha = .15f))
                    }
                }
            }
        }
        Button(onClick = { editing = Expense(date = today()) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("تسجيل مصروف")
        }
        if (list.isEmpty()) EmptyState(Icons.Default.ReceiptLong, "لا توجد مصروفات مسجلة لهذا الشهر")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(list, key = { it.id }) { e ->
                Card(Modifier.fillMaxWidth().clickable { editing = e }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ReceiptLong, null, tint = Gold); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.category, fontWeight = FontWeight.Bold)
                            Text(listOf(ltr(e.date), e.note).filter { it.isNotBlank() }.joinToString(" • "), fontSize = 12.sp, color = Color.Gray)
                        }
                        Text(money(e.amount), color = Red, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    editing?.let { e -> ExpenseDialog(state, e) { editing = null } }
}

@Composable
private fun ExpenseDialog(state: AppState, initial: Expense, close: () -> Unit) {
    var category by remember { mutableStateOf(initial.category.ifBlank { ExpenseCategories.first() }) }
    var amount by remember { mutableStateOf(initial.amount.takeIf { it > 0 }?.toString() ?: "") }
    var date by remember { mutableStateOf(initial.date.ifBlank { today() }) }
    var note by remember { mutableStateOf(initial.note) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (initial.id.isBlank()) "مصروف جديد" else "تعديل المصروف") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("النوع", fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ExpenseCategories.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { c -> FilterChip(category == c, { category = c }, { Text(c, fontSize = 13.sp) }) }
                        }
                    }
                }
                AmountField(amount, { amount = it }, "المبلغ (فرنك)")
                DateField(date, { date = it }, "التاريخ")
                AppField(note, { note = it }, "ملاحظة (اختياري)")
                if (initial.id.isNotBlank()) TextButton(onClick = { confirmDelete = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("حذف المصروف") }
            }
        },
        confirmButton = { Button(onClick = {
            state.saveExpense(initial.copy(category = category, amount = amount.toIntOrNull() ?: 0, date = date, note = note), close)
        }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } }
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text("حذف المصروف") },
        text = { Text("هل تريد حذف هذا المصروف (${money(initial.amount)})؟") },
        confirmButton = { Button(onClick = { state.deleteExpense(initial); confirmDelete = false; close() },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("حذف") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("تراجع") } }
    )
}

// ---------------- Summary ----------------

@Composable
private fun SummaryTab(state: AppState) {
    // Academic year September–August, like the centre's fee register.
    var anchor by rememberSaveable { mutableStateOf(currentMonth()) }
    val yearMonths = academicYearMonths(anchor)
    var payments by remember { mutableStateOf<List<Payment>?>(null) }
    var expenses by remember { mutableStateOf(emptyList<Expense>()) }
    // Reload whenever the year changes or live data (this month) changes.
    LaunchedEffect(anchor, state.monthPayments, state.registrationPayments, state.monthExpenses) {
        state.loadMonths(yearMonths.first(), yearMonths.last()) { p, e -> payments = p; expenses = e }
    }
    val ps = payments.orEmpty()
    // Months already reached, plus later months that already have payments (fees paid in advance).
    val months = yearMonths.filter { m -> m <= currentMonth() || ps.any { it.month == m } || expenses.any { it.month == m } }
    val monthlyIncome = ps.filter { it.type == PaymentType.MONTHLY }.sumOf { it.amount }
    val regIncome = ps.filter { it.type == PaymentType.REGISTRATION }.sumOf { it.amount }
    val totalExp = expenses.sumOf { it.amount }
    val net = monthlyIncome + regIncome - totalExp

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            MonthSwitcher("الملخص المالي ${academicYearLabel(anchor)}", yearMonths.last() < currentMonth(),
                { anchor = shiftMonth(yearMonths.first(), -12); payments = null },
                { anchor = shiftMonth(yearMonths.first(), 12); payments = null })
        }
        if (payments == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (net >= 0) Green else Red)) {
                Column(Modifier.padding(18.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("الرصيد الصافي", color = Color.White.copy(.85f))
                    Text(money(net), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("الإيرادات ${money(monthlyIncome + regIncome)} − المصروفات ${money(totalExp)}",
                        color = Color.White.copy(.85f), fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("الرسوم الشهرية", money(monthlyIncome), Icons.Default.Payments, Modifier.weight(1f), Green)
                StatCard("رسوم التسجيل", money(regIncome), Icons.Default.HowToReg, Modifier.weight(1f), Green)
            }
        }
        item { Text("حسب الشهر", fontWeight = FontWeight.Bold, fontSize = 17.sp) }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.horizontalScroll(rememberScrollState()).padding(8.dp)) {
                    SummaryRow("الشهر", "الإيرادات", "المصروفات", "الصافي", header = true)
                    months.reversed().forEach { m ->
                        val inc = ps.filter { it.month == m }.sumOf { it.amount }
                        val exp = expenses.filter { it.month == m }.sumOf { it.amount }
                        SummaryRow(monthName(m), money(inc), money(exp), money(inc - exp), netColor = if (inc - exp >= 0) Green else Red)
                    }
                }
            }
        }
        val cats = expenses.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amount } }.toList().sortedByDescending { it.second }
        if (cats.isNotEmpty()) {
            item { Text("المصروفات حسب النوع", fontWeight = FontWeight.Bold, fontSize = 17.sp) }
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        cats.forEach { (c, s) ->
                            Row { Text(c, Modifier.weight(1f)); Text(money(s), color = Red) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(a: String, b: String, c: String, d: String, header: Boolean = false, netColor: Color = Color.Unspecified) {
    val w = if (header) FontWeight.Bold else FontWeight.Normal
    Row(Modifier.then(if (header) Modifier.background(Cream) else Modifier).padding(vertical = 6.dp)) {
        Text(a, Modifier.width(120.dp), fontWeight = w, fontSize = 13.sp)
        Text(b, Modifier.width(110.dp), fontWeight = w, fontSize = 13.sp, color = if (header) Color.Unspecified else Green)
        Text(c, Modifier.width(110.dp), fontWeight = w, fontSize = 13.sp, color = if (header) Color.Unspecified else Red)
        Text(d, Modifier.width(110.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = netColor)
    }
}

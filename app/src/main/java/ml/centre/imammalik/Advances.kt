package ml.centre.imammalik

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

private val Red = Color(0xFFC53030)

@Composable
private fun Amount(value: String, change: (String) -> Unit, label: String) {
    OutlinedTextField(value, { v -> change(v.filter { it.isDigit() }.take(8)) }, Modifier.fillMaxWidth(), singleLine = true,
        label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}

/** Petty-cash advances (عُهَد) with their daily expense registers. */
@Composable
fun AdvancesTab(state: AppState) {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val open = state.advances.firstOrNull { it.id == openId }
    if (open != null) { AdvanceDetail(state, open) { openId = null }; return }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 10.dp)) {
        item {
            Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("عهدة جديدة (سجل مصروفات)")
            }
        }
        item {
            Text("تُسلَّم العهدة لشخص لصرف المصروفات اليومية، ثم يُسجَّل كل مصروف ويُصفّى الحساب. " +
                "كل مصروف يُضاف تلقائيًا إلى مصروفات المركز.", fontSize = 12.sp, color = Color.Gray)
        }
        if (state.advances.isEmpty()) item { EmptyState(Icons.Default.ReceiptLong, "لا توجد عُهَد بعد") }
        items(state.advances, key = { it.id }) { a ->
            Card(Modifier.fillMaxWidth().clickable { openId = a.id }, colors = CardDefaults.cardColors(containerColor = CardBg)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(a.recuPar, 40.dp); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.recuPar, fontWeight = FontWeight.Bold)
                        Text("${monthName(a.month)}" + (if (a.week.isNotBlank()) " • ${a.week}" else "") + " • ${ltr(a.receivedDate)}",
                            fontSize = 12.sp, color = Color.Gray)
                        Text("المستلم: ${money(a.amount)}", fontSize = 13.sp)
                    }
                    Surface(color = (if (a.closed) Green else Gold).copy(alpha = .12f), shape = MaterialTheme.shapes.small) {
                        Text(if (a.closed) "مُصفّى" else "مفتوح", Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = if (a.closed) Green else Color(0xFF8A6D00), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
    if (creating) AdvanceDialog(state, CashAdvance(receivedDate = today(), remisPar = Centre.director)) { id ->
        creating = false; if (id != null) openId = id
    }
}

@Composable
private fun AdvanceDialog(state: AppState, initial: CashAdvance, close: (String?) -> Unit) {
    var amount by remember { mutableStateOf(initial.amount.takeIf { it > 0 }?.toString() ?: "") }
    var recu by remember { mutableStateOf(initial.recuPar) }
    var remis by remember { mutableStateOf(initial.remisPar) }
    var week by remember { mutableStateOf(initial.week) }
    var date by remember { mutableStateOf(initial.receivedDate) }
    val people = (state.teachers.map { it.name } + state.users.map { it.name }).filter { it.isNotBlank() }.distinct()
    AlertDialog(
        onDismissRequest = { close(null) },
        title = { Text(if (initial.id.isBlank()) "عهدة جديدة" else "تعديل العهدة") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Amount(amount, { amount = it }, "المبلغ المستلم من الإدارة (${Centre.currency})")
                AppField(recu, { recu = it }, "المستلم (Reçu par)")
                if (people.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    people.forEach { p -> FilterChip(recu == p, { recu = p }, { Text(p) }) }
                }
                AppField(remis, { remis = it }, "المستلم منه (Remis par)")
                AppField(week, { week = it }, "الأسبوع (مثال: الأسبوع الثاني)")
                DateField(date, { date = it }, "تاريخ الاستلام")
            }
        },
        confirmButton = { Button(onClick = {
            val id = state.saveAdvance(initial.copy(amount = amount.toIntOrNull() ?: 0, recuPar = recu, remisPar = remis,
                week = week, receivedDate = date))
            if (id != null) close(id)
        }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = { close(null) }) { Text("إلغاء") } }
    )
}

@Composable
private fun AdvanceDetail(state: AppState, a: CashAdvance, back: () -> Unit) {
    val context = LocalContext.current
    var lines by remember(a.id) { mutableStateOf(emptyList<Expense>()) }
    DisposableEffect(a.id) {
        val reg = state.listenAdvanceLines(a.id) { lines = it }
        onDispose { reg.remove() }
    }
    var editingLine by remember { mutableStateOf<Expense?>(null) }
    var editingHeader by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val spent = lines.sumOf { it.amount }
    val bal = advanceBalance(a, spent)
    androidx.activity.compose.BackHandler { back() }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = back) { Icon(Icons.Default.ArrowForward, "رجوع") }
                Text("عهدة ${a.recuPar}", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                IconButton(onClick = { editingHeader = true }) { Icon(Icons.Default.Edit, "تعديل", tint = Color.Gray) }
                IconButton(onClick = { shareExpenseRegister(context, a, lines) }) { Icon(Icons.Default.PictureAsPdf, "طباعة", tint = Green) }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardBg)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${monthName(a.month)}" + (if (a.week.isNotBlank()) " • ${a.week}" else "") + " • استلام ${ltr(a.receivedDate)}",
                        fontSize = 13.sp, color = Color.Gray)
                    if (a.remisPar.isNotBlank()) Text("المستلم منه: ${a.remisPar}", fontSize = 13.sp)
                    Row { Text("المبلغ المستلم", Modifier.weight(1f)); Text(money(a.amount), fontWeight = FontWeight.Bold) }
                    Row { Text("مجموع المصروفات", Modifier.weight(1f)); Text(money(spent), color = Red, fontWeight = FontWeight.Bold) }
                    HorizontalDivider()
                    Row {
                        Text(if (bal >= 0) "الباقي (Reste)" else "العجز (Déficit)", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text(money(kotlin.math.abs(bal)), fontWeight = FontWeight.Bold, color = if (bal >= 0) Green else Red, fontSize = 17.sp)
                    }
                    if (a.settlementAmount > 0 || a.closed) Text("التسوية: ${money(a.settlementAmount)} • ${ltr(a.settlementDate)}" +
                        if (a.closed) " • الحساب مُصفّى ✅" else "", fontSize = 13.sp, color = SubText)
                }
            }
        }
        if (!a.closed) item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { editingLine = Expense(date = today(), category = DAILY_EXPENSES_CATEGORY) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("مصروف")
                }
                OutlinedButton(onClick = { settling = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.TaskAlt, null); Spacer(Modifier.width(4.dp)); Text("تصفية الحساب")
                }
            }
        }
        if (lines.isEmpty()) item { EmptyState(Icons.Default.ReceiptLong, "لم تُسجَّل مصروفات في هذه العهدة بعد") }
        items(lines, key = { it.id }) { l ->
            Card(Modifier.fillMaxWidth().clickable(enabled = !a.closed) { editingLine = l },
                colors = CardDefaults.cardColors(containerColor = CardBg)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.note, fontWeight = FontWeight.Medium)
                        Text(listOf(ltr(l.date), l.category, l.observation).filter { it.isNotBlank() }.joinToString(" • "),
                            fontSize = 12.sp, color = Color.Gray)
                    }
                    Text(money(l.amount), color = Red, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            TextButton(onClick = { confirmDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text("حذف العهدة ومصروفاتها")
            }
        }
    }
    editingLine?.let { l -> LineDialog(state, a, l) { editingLine = null } }
    if (editingHeader) AdvanceDialog(state, a) { editingHeader = false }
    if (settling) {
        var amount by remember { mutableStateOf(kotlin.math.abs(bal).takeIf { it > 0 }?.toString() ?: "") }
        var date by remember { mutableStateOf(today()) }
        var closed by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { settling = false },
            title = { Text("تصفية الحساب") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (bal >= 0) "الباقي مع المستلم: ${money(bal)} — يُعيده للإدارة." else "العجز: ${money(-bal)} — تدفعه الإدارة للمستلم.",
                        color = if (bal >= 0) Green else Red, fontWeight = FontWeight.Bold)
                    Amount(amount, { amount = it }, "المبلغ المُعاد أو المدفوع للتسوية")
                    DateField(date, { date = it }, "تاريخ التسوية")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(closed, { closed = it }); Text("الحساب مُصفّى (Compte soldé)")
                    }
                }
            },
            confirmButton = { Button(onClick = {
                state.closeAdvance(a, spent, amount.toIntOrNull() ?: 0, date, closed); settling = false
            }) { Text("حفظ") } },
            dismissButton = { TextButton(onClick = { settling = false }) { Text("إلغاء") } }
        )
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text("حذف العهدة") },
        text = { Text("ستُحذف العهدة و${lines.size} مصروفًا مسجلًا فيها من مصروفات المركز. هل تريد المتابعة؟") },
        confirmButton = { Button(onClick = { confirmDelete = false; state.deleteAdvance(a); back() },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("حذف") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("تراجع") } }
    )
}

@Composable
private fun LineDialog(state: AppState, a: CashAdvance, initial: Expense, close: () -> Unit) {
    var note by remember { mutableStateOf(initial.note) }
    var amount by remember { mutableStateOf(initial.amount.takeIf { it > 0 }?.toString() ?: "") }
    var date by remember { mutableStateOf(initial.date.ifBlank { today() }) }
    var obs by remember { mutableStateOf(initial.observation) }
    var category by remember { mutableStateOf(initial.category.ifBlank { DAILY_EXPENSES_CATEGORY }) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (initial.id.isBlank()) "مصروف جديد" else "تعديل المصروف") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppField(note, { note = it }, "البيان (Libellé) — مثال: خبز وسكر للمطبخ")
                Amount(amount, { amount = it }, "المبلغ (${Centre.currency})")
                DateField(date, { date = it }, "التاريخ")
                AppField(obs, { obs = it }, "ملاحظات (Observations)")
                Text("النوع", fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExpenseCategories.filter { it != SALARY_CATEGORY }.forEach { c -> FilterChip(category == c, { category = c }, { Text(c) }) }
                }
                if (initial.id.isNotBlank()) TextButton(onClick = { state.deleteExpense(initial); close() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("حذف هذا المصروف") }
            }
        },
        confirmButton = { Button(onClick = {
            state.saveAdvanceLine(a, initial.copy(note = note, amount = amount.toIntOrNull() ?: 0, date = date,
                observation = obs, category = category), close)
        }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } }
    )
}

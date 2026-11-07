package ml.centre.imammalik

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private fun utcFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

/** Read-only date field (yyyy-MM-dd) that opens a calendar; avoids typing mistakes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    value: String, onChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), optional: Boolean = false
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = ltr(value).takeIf { value.isNotBlank() } ?: "", onValueChange = {}, readOnly = true,
            label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            trailingIcon = { Icon(Icons.Default.CalendarMonth, "اختيار التاريخ", tint = Green) }
        )
        // Transparent layer so the whole field opens the calendar.
        val source = remember { MutableInteractionSource() }
        Box(Modifier.matchParentSize().clickable(source, null) { open = true })
    }
    if (open) {
        val initial = runCatching { utcFormat().parse(value)?.time }.getOrNull()
        val st = rememberDatePickerState(initialSelectedDateMillis = initial ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = { st.selectedDateMillis?.let { onChange(utcFormat().format(it)) }; open = false }) { Text("تم") }
            },
            dismissButton = {
                Row {
                    if (optional && value.isNotBlank()) TextButton(onClick = { onChange(""); open = false }) { Text("مسح") }
                    TextButton(onClick = { open = false }) { Text("إلغاء") }
                }
            }
        ) { DatePicker(st, showModeToggle = true) }
    }
}

// ---------------- Centre settings ----------------

@Composable
fun SettingsScreen(state: AppState, close: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val v = state.settingsVersion
    var name by remember(v) { mutableStateOf(Centre.name) }
    var director by remember(v) { mutableStateOf(Centre.director) }
    var phone by remember(v) { mutableStateOf(Centre.phone) }
    var address by remember(v) { mutableStateOf(Centre.address) }
    var year by remember(v) { mutableStateOf(Centre.academicYear) }
    var currency by remember(v) { mutableStateOf(Centre.currency) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("بيانات المركز", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Green)
        Text("تظهر هذه البيانات في كشوف النتائج والشهادات ورسائل واتساب.", fontSize = 13.sp, color = Color.Gray,
            modifier = Modifier.padding(bottom = 12.dp))
        AppField(name, { name = it }, "اسم المركز الكامل")
        AppField(director, { director = it }, "اسم مدير المركز")
        AppField(phone, { phone = it }, "هاتف المركز", KeyboardType.Phone)
        AppField(address, { address = it }, "العنوان (الحي، المدينة)")
        AppField(year, { year = it }, "العام الدراسي (مثال: 2026–2027)")
        AppField(currency, { currency = it }, "العملة (مثال: فرنك)")
        Spacer(Modifier.height(8.dp))
        Button(onClick = { state.saveSettings(name, director, phone, address, year, currency, close) },
            modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Icon(Icons.Default.Save, null); Spacer(Modifier.width(6.dp)); Text("حفظ الإعدادات")
        }
    }
}

// ---------------- About ----------------

@Composable
fun AboutDialog(close: () -> Unit) {
    AlertDialog(
        onDismissRequest = close,
        confirmButton = { TextButton(onClick = close) { Text("إغلاق") } },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.centre_logo), null, Modifier.size(96.dp).clip(CircleShape))
                Spacer(Modifier.height(10.dp))
                Text(Centre.name, fontWeight = FontWeight.Bold, color = Green, textAlign = TextAlign.Center)
                Text("تطبيق إدارة المركز — الإصدار ${BuildConfig.VERSION_NAME}", fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(10.dp))
                if (Centre.director.isNotBlank()) Text("المدير: ${Centre.director}", fontSize = 14.sp)
                if (Centre.contactLine().isNotBlank()) Text(Centre.contactLine(), fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text("الطلاب • الحضور • الحفظ والمراجعة • الدراسات الإسلامية • المالية • التقارير",
                    fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
            }
        }
    )
}

// ---------------- Overflow menu ----------------

data class MenuEntry(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val onClick: () -> Unit)

@Composable
fun AppMenu(entries: List<MenuEntry>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, "القائمة", tint = Color.White) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            entries.forEach { e ->
                DropdownMenuItem(
                    text = { Text(e.title) },
                    leadingIcon = { Icon(e.icon, null, tint = if (e.title == "تسجيل الخروج") MaterialTheme.colorScheme.error else Green) },
                    onClick = { open = false; e.onClick() }
                )
            }
        }
    }
}

@Composable
fun LogoutConfirm(onConfirm: () -> Unit, close: () -> Unit) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text("تسجيل الخروج") },
        text = { Text("هل تريد تسجيل الخروج من التطبيق؟ تبقى بياناتك محفوظة.") },
        confirmButton = { Button(onClick = { close(); onConfirm() }) { Text("خروج") } },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } }
    )
}

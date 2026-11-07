package ml.centre.imammalik

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File

/** Writes a CSV (UTF-8 with BOM so Excel shows Arabic correctly) and opens the share sheet. */
fun shareCsv(context: Context, fileName: String, csv: String) {
    runCatching {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText("﻿" + csv, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "حفظ أو مشاركة الملف"))
    }
}

@Composable
private fun ExportRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Green); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 12.sp, color = Color.Gray)
        }
        Icon(Icons.Default.Share, null, tint = Color.Gray)
    }
}

@Composable
fun ExportDialog(state: AppState, close: () -> Unit) {
    val context = LocalContext.current
    var month by remember { mutableStateOf(currentMonth()) }
    var loading by remember { mutableStateOf(false) }
    fun done(name: String): (String) -> Unit = { csv -> loading = false; shareCsv(context, name, csv) }

    AlertDialog(
        onDismissRequest = close,
        title = { Text("تصدير البيانات إلى Excel") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { month = shiftMonth(month, -1) }) { Icon(Icons.Default.ChevronRight, "الشهر السابق") }
                    Text(monthName(month), Modifier.weight(1f), fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(onClick = { month = shiftMonth(month, 1) }, enabled = month < currentMonth()) {
                        Icon(Icons.Default.ChevronLeft, "الشهر التالي")
                    }
                }
                HorizontalDivider()
                ExportRow(Icons.Default.Groups, "قائمة الطلاب", "كل البيانات والأحزاب والرسوم") {
                    shareCsv(context, "students.csv", state.studentsCsv())
                }
                ExportRow(Icons.Default.FactCheck, "ملخص الحضور", "لكل طالب خلال ${monthName(month)}") {
                    loading = true; state.attendanceCsv(month, done("attendance-$month.csv"))
                }
                ExportRow(Icons.Default.Payments, "الرسوم", "المدفوع وغير المدفوع لشهر ${monthName(month)}") {
                    loading = true; state.feesCsv(month, done("fees-$month.csv"))
                }
                ExportRow(Icons.Default.School, "درجات الدراسات الإسلامية", "كل الدرجات المسجلة") {
                    loading = true; state.gradesCsv(done("grades.csv"))
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                Text("يُفتح الملف في Excel أو Google Sheets، ويمكن حفظه أو إرساله عبر واتساب أو البريد.",
                    fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("إغلاق") } }
    )
}

package ml.centre.imammalik

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File

private const val GREEN = 0xFF075B3A.toInt()
private const val GOLD = 0xFFD7A91E.toInt()
private const val GREY = 0xFF555555.toInt()
private const val LIGHT = 0xFFF7F5EC.toInt()

private const val CENTRE_NAME = "مركز الإمام مالك لتحفيظ القرآن الكريم والدراسات الإسلامية"

/** Draws right-to-left text inside a box whose right edge is [right]; returns the text height. */
private fun Canvas.rtlText(
    text: String, right: Float, top: Float, width: Float, size: Float,
    color: Int = 0xFF222222.toInt(), bold: Boolean = false, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
): Float {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
    val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt())
        .setAlignment(align).setTextDirection(TextDirectionHeuristics.RTL).build()
    save(); translate(right - width, top); layout.draw(this); restore()
    return layout.height.toFloat()
}

private fun Canvas.line(x1: Float, y: Float, x2: Float, color: Int = 0xFFDDDDDD.toInt(), w: Float = 1f) {
    drawLine(x1, y, x2, y, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = w })
}

private fun Canvas.logo(context: Context, left: Float, top: Float, size: Float) {
    runCatching {
        val bmp = BitmapFactory.decodeResource(context.resources, R.drawable.centre_logo) ?: return
        drawBitmap(bmp, null, RectF(left, top, left + size, top + size), Paint(Paint.FILTER_BITMAP_FLAG))
    }
}

private fun sharePdf(context: Context, doc: PdfDocument, fileName: String) {
    runCatching {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.outputStream().use { doc.writeTo(it) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "طباعة أو مشاركة الملف"))
    }
    doc.close()
}

private fun safeName(s: String) = s.trim().replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").ifBlank { "student" }

/** A4 portrait report card: grades per subject, average, attendance and Quran progress. */
fun shareReportCard(
    context: Context, student: Student, term: String?, grades: List<Grade>, attendance: List<AttendanceRecord>
) {
    val W = 595f; val H = 842f; val M = 40f
    val doc = PdfDocument()
    val page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), 1).create())
    val c = page.canvas
    val right = W - M; val width = W - 2 * M

    // Header band
    c.drawRect(0f, 0f, W, 110f, Paint().apply { color = GREEN })
    c.logo(context, M, 18f, 74f)
    c.rtlText(CENTRE_NAME, right, 28f, width - 90f, 15f, 0xFFFFFFFF.toInt(), bold = true)
    c.rtlText("كشف النتائج" + (term?.let { " — $it" } ?: " — جميع الفصول"), right, 62f, width - 90f, 20f, GOLD, bold = true)

    var y = 130f
    // Student info box
    c.drawRoundRect(RectF(M, y, W - M, y + 92f), 10f, 10f, Paint().apply { color = LIGHT })
    val half = (width - 30f) / 2
    fun info(label: String, value: String, col: Int, row: Int) {
        if (value.isBlank()) return
        val r = right - 12f - col * (half + 6f)
        c.rtlText("$label: $value", r, y + 12f + row * 26f, half, 12.5f, 0xFF222222.toInt())
    }
    info("الطالب", student.name, 0, 0); info("الحلقة", student.group, 1, 0)
    info("المستوى", student.level, 0, 1); info("المعلم", student.teacher, 1, 1)
    info("تاريخ الميلاد", ltr(student.birthDate), 0, 2); info("تاريخ التسجيل", ltr(student.enrollmentDate), 1, 2)
    y += 112f

    // Grades table
    val g = grades.filter { term == null || it.term == term }
    c.rtlText("الدراسات الإسلامية", right, y, width, 15f, GREEN, bold = true); y += 26f
    val colSubject = width * .5f; val colScore = width * .22f; val colMention = width * .28f
    fun row(a: String, b: String, d: String, header: Boolean, color: Int = 0xFF222222.toInt()) {
        if (header) c.drawRect(M, y - 4f, W - M, y + 22f, Paint().apply { this.color = GREEN })
        val tc = if (header) 0xFFFFFFFF.toInt() else color
        c.rtlText(a, right - 8f, y, colSubject - 8f, 12.5f, if (header) tc else 0xFF222222.toInt(), bold = header)
        c.rtlText(b, right - colSubject, y, colScore, 12.5f, tc, bold = true, align = Layout.Alignment.ALIGN_CENTER)
        c.rtlText(d, right - colSubject - colScore, y, colMention, 12.5f, tc, bold = header, align = Layout.Alignment.ALIGN_CENTER)
        y += 26f
        if (!header) c.line(M, y - 4f, W - M)
    }
    row("المادة", "المعدل /$MAX_SCORE", "التقدير", header = true)
    val subjects = Subjects.filter { s -> g.any { it.subject == s } }
    if (subjects.isEmpty()) {
        c.rtlText("لا توجد درجات مسجلة لهذه الفترة", right, y + 4f, width, 12f, GREY); y += 30f
    }
    subjects.forEach { s ->
        val l = g.filter { it.subject == s }
        val avg = l.sumOf { it.score } / l.size
        row(s, scoreText(avg), mention(avg), header = false, color = if (avg >= 10) GREEN else 0xFFC53030.toInt())
    }
    if (g.isNotEmpty()) {
        val avg = g.sumOf { it.score } / g.size
        y += 6f
        c.drawRoundRect(RectF(M, y, W - M, y + 34f), 8f, 8f, Paint().apply { color = 0x22D7A91E })
        c.rtlText("المعدل العام: ${scoreText(avg)} / $MAX_SCORE  —  التقدير: ${mention(avg)}", right - 10f, y + 8f, width - 20f,
            14f, GREEN, bold = true)
        y += 50f
    } else y += 10f

    // Quran & attendance
    c.rtlText("القرآن الكريم والمواظبة", right, y, width, 15f, GREEN, bold = true); y += 26f
    val present = attendance.count { AttendanceStatus.isPresent(it.status) }
    val rate = if (attendance.isEmpty()) "—" else "${present * 100 / attendance.size}٪"
    c.rtlText("الحفظ: ${hizbLabel(student.hizbs)}", right, y, width, 12.5f); y += 22f
    // progress bar
    val barW = width; val pct = student.hizbs.coerceIn(0, TOTAL_HIZBS) / TOTAL_HIZBS.toFloat()
    c.drawRoundRect(RectF(M, y, M + barW, y + 10f), 5f, 5f, Paint().apply { color = 0x22075B3A })
    c.drawRoundRect(RectF(M + barW * (1 - pct), y, M + barW, y + 10f), 5f, 5f, Paint().apply { color = GREEN })
    y += 24f
    c.rtlText("نسبة الحضور: $rate  (حضور ${present} من ${attendance.size} يومًا مسجلًا)" +
        "  •  غياب: ${attendance.count { it.status == AttendanceStatus.ABSENT }}", right, y, width, 12.5f)
    y += 40f

    // Remarks & signatures
    c.rtlText("ملاحظات المعلم:", right, y, width, 12.5f, GREY, bold = true); y += 22f
    repeat(3) { c.line(M, y + 14f, W - M, 0xFFBBBBBB.toInt()); y += 24f }
    y = maxOf(y + 20f, H - 150f)
    val third = width / 3
    listOf("توقيع المعلم", "توقيع مدير المركز", "توقيع ولي الأمر").forEachIndexed { i, t ->
        val r = right - i * third
        c.rtlText(t, r, y, third - 10f, 12f, GREY, align = Layout.Alignment.ALIGN_CENTER)
        c.line(r - third + 20f, y + 50f, r - 20f, 0xFF999999.toInt())
    }
    c.line(M, H - 50f, W - M, GOLD, 1.5f)
    c.rtlText("حُرّر بتاريخ ${ltr(today())}" + hijriToday().let { if (it.isBlank()) "" else " الموافق $it" },
        right, H - 42f, width, 10.5f, GREY, align = Layout.Alignment.ALIGN_CENTER)

    doc.finishPage(page)
    sharePdf(context, doc, "report-${safeName(student.name)}.pdf")
}

/** A4 landscape certificate of memorization (or completion when all 60 hizbs are memorized). */
fun shareCertificate(context: Context, student: Student) {
    val W = 842f; val H = 595f
    val doc = PdfDocument()
    val page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), 1).create())
    val c = page.canvas
    c.drawRect(0f, 0f, W, H, Paint().apply { color = 0xFFFFFDF5.toInt() })
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    border.color = GREEN; border.strokeWidth = 10f; c.drawRect(22f, 22f, W - 22f, H - 22f, border)
    border.color = GOLD; border.strokeWidth = 2.5f; c.drawRect(38f, 38f, W - 38f, H - 38f, border)

    val inner = W - 160f; val right = W - 80f
    c.logo(context, W / 2 - 42f, 52f, 84f)
    c.rtlText(CENTRE_NAME, right, 146f, inner, 16f, GREEN, bold = true, align = Layout.Alignment.ALIGN_CENTER)
    val khatm = student.hizbs >= TOTAL_HIZBS
    c.rtlText(if (khatm) "شهادة ختم القرآن الكريم" else "شهادة تقدير", right, 182f, inner, 34f, GOLD, bold = true,
        align = Layout.Alignment.ALIGN_CENTER)
    c.rtlText("يشهد المركز بأن الطالب/ة", right, 248f, inner, 17f, GREY, align = Layout.Alignment.ALIGN_CENTER)
    c.rtlText(student.name, right, 278f, inner, 30f, GREEN, bold = true, align = Layout.Alignment.ALIGN_CENTER)
    val body = if (khatm) "قد أتمّ حفظ كتاب الله تعالى كاملًا (ستين حزبًا) بفضل الله وتوفيقه."
    else "قد أتمّ حفظ ${student.hizbs} حزبًا من كتاب الله تعالى" + (if (student.hizbs >= 2) " (≈ ${student.hizbs / 2} جزءًا)" else "") +
        "، تقديرًا لجهده ومواظبته."
    c.rtlText(body, right, 334f, inner, 18f, 0xFF222222.toInt(), align = Layout.Alignment.ALIGN_CENTER)
    c.rtlText("﴿وَلَقَدْ يَسَّرْنَا الْقُرْآنَ لِلذِّكْرِ فَهَلْ مِن مُّدَّكِرٍ﴾", right, 380f, inner, 16f, GREEN,
        align = Layout.Alignment.ALIGN_CENTER)
    c.rtlText("نسأل الله أن يجعله من أهل القرآن الذين هم أهل الله وخاصته.", right, 412f, inner, 14f, GREY,
        align = Layout.Alignment.ALIGN_CENTER)

    val date = "حُرّر بتاريخ ${ltr(today())}" + hijriToday().let { if (it.isBlank()) "" else "\nالموافق $it" }
    c.rtlText(date, right, 470f, 240f, 12f, GREY, align = Layout.Alignment.ALIGN_CENTER)
    c.rtlText("مدير المركز", 80f + 240f, 470f, 240f, 13f, GREY, bold = true, align = Layout.Alignment.ALIGN_CENTER)
    c.line(80f + 30f, 530f, 80f + 210f, 0xFF999999.toInt())
    if (student.teacher.isNotBlank()) {
        c.rtlText("المعلم: ${student.teacher}", W / 2 + 100f, 470f, 200f, 13f, GREY, bold = true, align = Layout.Alignment.ALIGN_CENTER)
        c.line(W / 2 - 90f, 530f, W / 2 + 90f, 0xFF999999.toInt())
    }

    doc.finishPage(page)
    sharePdf(context, doc, "certificate-${safeName(student.name)}.pdf")
}

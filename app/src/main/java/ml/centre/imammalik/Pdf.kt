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

/** Largest text size ≤ [max] at which [text] fits on one line of [width]. */
private fun fitTextSize(text: String, width: Float, max: Float, bold: Boolean = false): Float {
    val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }
    var size = max
    while (size > 6f) { p.textSize = size; if (p.measureText(text) <= width - 4f) break; size -= .25f }
    return size
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
    c.rtlText(Centre.name, right, 28f, width - 90f, 15f, 0xFFFFFFFF.toInt(), bold = true)
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
    listOf("توقيع المعلم" + (if (student.teacher.isNotBlank()) "\n${student.teacher}" else ""),
        "توقيع مدير المركز" + (if (Centre.director.isNotBlank()) "\n${Centre.director}" else ""),
        "توقيع ولي الأمر").forEachIndexed { i, t ->
        val r = right - i * third
        c.rtlText(t, r, y, third - 10f, 12f, GREY, align = Layout.Alignment.ALIGN_CENTER)
        c.line(r - third + 20f, y + 50f, r - 20f, 0xFF999999.toInt())
    }
    c.line(M, H - 58f, W - M, GOLD, 1.5f)
    c.rtlText("حُرّر بتاريخ ${ltr(today())}" + hijriToday().let { if (it.isBlank()) "" else " الموافق $it" } +
        (if (Centre.academicYear.isNotBlank()) " • العام الدراسي ${Centre.academicYear}" else ""),
        right, H - 50f, width, 10.5f, GREY, align = Layout.Alignment.ALIGN_CENTER)
    if (Centre.contactLine().isNotBlank())
        c.rtlText(Centre.contactLine(), right, H - 34f, width, 10f, GREY, align = Layout.Alignment.ALIGN_CENTER)

    doc.finishPage(page)
    sharePdf(context, doc, "report-${safeName(student.name)}.pdf")
}

/** A4 landscape certificate of memorization (or completion when all 60 hizbs are memorized). */
/** Student ID card at real card size (85.6 × 54 mm). */
fun shareStudentCard(context: Context, student: Student) {
    fun date(d: String) = if (d.isBlank()) "" else ltr(frDate(d))
    shareIdCard(context, "بطاقة طالب", "CARTE D'ÉLÈVE", student.name, studentNumber(student.id), student.photo, qrText(student),
        listOf(Triple("الحلقة", "Groupe", student.group), Triple("المعلم", "Enseignant", student.teacher),
            Triple("تاريخ الميلاد", "Né(e) le", date(student.birthDate)),
            Triple("هاتف ولي الأمر", "Tuteur", student.phone.takeIf { it.isNotBlank() }?.let { ltr(it) } ?: "………………………"),
            Triple("التسجيل", "Inscription", date(student.enrollmentDate))),
        "card-${safeName(student.name)}.pdf")
}

/** Staff ID card (teachers, administration, supervisors, workers). */
fun shareStaffCard(context: Context, t: Teacher) {
    shareIdCard(context, "بطاقة موظف", "CARTE PROFESSIONNELLE", t.name, staffNumber(t.id), t.photo, staffQrText(t),
        listOf(Triple("الفئة", "Catégorie", "${t.category}  ${ltr(StaffCategory.frenchSingular(t.category))}"),
            Triple("الوظيفة", "Fonction", t.function),
            Triple("الهاتف", "Téléphone", t.phone.takeIf { it.isNotBlank() }?.let { ltr(it) } ?: ""),
            Triple("التعيين", "Embauche", if (t.hireDate.isBlank()) "" else ltr(frDate(t.hireDate)))),
        "card-${safeName(t.name)}.pdf")
}

/** Bilingual ID card drawn at 2× and scaled for crisp printing: header, photo, number, fields, QR, footer. */
private fun shareIdCard(context: Context, kindAr: String, kindFr: String, name: String, number: String, photoUrl: String,
                        qr: String, fields: List<Triple<String, String, String>>, fileName: String) {
    val doc = PdfDocument()
    val page = doc.startPage(PdfDocument.PageInfo.Builder(243, 153, 1).create())
    val c = page.canvas
    c.scale(.5f, .5f)
    val W = 486f; val H = 306f
    c.drawRect(0f, 0f, W, H, Paint().apply { color = 0xFFFFFDF5.toInt() })
    c.drawRect(0f, 0f, W, 72f, Paint().apply { color = GREEN })
    c.drawRect(0f, 72f, W, 76f, Paint().apply { color = GOLD })
    c.logo(context, W - 64f, 9f, 54f)
    // Arabic centre name on a single line: shrink the text until it fits the width
    c.rtlText(Centre.name, W - 74f, 8f, 400f, fitTextSize(Centre.name, 400f, 13f, bold = true), 0xFFFFFFFF.toInt(), bold = true)
    c.rtlText(CENTRE_FR, W - 74f, 28f, 400f, fitTextSize(CENTRE_FR, 400f, 8.5f), 0xFFE8F1EC.toInt())
    val year = academicYearLabel(currentMonth())
    c.rtlText("$kindAr   ${ltr(kindFr)}   •   ${ltr(year)}", W - 74f, 46f, 400f, 12f, GOLD, bold = true)
    // photo box + number
    val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFBBBBBB.toInt(); strokeWidth = 1.5f }
    val photo = decodePhoto(photoUrl)
    if (photo != null) c.drawBitmap(photo, null, RectF(20f, 86f, 112f, 208f), Paint(Paint.FILTER_BITMAP_FLAG))
    else c.rtlText("الصورة\nPhoto", 112f, 132f, 92f, 11f, 0xFFAAAAAA.toInt(), align = Layout.Alignment.ALIGN_CENTER)
    c.drawRect(20f, 86f, 112f, 208f, box)
    c.rtlText(ltr("N° $number"), 126f, 212f, 120f, 12.5f, GREEN, bold = true, align = Layout.Alignment.ALIGN_CENTER)
    // details
    val right = W - 20f; val w = 330f
    c.rtlText(name, right, 88f, w, 20f, GREEN, bold = true)
    c.line(W - 20f - w, 114f, W - 20f, 0xFFEADFB8.toInt(), 1.2f)
    var y = 120f
    for ((ar, fr, value) in fields) {
        if (value.isBlank()) continue
        // label column (Arabic + French in capitals), then the value in bold
        c.rtlText("$ar  ${ltr(fr.uppercase())}", right, y + 1.5f, 118f, 9.5f, 0xFF8A9199.toInt())
        c.rtlText(value, right - 122f, y, w - 122f, 12f, 0xFF222222.toInt(), bold = true); y += 20f
    }
    // QR code (bottom-left of the details area)
    runCatching {
        val hints = mapOf(com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8",
            com.google.zxing.EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.L,
            com.google.zxing.EncodeHintType.MARGIN to 1)
        val m = com.google.zxing.qrcode.QRCodeWriter().encode(qr, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0, hints)
        val size = 74f; val left = 130f; val top = H - 46f - size - 4f; val cell = size / m.width
        c.drawRect(left, top, left + size, top + size, Paint().apply { color = 0xFFFFFFFF.toInt() })
        val dark = Paint().apply { color = 0xFF111111.toInt() }
        for (yy in 0 until m.height) for (xx in 0 until m.width) if (m.get(xx, yy))
            c.drawRect(left + xx * cell, top + yy * cell, left + (xx + 1) * cell, top + (yy + 1) * cell, dark)
    }
    // footer (two lines, Arabic and French)
    c.drawRect(0f, H - 46f, W, H, Paint().apply { color = GREEN })
    c.rtlText(Centre.address.ifBlank { "داودابوغو، باماكو" } + " — ${ltr("Daoudabougou, Bamako")}",
        W - 16f, H - 42f, W - 32f, 10.5f, 0xFFFFFFFF.toInt(), align = Layout.Alignment.ALIGN_CENTER)
    val contact = listOfNotNull(Centre.phone.takeIf { it.isNotBlank() }?.let { "☎ ${ltr(it)}" },
        Centre.email.takeIf { it.isNotBlank() }?.let { "✉ ${ltr(it)}" }).joinToString("   •   ")
        .ifBlank { "العام الدراسي ${ltr(year)}  •  ${ltr("Année scolaire $year")}" }
    c.rtlText(contact, W - 16f, H - 22f, W - 32f, 10.5f, GOLD, align = Layout.Alignment.ALIGN_CENTER)
    doc.finishPage(page)
    sharePdf(context, doc, fileName)
}

fun shareCertificate(context: Context, student: Student) {
    val W = 842f; val H = 595f
    val doc = PdfDocument()
    val page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), 1).create())
    val c = page.canvas
    c.drawRect(0f, 0f, W, H, Paint().apply { color = 0xFFFFFDF5.toInt() })
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    border.color = GREEN; border.strokeWidth = 10f; c.drawRect(22f, 22f, W - 22f, H - 22f, border)
    border.color = GOLD; border.strokeWidth = 2.5f; c.drawRect(38f, 38f, W - 38f, H - 38f, border)
    border.strokeWidth = 1f; c.drawRect(44f, 44f, W - 44f, H - 44f, border)
    // Islamic ornaments: rosettes in the four corners
    for ((x, y) in listOf(50f to 48f, W - 50f to 48f, 50f to H - 84f, W - 50f to H - 84f))
        c.rtlText("۞", x + 22f, y, 44f, 28f, GOLD, align = Layout.Alignment.ALIGN_CENTER)

    val inner = W - 160f; val right = W - 80f
    c.logo(context, W / 2 - 42f, 52f, 84f)
    c.rtlText(Centre.name, right, 146f, inner, 16f, GREEN, bold = true, align = Layout.Alignment.ALIGN_CENTER)
    val khatm = student.hizbs >= TOTAL_HIZBS
    c.rtlText(if (khatm) "شهادة ختم القرآن الكريم" else "شهادة تقدير", right, 182f, inner, 34f, GOLD, bold = true,
        align = Layout.Alignment.ALIGN_CENTER)
    c.rtlText("❁   ❁   ❁", right, 226f, inner, 11f, GOLD, align = Layout.Alignment.ALIGN_CENTER)
    c.rtlText("يشهد المركز بأن الطالب/ة", right, 248f, inner, 17f, GREY, align = Layout.Alignment.ALIGN_CENTER)
    c.rtlText(student.name, right, 278f, inner, 30f, GREEN, bold = true, align = Layout.Alignment.ALIGN_CENTER)
    val body = if (khatm) "قد أتمّ حفظ كتاب الله تعالى كاملًا (ستين حزبًا) بفضل الله وتوفيقه."
    else if (student.hizbs == 0) "تقديرًا لجهده ومواظبته في حلقات تحفيظ القرآن الكريم."
    else "قد أتمّ حفظ ${student.hizbs} حزبًا من كتاب الله تعالى" + (if (student.hizbs >= 2) " (≈ ${student.hizbs / 2} جزءًا)" else "") +
        "، تقديرًا لجهده ومواظبته."
    c.rtlText(body, right, 334f, inner, 18f, 0xFF222222.toInt(), align = Layout.Alignment.ALIGN_CENTER)
    c.rtlText("﴿وَلَقَدْ يَسَّرْنَا الْقُرْآنَ لِلذِّكْرِ فَهَلْ مِن مُّدَّكِرٍ﴾", right, 380f, inner, 16f, GREEN,
        align = Layout.Alignment.ALIGN_CENTER)
    c.rtlText("نسأل الله أن يجعله من أهل القرآن الذين هم أهل الله وخاصته.", right, 412f, inner, 14f, GREY,
        align = Layout.Alignment.ALIGN_CENTER)

    val date = "حُرّر بتاريخ ${ltr(today())}" + hijriToday().let { if (it.isBlank()) "" else "\nالموافق $it" }
    c.rtlText(date, right, 470f, 240f, 12f, GREY, align = Layout.Alignment.ALIGN_CENTER)
    c.rtlText("مدير المركز" + (if (Centre.director.isNotBlank()) "\n${Centre.director}" else ""),
        80f + 240f, 470f, 240f, 13f, GREY, bold = true, align = Layout.Alignment.ALIGN_CENTER)
    c.line(80f + 30f, 530f, 80f + 210f, 0xFF999999.toInt())
    if (student.teacher.isNotBlank()) {
        c.rtlText("المعلم: ${student.teacher}", W / 2 + 100f, 470f, 200f, 13f, GREY, bold = true, align = Layout.Alignment.ALIGN_CENTER)
        c.line(W / 2 - 90f, 530f, W / 2 + 90f, 0xFF999999.toInt())
    }

    doc.finishPage(page)
    sharePdf(context, doc, "certificate-${safeName(student.name)}.pdf")
}

// =====================================================================
// Administrative documents (bilingual Arabic / French), matching the centre's paper forms
// =====================================================================

private val FrenchMonths = listOf("Janvier", "Février", "Mars", "Avril", "Mai", "Juin", "Juillet", "Août",
    "Septembre", "Octobre", "Novembre", "Décembre")

private fun frenchMonth(m: String): String {
    val i = m.drop(5).toIntOrNull() ?: return m
    return "${FrenchMonths.getOrElse(i - 1) { m }} ${m.take(4)}"
}

private fun num(n: Int, dashIfZero: Boolean = false): String =
    if (n == 0 && dashIfZero) "-" else "%,d".format(java.util.Locale.US, n)

private fun frDate(d: String): String = if (d.length == 10) "${d.substring(8, 10)}/${d.substring(5, 7)}/${d.take(4)}" else d

private val CENTRE_FR = "Centre Imam Malik pour la mémorisation du Saint Coran et les études islamiques"

/** Simple table cell: border + centred (or right-aligned) text. */
private fun Canvas.cell(left: Float, top: Float, w: Float, h: Float, text: String, size: Float = 9.5f,
                        bold: Boolean = false, fill: Int? = null, color: Int = 0xFF222222.toInt(),
                        align: Layout.Alignment = Layout.Alignment.ALIGN_CENTER) {
    if (fill != null) drawRect(left, top, left + w, top + h, Paint().apply { this.color = fill })
    drawRect(left, top, left + w, top + h, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = .7f; this.color = 0xFF888888.toInt()
    })
    if (text.isNotEmpty()) {
        val est = text.split("\n").size * size * 1.25f   // approximate height to centre vertically
        rtlText(text, left + w - 3f, top + ((h - est) / 2).coerceAtLeast(1f), w - 6f, size, color, bold, align)
    }
}

/** Bilingual page header used by the administrative documents. */
private fun Canvas.adminHeader(context: Context, W: Float, titleAr: String, titleFr: String): Float {
    drawRect(0f, 0f, W, 74f, Paint().apply { color = GREEN })
    logo(context, 18f, 7f, 60f)
    rtlText(Centre.name + if (Centre.address.isNotBlank()) " — ${Centre.address}" else "", W - 20f, 10f, W - 110f, 12.5f,
        0xFFFFFFFF.toInt(), bold = true, align = Layout.Alignment.ALIGN_CENTER)
    rtlText(CENTRE_FR + if (Centre.address.isNotBlank()) " — ${Centre.address}" else "", W - 20f, 30f, W - 110f, 9.5f,
        0xFFEEEEEE.toInt(), align = Layout.Alignment.ALIGN_CENTER)
    rtlText(titleAr, W - 20f, 84f, W - 40f, 14f, GREEN, bold = true, align = Layout.Alignment.ALIGN_CENTER)
    rtlText(titleFr, W - 20f, 104f, W - 40f, 10.5f, GREY, align = Layout.Alignment.ALIGN_CENTER)
    return 126f
}

/** "État des salaires" of one month + recap by category + deposits of the academic year. */
fun sharePayrollStatement(context: Context, month: String, staff: List<Teacher>, entries: List<PayrollEntry>, year: List<PayrollEntry>) {
    val W = 842f; val H = 595f; val M = 20f
    val doc = PdfDocument()
    var pageNo = 1
    var page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), pageNo).create())
    var c = page.canvas
    val byId = entries.associateBy { it.staffId }
    val titleAr = "كشف رواتب المدرسين والمراقبين والعمال — شهر ${monthName(month)} (المبالغ ب${Centre.currency})"
    val titleFr = "État des salaires des enseignants, surveillants et ouvriers — ${frenchMonth(month)} (montants en FCFA)"
    var y = c.adminHeader(context, W, titleAr, titleFr)
    // columns from right to left
    val cols = listOf(28f to "م\nN°", 140f to "الاسم الكامل\nNom complet", 135f to "الوظيفة\nFonction",
        72f to "الراتب الأساسي\nSalaire de base", 60f to "السلفة\nAvance", 72f to "صافي الراتب\nSalaire net",
        72f to "المستلم فعلًا\nMontant perçu", 72f to "طريقة الدفع\nMode de paiement", 66f to "تاريخ التعيين\nDate d'embauche",
        66f to "تاريخ التوقف\nDate de cessation")
    val tableW = cols.sumOf { it.first.toDouble() }.toFloat()
    val right0 = W - (W - tableW) / 2
    fun row(values: List<String>, h: Float, bold: Boolean = false, fill: Int? = null) {
        if (y + h > H - 30f) {
            doc.finishPage(page); pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), pageNo).create()); c = page.canvas; y = 30f
        }
        var r = right0
        cols.forEachIndexed { i, (w, _) -> c.cell(r - w, y, w, h, values.getOrElse(i) { "" }, 9f, bold, fill); r -= w }
        y += h
    }
    fun span(text: String, h: Float, fill: Int, bold: Boolean = true) {
        c.cell(right0 - tableW, y, tableW, h, text, 9.5f, bold, fill, align = Layout.Alignment.ALIGN_NORMAL); y += h
    }
    row(cols.map { it.second }, 30f, bold = true, fill = 0xFFE8F0EC.toInt())
    var n = 0
    data class Tot(var base: Int = 0, var adv: Int = 0, var net: Int = 0, var rec: Int = 0)
    val grand = Tot()
    StaffCategory.all.forEach { cat ->
        val list = staff.filter { it.category == cat }
        if (list.isEmpty()) return@forEach
        span("${StaffCategory.plural(cat)} — ${StaffCategory.french(cat)}", 18f, 0xFFF7F5EC.toInt())
        val t = Tot()
        list.forEach { s ->
            n++
            val e = byId[s.id]
            val base = e?.base ?: s.salary; val adv = e?.advance ?: 0; val rec = e?.received ?: 0
            t.base += base; t.adv += adv; t.net += base - adv; t.rec += rec
            row(listOf("$n", s.name, s.function.ifBlank { s.category }, num(base), num(adv, true), num(base - adv),
                if (e == null) "" else num(rec), e?.paymentMode?.ifBlank { null } ?: s.paymentMode, frDate(s.hireDate), frDate(s.cessationDate)), 24f)
        }
        row(listOf("", "مجموع ${StaffCategory.plural(cat)}\nTotal ${StaffCategory.french(cat).lowercase()}", "", num(t.base), num(t.adv, true),
            num(t.net), num(t.rec)), 26f, bold = true, fill = 0xFFFAFAFA.toInt())
        grand.base += t.base; grand.adv += t.adv; grand.net += t.net; grand.rec += t.rec
    }
    row(listOf("", "المجموع العام\nTotal général", "", num(grand.base), num(grand.adv, true), num(grand.net), num(grand.rec)),
        28f, bold = true, fill = 0xFFFFF4D6.toInt())

    // ---- page 2: recap + deposits
    doc.finishPage(page); pageNo++
    page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), pageNo).create()); c = page.canvas
    y = c.adminHeader(context, W, "ملخص رواتب شهر ${monthName(month)}", "Récapitulatif des salaires — ${frenchMonth(month)}")
    val rc = listOf(170f to "الفئة\nCatégorie", 110f to "عدد الموظفين\nNombre d'employés", 150f to "إجمالي الرواتب الأساسية\nTotal des salaires de base",
        120f to "إجمالي السلف\nTotal des avances", 120f to "صافي المدفوع\nNet payé")
    val rw = rc.sumOf { it.first.toDouble() }.toFloat(); val rr = W - (W - rw) / 2
    fun rrow(v: List<String>, h: Float, bold: Boolean = false, fill: Int? = null) {
        var r = rr; rc.forEachIndexed { i, (w, _) -> c.cell(r - w, y, w, h, v.getOrElse(i) { "" }, 9.5f, bold, fill); r -= w }; y += h
    }
    rrow(rc.map { it.second }, 30f, true, 0xFFE8F0EC.toInt())
    var cnt = 0; var sb = 0; var sa = 0
    StaffCategory.all.forEach { cat ->
        val list = staff.filter { it.category == cat }; if (list.isEmpty()) return@forEach
        val b = list.sumOf { byId[it.id]?.base ?: it.salary }; val a = list.sumOf { byId[it.id]?.advance ?: 0 }
        cnt += list.size; sb += b; sa += a
        rrow(listOf("${StaffCategory.plural(cat)}\n${StaffCategory.french(cat)}", "${list.size}", num(b), num(a, true), num(b - a)), 28f)
    }
    rrow(listOf("المجموع\nTotal", "$cnt", num(sb), num(sa, true), num(sb - sa)), 28f, true, 0xFFFFF4D6.toInt())

    y += 22f
    c.rtlText("الرواتب المتروكة أمانةً لدى إدارة المركز — العام الدراسي ${academicYearLabel(month)}", W - 20f, y, W - 40f, 13f, GREEN,
        bold = true, align = Layout.Alignment.ALIGN_CENTER); y += 18f
    c.rtlText("Salaires laissés en dépôt auprès de l'administration — Année scolaire ${academicYearLabel(month)}", W - 20f, y, W - 40f, 10f,
        GREY, align = Layout.Alignment.ALIGN_CENTER); y += 20f
    val dc = listOf(30f to "م\nN°", 200f to "الاسم\nNom", 130f to "مجموع المستحق\nTotal dû", 130f to "مجموع ما استلمه\nTotal perçu",
        150f to "الرصيد المتروك لدى الإدارة\nSolde en dépôt")
    val dw = dc.sumOf { it.first.toDouble() }.toFloat(); val dr = W - (W - dw) / 2
    fun drow(v: List<String>, h: Float, bold: Boolean = false, fill: Int? = null) {
        var r = dr; dc.forEachIndexed { i, (w, _) -> c.cell(r - w, y, w, h, v.getOrElse(i) { "" }, 9.5f, bold, fill); r -= w }; y += h
    }
    drow(dc.map { it.second }, 30f, true, 0xFFE8F0EC.toInt())
    var td = 0; var tp = 0
    staff.forEachIndexed { i, s ->
        val mine = year.filter { it.staffId == s.id }
        val due = mine.sumOf { it.net }; val got = mine.sumOf { it.received }; td += due; tp += got
        if (y < H - 40f) drow(listOf("${i + 1}", s.name, num(due), num(got), num(due - got)), 20f)
    }
    if (y < H - 40f) drow(listOf("", "المجموع\nTotal", num(td), num(tp), num(td - tp)), 26f, true, 0xFFFFF4D6.toInt())
    doc.finishPage(page)
    sharePdf(context, doc, "salaires-$month.pdf")
}

/** "Fiche de réception du salaire mensuel" for one employee over the academic year. */
fun shareSalaryCard(context: Context, t: Teacher, entries: List<PayrollEntry>, month: String) {
    val W = 842f; val H = 595f
    val doc = PdfDocument()
    val page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), 1).create())
    val c = page.canvas
    val months = academicYearMonths(month)
    var y = c.adminHeader(context, W, "بطاقة استلام الراتب الشهري — العام الدراسي ${academicYearLabel(month)}",
        "Fiche de réception du salaire mensuel — Année scolaire ${academicYearLabel(month)} (${frenchMonth(months.first())} – ${frenchMonth(months.last())})")
    val byMonth = entries.associateBy { it.month }
    val bal = depositBalance(entries)
    val third = (W - 40f) / 3
    fun info(col: Int, row: Int, text: String) = c.rtlText(text, W - 20f - col * third, y + row * 20f, third - 8f, 10.5f)
    info(0, 0, "الاسم الكامل / Nom complet: ${t.name}")
    info(1, 0, "الوظيفة / Fonction: ${t.function.ifBlank { t.category }}")
    info(2, 0, "الراتب الأساسي / Salaire de base: ${num(t.salary)} FCFA")
    info(0, 1, "تاريخ التعيين / Date d'embauche: ${frDate(t.hireDate)}")
    info(1, 1, "الرصيد لدى الإدارة / Solde en dépôt: ${num(bal)} FCFA")
    info(2, 1, "تاريخ التوقف / Date de cessation: ${frDate(t.cessationDate)}")
    y += 48f
    val cols = listOf(92f to "الشهر\nMois", 70f to "الراتب الأساسي\nSalaire de base", 58f to "السلفة\nAvance",
        74f to "المستلم فعلًا\nMontant perçu", 80f to "المتروك (+) / المسحوب (−)\nLaissé (+) / Retiré (−)",
        84f to "الرصيد لدى الإدارة\nSolde en dépôt", 66f to "تاريخ الاستلام\nDate de réception",
        92f to "توقيع المدرس\nSignature de l'employé(e)", 92f to "توقيع السكرتير\nSignature du secrétaire", 92f to "توقيع المدير\nSignature du directeur")
    val tw = cols.sumOf { it.first.toDouble() }.toFloat(); val r0 = W - (W - tw) / 2
    fun row(v: List<String>, h: Float, bold: Boolean = false, fill: Int? = null) {
        var r = r0; cols.forEachIndexed { i, (w, _) -> c.cell(r - w, y, w, h, v.getOrElse(i) { "" }, 8.5f, bold, fill); r -= w }; y += h
    }
    row(cols.map { it.second }, 34f, true, 0xFFE8F0EC.toInt())
    var running = 0
    months.forEach { m ->
        val e = byMonth[m]
        if (e != null) running += e.depositChange
        val ch = e?.depositChange ?: 0
        row(listOf("${monthName(m)}\n${frenchMonth(m)}", num(e?.base ?: t.salary), if (e == null) "" else num(e.advance, true),
            if (e == null) "" else num(e.received), if (e == null) "" else (if (ch > 0) "+${num(ch)}" else if (ch < 0) "−${num(-ch)}" else "0"),
            if (e == null) "" else num(running), if (e == null) "" else frDate(e.receivedDate)), 30f)
    }
    doc.finishPage(page)
    sharePdf(context, doc, "fiche-salaire-${safeName(t.name)}.pdf")
}

/** "Registre des dépenses journalières" for one petty-cash advance. */
fun shareExpenseRegister(context: Context, a: CashAdvance, lines: List<Expense>) {
    val W = 595f; val H = 842f; val M = 30f
    val doc = PdfDocument()
    val page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), 1).create())
    val c = page.canvas
    var y = c.adminHeader(context, W, "سجل المصروفات اليومية", "Registre des dépenses journalières")
    val tw = W - 2 * M; val q = tw / 4
    fun pair(lab: String, v: String, lab2: String, v2: String) {
        c.cell(W - M - q, y, q, 30f, lab, 8.5f, true, 0xFFF7F5EC.toInt()); c.cell(W - M - 2 * q, y, q, 30f, v, 9.5f)
        c.cell(W - M - 3 * q, y, q, 30f, lab2, 8.5f, true, 0xFFF7F5EC.toInt()); c.cell(M, y, q, 30f, v2, 9.5f); y += 30f
    }
    pair("العام\nAnnée", academicYearLabel(a.month.ifBlank { currentMonth() }), "الشهر\nMois", "${monthName(a.month)}\n${frenchMonth(a.month)}")
    pair("الأسبوع\nSemaine", a.week, "تاريخ الاستلام\nDate de réception", frDate(a.receivedDate))
    pair("المبلغ المستلم من الإدارة\nMontant reçu (FCFA)", num(a.amount), "المستلم منه\nRemis par", a.remisPar)
    pair("المستلم\nReçu par", a.recuPar, "توقيع المستلم\nSignature du bénéficiaire", "")
    y += 12f
    val cols = listOf(30f to "م\nN°", 220f to "البيان\nLibellé", 85f to "المبلغ (فرنك)\nMontant (FCFA)", 75f to "التاريخ\nDate",
        tw - 410f to "ملاحظات\nObservations")
    fun row(v: List<String>, h: Float, bold: Boolean = false, fill: Int? = null) {
        var r = W - M; cols.forEachIndexed { i, (w, _) -> c.cell(r - w, y, w, h, v.getOrElse(i) { "" }, 9f, bold, fill); r -= w }; y += h
    }
    row(cols.map { it.second }, 30f, true, 0xFFE8F0EC.toInt())
    val rows = maxOf(lines.size, 20)
    val rh = if (rows > 20) ((H - y - 210f) / rows).coerceAtLeast(14f) else 19f
    for (i in 0 until rows) {
        val l = lines.getOrNull(i)
        row(listOf("${i + 1}", l?.note ?: "", l?.let { num(it.amount) } ?: "", l?.let { frDate(it.date) } ?: "", l?.observation ?: ""), rh)
    }
    val spent = lines.sumOf { it.amount }
    c.cell(W - M - 250f, y, 250f, 24f, "مجموع المصروفات / Total des dépenses", 9.5f, true, 0xFFFFF4D6.toInt())
    c.cell(W - M - 335f, y, 85f, 24f, num(spent), 10f, true, 0xFFFFF4D6.toInt()); y += 34f
    val bal = advanceBalance(a, spent)
    pair("الباقي\nReste", if (bal > 0) num(bal) else "", "العجز\nDéficit", if (bal < 0) num(-bal) else "")
    pair("المبلغ المُعاد أو المدفوع للتسوية\nMontant restitué ou payé", if (a.settlementAmount > 0) num(a.settlementAmount) else "",
        "تاريخ التسوية\nDate de règlement", frDate(a.settlementDate))
    c.cell(W - M - q, y, q, 30f, "تصفية الحساب\nClôture du compte", 8.5f, true, 0xFFF7F5EC.toInt())
    c.cell(M, y, tw - q, 30f, if (a.closed) "☑ الحساب مُصفّى / Compte soldé      ☐ غير مُصفّى / Non soldé"
        else "☐ الحساب مُصفّى / Compte soldé      ☑ غير مُصفّى / Non soldé", 9.5f); y += 46f
    val third = tw / 3
    listOf("المستلم\nLe Bénéficiaire", "السكرتير\nLe Secrétaire", "المدير\nLe Directeur" + if (Centre.director.isNotBlank()) "\n${Centre.director}" else "")
        .forEachIndexed { i, t ->
            val r = W - M - i * third
            c.rtlText(t, r, y, third - 10f, 10f, GREY, bold = true, align = Layout.Alignment.ALIGN_CENTER)
            c.line(r - third + 25f, y + 62f, r - 25f, 0xFF999999.toInt())
        }
    doc.finishPage(page)
    sharePdf(context, doc, "registre-depenses-${a.receivedDate.ifBlank { today() }}.pdf")
}


/** Monthly financial statement (bilingual titles): income, expenses by category, net. */
fun shareMonthStatement(context: Context, month: String, payments: List<Payment>, expenses: List<Expense>) {
    val W = 595f; val H = 842f; val M = 40f
    val doc = PdfDocument()
    val page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), 1).create())
    val c = page.canvas
    var y = c.adminHeader(context, W, "البيانات المالية لشهر ${monthName(month)}",
        "Situation financière — ${frenchMonth(month)} (montants en ${Centre.currency})") + 10f
    val tw = W - 2 * M
    fun line(label: String, value: String, bold: Boolean = false, fill: Int? = null) {
        c.cell(W - M - tw * .65f, y, tw * .65f, 24f, label, 10f, bold, fill, align = Layout.Alignment.ALIGN_NORMAL)
        c.cell(M, y, tw * .35f, 24f, value, 10.5f, bold, fill); y += 24f
    }
    val monthly = payments.filter { it.type == PaymentType.MONTHLY }
    val reg = payments.filter { it.type == PaymentType.REGISTRATION }
    val income = payments.sumOf { it.amount }; val out = expenses.sumOf { it.amount }
    val head = 0xFFE8F0EC.toInt(); val total = 0xFFFFF4D6.toInt()
    line("الإيرادات  /  Recettes", "", true, head)
    line("الرسوم الشهرية (${monthly.size} طالب)  /  Mensualités", num(monthly.sumOf { it.amount }))
    line("رسوم التسجيل (${reg.size})  /  Inscriptions", num(reg.sumOf { it.amount }))
    line("مجموع الإيرادات  /  Total des recettes", num(income), true, total)
    y += 14f
    line("المصروفات  /  Dépenses", "", true, head)
    expenses.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amount } }.toList().sortedByDescending { it.second }
        .forEach { (cat, v) -> line(cat, num(v)) }
    line("مجموع المصروفات  /  Total des dépenses", num(out), true, total)
    y += 14f
    line("الرصيد الصافي  /  Solde net", num(income - out), true, if (income - out >= 0) 0xFFDDF3E6.toInt() else 0xFFFBE3E3.toInt())
    y += 40f
    val third = tw / 2
    listOf("المحاسب\nLe comptable", "مدير المركز\nLe directeur" + (if (Centre.director.isNotBlank()) "\n${Centre.director}" else ""))
        .forEachIndexed { i, t ->
            val r = W - M - i * third
            c.rtlText(t, r, y, third - 20f, 11f, GREY, bold = true, align = Layout.Alignment.ALIGN_CENTER)
        }
    c.line(M, H - 50f, W - M, GOLD, 1.5f)
    c.rtlText("حُرّر بتاريخ ${ltr(today())}" + (if (Centre.contactLine().isNotBlank()) "  •  ${Centre.contactLine()}" else ""),
        W - M, H - 42f, tw, 9.5f, GREY, align = Layout.Alignment.ALIGN_CENTER)
    doc.finishPage(page)
    sharePdf(context, doc, "finance-$month.pdf")
}

/** Memorization & review report for a period (day … academic year), one row per student; daily report lists each record. */
fun shareMemoReport(context: Context, period: LogPeriod, range: PeriodRange, group: String, rows: List<MemoRow>, dayLogs: List<QuranLog>) {
    val W = 842f; val H = 595f; val M = 20f
    val doc = PdfDocument()
    var pageNo = 1
    var page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), pageNo).create())
    var c = page.canvas
    val frPeriod = mapOf(LogPeriod.DAY to "journalier", LogPeriod.WEEK to "hebdomadaire", LogPeriod.MONTH to "mensuel",
        LogPeriod.TERM to "trimestriel", LogPeriod.YEAR to "annuel")[period]
    val titleAr = "سجل متابعة الحفظ والمراجعة (${period.label}) — ${range.label}" + (if (group.isNotBlank()) " — $group" else "")
    val titleFr = "Suivi $frPeriod de la mémorisation et de la révision — du ${frDate(range.from)} au ${frDate(range.to)}"
    var y = c.adminHeader(context, W, titleAr, titleFr)
    val byStudent = dayLogs.groupBy { it.studentId }
    val cols = listOf(26f to "م\nN°", 150f to "الطالب\nÉlève", 52f to "جلسات الحفظ\nSéances", 52f to "آيات الحفظ\nVersets",
        200f to (if (period == LogPeriod.DAY) "التفاصيل\nDétails" else "السور المحفوظة\nSourates mémorisées"),
        52f to "جلسات المراجعة\nRévisions", 52f to "آيات المراجعة\nVersets", 70f to "المعدل\nAppréciation", 0f to "الأيام\nJours")
        .let { l -> l.map { (w, t) -> (if (w == 0f) W - 2 * M - l.sumOf { it.first.toDouble() }.toFloat() else w) to t } }
    fun row(v: List<String>, h: Float, bold: Boolean = false, fill: Int? = null) {
        var r = W - M; cols.forEachIndexed { i, (w, _) -> c.cell(r - w, y, w, h, v.getOrElse(i) { "" }, 8.5f, bold, fill); r -= w }; y += h
    }
    row(cols.map { it.second }, 30f, true, 0xFFE8F0EC.toInt())
    rows.forEachIndexed { i, r ->
        val detail = if (period == LogPeriod.DAY) byStudent[r.student.id].orEmpty().joinToString("\n") { l ->
            "${l.type}: ${l.surah}" + (if (l.fromAyah > 0) " ${l.fromAyah}-${l.toAyah}" else "") + (if (l.grade.isNotBlank()) " (${l.grade})" else "")
        } else r.surahs.joinToString("، ")
        val h = (18f + 11f * (detail.count { it == '\n' } + if (detail.length > 40) 1 else 0)).coerceAtMost(60f)
        if (y + h > H - 50f) {
            doc.finishPage(page); pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), pageNo).create()); c = page.canvas; y = M
            row(cols.map { it.second }, 30f, true, 0xFFE8F0EC.toInt())
        }
        row(listOf("${i + 1}", r.student.name, num(r.newSessions, true), num(r.newAyat, true), detail,
            num(r.reviewSessions, true), num(r.reviewAyat, true), r.avgGrade?.let(::gradeFromScore) ?: "-", num(r.days, true)), h,
            fill = if (r.sessions == 0) 0xFFFBE3E3.toInt() else null)
    }
    row(listOf("", "المجموع / Total", num(rows.sumOf { it.newSessions }), num(rows.sumOf { it.newAyat }), "",
        num(rows.sumOf { it.reviewSessions }), num(rows.sumOf { it.reviewAyat }), "", ""), 22f, true, 0xFFFFF4D6.toInt())
    y += 24f
    if (y < H - 60f) listOf("توقيع المعلم\nL'enseignant", "توقيع مدير المركز\nLe directeur").forEachIndexed { i, t ->
        c.rtlText(t, W - M - i * (W / 2), y, W / 2 - 40f, 10f, GREY, bold = true, align = Layout.Alignment.ALIGN_CENTER)
    }
    doc.finishPage(page)
    sharePdf(context, doc, "hifz-${period.name.lowercase()}-${range.from}.pdf")
}

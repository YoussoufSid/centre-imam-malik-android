package ml.centre.imammalik

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import androidx.compose.runtime.mutableStateMapOf
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.util.Date
import java.util.Locale

class AppState {
    val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    var firebaseUser by mutableStateOf(auth.currentUser)
    var profile by mutableStateOf<AppUser?>(null)
    private var allStudents by mutableStateOf(emptyList<Student>())
    private var allTodayAttendance by mutableStateOf(emptyList<AttendanceRecord>())
    private var allDayAttendance by mutableStateOf(emptyList<AttendanceRecord>())
    private var allRecentLogs by mutableStateOf(emptyList<QuranLog>())

    /** Teachers assigned to groups only see the students of those groups. */
    private val scopedGroups: Set<String>?
        get() = profile?.takeIf { it.role == Roles.TEACHER && it.groups.isNotEmpty() }?.groups?.toSet()

    var students: List<Student>
        get() { val g = scopedGroups; return if (g == null) allStudents else allStudents.filter { it.group in g } }
        set(v) { allStudents = v }

    /** All group names known in the centre (unfiltered), for admin screens. */
    val allGroups: List<String> get() = allStudents.map { it.group }.filter { it.isNotBlank() }.distinct().sorted()

    private fun <T> scoped(list: List<T>, id: (T) -> String): List<T> {
        if (scopedGroups == null) return list
        val ids = students.map { it.id }.toSet()
        return list.filter { id(it) in ids }
    }

    var todayAttendance: List<AttendanceRecord>
        get() = scoped(allTodayAttendance) { it.studentId }
        set(v) { allTodayAttendance = v }
    var dayAttendance: List<AttendanceRecord>
        get() = scoped(allDayAttendance) { it.studentId }
        set(v) { allDayAttendance = v }
    var recentLogs: List<QuranLog>
        get() = scoped(allRecentLogs) { it.studentId }
        set(v) { allRecentLogs = v }
    var users by mutableStateOf(emptyList<AppUser>())
    var attendanceDate by mutableStateOf(today())
    var announcements by mutableStateOf(emptyList<Announcement>())
    var feeMonth by mutableStateOf(currentMonth())
    var monthPayments by mutableStateOf(emptyList<Payment>())
    var registrationPayments by mutableStateOf(emptyList<Payment>())
    var expenseMonth by mutableStateOf(currentMonth())
    var monthExpenses by mutableStateOf(emptyList<Expense>())
    var busy by mutableStateOf(false)

    // ---------- Sync status ----------
    private val pending = mutableStateMapOf<String, Boolean>()
    private val fromCache = mutableStateMapOf<String, Boolean>()
    /** True while some local changes have not reached the server yet (offline or slow network). */
    val hasPendingWrites: Boolean get() = pending.values.any { it }
    /** True when the data shown comes from the local cache only (likely offline). */
    val isOffline: Boolean get() = fromCache.isNotEmpty() && fromCache.values.all { it }
    private fun track(key: String, snap: QuerySnapshot?) {
        snap ?: return
        pending[key] = snap.metadata.hasPendingWrites()
        fromCache[key] = snap.metadata.isFromCache
    }
    var message by mutableStateOf<String?>(null)

    private val listeners = mutableListOf<ListenerRegistration>()
    private var profileListener: ListenerRegistration? = null
    private var dayListener: ListenerRegistration? = null
    private var feeListener: ListenerRegistration? = null
    private var expenseListener: ListenerRegistration? = null
    private val linkedListeners = mutableMapOf<String, ListenerRegistration>()
    private val linkedStudents = mutableMapOf<String, Student>()

    init { firebaseUser?.let { listenProfile(it.uid, it.email ?: "") } }

    // ---------- Authentication ----------

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) { message = "أدخل البريد وكلمة المرور"; return }
        busy = true; message = null
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { r ->
                busy = false; firebaseUser = r.user
                r.user?.let { listenProfile(it.uid, it.email ?: "") }
            }
            .addOnFailureListener { busy = false; message = arabicError(it.message) }
    }

    fun register(email: String, password: String, name: String) {
        if (name.isBlank()) { message = "اكتب الاسم الكامل"; return }
        if (password.length < 6) { message = "كلمة المرور يجب أن تكون 6 أحرف على الأقل"; return }
        busy = true; message = null
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val u = result.user ?: return@addOnSuccessListener
                val role = if (isAdminEmail(email)) Roles.ADMIN else Roles.STUDENT
                db.collection("users").document(u.uid)
                    .set(mapOf("name" to name.trim(), "email" to email.trim(), "role" to role, "createdAt" to Date()))
                    .addOnCompleteListener {
                        busy = false; firebaseUser = u
                        listenProfile(u.uid, u.email ?: "")
                    }
            }.addOnFailureListener { busy = false; message = arabicError(it.message) }
    }

    fun resetPassword(email: String) {
        if (email.isBlank()) { message = "اكتب البريد الإلكتروني أولًا"; return }
        auth.sendPasswordResetEmail(email.trim())
            .addOnSuccessListener { message = "أُرسل رابط استعادة كلمة المرور إلى بريدك" }
            .addOnFailureListener { message = arabicError(it.message) }
    }

    fun logout() {
        stopData()
        profileListener?.remove(); profileListener = null
        auth.signOut()
        firebaseUser = null; profile = null
    }

    private fun isAdminEmail(email: String) = email.trim().equals(ADMIN_EMAIL, ignoreCase = true)

    // ---------- Profile & role-based data ----------

    private fun listenProfile(uid: String, email: String) {
        profileListener?.remove()
        profileListener = db.collection("users").document(uid).addSnapshotListener { snap, error ->
            if (error != null) { message = arabicError(error.message) }
            val p = snap?.takeIf { it.exists() }?.let { toUser(it) }
                ?: AppUser(uid = uid, email = email, role = Roles.STUDENT)
            val fixed = if (isAdminEmail(email)) p.copy(role = Roles.ADMIN) else p
            val old = profile
            profile = fixed
            if (old == null || old.role != fixed.role || old.studentIds != fixed.studentIds || old.groups != fixed.groups) startData(fixed)
        }
    }

    private fun stopData() {
        listeners.forEach { it.remove() }; listeners.clear()
        dayListener?.remove(); dayListener = null
        feeListener?.remove(); feeListener = null
        expenseListener?.remove(); expenseListener = null
        linkedListeners.values.forEach { it.remove() }; linkedListeners.clear(); linkedStudents.clear()
        pending.clear(); fromCache.clear()
        students = emptyList(); users = emptyList(); todayAttendance = emptyList()
        dayAttendance = emptyList(); recentLogs = emptyList()
        announcements = emptyList(); monthPayments = emptyList()
        registrationPayments = emptyList(); monthExpenses = emptyList()
    }

    private fun startData(p: AppUser) {
        stopData()
        listeners += db.collection("announcements").orderBy("createdAt", Query.Direction.DESCENDING).limit(30)
            .addSnapshotListener { snap, _ ->
                announcements = snap?.documents?.map(::toAnnouncement)?.filter { Audience.visibleTo(it.audience, p) } ?: emptyList()
            }
        if (p.isAdmin) {
            listenFees(feeMonth)
            listenExpenses(expenseMonth)
            listeners += db.collection("payments").whereEqualTo("type", PaymentType.REGISTRATION)
                .addSnapshotListener { snap, _ -> registrationPayments = snap?.documents?.map(::toPayment) ?: emptyList() }
        }
        if (p.isStaff) {
            listeners += db.collection("students").orderBy("name").addSnapshotListener(MetadataChanges.INCLUDE) { snap, e ->
                if (e != null) { message = arabicError(e.message); return@addSnapshotListener }
                track("students", snap)
                students = snap?.documents?.map(::toStudent) ?: emptyList()
            }
            listeners += db.collection("attendance").whereEqualTo("date", today()).addSnapshotListener(MetadataChanges.INCLUDE) { snap, _ ->
                track("today", snap)
                todayAttendance = snap?.documents?.map(::toAttendance) ?: emptyList()
            }
            listeners += db.collection("quranLogs").orderBy("createdAt", Query.Direction.DESCENDING).limit(300)
                .addSnapshotListener(MetadataChanges.INCLUDE) { snap, _ -> track("logs", snap); recentLogs = snap?.documents?.map(::toLog) ?: emptyList() }
            listenDay(attendanceDate)
            if (p.isAdmin) listeners += db.collection("users").addSnapshotListener { snap, _ ->
                users = snap?.documents?.map(::toUser)?.sortedBy { it.name } ?: emptyList()
            }
        } else {
            p.studentIds.forEach { id ->
                linkedListeners[id] = db.collection("students").document(id).addSnapshotListener { snap, _ ->
                    if (snap != null && snap.exists()) linkedStudents[id] = toStudent(snap) else linkedStudents.remove(id)
                    students = linkedStudents.values.sortedBy { it.name }
                }
            }
        }
    }

    // ---------- Students ----------

    fun saveStudent(student: Student, done: () -> Unit) {
        if (student.name.isBlank()) { message = "اسم الطالب مطلوب"; return }
        val data = mapOf(
            "name" to student.name.trim(), "gender" to student.gender.trim(),
            "birthDate" to student.birthDate.trim(), "level" to student.level.trim(),
            "group" to student.group.trim(), "teacher" to student.teacher.trim(),
            "phone" to student.phone.trim(), "address" to student.address.trim(),
            "enrollmentDate" to student.enrollmentDate.trim(), "status" to student.status,
            "monthlyFee" to student.monthlyFee, "registrationFee" to student.registrationFee,
            "updatedAt" to Date()
        )
        // merge keeps fields edited elsewhere (e.g. hizb progress).
        val task = if (student.id.isBlank()) db.collection("students").document().set(data)
        else db.collection("students").document(student.id).set(data, SetOptions.merge())
        // Writes are applied locally at once (offline cache), so close the form immediately.
        done(); message = if (student.id.isBlank()) "تمت إضافة الطالب" else "تم تحديث بيانات الطالب"
        task.addOnFailureListener { message = arabicError(it.message) }
    }

    fun setHizbs(student: Student, hizbs: Int) {
        val n = hizbs.coerceIn(0, TOTAL_HIZBS)
        db.collection("students").document(student.id)
            .update(mapOf("hizbs" to n, "updatedAt" to Date()))
            .addOnFailureListener { message = arabicError(it.message) }
        if (n == TOTAL_HIZBS && student.hizbs < TOTAL_HIZBS) message = "مبارك! أتمّ ${student.name} حفظ القرآن الكريم 🎉"
    }

    fun deleteStudent(student: Student, done: () -> Unit) {
        done(); message = "تم حذف ${student.name}"
        db.collection("students").document(student.id).delete()
            .addOnFailureListener { message = arabicError(it.message) }
    }

    // ---------- Attendance ----------

    fun changeAttendanceDate(delta: Int) {
        val next = shiftDay(attendanceDate, delta)
        if (next > today()) return
        attendanceDate = next
        listenDay(next)
    }

    private fun listenDay(day: String) {
        dayListener?.remove()
        dayListener = db.collection("attendance").whereEqualTo("date", day).addSnapshotListener(MetadataChanges.INCLUDE) { snap, e ->
            if (e != null) { message = arabicError(e.message); return@addSnapshotListener }
            track("day", snap)
            dayAttendance = snap?.documents?.map(::toAttendance) ?: emptyList()
        }
    }

    fun setAttendance(student: Student, status: String, date: String = attendanceDate) {
        db.collection("attendance").document("${date}_${student.id}").set(
            mapOf(
                "studentId" to student.id, "studentName" to student.name, "date" to date,
                "status" to status, "present" to AttendanceStatus.isPresent(status),
                "recordedBy" to (profile?.name ?: ""), "updatedAt" to Date()
            )
        ).addOnFailureListener { message = arabicError(it.message) }
    }

    fun markAllPresent(list: List<Student>) {
        val marked = dayAttendance.map { it.studentId }.toSet()
        val pending = list.filter { it.id !in marked }
        if (pending.isEmpty()) { message = "جميع الطلاب مسجلون"; return }
        pending.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { s ->
                batch.set(db.collection("attendance").document("${attendanceDate}_${s.id}"), mapOf(
                    "studentId" to s.id, "studentName" to s.name, "date" to attendanceDate,
                    "status" to AttendanceStatus.PRESENT, "present" to true,
                    "recordedBy" to (profile?.name ?: ""), "updatedAt" to Date()
                ))
            }
            batch.commit().addOnFailureListener { message = arabicError(it.message) }
        }
        message = "سُجّل حضور ${pending.size} طالب"
    }

    // ---------- Quran memorization ----------

    fun saveLog(log: QuranLog, done: () -> Unit) {
        if (log.studentId.isBlank()) { message = "اختر الطالب"; return }
        if (log.surah.isBlank()) { message = "اختر السورة"; return }
        if (log.fromAyah > 0 && log.toAyah > 0 && log.toAyah < log.fromAyah) {
            message = "رقم آية النهاية أصغر من البداية"; return
        }
        val data = mapOf(
            "studentId" to log.studentId, "studentName" to log.studentName, "date" to log.date,
            "type" to log.type, "surah" to log.surah, "fromAyah" to log.fromAyah, "toAyah" to log.toAyah,
            "grade" to log.grade, "notes" to log.notes.trim(),
            "teacherName" to (profile?.name ?: ""), "createdAt" to Date()
        )
        done(); message = "تم تسجيل ${log.type} لـ ${log.studentName}"
        db.collection("quranLogs").add(data).addOnFailureListener { message = arabicError(it.message) }
    }

    fun deleteLog(log: QuranLog) {
        db.collection("quranLogs").document(log.id).delete()
            .addOnSuccessListener { message = "تم حذف السجل" }
            .addOnFailureListener { message = arabicError(it.message) }
    }

    /** Live history of one student (used by the profile screen). Caller must remove the returned listeners. */
    fun listenStudentHistory(
        studentId: String,
        onAttendance: (List<AttendanceRecord>) -> Unit,
        onLogs: (List<QuranLog>) -> Unit
    ): List<ListenerRegistration> = listOf(
        db.collection("attendance").whereEqualTo("studentId", studentId).addSnapshotListener { snap, _ ->
            onAttendance(snap?.documents?.map(::toAttendance)?.sortedByDescending { it.date } ?: emptyList())
        },
        db.collection("quranLogs").whereEqualTo("studentId", studentId).addSnapshotListener { snap, _ ->
            onLogs(snap?.documents?.map(::toLog)?.sortedWith(compareByDescending<QuranLog> { it.date }.thenByDescending { it.createdAt }) ?: emptyList())
        }
    )

    // ---------- Announcements ----------

    fun postAnnouncement(title: String, body: String, audience: String, done: () -> Unit) {
        if (title.isBlank()) { message = "اكتب عنوان الإعلان"; return }
        done(); message = "تم نشر الإعلان"
        db.collection("announcements").add(mapOf(
            "title" to title.trim(), "body" to body.trim(), "audience" to audience,
            "author" to (profile?.name?.ifBlank { null } ?: "إدارة المركز"), "createdAt" to Date()
        )).addOnFailureListener { message = arabicError(it.message) }
    }

    fun deleteAnnouncement(a: Announcement) {
        db.collection("announcements").document(a.id).delete()
            .addOnSuccessListener { message = "تم حذف الإعلان" }
            .addOnFailureListener { message = arabicError(it.message) }
    }

    // ---------- Fees ----------

    fun changeFeeMonth(delta: Int) {
        val next = shiftMonth(feeMonth, delta)
        if (next > currentMonth()) return
        feeMonth = next
        listenFees(next)
    }

    private fun listenFees(month: String) {
        feeListener?.remove()
        feeListener = db.collection("payments").whereEqualTo("month", month).addSnapshotListener(MetadataChanges.INCLUDE) { snap, e ->
            if (e != null) { message = arabicError(e.message); return@addSnapshotListener }
            track("fees", snap)
            monthPayments = snap?.documents?.map(::toPayment)?.filter { it.type == PaymentType.MONTHLY } ?: emptyList()
        }
    }

    fun savePayment(student: Student, month: String, amount: Int, done: () -> Unit) {
        if (amount <= 0) { message = "أدخل المبلغ المدفوع"; return }
        done(); message = "سُجّل دفع ${student.name} لشهر ${monthName(month)}"
        db.collection("payments").document("${month}_${student.id}").set(mapOf(
            "studentId" to student.id, "studentName" to student.name, "month" to month,
            "amount" to amount, "paidOn" to today(), "recordedBy" to (profile?.name ?: ""),
            "type" to PaymentType.MONTHLY, "updatedAt" to Date()
        )).addOnFailureListener { message = arabicError(it.message) }
    }

    fun saveRegistrationPayment(student: Student, amount: Int, done: () -> Unit) {
        if (amount <= 0) { message = "أدخل المبلغ المدفوع"; return }
        done(); message = "سُجّلت رسوم تسجيل ${student.name}"
        db.collection("payments").document("reg_${student.id}").set(mapOf(
            "studentId" to student.id, "studentName" to student.name, "month" to currentMonth(),
            "amount" to amount, "paidOn" to today(), "recordedBy" to (profile?.name ?: ""),
            "type" to PaymentType.REGISTRATION, "updatedAt" to Date()
        )).addOnFailureListener { message = arabicError(it.message) }
    }

    // ---------- Expenses ----------

    fun changeExpenseMonth(delta: Int) {
        val next = shiftMonth(expenseMonth, delta)
        if (next > currentMonth()) return
        expenseMonth = next
        listenExpenses(next)
    }

    private fun listenExpenses(month: String) {
        expenseListener?.remove()
        expenseListener = db.collection("expenses").whereEqualTo("month", month).addSnapshotListener(MetadataChanges.INCLUDE) { snap, e ->
            if (e != null) { message = arabicError(e.message); return@addSnapshotListener }
            track("expenses", snap)
            monthExpenses = snap?.documents?.map(::toExpense)?.sortedByDescending { it.date } ?: emptyList()
        }
    }

    fun saveExpense(e: Expense, done: () -> Unit) {
        if (e.amount <= 0) { message = "أدخل المبلغ"; return }
        if (e.category.isBlank()) { message = "اختر نوع المصروف"; return }
        val date = e.date.trim().ifBlank { today() }
        done(); message = "تم تسجيل المصروف"
        val data = mapOf("category" to e.category, "amount" to e.amount, "date" to date, "month" to date.take(7),
            "note" to e.note.trim(), "recordedBy" to (profile?.name ?: ""), "updatedAt" to Date())
        val ref = if (e.id.isBlank()) db.collection("expenses").document() else db.collection("expenses").document(e.id)
        ref.set(data).addOnFailureListener { message = arabicError(it.message) }
    }

    fun deleteExpense(e: Expense) {
        db.collection("expenses").document(e.id).delete()
            .addOnSuccessListener { message = "تم حذف المصروف" }
            .addOnFailureListener { message = arabicError(it.message) }
    }

    /** Loads all payments and expenses of a year (yyyy) for the financial summary. */
    fun loadYear(year: String, onReady: (List<Payment>, List<Expense>) -> Unit) {
        db.collection("payments").whereGreaterThanOrEqualTo("month", "$year-01").whereLessThanOrEqualTo("month", "$year-12")
            .get().addOnSuccessListener { ps ->
                db.collection("expenses").whereGreaterThanOrEqualTo("month", "$year-01").whereLessThanOrEqualTo("month", "$year-12")
                    .get().addOnSuccessListener { es ->
                        onReady(ps.documents.map(::toPayment), es.documents.map(::toExpense))
                    }.addOnFailureListener { message = arabicError(it.message) }
            }.addOnFailureListener { message = arabicError(it.message) }
    }

    fun deletePayment(p: Payment) {
        db.collection("payments").document(p.id).delete()
            .addOnSuccessListener { message = "تم إلغاء الدفع" }
            .addOnFailureListener { message = arabicError(it.message) }
    }

    // ---------- Islamic studies grades ----------

    /** Saves one grade per entry; entries with a blank score are skipped. */
    fun saveGrades(subject: String, term: String, date: String, entries: List<Pair<Student, String>>, notes: String = "", done: () -> Unit) {
        val valid = entries.mapNotNull { (s, raw) ->
            val v = raw.replace(',', '.').trim().toDoubleOrNull() ?: return@mapNotNull null
            if (v < 0 || v > MAX_SCORE) { message = "الدرجة يجب أن تكون بين 0 و$MAX_SCORE"; return }
            s to v
        }
        if (valid.isEmpty()) { message = "أدخل درجة واحدة على الأقل"; return }
        valid.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (s, v) ->
                batch.set(db.collection("grades").document(), mapOf(
                    "studentId" to s.id, "studentName" to s.name, "subject" to subject, "term" to term,
                    "score" to v, "date" to date, "notes" to notes.trim(),
                    "teacherName" to (profile?.name ?: ""), "createdAt" to Date()
                ))
            }
            batch.commit().addOnFailureListener { message = arabicError(it.message) }
        }
        done(); message = "سُجّلت ${valid.size} درجة في $subject"
    }

    fun deleteGrade(g: Grade) {
        db.collection("grades").document(g.id).delete()
            .addOnSuccessListener { message = "تم حذف الدرجة" }
            .addOnFailureListener { message = arabicError(it.message) }
    }

    fun listenStudentGrades(studentId: String, onGrades: (List<Grade>) -> Unit): ListenerRegistration =
        db.collection("grades").whereEqualTo("studentId", studentId).addSnapshotListener { snap, _ ->
            onGrades(snap?.documents?.map { d ->
                Grade(
                    id = d.id,
                    studentId = d.getString("studentId") ?: "",
                    studentName = d.getString("studentName") ?: "",
                    subject = d.getString("subject") ?: "",
                    term = d.getString("term") ?: "",
                    score = d.getDouble("score") ?: 0.0,
                    date = d.getString("date") ?: "",
                    notes = d.getString("notes") ?: "",
                    teacherName = d.getString("teacherName") ?: ""
                )
            }?.sortedByDescending { it.date } ?: emptyList())
        }

    fun listenStudentPayments(studentId: String, onPayments: (List<Payment>) -> Unit): ListenerRegistration =
        db.collection("payments").whereEqualTo("studentId", studentId).addSnapshotListener { snap, _ ->
            onPayments(snap?.documents?.map(::toPayment)?.sortedByDescending { it.month } ?: emptyList())
        }

    // ---------- Analytics ----------

    /** Loads one month of attendance and recitation logs (scoped to the students this user can see). */
    fun loadMonthStats(month: String, onReady: (List<AttendanceRecord>, List<QuranLog>) -> Unit) {
        db.collection("attendance").whereGreaterThanOrEqualTo("date", "$month-01").whereLessThanOrEqualTo("date", "$month-31")
            .get().addOnSuccessListener { a ->
                db.collection("quranLogs").whereGreaterThanOrEqualTo("date", "$month-01").whereLessThanOrEqualTo("date", "$month-31")
                    .get().addOnSuccessListener { l ->
                        val ids = students.map { it.id }.toSet()
                        onReady(a.documents.map(::toAttendance).filter { it.studentId in ids },
                            l.documents.map(::toLog).filter { it.studentId in ids })
                    }.addOnFailureListener { message = arabicError(it.message) }
            }.addOnFailureListener { message = arabicError(it.message) }
    }

    // ---------- CSV export (admin) ----------

    private fun csv(rows: List<List<Any?>>): String = rows.joinToString("\r\n") { row ->
        row.joinToString(";") { v ->
            val t = (v ?: "").toString()
            if (t.any { it == ';' || it == '"' || it == '\n' }) "\"" + t.replace("\"", "\"\"") + "\"" else t
        }
    }

    fun studentsCsv(): String = csv(listOf(listOf("الاسم", "الجنس", "تاريخ الميلاد", "المستوى", "الحلقة", "المعلم",
        "هاتف ولي الأمر", "العنوان", "تاريخ التسجيل", "الحالة", "الأحزاب المحفوظة", "الرسوم الشهرية")) +
        allStudents.map { listOf(it.name, it.gender, it.birthDate, it.level, it.group, it.teacher, it.phone, it.address,
            it.enrollmentDate, it.status, it.hizbs, it.monthlyFee) })

    fun attendanceCsv(month: String, onReady: (String) -> Unit) {
        db.collection("attendance").whereGreaterThanOrEqualTo("date", "$month-01").whereLessThanOrEqualTo("date", "$month-31")
            .get().addOnSuccessListener { snap ->
                val recs = snap.documents.map(::toAttendance).groupBy { it.studentId }
                val header = listOf("الاسم", "الحلقة") + AttendanceStatus.all + listOf("أيام مسجلة", "نسبة الحضور ٪")
                onReady(csv(listOf(header) + allStudents.filter { it.status == "نشط" || it.id in recs }.map { s ->
                    val r = recs[s.id].orEmpty()
                    val present = r.count { AttendanceStatus.isPresent(it.status) }
                    listOf(s.name, s.group) + AttendanceStatus.all.map { st -> r.count { it.status == st } } +
                        listOf(r.size, if (r.isEmpty()) "" else present * 100 / r.size)
                }))
            }.addOnFailureListener { message = arabicError(it.message) }
    }

    fun feesCsv(month: String, onReady: (String) -> Unit) {
        db.collection("payments").whereEqualTo("month", month).get().addOnSuccessListener { snap ->
            val paid = snap.documents.map(::toPayment).associateBy { it.studentId }
            onReady(csv(listOf(listOf("الاسم", "الحلقة", "الرسوم الشهرية", "المدفوع", "تاريخ الدفع", "الحالة")) +
                allStudents.filter { (it.status == "نشط" && it.monthlyFee > 0) || it.id in paid }.map { s ->
                    val p = paid[s.id]
                    listOf(s.name, s.group, s.monthlyFee, p?.amount ?: 0, p?.paidOn ?: "", if (p != null) "مدفوع" else "غير مدفوع")
                }))
        }.addOnFailureListener { message = arabicError(it.message) }
    }

    fun gradesCsv(onReady: (String) -> Unit) {
        db.collection("grades").get().addOnSuccessListener { snap ->
            val groupOf = allStudents.associate { it.id to it.group }
            onReady(csv(listOf(listOf("الاسم", "الحلقة", "المادة", "الفصل", "الدرجة من $MAX_SCORE", "التاريخ", "المعلم", "ملاحظة")) +
                snap.documents.map { d ->
                    listOf(d.getString("studentName"), groupOf[d.getString("studentId")] ?: "", d.getString("subject"),
                        d.getString("term"), scoreText(d.getDouble("score") ?: 0.0), d.getString("date"),
                        d.getString("teacherName"), d.getString("notes"))
                }.sortedWith(compareBy({ it[0].toString() }, { it[2].toString() }))))
        }.addOnFailureListener { message = arabicError(it.message) }
    }

    fun expensesCsv(month: String, onReady: (String) -> Unit) {
        db.collection("expenses").whereEqualTo("month", month).get().addOnSuccessListener { snap ->
            val list = snap.documents.map(::toExpense).sortedBy { it.date }
            onReady(csv(listOf(listOf("التاريخ", "النوع", "المبلغ", "ملاحظة", "سجّله")) +
                list.map { listOf(it.date, it.category, it.amount, it.note, it.recordedBy) } +
                listOf(listOf("", "المجموع", list.sumOf { it.amount }, "", ""))))
        }.addOnFailureListener { message = arabicError(it.message) }
    }

    fun financeSummaryCsv(year: String, onReady: (String) -> Unit) = loadYear(year) { ps, es ->
        val rows = (1..12).map { "%s-%02d".format(Locale.US, year, it) }.map { m ->
            val monthly = ps.filter { it.month == m && it.type == PaymentType.MONTHLY }.sumOf { it.amount }
            val reg = ps.filter { it.month == m && it.type == PaymentType.REGISTRATION }.sumOf { it.amount }
            val exp = es.filter { it.month == m }.sumOf { it.amount }
            listOf(monthName(m), monthly, reg, monthly + reg, exp, monthly + reg - exp)
        }
        val totals = (1..5).map { i -> rows.sumOf { it[i] as Int } }
        onReady(csv(listOf(listOf("الشهر", "الرسوم الشهرية", "رسوم التسجيل", "مجموع الإيرادات", "المصروفات", "الصافي")) +
            rows + listOf(listOf<Any?>("المجموع") + totals)))
    }

    // ---------- Users (admin) ----------

    fun updateUser(user: AppUser, role: String, studentIds: List<String>, groups: List<String> = emptyList()) {
        db.collection("users").document(user.uid)
            .update(mapOf("role" to role, "studentIds" to studentIds, "groups" to groups, "updatedAt" to FieldValue.serverTimestamp()))
            .addOnSuccessListener { message = "تم تحديث حساب ${user.name.ifBlank { user.email }}" }
            .addOnFailureListener { message = arabicError(it.message) }
    }

    // ---------- Mapping ----------

    private fun toStudent(d: DocumentSnapshot) = Student(
        id = d.id,
        name = d.getString("name") ?: "",
        gender = d.getString("gender") ?: "",
        birthDate = d.getString("birthDate") ?: "",
        level = d.getString("level") ?: "",
        group = d.getString("group") ?: "",
        teacher = d.getString("teacher") ?: "",
        phone = d.getString("phone") ?: "",
        address = d.getString("address") ?: "",
        enrollmentDate = d.getString("enrollmentDate") ?: "",
        status = d.getString("status") ?: "نشط",
        hizbs = d.getLong("hizbs")?.toInt() ?: 0,
        monthlyFee = d.getLong("monthlyFee")?.toInt() ?: 0,
        registrationFee = d.getLong("registrationFee")?.toInt() ?: 0
    )

    private fun toAnnouncement(d: DocumentSnapshot) = Announcement(
        id = d.id,
        title = d.getString("title") ?: "",
        body = d.getString("body") ?: "",
        audience = d.getString("audience") ?: Audience.ALL,
        author = d.getString("author") ?: "",
        createdAt = d.getDate("createdAt")?.time ?: 0
    )

    private fun toPayment(d: DocumentSnapshot) = Payment(
        id = d.id,
        studentId = d.getString("studentId") ?: "",
        studentName = d.getString("studentName") ?: "",
        month = d.getString("month") ?: "",
        amount = d.getLong("amount")?.toInt() ?: 0,
        paidOn = d.getString("paidOn") ?: "",
        recordedBy = d.getString("recordedBy") ?: "",
        type = d.getString("type") ?: PaymentType.MONTHLY
    )

    private fun toExpense(d: DocumentSnapshot) = Expense(
        id = d.id,
        category = d.getString("category") ?: "",
        amount = d.getLong("amount")?.toInt() ?: 0,
        date = d.getString("date") ?: "",
        month = d.getString("month") ?: "",
        note = d.getString("note") ?: "",
        recordedBy = d.getString("recordedBy") ?: ""
    )

    private fun toAttendance(d: DocumentSnapshot) = AttendanceRecord(
        id = d.id,
        studentId = d.getString("studentId") ?: "",
        studentName = d.getString("studentName") ?: "",
        date = d.getString("date") ?: "",
        // Older records only stored a boolean "present".
        status = d.getString("status")
            ?: if (d.getBoolean("present") == false) AttendanceStatus.ABSENT else AttendanceStatus.PRESENT
    )

    private fun toLog(d: DocumentSnapshot) = QuranLog(
        id = d.id,
        studentId = d.getString("studentId") ?: "",
        studentName = d.getString("studentName") ?: "",
        date = d.getString("date") ?: "",
        type = d.getString("type") ?: LogTypes.NEW,
        surah = d.getString("surah") ?: "",
        fromAyah = d.getLong("fromAyah")?.toInt() ?: 0,
        toAyah = d.getLong("toAyah")?.toInt() ?: 0,
        grade = d.getString("grade") ?: "",
        notes = d.getString("notes") ?: "",
        teacherName = d.getString("teacherName") ?: "",
        createdAt = d.getDate("createdAt")?.time ?: 0
    )

    @Suppress("UNCHECKED_CAST")
    private fun toUser(d: DocumentSnapshot) = AppUser(
        uid = d.id,
        name = d.getString("name") ?: "",
        email = d.getString("email") ?: "",
        role = d.getString("role") ?: Roles.STUDENT,
        studentIds = (d.get("studentIds") as? List<String>) ?: emptyList(),
        groups = (d.get("groups") as? List<String>) ?: emptyList()
    )

    private fun arabicError(raw: String?) = when {
        raw == null -> "حدث خطأ غير متوقع"
        raw.contains("password", true) || raw.contains("credential", true) -> "البريد أو كلمة المرور غير صحيحة"
        raw.contains("already in use", true) -> "هذا البريد مسجل مسبقًا"
        raw.contains("badly formatted", true) -> "صيغة البريد الإلكتروني غير صحيحة"
        raw.contains("network", true) -> "لا يوجد اتصال؛ حاول عند عودة الإنترنت"
        raw.contains("permission", true) -> "ليست لديك صلاحية لتنفيذ هذا الإجراء"
        else -> raw
    }
}

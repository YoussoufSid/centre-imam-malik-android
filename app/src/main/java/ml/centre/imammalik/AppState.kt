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
import com.google.firebase.firestore.WriteBatch
import com.google.android.gms.tasks.Tasks
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
    val allGroups: List<String> get() = (halaqat.map { it.name } + allStudents.map { it.group })
        .filter { it.isNotBlank() }.distinct().sorted()

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
    var halaqat by mutableStateOf(emptyList<Halaqa>())
    var teachers by mutableStateOf(emptyList<Teacher>())
    var teacherDate by mutableStateOf(today())
    var teacherDayAttendance by mutableStateOf(emptyList<AttendanceRecord>())
    var salaryMonth by mutableStateOf(currentMonth())
    var monthPayroll by mutableStateOf(emptyList<PayrollEntry>())
    var advances by mutableStateOf(emptyList<CashAdvance>())
    var busy by mutableStateOf(false)
    /** Bumped whenever centre settings change, so screens showing them recompose. */
    var settingsVersion by mutableStateOf(0)

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
    private var teacherDayListener: ListenerRegistration? = null
    private var salaryListener: ListenerRegistration? = null
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
        teacherDayListener?.remove(); teacherDayListener = null
        salaryListener?.remove(); salaryListener = null
        linkedListeners.values.forEach { it.remove() }; linkedListeners.clear(); linkedStudents.clear()
        pending.clear(); fromCache.clear()
        students = emptyList(); users = emptyList(); todayAttendance = emptyList()
        dayAttendance = emptyList(); recentLogs = emptyList()
        announcements = emptyList(); monthPayments = emptyList(); halaqat = emptyList()
        teachers = emptyList(); teacherDayAttendance = emptyList(); monthPayroll = emptyList(); advances = emptyList()
        registrationPayments = emptyList(); monthExpenses = emptyList()
    }

    private fun startData(p: AppUser) {
        stopData()
        listeners += db.collection("settings").document("centre").addSnapshotListener { snap, _ ->
            if (snap != null && snap.exists()) {
                Centre.name = snap.getString("name")?.ifBlank { null } ?: DEFAULT_CENTRE_NAME
                Centre.director = snap.getString("director") ?: ""
                Centre.phone = snap.getString("phone") ?: ""
                Centre.address = snap.getString("address") ?: ""
                Centre.academicYear = snap.getString("academicYear") ?: ""
                Centre.currency = snap.getString("currency")?.ifBlank { null } ?: "فرنك"
                settingsVersion++
            }
        }
        listeners += db.collection("announcements").orderBy("createdAt", Query.Direction.DESCENDING).limit(30)
            .addSnapshotListener { snap, _ ->
                announcements = snap?.documents?.map(::toAnnouncement)?.filter { Audience.visibleTo(it.audience, p) } ?: emptyList()
            }
        if (p.isAdmin) {
            listeners += db.collection("teachers").addSnapshotListener { snap, _ ->
                teachers = snap?.documents?.map(::toTeacher)?.sortedBy { it.name } ?: emptyList()
            }
            listeners += db.collection("cashAdvances").addSnapshotListener { snap, _ ->
                advances = snap?.documents?.map(::toAdvance)?.sortedByDescending { it.receivedDate } ?: emptyList()
            }
            listenTeacherDay(teacherDate)
            listenSalaries(salaryMonth)
            listenFees(feeMonth)
            listenExpenses(expenseMonth)
            listeners += db.collection("payments").whereEqualTo("type", PaymentType.REGISTRATION)
                .addSnapshotListener { snap, _ -> registrationPayments = snap?.documents?.map(::toPayment) ?: emptyList() }
        }
        if (p.isStaff) {
            listeners += db.collection("halaqat").addSnapshotListener { snap, _ ->
                halaqat = snap?.documents?.map { d ->
                    Halaqa(d.id, d.getString("name") ?: "", d.getString("teacher") ?: "",
                        d.getString("schedule") ?: "", d.getString("notes") ?: "")
                }?.sortedBy { it.name } ?: emptyList()
            }
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
            "photo" to student.photo, "updatedAt" to Date()
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

    /** Deletes a student and everything linked to them, and unlinks them from parent accounts. */
    fun deleteStudent(student: Student, done: () -> Unit) {
        done(); message = "جارٍ حذف ${student.name} وسجلاته…"
        val related = listOf("attendance", "quranLogs", "grades", "payments").map {
            db.collection(it).whereEqualTo("studentId", student.id).get()
        }
        val parents = db.collection("users").whereArrayContains("studentIds", student.id).get()
        Tasks.whenAllComplete(related + parents).addOnCompleteListener {
            val refs = related.filter { it.isSuccessful }.flatMap { t -> t.result.documents.map { it.reference } }
            val ops = refs.map { r -> { b: WriteBatch -> b.delete(r); Unit } } +
                (if (parents.isSuccessful) parents.result.documents.map { d ->
                    { b: WriteBatch -> b.update(d.reference, "studentIds", FieldValue.arrayRemove(student.id)); Unit }
                } else emptyList()) +
                listOf({ b: WriteBatch -> b.delete(db.collection("students").document(student.id)); Unit })
            ops.chunked(450).forEach { chunk ->
                val batch = db.batch(); chunk.forEach { it(batch) }
                batch.commit().addOnFailureListener { e -> message = arabicError(e.message) }
            }
            message = "تم حذف ${student.name} و${refs.size} سجلًا مرتبطًا به"
            audit("حذف طالب", "${student.name} (${student.group.ifBlank { "بدون حلقة" }}) مع ${refs.size} سجل")
        }
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
        audit("حذف تسميع", "${log.studentName} — ${log.type} سورة ${log.surah} (${log.date})")
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
        audit("دفع رسوم شهرية", "${student.name} — ${monthName(month)} — ${money(amount)}")
    }

    fun saveRegistrationPayment(student: Student, amount: Int, done: () -> Unit) {
        if (amount <= 0) { message = "أدخل المبلغ المدفوع"; return }
        done(); message = "سُجّلت رسوم تسجيل ${student.name}"
        db.collection("payments").document("reg_${student.id}").set(mapOf(
            "studentId" to student.id, "studentName" to student.name, "month" to currentMonth(),
            "amount" to amount, "paidOn" to today(), "recordedBy" to (profile?.name ?: ""),
            "type" to PaymentType.REGISTRATION, "updatedAt" to Date()
        )).addOnFailureListener { message = arabicError(it.message) }
        audit("دفع رسوم تسجيل", "${student.name} — ${money(amount)}")
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
        ref.set(data, SetOptions.merge()).addOnFailureListener { message = arabicError(it.message) }
        audit(if (e.id.isBlank()) "تسجيل مصروف" else "تعديل مصروف", "${e.category} — ${money(e.amount)} (${date})")
    }

    fun deleteExpense(e: Expense) {
        db.collection("expenses").document(e.id).delete()
            .addOnSuccessListener { message = "تم حذف المصروف" }
            .addOnFailureListener { message = arabicError(it.message) }
        audit("حذف مصروف", "${e.category} — ${money(e.amount)} (${e.date}) ${e.note}".trim())
    }

    /** Loads all payments and expenses of a year (yyyy) for the financial summary. */
    fun loadYear(year: String, onReady: (List<Payment>, List<Expense>) -> Unit) = loadMonths("$year-01", "$year-12", onReady)

    /** Payments and expenses whose month is between [first] and [last] (yyyy-MM, inclusive). */
    fun loadMonths(first: String, last: String, onReady: (List<Payment>, List<Expense>) -> Unit) {
        db.collection("payments").whereGreaterThanOrEqualTo("month", first).whereLessThanOrEqualTo("month", last)
            .get().addOnSuccessListener { ps ->
                db.collection("expenses").whereGreaterThanOrEqualTo("month", first).whereLessThanOrEqualTo("month", last)
                    .get().addOnSuccessListener { es ->
                        onReady(ps.documents.map(::toPayment), es.documents.map(::toExpense))
                    }.addOnFailureListener { message = arabicError(it.message) }
            }.addOnFailureListener { message = arabicError(it.message) }
    }

    fun deletePayment(p: Payment) {
        db.collection("payments").document(p.id).delete()
            .addOnSuccessListener { message = "تم إلغاء الدفع" }
            .addOnFailureListener { message = arabicError(it.message) }
        audit("إلغاء دفع", "${p.studentName} — ${if (p.type == PaymentType.REGISTRATION) "رسوم تسجيل" else monthName(p.month)} — ${money(p.amount)}")
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
        audit("حذف درجة", "${g.studentName} — ${g.subject} ${scoreText(g.score)}/$MAX_SCORE (${g.date})")
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

    // ---------- Halaqat ----------

    fun saveHalaqa(h: Halaqa, done: () -> Unit) {
        val name = h.name.trim()
        if (name.isBlank()) { message = "اسم الحلقة مطلوب"; return }
        if (halaqat.any { it.name == name && it.id != h.id }) { message = "توجد حلقة بهذا الاسم"; return }
        val old = halaqat.firstOrNull { it.id == h.id }
        val data = mapOf("name" to name, "teacher" to h.teacher.trim(), "schedule" to h.schedule.trim(),
            "notes" to h.notes.trim(), "updatedAt" to Date())
        val ref = if (h.id.isBlank()) db.collection("halaqat").document() else db.collection("halaqat").document(h.id)
        ref.set(data).addOnFailureListener { message = arabicError(it.message) }
        // Renaming a halaqa (or changing its teacher) updates its students so nothing gets lost.
        if (old != null && (old.name != name || old.teacher != h.teacher.trim())) {
            allStudents.filter { it.group == old.name }.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { s ->
                    val upd = mutableMapOf<String, Any>("group" to name, "updatedAt" to Date())
                    if (s.teacher.isBlank() || s.teacher == old.teacher) upd["teacher"] = h.teacher.trim()
                    batch.update(db.collection("students").document(s.id), upd)
                }
                batch.commit().addOnFailureListener { message = arabicError(it.message) }
            }
        }
        done(); message = if (h.id.isBlank()) "تمت إضافة حلقة «$name»" else "تم تحديث الحلقة"
    }

    fun deleteHalaqa(h: Halaqa) {
        db.collection("halaqat").document(h.id).delete()
            .addOnSuccessListener { message = "حُذفت الحلقة (يبقى طلابها بلا تغيير)" }
            .addOnFailureListener { message = arabicError(it.message) }
        audit("حذف حلقة", h.name)
    }

    /** Adds many students at once; names that already exist in the same halaqa are skipped. */
    fun bulkAddStudents(list: List<ParsedStudent>, group: String, gender: String, monthlyFee: Int, registrationFee: Int,
                        done: (Int) -> Unit) {
        val existing = allStudents.filter { it.group == group }.map { it.name.trim() }.toSet()
        val toAdd = list.filter { it.name !in existing }
        if (toAdd.isEmpty()) { message = "كل الأسماء موجودة مسبقًا في هذه الحلقة"; return }
        val teacher = halaqat.firstOrNull { it.name == group }?.teacher ?: ""
        toAdd.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { p ->
                batch.set(db.collection("students").document(), mapOf(
                    "name" to p.name, "gender" to gender, "birthDate" to "", "level" to "", "group" to group,
                    "teacher" to teacher, "phone" to p.phone, "address" to "", "enrollmentDate" to today(),
                    "status" to "نشط", "monthlyFee" to monthlyFee, "registrationFee" to registrationFee,
                    "hizbs" to 0, "updatedAt" to Date()
                ))
            }
            batch.commit().addOnFailureListener { message = arabicError(it.message) }
        }
        message = "تمت إضافة ${toAdd.size} طالب" + if (toAdd.size < list.size) " (تخطّي ${list.size - toAdd.size} مكرر)" else ""
        audit("إضافة جماعية", "${toAdd.size} طالب إلى ${group.ifBlank { "بدون حلقة" }}")
        done(toAdd.size)
    }

    // ---------- Petty-cash register (عُهَد) ----------

    private fun toAdvance(d: DocumentSnapshot) = CashAdvance(
        id = d.id, month = d.getString("month") ?: "", week = d.getString("week") ?: "",
        receivedDate = d.getString("receivedDate") ?: "", amount = d.getLong("amount")?.toInt() ?: 0,
        remisPar = d.getString("remisPar") ?: "", recuPar = d.getString("recuPar") ?: "",
        settlementAmount = d.getLong("settlementAmount")?.toInt() ?: 0, settlementDate = d.getString("settlementDate") ?: "",
        closed = d.getBoolean("closed") ?: false
    )

    /** Creates or updates an advance; returns its id (new ids are generated locally, so it works offline). */
    fun saveAdvance(a: CashAdvance): String? {
        if (a.amount <= 0) { message = "أدخل المبلغ المستلم"; return null }
        if (a.recuPar.isBlank()) { message = "اكتب اسم المستلم"; return null }
        val date = a.receivedDate.ifBlank { today() }
        val ref = if (a.id.isBlank()) db.collection("cashAdvances").document() else db.collection("cashAdvances").document(a.id)
        ref.set(mapOf(
            "month" to date.take(7), "week" to a.week.trim(), "receivedDate" to date, "amount" to a.amount,
            "remisPar" to a.remisPar.trim(), "recuPar" to a.recuPar.trim(), "settlementAmount" to a.settlementAmount,
            "settlementDate" to a.settlementDate, "closed" to a.closed, "updatedAt" to Date()
        ), SetOptions.merge()).addOnFailureListener { message = arabicError(it.message) }
        if (a.id.isBlank()) { message = "تم فتح عهدة ${money(a.amount)} لـ ${a.recuPar.trim()}"; audit("فتح عهدة", "${a.recuPar.trim()} — ${money(a.amount)}") }
        return ref.id
    }

    fun listenAdvanceLines(id: String, onLines: (List<Expense>) -> Unit): ListenerRegistration =
        db.collection("expenses").whereEqualTo("registerId", id).addSnapshotListener { snap, _ ->
            onLines(snap?.documents?.map(::toExpense)?.sortedWith(compareBy({ it.date }, { it.id })) ?: emptyList())
        }

    /** Adds (or edits) a line of the register; it is also a normal expense of the centre. */
    fun saveAdvanceLine(a: CashAdvance, line: Expense, done: () -> Unit) {
        if (line.note.isBlank()) { message = "اكتب البيان"; return }
        if (line.amount <= 0) { message = "أدخل المبلغ"; return }
        val date = line.date.ifBlank { today() }
        val ref = if (line.id.isBlank()) db.collection("expenses").document() else db.collection("expenses").document(line.id)
        ref.set(mapOf(
            "category" to line.category.ifBlank { DAILY_EXPENSES_CATEGORY }, "amount" to line.amount, "date" to date,
            "month" to date.take(7), "note" to line.note.trim(), "observation" to line.observation.trim(),
            "registerId" to a.id, "recordedBy" to (profile?.name ?: ""), "updatedAt" to Date()
        ), SetOptions.merge()).addOnFailureListener { message = arabicError(it.message) }
        done()
    }

    fun closeAdvance(a: CashAdvance, spent: Int, settlementAmount: Int, settlementDate: String, closed: Boolean) {
        db.collection("cashAdvances").document(a.id).update(mapOf(
            "settlementAmount" to settlementAmount, "settlementDate" to settlementDate.ifBlank { today() },
            "closed" to closed, "updatedAt" to Date()
        )).addOnFailureListener { message = arabicError(it.message) }
        message = if (closed) "تمت تصفية العهدة ✅" else "حُفظت التسوية"
        val bal = advanceBalance(a, spent)
        audit(if (closed) "تصفية عهدة" else "تسوية عهدة", "${a.recuPar} — مستلم ${money(a.amount)}، مصروف ${money(spent)}، " +
            (if (bal >= 0) "باقٍ ${money(bal)}" else "عجز ${money(-bal)}") + "، تسوية ${money(settlementAmount)}")
    }

    /** Deletes an advance together with its expense lines. */
    fun deleteAdvance(a: CashAdvance) {
        db.collection("expenses").whereEqualTo("registerId", a.id).get().addOnCompleteListener { t ->
            val batch = db.batch()
            if (t.isSuccessful) t.result.documents.forEach { batch.delete(it.reference) }
            batch.delete(db.collection("cashAdvances").document(a.id))
            batch.commit().addOnFailureListener { message = arabicError(it.message) }
        }
        message = "تم حذف العهدة ومصروفاتها"
        audit("حذف عهدة", "${a.recuPar} — ${money(a.amount)} (${a.receivedDate})")
    }

    // ---------- Teachers (admin) ----------

    private fun toTeacher(d: DocumentSnapshot) = Teacher(
        id = d.id, name = d.getString("name") ?: "", phone = d.getString("phone") ?: "",
        salary = d.getLong("salary")?.toInt() ?: 0, hireDate = d.getString("hireDate") ?: "",
        status = d.getString("status") ?: "نشط", notes = d.getString("notes") ?: "",
        category = d.getString("category") ?: StaffCategory.TEACHER, function = d.getString("function") ?: "",
        cessationDate = d.getString("cessationDate") ?: "", paymentMode = d.getString("paymentMode") ?: "",
        photo = d.getString("photo") ?: ""
    )

    fun saveTeacher(t: Teacher, done: () -> Unit) {
        if (t.name.isBlank()) { message = "اسم المعلم مطلوب"; return }
        val data = mapOf("name" to t.name.trim(), "phone" to t.phone.trim(), "salary" to t.salary,
            "hireDate" to t.hireDate, "status" to t.status, "notes" to t.notes.trim(), "category" to t.category,
            "function" to t.function.trim(), "cessationDate" to t.cessationDate, "paymentMode" to t.paymentMode,
            "photo" to t.photo, "updatedAt" to Date())
        val ref = if (t.id.isBlank()) db.collection("teachers").document() else db.collection("teachers").document(t.id)
        ref.set(data).addOnFailureListener { message = arabicError(it.message) }
        done(); message = if (t.id.isBlank()) "تمت إضافة المعلم ${t.name.trim()}" else "تم تحديث بيانات المعلم"
        if (t.id.isBlank()) audit("إضافة معلم", t.name.trim())
    }

    fun deleteTeacher(t: Teacher) {
        db.collection("teachers").document(t.id).delete()
            .addOnSuccessListener { message = "تم حذف المعلم" }
            .addOnFailureListener { message = arabicError(it.message) }
        audit("حذف معلم", t.name)
    }

    fun changeTeacherDate(delta: Int) {
        val next = shiftDay(teacherDate, delta)
        if (next > today()) return
        teacherDate = next; listenTeacherDay(next)
    }

    private fun listenTeacherDay(day: String) {
        teacherDayListener?.remove()
        teacherDayListener = db.collection("teacherAttendance").whereEqualTo("date", day).addSnapshotListener { snap, e ->
            if (e != null) { message = arabicError(e.message); return@addSnapshotListener }
            teacherDayAttendance = snap?.documents?.map { d ->
                AttendanceRecord(d.id, d.getString("teacherId") ?: "", d.getString("name") ?: "", d.getString("date") ?: "",
                    d.getString("status") ?: AttendanceStatus.PRESENT)
            } ?: emptyList()
        }
    }

    fun setTeacherAttendance(t: Teacher, status: String) {
        db.collection("teacherAttendance").document("${teacherDate}_${t.id}").set(mapOf(
            "teacherId" to t.id, "name" to t.name, "date" to teacherDate, "status" to status,
            "recordedBy" to (profile?.name ?: ""), "updatedAt" to Date()
        )).addOnFailureListener { message = arabicError(it.message) }
    }

    fun loadTeacherMonth(month: String, onReady: (Map<String, List<String>>) -> Unit) {
        db.collection("teacherAttendance").whereGreaterThanOrEqualTo("date", "$month-01").whereLessThanOrEqualTo("date", "$month-31")
            .get().addOnSuccessListener { snap ->
                onReady(snap.documents.groupBy({ it.getString("teacherId") ?: "" }, { it.getString("status") ?: "" }))
            }.addOnFailureListener { message = arabicError(it.message) }
    }

    fun changeSalaryMonth(delta: Int) {
        val next = shiftMonth(salaryMonth, delta)
        if (next > currentMonth()) return
        salaryMonth = next; listenSalaries(next)
    }

    private fun toPayroll(d: DocumentSnapshot) = PayrollEntry(
        id = d.id, staffId = d.getString("staffId") ?: "", name = d.getString("name") ?: "", month = d.getString("month") ?: "",
        base = d.getLong("base")?.toInt() ?: 0, advance = d.getLong("advance")?.toInt() ?: 0,
        received = d.getLong("received")?.toInt() ?: 0, receivedDate = d.getString("receivedDate") ?: "",
        paymentMode = d.getString("paymentMode") ?: ""
    )

    private fun listenSalaries(month: String) {
        salaryListener?.remove()
        salaryListener = db.collection("payroll").whereEqualTo("month", month).addSnapshotListener { snap, e ->
            if (e != null) { message = arabicError(e.message); return@addSnapshotListener }
            monthPayroll = snap?.documents?.map(::toPayroll) ?: emptyList()
        }
    }

    /** Payroll entries of the academic year (Sep–Aug) containing [month]. */
    fun loadPayrollYear(month: String, onReady: (List<PayrollEntry>) -> Unit) {
        val ms = academicYearMonths(month)
        db.collection("payroll").whereGreaterThanOrEqualTo("month", ms.first()).whereLessThanOrEqualTo("month", ms.last())
            .get().addOnSuccessListener { snap -> onReady(snap.documents.map(::toPayroll)) }
            .addOnFailureListener { message = arabicError(it.message) }
    }

    /**
     * Records a month's salary. The cash actually paid (advance + received) is mirrored as an expense
     * "رواتب المعلمين" so the financial summary stays accurate.
     */
    fun savePayroll(t: Teacher, month: String, base: Int, advance: Int, received: Int, date: String, mode: String, done: () -> Unit) {
        if (base <= 0) { message = "أدخل الراتب الأساسي"; return }
        if (advance < 0 || received < 0) { message = "المبالغ لا تكون سالبة"; return }
        val id = "${month}_${t.id}"
        db.collection("payroll").document(id).set(mapOf(
            "staffId" to t.id, "name" to t.name, "month" to month, "base" to base, "advance" to advance,
            "received" to received, "receivedDate" to date.ifBlank { today() }, "paymentMode" to mode,
            "recordedBy" to (profile?.name ?: ""), "updatedAt" to Date()
        )).addOnFailureListener { message = arabicError(it.message) }
        val cash = advance + received
        val exp = db.collection("expenses").document("salary_$id")
        if (cash > 0) exp.set(mapOf(
            "category" to SALARY_CATEGORY, "amount" to cash, "date" to date.ifBlank { today() },
            "month" to date.ifBlank { today() }.take(7), "note" to "راتب ${t.name} — ${monthName(month)}",
            "teacherId" to t.id, "salaryMonth" to month, "recordedBy" to (profile?.name ?: ""), "updatedAt" to Date()
        )) else exp.delete()
        done()
        val left = base - advance - received
        message = "حُفظ راتب ${t.name} لشهر ${monthName(month)}"
        audit("راتب", "${t.name} — ${monthName(month)}: أساسي ${money(base)}، سلفة ${money(advance)}، مستلم ${money(received)}" +
            when { left > 0 -> "، متروك أمانة ${money(left)}"; left < 0 -> "، مسحوب من الأمانة ${money(-left)}"; else -> "" })
    }

    fun deletePayroll(e: PayrollEntry) {
        db.collection("payroll").document(e.id).delete().addOnFailureListener { message = arabicError(it.message) }
        db.collection("expenses").document("salary_${e.id}").delete()
        message = "حُذف سجل الراتب"
        audit("حذف راتب", "${e.name} — ${monthName(e.month)}")
    }

    // ---------- Audit log ----------

    /** Records a sensitive operation (append-only; only the admin can read it). */
    fun audit(action: String, details: String) {
        if (profile?.isStaff != true) return
        db.collection("auditLog").add(mapOf(
            "action" to action, "details" to details,
            "by" to (profile?.name?.ifBlank { null } ?: firebaseUser?.email ?: ""), "byUid" to (firebaseUser?.uid ?: ""),
            "at" to Date()
        ))
    }

    fun loadAudit(onReady: (List<AuditEntry>) -> Unit) {
        db.collection("auditLog").orderBy("at", Query.Direction.DESCENDING).limit(300).get()
            .addOnSuccessListener { snap ->
                onReady(snap.documents.map { d ->
                    AuditEntry(d.getString("action") ?: "", d.getString("details") ?: "", d.getString("by") ?: "",
                        d.getDate("at")?.time ?: 0)
                })
            }.addOnFailureListener { message = arabicError(it.message) }
    }

    // ---------- Backup & restore (admin) ----------

    val backupCollections = listOf("settings", "halaqat", "students", "teachers", "attendance", "teacherAttendance",
        "quranLogs", "grades", "payments", "expenses", "cashAdvances", "payroll", "announcements", "users")

    /** Reads every collection and returns a JSON backup (timestamps kept as {"__ts": millis}). */
    fun createBackup(onReady: (String) -> Unit) {
        val tasks = backupCollections.map { db.collection(it).get() }
        Tasks.whenAllSuccess<QuerySnapshot>(tasks).addOnSuccessListener { snaps ->
            val root = org.json.JSONObject()
            root.put("_app", "centre-imam-malik"); root.put("_version", 1); root.put("_createdAt", System.currentTimeMillis())
            var count = 0
            backupCollections.zip(snaps).forEach { (name, snap) ->
                val col = org.json.JSONObject()
                snap.documents.forEach { d -> col.put(d.id, BackupCodec.encode(d.data ?: emptyMap<String, Any>())); count++ }
                root.put(name, col)
            }
            audit("نسخة احتياطية", "$count مستند")
            onReady(root.toString())
        }.addOnFailureListener { message = arabicError(it.message) }
    }

    /** Restores a backup produced by [createBackup]; documents are merged by id (nothing is deleted). */
    fun restoreBackup(json: String, done: (Int) -> Unit) {
        val root = runCatching { org.json.JSONObject(json) }.getOrNull()
        if (root == null || root.optString("_app") != "centre-imam-malik") { message = "الملف ليس نسخة احتياطية صالحة للتطبيق"; return }
        val ops = mutableListOf<(WriteBatch) -> Unit>()
        backupCollections.filter { root.has(it) }.forEach { name ->
            val col = root.getJSONObject(name)
            col.keys().forEach { id ->
                @Suppress("UNCHECKED_CAST")
                val data = BackupCodec.decode(col.getJSONObject(id)) as Map<String, Any?>
                ops += { b -> b.set(db.collection(name).document(id), data, SetOptions.merge()) }
            }
        }
        ops.chunked(450).forEach { chunk ->
            val batch = db.batch(); chunk.forEach { it(batch) }
            batch.commit().addOnFailureListener { message = arabicError(it.message) }
        }
        message = "تمت استعادة ${ops.size} مستند"
        audit("استعادة نسخة احتياطية", "${ops.size} مستند")
        done(ops.size)
    }

    // ---------- Centre settings (admin) ----------

    fun saveSettings(name: String, director: String, phone: String, address: String, year: String, currency: String, done: () -> Unit) {
        if (name.isBlank()) { message = "اسم المركز مطلوب"; return }
        done(); message = "تم حفظ إعدادات المركز"
        db.collection("settings").document("centre").set(mapOf(
            "name" to name.trim(), "director" to director.trim(), "phone" to phone.trim(), "address" to address.trim(),
            "academicYear" to year.trim(), "currency" to currency.trim().ifBlank { "فرنك" }, "updatedAt" to Date()
        )).addOnFailureListener { message = arabicError(it.message) }
        audit("تعديل إعدادات المركز", name.trim())
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

    /** Summary of the academic year (September–August) containing [month]. */
    fun financeSummaryCsv(month: String, onReady: (String) -> Unit) {
        val ms = academicYearMonths(month)
        loadMonths(ms.first(), ms.last()) { ps, es -> onReady(summaryCsv(ms, ps, es)) }
    }

    private fun summaryCsv(months: List<String>, ps: List<Payment>, es: List<Expense>): String {
        val rows = months.map { m ->
            val monthly = ps.filter { it.month == m && it.type == PaymentType.MONTHLY }.sumOf { it.amount }
            val reg = ps.filter { it.month == m && it.type == PaymentType.REGISTRATION }.sumOf { it.amount }
            val exp = es.filter { it.month == m }.sumOf { it.amount }
            listOf(monthName(m), monthly, reg, monthly + reg, exp, monthly + reg - exp)
        }
        val totals = (1..5).map { i -> rows.sumOf { it[i] as Int } }
        return csv(listOf(listOf("الشهر", "الرسوم الشهرية", "رسوم التسجيل", "مجموع الإيرادات", "المصروفات", "الصافي")) +
            rows + listOf(listOf<Any?>("المجموع") + totals))
    }

    // ---------- Users (admin) ----------

    fun updateUser(user: AppUser, role: String, studentIds: List<String>, groups: List<String> = emptyList()) {
        db.collection("users").document(user.uid)
            .update(mapOf("role" to role, "studentIds" to studentIds, "groups" to groups, "updatedAt" to FieldValue.serverTimestamp()))
            .addOnSuccessListener { message = "تم تحديث حساب ${user.name.ifBlank { user.email }}" }
            .addOnFailureListener { message = arabicError(it.message) }
        audit("تعديل صلاحيات حساب", "${user.name.ifBlank { user.email }} ← ${Roles.label(role)}")
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
        registrationFee = d.getLong("registrationFee")?.toInt() ?: 0,
        photo = d.getString("photo") ?: ""
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
        recordedBy = d.getString("recordedBy") ?: "",
        registerId = d.getString("registerId") ?: "",
        observation = d.getString("observation") ?: ""
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

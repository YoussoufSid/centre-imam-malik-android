package ml.centre.imammalik

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*

private val Green = Color(0xFF075B3A)
private val Gold = Color(0xFFD7A91E)
private val Cream = Color(0xFFF7F5EC)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        FirebaseFirestore.getInstance().firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true).build()
        setContent { ImamMalikApp() }
    }
}

data class Student(val id: String = "", val name: String = "", val level: String = "", val phone: String = "")

class AppState {
    val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    var user by mutableStateOf(auth.currentUser)
    var students by mutableStateOf(emptyList<Student>())
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    private var listener: ListenerRegistration? = null

    init { if (user != null) listenStudents() }

    fun login(email: String, password: String) {
        busy = true; message = null
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { user = it.user; busy = false; listenStudents() }
            .addOnFailureListener { busy = false; message = arabicError(it.message) }
    }

    fun register(email: String, password: String, name: String) {
        busy = true; message = null
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                user = result.user
                val role = if (email.trim().equals("sidibey487@gmail.com", true)) "admin" else "student"
                db.collection("users").document(result.user!!.uid)
                    .set(mapOf("name" to name.trim(), "email" to email.trim(), "role" to role))
                    .addOnCompleteListener { busy = false; listenStudents() }
            }.addOnFailureListener { busy = false; message = arabicError(it.message) }
    }

    fun resetPassword(email: String) {
        if (email.isBlank()) { message = "اكتب البريد الإلكتروني أولًا"; return }
        auth.sendPasswordResetEmail(email.trim())
            .addOnSuccessListener { message = "أُرسل رابط استعادة كلمة المرور إلى بريدك" }
            .addOnFailureListener { message = arabicError(it.message) }
    }

    fun logout() { listener?.remove(); auth.signOut(); user = null; students = emptyList() }

    private fun listenStudents() {
        listener?.remove()
        listener = db.collection("students").orderBy("name").addSnapshotListener { snap, error ->
            if (error != null) { message = arabicError(error.message); return@addSnapshotListener }
            students = snap?.documents?.map { d ->
                Student(d.id, d.getString("name") ?: "", d.getString("level") ?: "", d.getString("phone") ?: "")
            } ?: emptyList()
        }
    }

    fun addStudent(name: String, level: String, phone: String, done: () -> Unit) {
        if (name.isBlank()) { message = "اسم الطالب مطلوب"; return }
        db.collection("students").add(mapOf("name" to name.trim(), "level" to level.trim(), "phone" to phone.trim(), "createdAt" to Date()))
            .addOnSuccessListener { done(); message = "تمت إضافة الطالب" }
            .addOnFailureListener { message = arabicError(it.message) }
    }

    fun attendance(student: Student, present: Boolean) {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        db.collection("attendance").document("${date}_${student.id}").set(
            mapOf("studentId" to student.id, "studentName" to student.name, "date" to date, "present" to present, "updatedAt" to Date())
        ).addOnSuccessListener { message = if (present) "سُجّل حضور ${student.name}" else "سُجّل غياب ${student.name}" }
            .addOnFailureListener { message = arabicError(it.message) }
    }

    private fun arabicError(raw: String?) = when {
        raw?.contains("password", true) == true -> "البريد أو كلمة المرور غير صحيحة"
        raw?.contains("network", true) == true -> "لا يوجد اتصال؛ حاول عند عودة الإنترنت"
        raw?.contains("permission", true) == true -> "ليست لديك صلاحية لتنفيذ هذا الإجراء"
        else -> raw ?: "حدث خطأ غير متوقع"
    }
}

@Composable
fun ImamMalikApp() {
    val state = remember { AppState() }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = lightColorScheme(primary = Green, secondary = Gold, background = Cream)) {
            Surface(Modifier.fillMaxSize(), color = Cream) {
                if (state.user == null) AuthScreen(state) else Dashboard(state)
            }
        }
    }
}

@Composable
private fun AuthScreen(state: AppState) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var registering by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(painterResource(R.drawable.centre_logo), "شعار المركز", Modifier.size(150.dp), contentScale = ContentScale.Fit)
        Spacer(Modifier.height(16.dp))
        Text("مركز الإمام مالك", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Green)
        Text("لتحفيظ القرآن الكريم والدراسات الإسلامية", color = Color.DarkGray)
        Spacer(Modifier.height(28.dp))
        if (registering) AppField(name, { name = it }, "الاسم الكامل")
        AppField(email, { email = it }, "البريد الإلكتروني", KeyboardType.Email)
        OutlinedTextField(password, { password = it }, label = { Text("كلمة المرور") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { if (registering) state.register(email, password, name) else state.login(email, password) },
            enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (state.busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White)
            else Text(if (registering) "إنشاء الحساب" else "تسجيل الدخول")
        }
        TextButton(onClick = { registering = !registering; state.message = null }) {
            Text(if (registering) "لدي حساب بالفعل" else "إنشاء حساب جديد")
        }
        if (!registering) TextButton(onClick = { state.resetPassword(email) }) { Text("نسيت كلمة المرور؟") }
    }
}

@Composable
private fun AppField(value: String, change: (String) -> Unit, label: String, type: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(value, change, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dashboard(state: AppState) {
    var tab by remember { mutableIntStateOf(0) }
    var addOpen by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("مركز الإمام مالك") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Green, titleContentColor = Color.White),
            actions = { IconButton(onClick = state::logout) { Icon(Icons.Default.Logout, "خروج", tint = Color.White) } }) },
        bottomBar = { NavigationBar { listOf("الرئيسية" to Icons.Default.Home, "الطلاب" to Icons.Default.Groups, "الحضور" to Icons.Default.FactCheck).forEachIndexed { i, item ->
            NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(item.second, item.first) }, label = { Text(item.first) })
        } } },
        floatingActionButton = { if (tab == 1) FloatingActionButton(onClick = { addOpen = true }, containerColor = Gold) { Icon(Icons.Default.PersonAdd, "إضافة طالب") } }
    ) { pad -> Box(Modifier.padding(pad).fillMaxSize()) {
        when(tab) { 0 -> Home(state); 1 -> Students(state); else -> Attendance(state) }
        state.message?.let { msg -> Card(Modifier.align(Alignment.BottomCenter).padding(12.dp)) { Text(msg, Modifier.padding(12.dp)) } }
    } }
    if (addOpen) AddStudentDialog(state) { addOpen = false }
}

@Composable
private fun Home(state: AppState) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Card(colors = CardDefaults.cardColors(containerColor = Green), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.centre_logo), null, Modifier.size(72.dp)); Spacer(Modifier.width(14.dp))
                Column { Text("السلام عليكم ورحمة الله وبركاته", color = Color.White, fontWeight = FontWeight.Bold); Text(state.user?.email ?: "", color = Color.White.copy(.8f)) }
            }
        } }
        item { Text("ملخص اليوم", fontSize = 21.sp, fontWeight = FontWeight.Bold) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("الطلاب", state.students.size.toString(), Icons.Default.Groups, Modifier.weight(1f))
            StatCard("المزامنة", "تلقائية", Icons.Default.CloudDone, Modifier.weight(1f))
        } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) {
            Text("العمل دون إنترنت", fontWeight = FontWeight.Bold, color = Green)
            Text("يمكنك متابعة العمل، وستُرسل التغييرات تلقائيًا عند عودة الاتصال.")
        } } }
    }
}

@Composable private fun StatCard(title:String, value:String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier:Modifier) {
    Card(modifier) { Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon,null,tint=Gold); Text(value,fontSize=22.sp,fontWeight=FontWeight.Bold); Text(title) } }
}

@Composable
private fun Students(state: AppState) {
    if (state.students.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("لا يوجد طلاب بعد\nاضغط زر الإضافة", color=Color.Gray) }
    else LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(state.students, key={it.id}) { s ->
        Card(Modifier.fillMaxWidth()) { ListItem(headlineContent={Text(s.name,fontWeight=FontWeight.Bold)}, supportingContent={Text("المستوى: ${s.level.ifBlank { "غير محدد" }}")}, leadingContent={Icon(Icons.Default.Person,null,tint=Green)}) }
    } }
}

@Composable
private fun Attendance(state: AppState) {
    Column(Modifier.fillMaxSize().padding(12.dp)) { Text("حضور اليوم", fontSize=22.sp,fontWeight=FontWeight.Bold, modifier=Modifier.padding(8.dp));
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) { items(state.students,key={it.id}) { s -> Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(s.name,Modifier.weight(1f),fontWeight=FontWeight.Medium)
            FilledTonalButton(onClick={state.attendance(s,false)}){Text("غائب")}; Spacer(Modifier.width(6.dp)); Button(onClick={state.attendance(s,true)}){Text("حاضر")}
        } } } }
    }
}

@Composable
private fun AddStudentDialog(state: AppState, close:()->Unit) {
    var name by remember{mutableStateOf("")}; var level by remember{mutableStateOf("")}; var phone by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=close,title={Text("إضافة طالب")},text={Column{AppField(name,{name=it},"الاسم الكامل");AppField(level,{level=it},"المستوى");AppField(phone,{phone=it},"هاتف ولي الأمر",KeyboardType.Phone)}},
        confirmButton={Button(onClick={state.addStudent(name,level,phone,close)}){Text("حفظ")}},dismissButton={TextButton(onClick=close){Text("إلغاء")}})
}

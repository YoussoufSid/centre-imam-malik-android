package ml.centre.imammalik

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.delay

val Green = Color(0xFF075B3A)
val Gold = Color(0xFFD7A91E)
val Cream = Color(0xFFF7F5EC)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        FirebaseFirestore.getInstance().firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true).build()
        setContent { ImamMalikApp() }
    }
}

@Composable
fun ImamMalikApp() {
    val state = remember { AppState() }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = lightColorScheme(primary = Green, secondary = Gold, background = Cream, surface = Color.White)) {
            Surface(Modifier.fillMaxSize(), color = Cream) {
                when {
                    state.firebaseUser == null -> AuthScreen(state)
                    state.profile == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    else -> Dashboard(state, state.profile!!)
                }
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
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(24.dp))
        Image(painterResource(R.drawable.centre_logo), "شعار المركز", Modifier.size(150.dp), contentScale = ContentScale.Fit)
        Spacer(Modifier.height(16.dp))
        Text("مركز الإمام مالك", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Green)
        Text("لتحفيظ القرآن الكريم والدراسات الإسلامية", color = Color.DarkGray)
        Spacer(Modifier.height(28.dp))
        if (registering) AppField(name, { name = it }, "الاسم الكامل")
        AppField(email, { email = it }, "البريد الإلكتروني", KeyboardType.Email)
        OutlinedTextField(password, { password = it }, label = { Text("كلمة المرور") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
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
fun AppField(
    value: String, change: (String) -> Unit, label: String,
    type: KeyboardType = KeyboardType.Text, modifier: Modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
) {
    OutlinedTextField(value, change, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type), modifier = modifier)
}

private data class Tab(val title: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dashboard(state: AppState, me: AppUser) {
    val tabs = buildList {
        add(Tab("الرئيسية", Icons.Default.Home))
        add(Tab(if (me.isStaff) "الطلاب" else "أبنائي", Icons.Default.Groups))
        if (me.isStaff) {
            add(Tab("الحضور", Icons.Default.FactCheck))
            add(Tab("الحفظ", Icons.Default.MenuBook))
        }
        if (me.isAdmin) add(Tab("الرسوم", Icons.Default.Payments))
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    if (tab >= tabs.size) tab = 0
    var addOpen by remember { mutableStateOf(false) }
    var profileId by rememberSaveable { mutableStateOf<String?>(null) }
    var editStudent by remember { mutableStateOf<Student?>(null) }
    var showUsers by rememberSaveable { mutableStateOf(false) }
    var showGrades by rememberSaveable { mutableStateOf(false) }
    var announceOpen by remember { mutableStateOf(false) }
    var exportOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) { if (state.message != null) { delay(3500); state.message = null } }

    val profileStudent = profileId?.let { id -> state.students.firstOrNull { it.id == id } }
    if (profileId != null && profileStudent == null && state.students.isNotEmpty()) profileId = null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(when { profileStudent != null -> profileStudent.name; showUsers -> "حسابات المستخدمين"; showGrades -> "إدخال الدرجات"; else -> "مركز الإمام مالك" }) },
                navigationIcon = {
                    if (profileStudent != null || showUsers || showGrades) IconButton(onClick = {
                        if (profileStudent != null) profileId = null else { showUsers = false; showGrades = false }
                    }) {
                        Icon(Icons.Default.ArrowForward, "رجوع", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Green, titleContentColor = Color.White),
                actions = {
                    if (profileStudent != null && me.isStaff) IconButton(onClick = { editStudent = profileStudent }) {
                        Icon(Icons.Default.Edit, "تعديل", tint = Color.White)
                    }
                    if (me.isAdmin && profileStudent == null && !showUsers && !showGrades) IconButton(onClick = { exportOpen = true }) {
                        Icon(Icons.Default.FileDownload, "تصدير", tint = Color.White)
                    }
                    if (me.isAdmin && profileStudent == null && !showUsers && !showGrades) IconButton(onClick = { showUsers = true }) {
                        Icon(Icons.Default.ManageAccounts, "الحسابات", tint = Color.White)
                    }
                    IconButton(onClick = state::logout) { Icon(Icons.Default.Logout, "خروج", tint = Color.White) }
                }
            )
        },
        bottomBar = {
            if (profileStudent == null && !showUsers && !showGrades) NavigationBar {
                tabs.forEachIndexed { i, item ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i },
                        icon = { Icon(item.icon, item.title) }, label = { Text(item.title, maxLines = 1) })
                }
            }
        },
        floatingActionButton = {
            if (profileStudent == null && !showUsers && !showGrades && me.isStaff && tabs[tab].title == "الطلاب")
                FloatingActionButton(onClick = { addOpen = true }, containerColor = Gold) { Icon(Icons.Default.PersonAdd, "إضافة طالب") }
            if (profileStudent == null && !showUsers && !showGrades && me.isAdmin && tabs[tab].title == "الرئيسية")
                ExtendedFloatingActionButton(onClick = { announceOpen = true }, containerColor = Gold,
                    icon = { Icon(Icons.Default.Campaign, null) }, text = { Text("إعلان") })
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (profileStudent != null) {
                BackHandler { profileId = null }
                StudentProfileScreen(state, me, profileStudent)
            } else if (showGrades) {
                BackHandler { showGrades = false }
                GradeEntryScreen(state) { showGrades = false }
            } else if (showUsers) {
                BackHandler { showUsers = false }
                UsersScreen(state)
            } else when (tabs[tab].title) {
                "الرئيسية" -> HomeScreen(state, me) { profileId = it.id }
                "الطلاب", "أبنائي" -> StudentsScreen(state, me, onGradeEntry = { showGrades = true }) { profileId = it.id }
                "الحضور" -> AttendanceScreen(state)
                "الحفظ" -> QuranScreen(state) { profileId = it }
                "الرسوم" -> FeesScreen(state) { profileId = it }
            }
            state.message?.let { msg ->
                Card(
                    Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF263238))
                ) { Text(msg, Modifier.padding(14.dp), color = Color.White) }
            }
        }
    }
    if (addOpen) StudentDialog(state, null) { addOpen = false }
    if (announceOpen) AnnouncementDialog(state) { announceOpen = false }
    if (exportOpen) ExportDialog(state) { exportOpen = false }
    editStudent?.let { s -> StudentDialog(state, s, onDeleted = { profileId = null }) { editStudent = null } }
}

@Composable
fun StatCard(title: String, value: String, icon: ImageVector, modifier: Modifier, tint: Color = Gold) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = tint)
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(title, fontSize = 13.sp, color = Color.DarkGray, maxLines = 1)
        }
    }
}

@Composable
private fun HomeScreen(state: AppState, me: AppUser, open: (Student) -> Unit) {
    val active = state.students.filter { it.status == "نشط" }
    val present = state.todayAttendance.count { AttendanceStatus.isPresent(it.status) }
    val absent = state.todayAttendance.count { it.status == AttendanceStatus.ABSENT }
    val todayLogs = state.recentLogs.filter { it.date == today() }
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Green), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.centre_logo), null, Modifier.size(72.dp)); Spacer(Modifier.width(14.dp))
                    Column {
                        Text("السلام عليكم ورحمة الله وبركاته", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(me.name.ifBlank { me.email }, color = Color.White.copy(.9f))
                        Text("الصفة: ${Roles.label(me.role)}", color = Gold, fontSize = 13.sp)
                        val hijri = remember { hijriToday() }
                        if (hijri.isNotBlank()) Text(hijri, color = Color.White.copy(.85f), fontSize = 13.sp)
                    }
                }
            }
        }
        if (state.announcements.isNotEmpty()) {
            item { Text("الإعلانات", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            items(state.announcements.take(5).size) { i ->
                val a = state.announcements[i]
                AnnouncementCard(a, canDelete = me.isAdmin) { state.deleteAnnouncement(a) }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Text("﴿وَلَقَدْ يَسَّرْنَا الْقُرْآنَ لِلذِّكْرِ فَهَلْ مِن مُّدَّكِرٍ﴾", Modifier.padding(16.dp).fillMaxWidth(),
                    color = Green, fontSize = 18.sp, fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center)
            }
        }
        if (me.isStaff) {
            item { Text("ملخص اليوم • ${ltr(today())}", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("طلاب نشطون", active.size.toString(), Icons.Default.Groups, Modifier.weight(1f))
                    StatCard("حاضرون", present.toString(), Icons.Default.CheckCircle, Modifier.weight(1f), Green)
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("غائبون", absent.toString(), Icons.Default.Cancel, Modifier.weight(1f), MaterialTheme.colorScheme.error)
                    StatCard("تسميع اليوم", todayLogs.size.toString(), Icons.Default.MenuBook, Modifier.weight(1f))
                }
            }
            val unmarked = active.size - state.todayAttendance.count { r -> active.any { it.id == r.studentId } }
            if (active.isNotEmpty() && unmarked > 0) item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4D6))) {
                    Text("لم يُسجَّل حضور $unmarked طالب اليوم بعد.", Modifier.padding(14.dp), color = Color(0xFF6D4C00))
                }
            }
            val leaders = active.filter { it.hizbs > 0 }.sortedByDescending { it.hizbs }.take(5)
            if (leaders.isNotEmpty()) {
                item { Text("الأكثر حفظًا", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            leaders.forEachIndexed { i, s ->
                                Column(Modifier.clickable { open(s) }) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("${i + 1}. ${s.name}", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                        Text("${s.hizbs} حزبًا", color = Green, fontSize = 13.sp)
                                    }
                                    HizbProgressBar(s.hizbs, Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
            if (todayLogs.isNotEmpty()) {
                item { Text("آخر التسميع", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                items(todayLogs.take(5).size) { i -> LogCard(todayLogs[i], showStudent = true) }
            }
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDone, null, tint = Green); Spacer(Modifier.width(10.dp))
                        Text("يعمل التطبيق دون إنترنت، وتُرسل التغييرات تلقائيًا عند عودة الاتصال.", fontSize = 14.sp)
                    }
                }
            }
        } else {
            if (state.students.isEmpty()) item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Text("لم يُربط حسابك بأي طالب بعد. يرجى التواصل مع إدارة المركز لربط الحساب بملف الطالب.",
                        Modifier.padding(16.dp))
                }
            } else {
                item { Text("الملفات المرتبطة بحسابك", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                items(state.students.size) { i -> StudentRow(state.students[i]) { open(state.students[i]) } }
            }
        }
    }
}

package ml.centre.imammalik

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.delay

val Green = Color(0xFF075B3A)
val Gold = Color(0xFFD7A91E)
val Cream = Color(0xFFF7F5EC)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
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
        MaterialTheme(
            colorScheme = lightColorScheme(primary = Green, secondary = Gold, background = Cream, surface = Color.White,
                secondaryContainer = Green.copy(alpha = .12f), onSecondaryContainer = Green),
            shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp))
        ) {
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
    var showPassword by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Centre building with welcome message
        // Slow zoom on the building photo (Ken Burns) and rotating welcome slides
        val zoom by androidx.compose.animation.core.rememberInfiniteTransition(label = "kb").animateFloat(
            initialValue = 1f, targetValue = 1.15f, label = "zoom",
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(14000), androidx.compose.animation.core.RepeatMode.Reverse))
        var slide by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) { while (true) { delay(6000); slide = (slide + 1) % 4 } }
        Box(Modifier.fillMaxWidth().height(360.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))) {
            Image(painterResource(R.drawable.centre_building), "مبنى المركز",
                Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom }, contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(Green.copy(alpha = .25f), Green.copy(alpha = .93f)))))
            Column(Modifier.align(Alignment.BottomCenter).padding(start = 20.dp, end = 20.dp, bottom = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.centre_logo), "شعار المركز",
                    Modifier.size(74.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.White))
                Spacer(Modifier.height(10.dp))
                androidx.compose.animation.AnimatedContent(targetState = slide, label = "slides",
                    transitionSpec = {
                        (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(700)) +
                            androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(700)) { it / 3 })
                            .togetherWith(androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(500)))
                    }) { i ->
                    Column(Modifier.fillMaxWidth().heightIn(min = 120.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        when (i) {
                            0 -> {
                                Text(WELCOME_TEXT, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center, lineHeight = 28.sp)
                                Text("بسم الله الرحمن الرحيم", color = Color.White.copy(.85f), modifier = Modifier.padding(top = 6.dp))
                            }
                            1 -> {
                                Text("﴿وَلَقَدْ يَسَّرْنَا الْقُرْآنَ لِلذِّكْرِ فَهَلْ مِن مُّدَّكِرٍ﴾", color = Color.White, fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 30.sp)
                                Text(HADITH_TEXT, color = Gold, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                            }
                            2 -> Text("🤲 $DUA_TEXT", color = Color.White, fontSize = 14.5.sp, textAlign = TextAlign.Center, lineHeight = 24.sp)
                            else -> {
                                Text("حلقات تحفيظ القرآن الكريم والدراسات الإسلامية", color = Color.White, fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 28.sp)
                                Text("📍 داودابوغو، باماكو", color = Gold, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(4) { k ->
                        Box(Modifier.height(8.dp).width(if (k == slide) 24.dp else 8.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if (k == slide) Gold else Color.White.copy(alpha = .5f))
                            .clickable { slide = k })
                    }
                }
            }
        }
        // Login form
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).offset(y = (-18).dp),
            colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(6.dp)) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (registering) "إنشاء حساب جديد" else "تسجيل الدخول", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Green)
                Spacer(Modifier.height(12.dp))
                if (registering) AppField(name, { name = it }, "الاسم الكامل")
                AppField(email, { email = it }, "البريد الإلكتروني", KeyboardType.Email)
                OutlinedTextField(password, { password = it }, label = { Text("كلمة المرور") }, singleLine = true,
                    visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, "إظهار كلمة المرور", tint = Green)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp), textAlign = TextAlign.Center) }
                Spacer(Modifier.height(12.dp))
                Button(onClick = { if (registering) state.register(email, password, name) else state.login(email, password) },
                    enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    if (state.busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White)
                    else Text(if (registering) "إنشاء الحساب" else "تسجيل الدخول", fontSize = 16.sp)
                }
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { registering = !registering; state.message = null }) {
                        Text(if (registering) "لدي حساب بالفعل" else "إنشاء حساب جديد")
                    }
                    if (!registering) TextButton(onClick = { state.resetPassword(email) }) { Text("نسيت كلمة المرور؟") }
                }
            }
        }
        // Prayer for the founder
        DuaCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Text("بسم الله الرحمن الرحيم", Modifier.fillMaxWidth().padding(16.dp), textAlign = TextAlign.Center, color = Green.copy(alpha = .8f))
    }
}

@Composable
fun DuaCard(modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEA))) {
        Row(Modifier.padding(14.dp)) {
            Text("🤲", fontSize = 20.sp); Spacer(Modifier.width(8.dp))
            Text(DUA_TEXT, fontSize = 14.sp, lineHeight = 24.sp, color = Color(0xFF3B2E00))
        }
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
        if (me.isAdmin) add(Tab("المالية", Icons.Default.AccountBalanceWallet))
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    if (tab >= tabs.size) tab = 0
    var addOpen by remember { mutableStateOf(false) }
    var profileId by rememberSaveable { mutableStateOf<String?>(null) }
    var editStudent by remember { mutableStateOf<Student?>(null) }
    /** Secondary full screen shown above the tabs (null = tabs). */
    var screen by rememberSaveable { mutableStateOf<String?>(null) }
    var aboutOpen by remember { mutableStateOf(false) }
    var logoutOpen by remember { mutableStateOf(false) }
    var announceOpen by remember { mutableStateOf(false) }
    var exportOpen by remember { mutableStateOf(false) }
    var quickLogOpen by remember { mutableStateOf(false) }
    fun goTo(title: String) { tabs.indexOfFirst { it.title == title }.takeIf { it >= 0 }?.let { tab = it } }
    fun open(s: String) { profileId = null; screen = s }
    val quickActions = buildList {
        if (me.isStaff) {
            add(QuickAction("تسجيل الحضور", Icons.Default.FactCheck) { goTo("الحضور") })
            add(QuickAction("تسميع جديد", Icons.Default.MenuBook) { quickLogOpen = true })
            add(QuickAction("إضافة طالب", Icons.Default.PersonAdd) { addOpen = true })
            add(QuickAction("إدخال درجات", Icons.Default.School) { open(Screens.GRADES) })
            add(QuickAction("الإحصائيات", Icons.Default.Insights) { open(Screens.STATS) })
            add(QuickAction("الحلقات", Icons.Default.Diversity3) { open(Screens.HALAQAT) })
        }
        if (me.isAdmin) {
            add(QuickAction("المالية", Icons.Default.AccountBalanceWallet) { goTo("المالية") })
            add(QuickAction("إضافة جماعية", Icons.Default.GroupAdd) { open(Screens.BULK) })
            add(QuickAction("الموظفون والرواتب", Icons.Default.CoPresent) { open(Screens.TEACHERS) })
            add(QuickAction("إعلان جديد", Icons.Default.Campaign) { announceOpen = true })
        }
    }

    LaunchedEffect(state.message) { if (state.message != null) { delay(3500); state.message = null } }

    val profileStudent = profileId?.let { id -> state.students.firstOrNull { it.id == id } }
    if (profileId != null && profileStudent == null && state.students.isNotEmpty()) profileId = null
    val onMain = profileStudent == null && screen == null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(profileStudent?.name ?: Screens.title(screen) ?: "مركز الإمام مالك", maxLines = 1) },
                navigationIcon = {
                    if (!onMain) IconButton(onClick = { if (profileStudent != null) profileId = null else screen = null }) {
                        Icon(Icons.Default.ArrowForward, "رجوع", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Green, titleContentColor = Color.White),
                actions = {
                    if (me.isStaff) {
                        val (icon, msg) = when {
                            state.hasPendingWrites -> Icons.Default.CloudUpload to "توجد تغييرات محفوظة على الهاتف بانتظار الاتصال بالإنترنت، وستُرسل تلقائيًا."
                            state.isOffline -> Icons.Default.CloudOff to "لا يوجد اتصال؛ تعرض البيانات المحفوظة على الهاتف، ويمكنك متابعة العمل."
                            else -> Icons.Default.CloudDone to "كل البيانات متزامنة مع الخادم ✅"
                        }
                        IconButton(onClick = { state.message = msg }) {
                            Icon(icon, "حالة المزامنة", tint = if (state.hasPendingWrites || state.isOffline) Gold else Color.White)
                        }
                    }
                    if (profileStudent != null && me.isStaff) IconButton(onClick = { editStudent = profileStudent }) {
                        Icon(Icons.Default.Edit, "تعديل", tint = Color.White)
                    }
                    AppMenu(buildList {
                        if (me.isStaff) add(MenuEntry("الحلقات", Icons.Default.Diversity3) { open(Screens.HALAQAT) })
                        if (me.isAdmin) {
                            add(MenuEntry("الموظفون والرواتب", Icons.Default.CoPresent) { open(Screens.TEACHERS) })
                            add(MenuEntry("إضافة طلاب دفعة واحدة", Icons.Default.GroupAdd) { open(Screens.BULK) })
                            add(MenuEntry("إعدادات المركز", Icons.Default.Settings) { open(Screens.SETTINGS) })
                            add(MenuEntry("تصدير البيانات", Icons.Default.FileDownload) { exportOpen = true })
                            add(MenuEntry("حسابات المستخدمين", Icons.Default.ManageAccounts) { open(Screens.USERS) })
                            add(MenuEntry("سجل العمليات", Icons.Default.History) { open(Screens.AUDIT) })
                        }
                        if (me.isStaff) add(MenuEntry("الإحصائيات", Icons.Default.Insights) { open(Screens.STATS) })
                        add(MenuEntry("حول التطبيق", Icons.Default.Info) { aboutOpen = true })
                        add(MenuEntry("تسجيل الخروج", Icons.Default.Logout) { logoutOpen = true })
                    })
                }
            )
        },
        bottomBar = {
            if (onMain) NavigationBar {
                tabs.forEachIndexed { i, item ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i },
                        icon = { Icon(item.icon, item.title) }, label = { Text(item.title, maxLines = 1) })
                }
            }
        },
        floatingActionButton = {
            if (onMain && me.isStaff && tabs[tab].title == "الطلاب")
                FloatingActionButton(onClick = { addOpen = true }, containerColor = Gold) { Icon(Icons.Default.PersonAdd, "إضافة طالب") }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (profileStudent != null) {
                BackHandler { profileId = null }
                StudentProfileScreen(state, me, profileStudent)
            } else if (screen != null) {
                BackHandler { screen = null }
                when (screen) {
                    Screens.SETTINGS -> SettingsScreen(state) { screen = null }
                    Screens.STATS -> StatsScreen(state) { profileId = it }
                    Screens.GRADES -> GradeEntryScreen(state) { screen = null }
                    Screens.USERS -> UsersScreen(state)
                    Screens.HALAQAT -> HalaqatScreen(state, me, onBulk = { screen = Screens.BULK }) { profileId = it }
                    Screens.BULK -> BulkAddScreen(state) { screen = null }
                    Screens.TEACHERS -> TeachersScreen(state)
                    Screens.AUDIT -> AuditScreen(state)
                }
            } else when (tabs[tab].title) {
                "الرئيسية" -> HomeScreen(state, me, quickActions) { profileId = it.id }
                "الطلاب", "أبنائي" -> StudentsScreen(state, me, onGradeEntry = { open(Screens.GRADES) },
                    onBulkAdd = { open(Screens.BULK) }) { profileId = it.id }
                "الحضور" -> AttendanceScreen(state)
                "الحفظ" -> QuranScreen(state) { profileId = it }
                "المالية" -> FinanceScreen(state) { profileId = it }
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
    if (aboutOpen) AboutDialog { aboutOpen = false }
    if (logoutOpen) LogoutConfirm(state::logout) { logoutOpen = false }
    if (quickLogOpen) LogDialog(state, null) { quickLogOpen = false }
    editStudent?.let { s -> StudentDialog(state, s, onDeleted = { profileId = null }) { editStudent = null } }
}

object Screens {
    const val SETTINGS = "settings"
    const val STATS = "stats"
    const val GRADES = "grades"
    const val USERS = "users"
    const val HALAQAT = "halaqat"
    const val BULK = "bulk"
    const val TEACHERS = "teachers"
    const val AUDIT = "audit"
    fun title(s: String?): String? = when (s) {
        SETTINGS -> "إعدادات المركز"
        STATS -> "الإحصائيات"
        GRADES -> "إدخال الدرجات"
        USERS -> "حسابات المستخدمين"
        HALAQAT -> "الحلقات"
        BULK -> "إضافة طلاب دفعة واحدة"
        TEACHERS -> "الموظفون والرواتب"
        AUDIT -> "سجل العمليات"
        else -> null
    }
}

@Composable
fun StatCard(title: String, value: String, icon: ImageVector, modifier: Modifier, tint: Color = Gold) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = tint)
            // Amounts: number on one line (size adapted to its length), currency smaller underneath,
            // so every card looks the same instead of wrapping differently.
            val suffix = " " + Centre.currency
            val isAmount = value.endsWith(suffix)
            val number = if (isAmount) value.removeSuffix(suffix) else value
            val digits = number.count { it.isDigit() }
            Text(number, fontSize = when { digits >= 8 -> 17.sp; digits >= 6 -> 20.sp; else -> 22.sp },
                fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            if (isAmount) Text(Centre.currency, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
            Text(title, fontSize = 13.sp, color = Color.DarkGray, maxLines = 1)
        }
    }
}

@Composable
private fun HomeScreen(state: AppState, me: AppUser, quickActions: List<QuickAction>, open: (Student) -> Unit) {
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
        if (quickActions.isNotEmpty()) item { QuickActions(quickActions) }
        item { DuaCard() }
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

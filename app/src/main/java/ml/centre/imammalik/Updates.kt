package ml.centre.imammalik

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Latest published version name (e.g. "2.6.0"), or null when offline / unavailable. */
suspend fun fetchLatestVersion(): String? = withContext(Dispatchers.IO) {
    runCatching {
        val c = URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (c.responseCode != 200) null
            else org.json.JSONObject(c.inputStream.bufferedReader().readText()).optString("tag_name").removePrefix("v").ifBlank { null }
        } finally { c.disconnect() }
    }.getOrNull()
}

/** Opens [link] following GitHub's redirect to its download host; null unless the final answer is 200. */
private fun openFollowing(link: String): HttpURLConnection? {
    var url = URL(link); var hops = 0
    while (true) {
        val c = url.openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false; c.connectTimeout = 15000; c.readTimeout = 30000
        val code = c.responseCode
        if (code in 300..399 && hops++ < 5) { url = URL(url, c.getHeaderField("Location")); c.disconnect(); continue }
        if (code != 200) { c.disconnect(); return null }
        return c
    }
}

/** SHA-256 published next to the APK (64 hex characters), or null for releases that have none / when offline. */
private fun fetchPublishedHash(): String? = runCatching {
    openFollowing("$APK_DOWNLOAD_URL.sha256")?.let { c ->
        try { c.inputStream.bufferedReader().readText().trim().lowercase().takeIf { Regex("[0-9a-f]{64}").matches(it) } } finally { c.disconnect() }
    }
}.getOrNull()

private fun sha256Of(file: java.io.File): String {
    val md = java.security.MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input -> val buf = ByteArray(64 * 1024); while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
    return md.digest().joinToString("") { "%02x".format(it) }
}

/** True on mobile data (or any metered network). */
fun isMeteredNow(context: Context): Boolean = runCatching {
    (context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).isActiveNetworkMetered
}.getOrDefault(false)

/** «Update automatically over Wi-Fi only» (default on, saved on the phone): mobile data is expensive. */
object UpdatePref {
    var wifiOnly by mutableStateOf(true)
    fun load(context: Context) { wifiOnly = context.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean("update_wifi_only", true) }
    fun toggle(context: Context) {
        wifiOnly = !wifiOnly
        context.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putBoolean("update_wifi_only", wifiOnly).apply()
    }
}

/**
 * Downloads the newest APK into the app cache, reporting progress 0..1. The file is accepted only when it is complete
 * (same size as announced) and, when the release publishes one, matches its SHA-256.
 * Returns the file, or null on failure (the caller then falls back to the browser).
 */
suspend fun downloadUpdate(context: Context, onProgress: (Float) -> Unit): java.io.File? = withContext(Dispatchers.IO) {
    runCatching {
        val dir = java.io.File(context.cacheDir, "updates").apply { mkdirs() }
        val file = java.io.File(dir, "centre-imam-malik.apk")
        val c = openFollowing(APK_DOWNLOAD_URL) ?: return@runCatching null
        val total = c.contentLengthLong
        var done = 0L
        c.inputStream.use { input ->
            file.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    out.write(buf, 0, n); done += n
                    if (total > 0) withContext(Dispatchers.Main) { onProgress(done.toFloat() / total) }
                }
            }
        }
        c.disconnect()
        if (done < 100_000 || (total > 0 && done != total)) { file.delete(); return@runCatching null }   // truncated download
        val expected = fetchPublishedHash()
        if (expected != null && sha256Of(file) != expected) { file.delete(); return@runCatching null }      // corrupted / altered
        file
    }.getOrNull()
}

/** Where the update flow is: download progress (0..1) or installing; read by the home screen. */
object UpdateState {
    var progress by mutableStateOf<Float?>(null)
    var installing by mutableStateOf(false)
    var running = false
}

/**
 * Installs the downloaded APK through PackageInstaller. On Android 12+ an update of an app that this app itself
 * installed earlier needs no confirmation («USER_ACTION_NOT_REQUIRED»); otherwise Android asks once
 * (the receiver below opens that confirmation).
 */
fun installUpdate(context: Context, file: java.io.File): Boolean = runCatching {
    val installer = context.packageManager.packageInstaller
    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
        setAppPackageName(context.packageName)
        if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
    }
    val id = installer.createSession(params)
    installer.openSession(id).use { session ->
        file.inputStream().use { input ->
            session.openWrite("update.apk", 0, file.length()).use { out -> input.copyTo(out); session.fsync(out) }
        }
        val result = PendingIntent.getBroadcast(context, id,
            Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        session.commit(result.intentSender)
    }
    true
}.getOrDefault(false)

/** Receives the installer's answer: shows Android's confirmation when needed, falls back to the browser on failure. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                              else intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            }
            PackageInstaller.STATUS_SUCCESS -> UpdateState.installing = false
            else -> { UpdateState.installing = false; openApkDownload(context) }   // blocked / failed: manual download
        }
    }
}

/**
 * Downloads the newest APK in the background and installs it. [manual] = started by a tap: a failed download
 * opens the browser instead of staying silent.
 */
suspend fun runUpdate(context: Context, manual: Boolean) {
    if (UpdateState.running) return
    UpdateState.running = true
    try {
        UpdateState.progress = 0f
        val f = downloadUpdate(context) { UpdateState.progress = it }
        UpdateState.progress = null
        if (f == null) { if (manual) openApkDownload(context); return }
        UpdateState.installing = true
        if (!installUpdate(context, f)) { UpdateState.installing = false; openApkDownload(context) }
    } finally { UpdateState.progress = null; UpdateState.running = false }
}

/** Confirmation for a manual update (banner / menu). Progress is shown by [UpdateStatusCard]. */
@Composable
fun AutoUpdateDialog(latest: String, close: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = close,
        icon = { Icon(Icons.Default.SystemUpdate, null, tint = Green) },
        title = { Text("يتوفر إصدار جديد ($latest)") },
        text = { Text("يُنزَّل التحديث ويُثبَّت وحده، وبياناتك محفوظة. قد يُغلق التطبيق لحظة؛ افتحه بعدها.") },
        confirmButton = { Button(onClick = { close(); scope.launch { runUpdate(context, manual = true) } }) { Text("تحديث الآن") } },
        dismissButton = { TextButton(onClick = close) { Text("لاحقًا") } }
    )
}

/** Small card while an update downloads / installs. */
@Composable
fun UpdateStatusCard(modifier: Modifier = Modifier) {
    val p = UpdateState.progress
    if (p == null && !UpdateState.installing) return
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Green)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(if (p != null) "🔄 جارٍ تنزيل التحديث… ${(p * 100).toInt()}٪" else "🔄 جارٍ تثبيت التحديث… قد يُغلق التطبيق لحظة",
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = Gold)
        }
    }
}

/** Opens the browser on the newest APK: Android downloads it, then a tap installs it over the current app. */
fun openApkDownload(context: Context) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(APK_DOWNLOAD_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
fun UpdateBanner(latest: String, onUpdate: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Gold.copy(alpha = .18f))) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SystemUpdate, null, tint = Green)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("يتوفر إصدار جديد ($latest)", fontWeight = FontWeight.Bold, color = Green)
                Text("اضغط «تحديث»، ثم افتح الملف بعد التنزيل واضغط «تثبيت». بياناتك محفوظة.", fontSize = 12.sp, color = SubText)
            }
            Button(onClick = onUpdate) { Text("تحديث") }
        }
    }
}

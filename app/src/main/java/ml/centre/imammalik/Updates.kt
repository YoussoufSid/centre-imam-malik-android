package ml.centre.imammalik

import android.content.Context
import android.content.Intent
import android.net.Uri
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

/**
 * Downloads the newest APK into the app cache, reporting progress 0..1.
 * Returns the file, or null on failure (the caller then falls back to the browser).
 */
suspend fun downloadUpdate(context: Context, onProgress: (Float) -> Unit): java.io.File? = withContext(Dispatchers.IO) {
    runCatching {
        val dir = java.io.File(context.cacheDir, "updates").apply { mkdirs() }
        val file = java.io.File(dir, "centre-imam-malik.apk")
        var url = URL(APK_DOWNLOAD_URL)
        var c: HttpURLConnection
        var hops = 0
        while (true) {  // follow GitHub's redirect to its download host
            c = url.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false; c.connectTimeout = 15000; c.readTimeout = 30000
            val code = c.responseCode
            if (code in 300..399 && hops++ < 5) { url = URL(url, c.getHeaderField("Location")); c.disconnect(); continue }
            if (code != 200) { c.disconnect(); return@runCatching null }
            break
        }
        val total = c.contentLengthLong
        c.inputStream.use { input ->
            file.outputStream().use { out ->
                val buf = ByteArray(64 * 1024); var done = 0L
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    out.write(buf, 0, n); done += n
                    if (total > 0) withContext(Dispatchers.Main) { onProgress(done.toFloat() / total) }
                }
            }
        }
        c.disconnect()
        file.takeIf { it.length() > 100_000 }
    }.getOrNull()
}

/** Hands the downloaded APK to Android's installer (the person confirms «تحديث»). */
fun installUpdate(context: Context, file: java.io.File): Boolean = runCatching {
    val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", file)
    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    })
    true
}.getOrDefault(false)

/**
 * Shown automatically when a newer version exists: one tap downloads inside the app with a progress bar,
 * then opens the installer. Falls back to the browser download if anything fails.
 */
@Composable
fun AutoUpdateDialog(latest: String, close: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Float?>(null) }
    AlertDialog(
        onDismissRequest = { if (progress == null) close() },
        icon = { Icon(Icons.Default.SystemUpdate, null, tint = Green) },
        title = { Text("يتوفر إصدار جديد ($latest)") },
        text = {
            Column {
                if (progress == null) Text("التحديث يأخذ دقيقة واحدة، وبياناتك محفوظة. بعد التنزيل اضغط «تحديث» ثم «فتح».")
                else {
                    Text("جارٍ تنزيل التحديث… ${(progress!! * 100).toInt()}٪")
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(progress = { progress!! }, modifier = Modifier.fillMaxWidth(), color = Green)
                }
            }
        },
        confirmButton = {
            if (progress == null) Button(onClick = {
                progress = 0f
                scope.launch {
                    val f = downloadUpdate(context) { progress = it }
                    if (f == null || !installUpdate(context, f)) openApkDownload(context)
                    close()
                }
            }) { Text("تحديث الآن") }
        },
        dismissButton = { if (progress == null) TextButton(onClick = close) { Text("لاحقًا") } }
    )
}

/** Opens the browser on the newest APK: Android downloads it, then a tap installs it over the current app. */
fun openApkDownload(context: Context) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(APK_DOWNLOAD_URL))) }
}

@Composable
fun UpdateBanner(latest: String, onUpdate: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Gold.copy(alpha = .18f))) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SystemUpdate, null, tint = Green)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("يتوفر إصدار جديد ($latest)", fontWeight = FontWeight.Bold, color = Green)
                Text("اضغط «تحديث»، ثم افتح الملف بعد التنزيل واضغط «تثبيت». بياناتك محفوظة.", fontSize = 12.sp, color = Color.DarkGray)
            }
            Button(onClick = onUpdate) { Text("تحديث") }
        }
    }
}

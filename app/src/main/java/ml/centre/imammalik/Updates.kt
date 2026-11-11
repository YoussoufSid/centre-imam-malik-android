package ml.centre.imammalik

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
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

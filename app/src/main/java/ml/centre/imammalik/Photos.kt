package ml.centre.imammalik

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.ByteArrayOutputStream

/** Student photos are stored as a small JPEG data URL (~10 KB) in the student document: no extra Firebase setup. */
private val Red = Color(0xFFC53030)
private const val PHOTO_W = 240
private const val PHOTO_H = 320

/** Centre-crops to 3:4, scales to 240×320 and encodes as a JPEG data URL. */
fun photoToDataUrl(src: Bitmap): String {
    val ratio = PHOTO_W.toFloat() / PHOTO_H
    val (cw, ch) = if (src.width.toFloat() / src.height > ratio) (src.height * ratio).toInt() to src.height
    else src.width to (src.width / ratio).toInt()
    val cropped = Bitmap.createBitmap(src, (src.width - cw) / 2, (src.height - ch) / 2, cw, ch)
    val scaled = Bitmap.createScaledBitmap(cropped, PHOTO_W, PHOTO_H, true)
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 72, out)
    return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

fun decodePhoto(dataUrl: String): Bitmap? = runCatching {
    if (dataUrl.isBlank()) return null
    val bytes = Base64.decode(dataUrl.substringAfter("base64,"), Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

/** Loads a gallery image with down-sampling and EXIF rotation. */
private fun loadBitmap(context: Context, uri: Uri): Bitmap? = runCatching {
    val cr = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= PHOTO_W * 2 && bounds.outHeight / (sample * 2) >= PHOTO_H * 2) sample *= 2
    val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        ?: return null
    val rotation = cr.openInputStream(uri)?.use {
        when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } ?: 0f
    if (rotation == 0f) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
}.getOrNull()

/** Round photo, or the initial-letter avatar when there is no photo. */
@Composable
fun StudentAvatar(s: Student, size: Dp = 40.dp) {
    val bmp = remember(s.photo) { decodePhoto(s.photo) }
    if (bmp == null) Avatar(s.name, size)
    else Image(bmp.asImageBitmap(), null, Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
}

/** Photo box with camera / gallery / delete buttons, used in the student form (also at registration). */
@Composable
fun PhotoPicker(photo: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) onChange(photoToDataUrl(bmp))
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) loadBitmap(context, uri)?.let { onChange(photoToDataUrl(it)) }
    }
    val bmp = remember(photo) { decodePhoto(photo) }
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(72.dp, 96.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF3F1E8))
            .border(1.dp, Color(0xFFCBD5D1), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            if (bmp != null) Image(bmp.asImageBitmap(), "صورة الطالب", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Default.Person, null, Modifier.size(40.dp), tint = Color.Gray)
        }
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("صورة الطالب (للبطاقة)", fontSize = 13.sp, color = Color.Gray)
            Row {
                FilledTonalButton(onClick = { runCatching { camera.launch(null) } }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Icon(Icons.Default.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("الكاميرا", fontSize = 13.sp)
                }
                Spacer(Modifier.width(6.dp))
                FilledTonalButton(onClick = {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Icon(Icons.Default.PhotoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("المعرض", fontSize = 13.sp)
                }
            }
            if (photo.isNotBlank()) TextButton(onClick = { onChange("") }, contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = Red); Text(" حذف الصورة", color = Red, fontSize = 13.sp)
            }
        }
    }
}

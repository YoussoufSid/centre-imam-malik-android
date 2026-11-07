package ml.centre.imammalik

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AvatarColors = listOf(
    Color(0xFF075B3A), Color(0xFF2B6CB0), Color(0xFFB7791F), Color(0xFF805AD5),
    Color(0xFFC05621), Color(0xFF2C7A7B), Color(0xFFB83280), Color(0xFF4A5568)
)

/** Round badge with the first letter of a name, coloured consistently per name. */
@Composable
fun Avatar(name: String, size: Dp = 40.dp) {
    val color = AvatarColors[(name.hashCode() and 0x7fffffff) % AvatarColors.size]
    Box(Modifier.size(size).clip(CircleShape).background(color.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).ifBlank { "؟" }, color = color, fontWeight = FontWeight.Bold, fontSize = (size.value * .45f).sp)
    }
}

@Composable
fun EmptyState(icon: ImageVector, text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(Green.copy(alpha = .08f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Green.copy(alpha = .6f), modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(text, color = Color.Gray, textAlign = TextAlign.Center)
    }
}

data class QuickAction(val title: String, val icon: ImageVector, val onClick: () -> Unit)

/** Grid of big shortcut buttons for the most common daily tasks. */
@Composable
fun QuickActions(actions: List<QuickAction>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        actions.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { a ->
                    Card(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(onClick = a.onClick),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(Green.copy(alpha = .1f)), contentAlignment = Alignment.Center) {
                                Icon(a.icon, null, tint = Green)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(a.title, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 16.sp)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

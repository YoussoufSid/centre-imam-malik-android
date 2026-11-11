package ml.centre.imammalik

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Screen title framed by gold Islamic ornaments: ۞ العنوان ۞ */
@Composable
fun OrnateTitle(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("۞", color = Gold, fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(8.dp))
        Text("۞", color = Gold, fontSize = 18.sp)
    }
}

/** Thin gold line that fades at both ends, under the top bar. */
@Composable
fun GoldRule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).background(
        Brush.horizontalGradient(listOf(Green, Gold, Color(0xFFF4D77A), Gold, Green))))
}

/** Section heading: ❁ title, then a short gold rule. */
@Composable
fun SectionHeading(title: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("❁", color = Gold, fontSize = 16.sp)
            Spacer(Modifier.width(6.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Green)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.width(64.dp).height(2.dp).background(Brush.horizontalGradient(listOf(Gold, Color.Transparent))))
    }
}

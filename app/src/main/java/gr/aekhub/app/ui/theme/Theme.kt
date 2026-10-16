package gr.aekhub.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import gr.aekhub.app.R

/** Τα χρώματα του aekhub.gr */
object C {
    val Ink = Color(0xFF0B0B0A)
    val Ink2 = Color(0xFF10100E)
    val Panel = Color(0xFF151513)
    val Panel2 = Color(0xFF1C1B18)
    val Panel3 = Color(0xFF242320)
    val Line = Color(0xFF2A2924)
    val Line2 = Color(0xFF37352F)
    val Text = Color(0xFFF3F1E9)
    val Text2 = Color(0xFFCFCBBE)
    val Muted = Color(0xFF8E8A7C)
    val Gold = Color(0xFFF9CF1C)
    val Live = Color(0xFFFF4B3E)
    val Win = Color(0xFF1A9B54)
    val Loss = Color(0xFFD63638)
}

val Body = FontFamily(
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_medium, FontWeight.Normal),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_bold, FontWeight.SemiBold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

val Display = FontFamily(
    Font(R.font.sofia_extrabold, FontWeight.ExtraBold),
    Font(R.font.sofia_extrabold, FontWeight.Bold),
    Font(R.font.sofia_black, FontWeight.Black),
)

fun disp(size: Int, weight: FontWeight = FontWeight.ExtraBold) =
    TextStyle(fontFamily = Display, fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.0).sp)

fun body(size: Int, weight: FontWeight = FontWeight.Medium, color: Color = C.Text) =
    TextStyle(fontFamily = Body, fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.35).sp, color = color)

@Composable
fun AekTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Gold, onPrimary = C.Ink,
            background = C.Ink, onBackground = C.Text,
            surface = C.Panel, onSurface = C.Text,
            surfaceVariant = C.Panel2, onSurfaceVariant = C.Text2,
            outline = C.Line, error = C.Live,
        ),
        typography = Typography(
            bodyLarge = body(16), bodyMedium = body(14), bodySmall = body(12, color = C.Muted),
            labelLarge = body(14, FontWeight.ExtraBold), labelMedium = body(12, FontWeight.Bold), labelSmall = body(11, FontWeight.ExtraBold),
            titleLarge = disp(28), titleMedium = body(16, FontWeight.Bold), titleSmall = body(14, FontWeight.Bold),
            headlineLarge = disp(36), headlineMedium = disp(30), headlineSmall = disp(22),
        ),
        content = content,
    )
}

package gr.aekhub.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import gr.aekhub.app.data.Clock
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body
import gr.aekhub.app.ui.theme.disp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val GR: Locale = Locale.forLanguageTag("el-GR")
val ATHENS: ZoneId = ZoneId.of("Europe/Athens")

private val MONTHS_GEN = listOf("Ιανουαρίου", "Φεβρουαρίου", "Μαρτίου", "Απριλίου", "Μαΐου", "Ιουνίου", "Ιουλίου", "Αυγούστου", "Σεπτεμβρίου", "Οκτωβρίου", "Νοεμβρίου", "Δεκεμβρίου")
private val DAYS = listOf("Δευτέρα", "Τρίτη", "Τετάρτη", "Πέμπτη", "Παρασκευή", "Σάββατο", "Κυριακή")
private val DAYS_SHORT = listOf("ΔΕΥ", "ΤΡΙ", "ΤΕΤ", "ΠΕΜ", "ΠΑΡ", "ΣΑΒ", "ΚΥΡ")

/** Πάντα ημερομηνία, ποτέ «πριν από…». */
fun postDate(iso: String, withYear: Boolean = true): String = runCatching {
    val d = OffsetDateTime.parse(iso).atZoneSameInstant(ATHENS).toLocalDate()
    d.dayOfMonth.toString() + " " + MONTHS_GEN[d.monthValue - 1] + if (withYear) " " + d.year else ""
}.getOrDefault("")

fun dayHeader(d: LocalDate): String = (DAYS[d.dayOfWeek.value - 1] + " " + d.dayOfMonth + " " + MONTHS_GEN[d.monthValue - 1]).uppercase(GR).stripAccents()
fun dayShort(d: LocalDate): String = DAYS_SHORT[d.dayOfWeek.value - 1]
fun String.stripAccents(): String = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
fun String.upperGr(): String = uppercase(GR).stripAccents()

/** Λεπτό αγώνα που «τρέχει» μόνο του (μόνο όταν το Match Center έχει ρολόι). */
@Composable
fun liveMinute(clock: Clock?, fallback: String): String {
    if (clock == null || clock.s <= 0) return fallback
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(clock) { while (true) { now = System.currentTimeMillis(); delay(15_000) } }
    val el = now - clock.s
    if (el > 3 * 3_600_000) return fallback
    val min = kotlin.math.floor(clock.b + maxOf(0.0, el) / 60000.0).toInt() + 1
    val end = (clock.b + clock.l).toInt()
    return if (min > end) "$end+${min - end}′" else "$min′"
}

@Composable
fun ChipRow(items: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { (key, label) ->
            val on = key == selected
            Box(
                Modifier.clip(RoundedCornerShape(999.dp))
                    .background(if (on) C.Gold else C.Panel2)
                    .clickable { onPick(key) }
                    .heightIn(min = 36.dp)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = body(13, if (on) FontWeight.ExtraBold else FontWeight.Bold, if (on) C.Ink else C.Text))
            }
        }
    }
}

@Composable
fun Crest(url: String, name: String, size: Dp) {
    val fb: @Composable () -> Unit = {
        Box(Modifier.size(size).clip(CircleShape).background(C.Panel3).border(1.dp, C.Line2, CircleShape), contentAlignment = Alignment.Center) {
            Text(name.upperGr().take(3), style = body((size.value / 3.4f).toInt().coerceAtLeast(8), FontWeight.ExtraBold, C.Text2))
        }
    }
    if (url.isBlank()) { fb(); return }
    SubcomposeAsyncImage(
        model = url, contentDescription = null, contentScale = ContentScale.Fit,
        modifier = Modifier.size(size),
        error = { fb() }, loading = { fb() },
    )
}

@Composable
fun LiveDot(color: Color = C.Live) = Box(Modifier.size(8.dp).clip(CircleShape).background(color))

@Composable
fun Loading() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(color = C.Gold, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
}

@Composable
fun ErrorBox(msg: String = "Δεν φόρτωσε. Έλεγξε τη σύνδεση.", onRetry: () -> Unit) =
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(msg, style = body(15, FontWeight.Bold, C.Text2), textAlign = TextAlign.Center)
        Spacer(Modifier.size(16.dp))
        Box(
            Modifier.clip(RoundedCornerShape(12.dp)).background(C.Gold).clickable(onClick = onRetry).padding(horizontal = 22.dp, vertical = 12.dp)
        ) { Text("Ξαναδοκίμασε", style = body(14, FontWeight.ExtraBold, C.Ink)) }
    }

@Composable
fun Tag(text: String) = Box(
    Modifier.clip(RoundedCornerShape(6.dp)).background(C.Gold).padding(horizontal = 8.dp, vertical = 4.dp)
) { Text(text.upperGr(), style = body(11, FontWeight.ExtraBold, C.Ink).copy(letterSpacing = 0.5.sp)) }

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier = modifier, style = body(12, FontWeight.ExtraBold, C.Muted).copy(letterSpacing = 1.sp))

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) =
    Text(text.upperGr(), modifier = modifier, style = disp(32), color = C.Text)

val ListPad = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)

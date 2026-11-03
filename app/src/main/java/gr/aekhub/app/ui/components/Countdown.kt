package gr.aekhub.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import gr.aekhub.app.data.MatchCard
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body
import gr.aekhub.app.ui.theme.disp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Ώρα σέντρας: από το ISO του server, αλλιώς ημερομηνία + ώρα (Αθήνα). */
fun kickoffMillis(m: MatchCard): Long? = runCatching { OffsetDateTime.parse(m.kickoff).toInstant().toEpochMilli() }.getOrNull()
    ?: runCatching {
        val d = LocalDate.parse(m.date.take(10))
        val t = m.time.takeIf { it.length >= 4 && it[0].isDigit() }?.let { LocalTime.parse(it.take(5)) } ?: return null
        LocalDateTime.of(d, t).atZone(ATHENS).toInstant().toEpochMilli()
    }.getOrNull()

/**
 * ⏱ Αντίστροφη μέτρηση πάνω-πάνω στην αρχική, για τον αγώνα που έχει επιλεγεί στο site (AEKhub → Αρχική).
 * Μέρες · ώρες · λεπτά · δευτερόλεπτα, ζωντανά. Πάτημα → σελίδα αγώνα. Κρύβεται όταν ξεκινήσει ο αγώνας.
 */
@Composable
fun NextMatchCountdown(m: MatchCard, open: () -> Unit) {
    val ko = remember(m.id, m.kickoff, m.date, m.time) { kickoffMillis(m) } ?: return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ko) { while (true) { now = System.currentTimeMillis(); delay(1_000) } }
    val left = ((ko - now) / 1000).coerceAtLeast(0)
    if (left <= 0 || m.live || m.finished) return
    val d = left / 86_400; val h = (left % 86_400) / 3_600; val mi = (left % 3_600) / 60; val s = left % 60
    val whenTxt = runCatching {
        java.time.Instant.ofEpochMilli(ko).atZone(ATHENS).format(DateTimeFormatter.ofPattern("EEE d/M · HH:mm", Locale("el"))).replaceFirstChar { it.uppercase() }
    }.getOrDefault(m.time)

    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1D1B13), C.Panel, Color(0xFF221C05))))
            .border(1.dp, C.Gold.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
            .clickable(onClick = open).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ΕΠΟΜΕΝΟΣ ΑΓΩΝΑΣ", style = body(11, FontWeight.ExtraBold, C.Gold), maxLines = 1)
            Spacer(Modifier.width(8.dp))
            Text(m.competition.ifBlank { m.sportLabel }, Modifier.weight(1f), style = body(11, FontWeight.Bold, C.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(whenTxt, style = body(11, FontWeight.Bold, C.Text2), maxLines = 1)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Team(m.home, m.homeLogo, Modifier.weight(1f), end = false)
            Text("VS", Modifier.padding(horizontal = 8.dp), style = body(12, FontWeight.ExtraBold, C.Muted))
            Team(m.away, m.awayLogo, Modifier.weight(1f), end = true)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Cell(d, "ΜΕΡΕΣ", Modifier.weight(1f)); Cell(h, "ΩΡΕΣ", Modifier.weight(1f))
            Cell(mi, "ΛΕΠΤΑ", Modifier.weight(1f)); Cell(s, "ΔΕΥΤ.", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Team(name: String, logo: String, modifier: Modifier, end: Boolean) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (end) Arrangement.End else Arrangement.Start) {
        if (!end) { Crest(logo); Spacer(Modifier.width(8.dp)) }
        Text(name.uppercase(), Modifier.weight(1f, fill = false), style = disp(15), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = if (end) TextAlign.End else TextAlign.Start)
        if (end) { Spacer(Modifier.width(8.dp)); Crest(logo) }
    }
}

@Composable
private fun Crest(url: String) {
    if (url.isNotBlank()) AsyncImage(url, null, Modifier.size(30.dp)) else Box(Modifier.size(30.dp).clip(RoundedCornerShape(15.dp)).background(C.Line))
}

@Composable
private fun Cell(v: Long, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(C.Ink).padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(v.toString().padStart(2, '0'), style = disp(22).copy(color = C.Gold), maxLines = 1)
        Text(label, style = body(9, FontWeight.ExtraBold, C.Muted), maxLines = 1)
    }
}

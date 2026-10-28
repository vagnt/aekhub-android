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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.aekhub.app.data.MatchCard
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body
import gr.aekhub.app.ui.theme.disp
import java.time.LocalDate

/** Κεκλιμένο σχήμα σαν τις κίτρινες ετικέτες του site. */
val Slant = GenericShape { size, _ ->
    val k = size.height * 0.35f
    moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width - k, size.height); lineTo(0f, size.height); close()
}
val SlantBoth = GenericShape { size, _ ->
    val k = size.height * 0.3f
    moveTo(k, 0f); lineTo(size.width, 0f); lineTo(size.width - k, size.height); lineTo(0f, size.height); close()
}

/** Τίτλος ενότητας όπως στο site: κίτρινη κεκλιμένη ετικέτα + γραμμή. */
@Composable
fun GoldHeader(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clip(Slant).background(C.Gold).padding(start = 14.dp, end = 26.dp, top = 6.dp, bottom = 6.dp)) {
            Text(text.upperGr(), style = disp(22), color = C.Ink)
        }
        Box(Modifier.weight(1f).padding(horizontal = 10.dp).height(1.dp).background(C.Line))
        if (action != null && onAction != null) Text(
            "$action ›", Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 6.dp, vertical = 6.dp),
            style = body(13, FontWeight.ExtraBold, C.Gold),
        )
    }
}

fun hex(s: String, fallback: Color): Color = runCatching {
    val h = s.trim().removePrefix("#")
    val v = when (h.length) { 3 -> h.map { "$it$it" }.joinToString(""); 6 -> h; else -> return fallback }
    Color(0xFF000000 or v.toLong(16))
}.getOrDefault(fallback)

fun matchMeta(m: MatchCard) = listOf(m.sportLabel, m.competition).filter { it.isNotBlank() }.joinToString(" · ").upperGr()

@Composable
private fun TeamCell(logo: String, name: String, aek: Boolean, modifier: Modifier) =
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Crest(logo, name, 48.dp)
        Text(name.upperGr(), style = disp(16), color = if (aek) C.Gold else C.Text, textAlign = TextAlign.Center, maxLines = 2, softWrap = name.trim().contains(' '), overflow = TextOverflow.Ellipsis)
    }

/** Κεντρικό κομμάτι: ώρα, σκορ ή LIVE. */
@Composable
private fun Center(m: MatchCard) = Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    when {
        m.live -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.scoreHome.ifBlank { "0" }, style = disp(32, FontWeight.Black), color = if (m.aek == "home") C.Gold else C.Text)
                Text(" : ", style = disp(26), color = C.Muted)
                Text(m.scoreAway.ifBlank { "0" }, style = disp(32, FontWeight.Black), color = if (m.aek == "away") C.Gold else C.Text)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                LiveDot()
                val mn = liveMinute(m.clock, m.minute)
                Text(if (mn.isNotBlank()) mn else m.statusLabel.upperGr(), style = body(12, FontWeight.ExtraBold, C.Live))
            }
        }
        m.finished && m.scoreHome.isNotBlank() -> {
            val bg = when (m.aekWon) { true -> C.Win; false -> C.Loss; null -> C.Panel3 }
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 10.dp, vertical = 2.dp)) {
                Text("${m.scoreHome} : ${m.scoreAway}", style = disp(26, FontWeight.Black), color = Color.White)
            }
            Text(if (m.penHome.isNotBlank()) "ΠΕΝ. ${m.penHome}-${m.penAway}" else "ΤΕΛΙΚΟ", style = body(10, FontWeight.ExtraBold, C.Muted))
        }
        m.postponed -> Text("ΑΝΑΒΟΛΗ", style = body(12, FontWeight.ExtraBold, C.Muted))
        else -> {
            Text("VS", style = body(10, FontWeight.ExtraBold, C.Muted).copy(letterSpacing = 2.sp))
            Text(m.time.ifBlank { "—" }, style = disp(28, FontWeight.Black), color = C.Gold)
        }
    }
}

/** Κάρτα αγώνα με λογότυπα (λίστα αγώνων). */
@Composable
fun MatchCardView(m: MatchCard, alert: Boolean, onAlert: () -> Unit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(C.Panel).border(1.dp, if (m.live) C.Live else C.Line, shape).clickable(onClick = onClick)
            .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(matchMeta(m), Modifier.weight(1f), style = body(11, FontWeight.ExtraBold, C.Gold).copy(letterSpacing = 0.4.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (m.upcoming) IconButton(onClick = onAlert) {
                Icon(if (alert) Icons.Filled.Notifications else Icons.Outlined.NotificationsNone, if (alert) "Ειδοποίηση ενεργή" else "Ειδοποίηση για αυτόν τον αγώνα", tint = if (alert) C.Gold else C.Muted)
            } else Spacer(Modifier.height(40.dp))
        }
        Row(Modifier.fillMaxWidth().padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TeamCell(m.homeLogo, m.home, m.aek == "home", Modifier.weight(1f))
            Center(m)
            TeamCell(m.awayLogo, m.away, m.aek == "away", Modifier.weight(1f))
        }
        val foot = listOf(m.venue, m.broadcast).filter { it.isNotBlank() }
        if (foot.isNotEmpty()) Row(Modifier.padding(top = 10.dp, end = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (m.broadcast.isNotBlank()) { Icon(Icons.Outlined.Tv, null, tint = C.Gold, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
            Text(foot.reversed().joinToString("  ·  "), style = body(12, color = C.Text2), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Μικρή κάρτα για το καρουζέλ «Επόμενοι αγώνες» της αρχικής. */
@Composable
fun MatchMini(m: MatchCard, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val date = runCatching { LocalDate.parse(m.date) }.getOrNull()
    Column(
        Modifier.width(250.dp).clip(shape).background(C.Panel).border(1.dp, if (m.live) C.Live else C.Line, shape).clickable(onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(matchMeta(m), style = body(10, FontWeight.ExtraBold, C.Gold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamCell(m.homeLogo, m.home, m.aek == "home", Modifier.weight(1f))
            Center(m)
            TeamCell(m.awayLogo, m.away, m.aek == "away", Modifier.weight(1f))
        }
        if (!m.live) Text(
            ((date?.let { dayShort(it) + " " + it.dayOfMonth + " " + monthShort(it) } ?: m.date) + if (m.broadcast.isNotBlank()) "  ·  " + m.broadcast else ""),
            style = body(11, FontWeight.Bold, C.Text2), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

private val MONTHS_SHORT = listOf("ΙΑΝ", "ΦΕΒ", "ΜΑΡ", "ΑΠΡ", "ΜΑΪ", "ΙΟΥΝ", "ΙΟΥΛ", "ΑΥΓ", "ΣΕΠ", "ΟΚΤ", "ΝΟΕ", "ΔΕΚ")
fun monthShort(d: LocalDate) = MONTHS_SHORT[d.monthValue - 1]

/** Κάθετη γραμμή στη μέση (για την πορεία αγώνα). */
fun Modifier.centerLine(color: Color): Modifier = this.drawBehind {
    drawLine(color, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), strokeWidth = 2.dp.toPx())
}

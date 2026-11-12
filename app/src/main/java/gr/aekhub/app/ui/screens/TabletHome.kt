package gr.aekhub.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import gr.aekhub.app.data.MatchCard
import gr.aekhub.app.data.PostCard
import gr.aekhub.app.data.Standing
import gr.aekhub.app.ui.components.Crest
import gr.aekhub.app.ui.components.LiveDot
import gr.aekhub.app.ui.components.PlayBadge
import gr.aekhub.app.ui.components.Tag
import gr.aekhub.app.ui.components.postDate
import gr.aekhub.app.ui.components.upperGr
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body
import gr.aekhub.app.ui.theme.disp
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/*
 * Tablet (νέο σχέδιο 12/11/2026, ίδιο με το iPad, το ενέκρινε ο χρήστης):
 * μπάρα αγώνων ΑΕΚ πάνω · κύριο θέμα με τίτλο πάνω στη φωτό + 2 μεσαία · δεξιά «Ροή ειδήσεων» με ώρα
 * και μικρή βαθμολογία (η ΑΕΚ με τον από πάνω/κάτω) · από κάτω ενότητες ανά κατηγορία σε 4 στήλες.
 */

private val ATH = ZoneId.of("Europe/Athens")

/** «13:08» σήμερα, αλλιώς «12/11» */
fun clockOrDay(iso: String): String = runCatching {
    val d = OffsetDateTime.parse(iso).atZoneSameInstant(ATH)
    if (d.toLocalDate() == LocalDate.now(ATH)) "%02d:%02d".format(d.hour, d.minute) else "${d.dayOfMonth}/${d.monthValue}"
}.getOrDefault("")

@Composable
fun TabletMatchStrip(items: List<MatchCard>, openMatch: (Long) -> Unit, openAll: () -> Unit) {
    if (items.isEmpty()) return
    LazyRow(
        Modifier.fillMaxWidth().background(C.Ink2).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        item { Text("ΑΓΩΝΕΣ\nΑΕΚ", style = disp(20).copy(lineHeight = 18.sp), color = C.Gold, modifier = Modifier.width(66.dp)) }
        items(items, key = { it.id }) { m -> TabletStripCard(m) { openMatch(m.id) } }
        item { Text("Όλοι οι αγώνες →", style = body(13, FontWeight.ExtraBold, C.Gold), modifier = Modifier.clickable(onClick = openAll).padding(10.dp)) }
    }
}

@Composable
private fun TabletStripCard(m: MatchCard, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.width(210.dp).clip(shape).background(if (m.live) C.Live.copy(alpha = 0.14f) else C.Panel)
            .border(1.dp, if (m.live) C.Live.copy(alpha = 0.5f) else C.Line, shape).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                m.live -> { LiveDot(); Text(("LIVE " + m.minute).upperGr(), style = body(11, FontWeight.ExtraBold, C.Live), maxLines = 1) }
                m.finished -> Text("ΤΕΛΙΚΟ", style = body(11, FontWeight.ExtraBold, C.Muted))
                else -> Text(m.date.takeLast(5).split("-").reversed().joinToString("/") + " · " + m.time, style = body(11, FontWeight.ExtraBold, C.Gold), maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            val r = if (m.finished && m.scoreHome.isNotBlank()) when (m.aekWon) { true -> "Ν"; false -> "Η"; null -> "Ι" } else null
            if (r != null) {
                val col = when (r) { "Ν" -> C.Win; "Η" -> C.Loss; else -> C.Muted }
                Text(r, style = body(11, FontWeight.ExtraBold, col), modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(col.copy(alpha = 0.18f)).padding(horizontal = 6.dp))
            } else Text(m.sportLabel, style = body(10, FontWeight.Bold, C.Muted), maxLines = 1)
        }
        StripRow(m.homeLogo, m.home, m.scoreHome, m.aek == "home", m.live || m.scoreHome.isNotBlank())
        StripRow(m.awayLogo, m.away, m.scoreAway, m.aek == "away", m.live || m.scoreAway.isNotBlank())
    }
}

@Composable
private fun StripRow(logo: String, name: String, score: String, aek: Boolean, showScore: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Crest(logo, name, 20.dp)
        Text(name.upperGr(), style = disp(18), color = if (aek) C.Gold else C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (showScore) Text(score, style = disp(20), color = if (aek) C.Gold else C.Text)
    }
}

/** Κύριο θέμα: φωτογραφία σε όλο το κουτί, τίτλος πάνω της */
@Composable
fun TabletHero(p: PostCard, height: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(22.dp)).background(C.Panel).clickable(onClick = onClick)) {
        if (p.image.isNotBlank()) AsyncImage(p.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 0.75f to Color.Black.copy(alpha = 0.55f), 1f to Color.Black.copy(alpha = 0.92f))))
        if (p.video) PlayBadge(Modifier.align(Alignment.Center), 64.dp)
        Column(Modifier.align(Alignment.BottomStart).padding(28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (p.category.isNotBlank()) Tag(p.category)
            Text(p.title.upperGr(), style = disp(44).copy(lineHeight = 42.sp), color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(postDate(p.date, withYear = false), style = body(13, FontWeight.SemiBold, C.Text2))
        }
    }
}

/** Μεσαίο θέμα (δίπλα στο κύριο / στις ενότητες): φωτό πάνω, κατηγορία, τίτλος */
@Composable
fun TabletTile(p: PostCard, imgH: androidx.compose.ui.unit.Dp = 130.dp, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Panel).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(imgH).background(C.Panel2)) {
            if (p.image.isNotBlank()) AsyncImage(p.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (p.video) PlayBadge(Modifier.align(Alignment.Center), 40.dp)
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (p.category.isNotBlank()) Text(p.category.upperGr(), style = body(11, FontWeight.ExtraBold, C.Gold), maxLines = 1)
            Text(p.title, style = body(16, FontWeight.ExtraBold).copy(lineHeight = 21.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(postDate(p.date, withYear = false), style = body(12, color = C.Muted))
        }
    }
}

/** Δεξιά: Ροή ειδήσεων (ώρα · κατηγορία · τίτλος) */
@Composable
fun TabletNewsRail(posts: List<PostCard>, openPost: (Long) -> Unit, openFeed: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Panel).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiveDot(); Text("ΡΟΗ ΕΙΔΗΣΕΩΝ", style = disp(24), color = C.Text)
        }
        posts.forEach { p ->
            Row(Modifier.fillMaxWidth().clickable { openPost(p.id) }.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(clockOrDay(p.date), style = disp(18), color = C.Gold, modifier = Modifier.width(46.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (p.category.isNotBlank()) Text(p.category.upperGr(), style = body(10, FontWeight.ExtraBold, C.Muted), maxLines = 1)
                    Text(p.title, style = body(14, FontWeight.Bold).copy(lineHeight = 19.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
        }
        Text("Όλη η ροή →", style = body(13, FontWeight.ExtraBold, C.Gold), modifier = Modifier.padding(top = 12.dp).clickable(onClick = openFeed))
    }
}

/** Μικρή βαθμολογία: η ΑΕΚ με τον από πάνω και τον από κάτω */
@Composable
fun TabletAekTable(s: Standing, open: () -> Unit) {
    val i = s.rows.indexOfFirst { it.aek }.coerceAtLeast(0)
    val lo = maxOf(0, minOf(i - 1, s.rows.size - 3)); val hi = minOf(s.rows.size, lo + 3)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Panel).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text((s.league.ifBlank { s.title }).upperGr(), style = body(12, FontWeight.ExtraBold, C.Gold), maxLines = 1, modifier = Modifier.padding(bottom = 6.dp))
        s.rows.subList(lo, hi).forEach { r ->
            val fg = if (r.aek) C.Ink else C.Text
            Row(
                Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(9.dp)).background(if (r.aek) C.Gold else Color.Transparent).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("${r.pos}", style = body(13, FontWeight.ExtraBold, if (r.aek) C.Ink else C.Muted), modifier = Modifier.width(20.dp))
                Crest(r.logo, r.name, 20.dp)
                Text(r.name.upperGr(), style = disp(17), color = fg, maxLines = 1, modifier = Modifier.weight(1f))
                Text(r.points, style = disp(19), color = fg)
            }
        }
        Text("Όλη η βαθμολογία →", style = body(13, FontWeight.ExtraBold, C.Gold), modifier = Modifier.padding(top = 8.dp).clickable(onClick = open))
    }
}

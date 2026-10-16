package gr.aekhub.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SportsVolleyball
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import gr.aekhub.app.LocalPrefs
import gr.aekhub.app.data.Api
import gr.aekhub.app.data.MatchDetail
import gr.aekhub.app.data.MatchEvent
import gr.aekhub.app.data.PitchPlayer
import gr.aekhub.app.data.Player
import gr.aekhub.app.data.Settings
import gr.aekhub.app.ui.components.Crest
import gr.aekhub.app.ui.components.ErrorBox
import gr.aekhub.app.ui.components.LiveDot
import gr.aekhub.app.ui.components.Loading
import gr.aekhub.app.ui.components.SectionLabel
import gr.aekhub.app.ui.components.SlantBoth
import gr.aekhub.app.ui.components.centerLine
import gr.aekhub.app.ui.components.dayHeader
import gr.aekhub.app.ui.components.hex
import gr.aekhub.app.ui.components.liveMinute
import gr.aekhub.app.ui.components.upperGr
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body
import gr.aekhub.app.ui.theme.disp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun MatchScreen(id: Long, back: () -> Unit) {
    val prefs = LocalPrefs.current
    val settings by prefs.settings.collectAsState(initial = Settings())
    var m by remember { mutableStateOf<MatchDetail?>(null) }
    var error by remember { mutableStateOf(false) }
    var key by remember { mutableIntStateOf(0) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    fun open(url: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    LaunchedEffect(id, key) {
        error = false
        while (true) {
            try { m = Api.match(id) } catch (e: Exception) { if (m == null) error = true }
            delay(if (m?.live == true) 10_000 else 120_000)
        }
    }
    val d = m
    if (d == null) { if (error) ErrorBox { key++ } else Loading(); return }
    val alert = settings.matchAlerts.contains(d.id.toString())
    val tabs = buildList {
        add("Πορεία")
        if (d.stats.isNotEmpty() || d.periods.isNotEmpty()) add("Στατιστικά")
        if (d.lineupHome.isNotEmpty() || d.lineupAway.isNotEmpty()) add("Συνθέσεις")
    }
    if (tab >= tabs.size) tab = 0

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(key = "hero") {
            Hero(d, alert, back) { scope.launch { prefs.toggleMatchAlert(d.id) } }
        }
        d.stream?.let { s -> item(key = "stream") { StreamCard(s.thumb, s.label, s.kicker, s.state) { open(s.url) } } }
        if (d.highlights.isNotEmpty()) item(key = "hl") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Στιγμιότυπα")
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(d.highlights) { h ->
                        Column(Modifier.width(210.dp).clickable { open(h.url) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(C.Panel2), contentAlignment = Alignment.Center) {
                                if (h.thumb.isNotBlank()) AsyncImage(h.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                Box(Modifier.size(40.dp).clip(CircleShape).background(C.Gold), contentAlignment = Alignment.Center) { Icon(Icons.Filled.PlayArrow, "Αναπαραγωγή", tint = C.Ink) }
                            }
                            Text(h.title, style = body(13, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (tabs.size > 1) item(key = "tabs") {
            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Panel).padding(4.dp)) {
                tabs.forEachIndexed { i, t ->
                    val on = i == tab
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (on) C.Gold else Color.Transparent).clickable { tab = i }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(t, style = body(13, if (on) FontWeight.ExtraBold else FontWeight.Bold, if (on) C.Ink else C.Text2))
                    }
                }
            }
        }
        when (tabs.getOrNull(tab)) {
            "Στατιστικά" -> item(key = "stats") { StatsView(d) }
            "Συνθέσεις" -> item(key = "lu") { LineupsView(d) }
            else -> {
                if (d.events.isEmpty()) item(key = "noev") {
                    Text(if (d.status == "upcoming") "Η πορεία του αγώνα θα εμφανιστεί εδώ μόλις ξεκινήσει." else "Δεν υπάρχουν γεγονότα.",
                        Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = body(14, color = C.Muted))
                } else {
                    item(key = "tlh") { TimelineHeader(d) }
                    // Ζωντανά: το πιο πρόσφατο πάνω. Τελικό: από την αρχή προς το τέλος (όπως στο site).
                    val evs = if (d.status == "finished") d.events.reversed() else d.events
                    items(evs.size) { i -> TimelineRow(evs[i], d) }
                }
            }
        }
    }
}

/* ─────────── Κεφαλίδα αγώνα (όπως στο site) ─────────── */

@Composable
private fun Hero(d: MatchDetail, alert: Boolean, back: () -> Unit, toggleAlert: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        if (d.bg.isNotBlank()) AsyncImage(d.bg, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to C.Ink.copy(alpha = 0.82f), 0.6f to C.Ink.copy(alpha = 0.88f), 1f to C.Ink)))
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, C.Gold, Color.Transparent))))
        Column(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Round(Icons.AutoMirrored.Filled.ArrowBack, "Πίσω", C.Text, back)
                Spacer(Modifier.weight(1f))
                if (d.status == "upcoming") Round(if (alert) Icons.Filled.Notifications else Icons.Outlined.NotificationsNone, if (alert) "Ειδοποίηση ενεργή" else "Ειδοποίηση για τον αγώνα", C.Gold, toggleAlert)
            }
            val comp = listOf(d.competition, d.matchday).filter { it.isNotBlank() }.joinToString(" · ")
            if (comp.isNotBlank()) Box(Modifier.widthIn(max = 340.dp).clip(SlantBoth).background(C.Gold).padding(horizontal = 18.dp, vertical = 6.dp)) {
                Text(comp.upperGr(), style = disp(17), color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val date = runCatching { LocalDate.parse(d.date) }.getOrNull()
            Text(((date?.let { dayHeader(it) } ?: d.date) + if (d.time.isNotBlank()) " · " + d.time else ""), style = body(12, FontWeight.ExtraBold, C.Text2).copy(letterSpacing = 1.sp))
            StatusPill(d)
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TeamCol(d.homeLogo, d.home, d.aek == "home", Modifier.weight(1f))
                if (d.status == "upcoming" || d.status == "postponed") Text("VS", style = disp(36, FontWeight.Black), color = C.Line2)
                else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ScoreBox(d.scoreHome.ifBlank { "0" }, d.aek == "home")
                    Text(":", style = disp(34, FontWeight.Black), color = C.Muted)
                    ScoreBox(d.scoreAway.ifBlank { "0" }, d.aek == "away")
                }
                TeamCol(d.awayLogo, d.away, d.aek == "away", Modifier.weight(1f))
            }
            if (d.penHome.isNotBlank() && d.penAway.isNotBlank()) Text("Πέναλτι ${d.penHome}–${d.penAway}", style = body(13, FontWeight.ExtraBold, C.Gold))
            if (d.scorersHome.isNotEmpty() || d.scorersAway.isNotEmpty()) {
                Box(Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(1.dp).background(C.Line2))
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        d.scorersHome.forEach { Row(verticalAlignment = Alignment.CenterVertically) { Text(it, Modifier.weight(1f, false), style = body(12, FontWeight.Bold, C.Text2)); Spacer(Modifier.width(5.dp)); SportIcon(d.sport, 13.dp) } }
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        d.scorersAway.forEach { Row(verticalAlignment = Alignment.CenterVertically) { SportIcon(d.sport, 13.dp); Spacer(Modifier.width(5.dp)); Text(it, style = body(12, FontWeight.Bold, C.Text2)) } }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                if (d.venue.isNotBlank()) InfoChip(Icons.Outlined.Place, d.venue)
                if (d.broadcast.isNotBlank()) InfoChip(Icons.Outlined.Tv, d.broadcast)
            }
            if (d.referee.isNotBlank()) Text("Διαιτητής: " + d.referee, style = body(12, color = C.Muted))
        }
    }
}

@Composable
private fun StatusPill(d: MatchDetail) {
    val (txt, col) = when {
        d.live -> {
            val mn = liveMinute(d.clock, d.minute)
            ("LIVE" + if (mn.isNotBlank()) " · $mn" else if (d.phase.isNotBlank()) " · " + d.phase.upperGr() else "") to C.Live
        }
        d.status == "finished" -> "ΤΕΛΙΚΟ ΑΠΟΤΕΛΕΣΜΑ" to C.Text2
        d.status == "postponed" -> "ΑΝΑΒΟΛΗ" to C.Muted
        else -> "ΕΠΕΡΧΟΜΕΝΟ" to C.Gold
    }
    Row(Modifier.clip(RoundedCornerShape(999.dp)).background(C.Ink.copy(alpha = 0.6f)).border(1.dp, col.copy(alpha = 0.5f), RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (d.live) LiveDot()
        Text(txt, style = body(11, FontWeight.ExtraBold, col).copy(letterSpacing = 1.sp))
    }
}

@Composable
private fun ScoreBox(v: String, aek: Boolean) = Box(
    Modifier.size(width = 60.dp, height = 70.dp).clip(RoundedCornerShape(14.dp)).background(C.Panel).border(1.dp, if (aek) C.Gold else C.Line2, RoundedCornerShape(14.dp)),
    contentAlignment = Alignment.Center,
) { Text(v, style = disp(48, FontWeight.Black), color = if (aek) C.Gold else C.Text) }

@Composable
private fun TeamCol(logo: String, name: String, aek: Boolean, modifier: Modifier) =
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Crest(logo, name, 66.dp)
        Text(name.upperGr(), style = disp(19), color = if (aek) C.Gold else C.Text, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }

@Composable
private fun InfoChip(icon: ImageVector, text: String) = Row(
    Modifier.clip(RoundedCornerShape(999.dp)).border(1.dp, C.Line2, RoundedCornerShape(999.dp)).background(C.Ink.copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
) {
    Icon(icon, null, tint = C.Gold, modifier = Modifier.size(15.dp))
    Text(text, style = body(12, FontWeight.Bold, C.Text), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun Round(icon: ImageVector, desc: String, tint: Color, onClick: () -> Unit) =
    Box(Modifier.size(44.dp).clip(CircleShape).background(C.Ink.copy(alpha = 0.55f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(22.dp))
    }

@Composable
private fun SectionTitle(t: String) = Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    Box(Modifier.size(26.dp).clip(CircleShape).background(C.Gold), contentAlignment = Alignment.Center) { Text("›", style = disp(20, FontWeight.Black), color = C.Ink) }
    Text(t.upperGr(), style = disp(24), color = C.Text)
}

/* ─────────── Live stream ─────────── */

@Composable
private fun StreamCard(thumb: String, label: String, kicker: String, state: String, onClick: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.clip(RoundedCornerShape(6.dp)).background(if (state == "live") C.Live else C.Panel3).padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (state == "live") LiveDot(Color.White)
                Text(if (state == "replay") "REPLAY" else "LIVE", style = body(11, FontWeight.ExtraBold, Color.White))
            }
            Text(label.upperGr(), style = disp(20), color = C.Text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(C.Panel2).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            if (thumb.isNotBlank()) AsyncImage(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(C.Ink.copy(alpha = 0.35f)))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(C.Gold), contentAlignment = Alignment.Center) { Icon(Icons.Filled.PlayArrow, "Αναπαραγωγή", tint = C.Ink, modifier = Modifier.size(36.dp)) }
                Text(if (state == "replay") "Πάτα για να δεις τον αγώνα" else "Πάτα για να δεις το live", style = body(13, FontWeight.ExtraBold, Color.White))
            }
        }
        Text(kicker, style = body(12, color = C.Muted))
    }
}

/* ─────────── Πορεία αγώνα: κεντρική γραμμή, γηπεδούχοι αριστερά, φιλοξενούμενοι δεξιά ─────────── */

private val GOAL = setOf("goal", "penalty_goal", "own_goal", "seven_m_score", "pen_score", "pen_so_score", "two_pointer", "three_pointer", "free_throw", "set_won")

@Composable
private fun TimelineHeader(d: MatchDetail) = Row(
    Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).background(C.Panel).padding(12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Crest(d.homeLogo, d.home, 20.dp); Text(d.home.upperGr(), style = disp(15), color = if (d.aek == "home") C.Gold else C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(C.Gold).padding(horizontal = 10.dp, vertical = 2.dp)) {
        Text("${d.scoreHome.ifBlank { "0" }} - ${d.scoreAway.ifBlank { "0" }}", style = disp(20, FontWeight.Black), color = C.Ink)
    }
    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
        Text(d.away.upperGr(), style = disp(15), color = if (d.aek == "away") C.Gold else C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis); Crest(d.awayLogo, d.away, 20.dp)
    }
}

@Composable
private fun TimelineRow(e: MatchEvent, d: MatchDetail) {
    val minute = e.minute.let { if (it.isNotBlank() && it.last().isDigit()) "$it′" else it }
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(C.Panel).centerLine(C.Line2).padding(horizontal = 10.dp, vertical = 7.dp)) {
        if (e.team != "home" && e.team != "away") {
            // Ουδέτερο (έναρξη, ημίχρονο, λήξη, VAR…): στη μέση
            Column(Modifier.align(Alignment.Center).widthIn(min = 120.dp).clip(RoundedCornerShape(999.dp)).background(C.Ink2).border(1.dp, C.Line2, RoundedCornerShape(999.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text((e.text.ifBlank { e.label }).upperGr(), style = body(11, FontWeight.ExtraBold, C.Text2).copy(letterSpacing = 0.8.sp), textAlign = TextAlign.Center)
                if (minute.isNotBlank()) Text(minute, style = disp(16), color = C.Gold)
            }
            return@Box
        }
        val home = e.team == "home"
        val goal = e.type in GOAL
        val ours = e.team == d.aek && d.aek.isNotEmpty()
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { if (home) EventSide(e, goal, ours, true) }
            Box(
                Modifier.padding(horizontal = 6.dp).widthIn(min = 44.dp).clip(RoundedCornerShape(999.dp)).background(if (goal) C.Gold else C.Ink)
                    .border(1.5.dp, if (goal) C.Gold else C.Text2, RoundedCornerShape(999.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) { Text(minute.ifBlank { "·" }, style = disp(15, FontWeight.Black), color = if (goal) C.Ink else C.Text) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { if (!home) EventSide(e, goal, ours, false) }
        }
    }
}

@Composable
private fun EventSide(e: MatchEvent, goal: Boolean, ours: Boolean, home: Boolean) {
    val textCol: @Composable () -> Unit = { Column(horizontalAlignment = if (home) Alignment.End else Alignment.Start) {
        Text(e.label.upperGr(), style = body(9, FontWeight.ExtraBold, if (goal) C.Gold else C.Muted).copy(letterSpacing = 0.6.sp))
        if (e.text.isNotBlank()) Text(e.text, style = body(13, if (goal) FontWeight.ExtraBold else FontWeight.Bold, if (ours && goal) C.Gold else C.Text), textAlign = if (home) TextAlign.End else TextAlign.Start)
        if (e.score.isNotBlank()) Text(e.score, style = disp(13), color = C.Gold)
    } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (home) { Box(Modifier.weight(1f, false)) { textCol() }; EventIcon(e.type) }
        else { EventIcon(e.type); Box(Modifier.weight(1f, false)) { textCol() } }
    }
}

@Composable
private fun EventIcon(type: String) {
    when (type) {
        "yellow", "sanction_yellow" -> Box(Modifier.size(11.dp, 15.dp).clip(RoundedCornerShape(2.dp)).background(C.Gold))
        "red", "sanction_red", "second_yellow", "technical_foul", "flagrant_foul", "exclusion_perm" -> Box(Modifier.size(11.dp, 15.dp).clip(RoundedCornerShape(2.dp)).background(C.Live))
        "susp_2min", "exclusion_20s", "personal_foul" -> Box(Modifier.size(11.dp, 15.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFF08A24)))
        "sub" -> Icon(Icons.Filled.SwapVert, null, tint = C.Win, modifier = Modifier.size(20.dp))
        "goal", "penalty_goal", "own_goal", "seven_m_score", "pen_score", "pen_so_score" -> Box(Modifier.size(24.dp).clip(CircleShape).background(C.Gold), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.SportsSoccer, null, tint = C.Ink, modifier = Modifier.size(18.dp))
        }
        "two_pointer", "three_pointer", "free_throw" -> Box(Modifier.size(24.dp).clip(CircleShape).background(C.Gold), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.SportsBasketball, null, tint = C.Ink, modifier = Modifier.size(18.dp))
        }
        "set_won" -> Box(Modifier.size(24.dp).clip(CircleShape).background(C.Gold), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.SportsVolleyball, null, tint = C.Ink, modifier = Modifier.size(18.dp))
        }
        "var" -> Icon(Icons.Outlined.Tv, null, tint = C.Text2, modifier = Modifier.size(18.dp))
        else -> Icon(Icons.Outlined.Info, null, tint = C.Muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SportIcon(sport: String, size: Dp) = Icon(
    when (sport) { "basketball" -> Icons.Filled.SportsBasketball; "volleyball" -> Icons.Filled.SportsVolleyball; else -> Icons.Filled.SportsSoccer },
    null, tint = C.Gold, modifier = Modifier.size(size),
)

/* ─────────── Στατιστικά ─────────── */

@Composable
private fun StatsView(d: MatchDetail) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (d.periods.isNotEmpty()) {
            SectionLabel("ΣΚΟΡ ΑΝΑ ΠΕΡΙΟΔΟ")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Panel).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                d.periods.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.label, Modifier.weight(1f), style = body(13, color = C.Text2))
                        Text(p.home, Modifier.width(36.dp), style = disp(18), color = if (d.aek == "home") C.Gold else C.Text, textAlign = TextAlign.End)
                        Text(p.away, Modifier.width(36.dp), style = disp(18), color = if (d.aek == "away") C.Gold else C.Text, textAlign = TextAlign.End)
                    }
                }
            }
        }
        if (d.stats.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Panel).padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Crest(d.homeLogo, d.home, 22.dp); Spacer(Modifier.weight(1f)); Crest(d.awayLogo, d.away, 22.dp)
            }
            val hc = if (d.aek == "home") C.Gold else C.Text2
            val ac = if (d.aek == "away") C.Gold else C.Text2
            d.stats.forEach { s ->
                val h = s.home.substringBefore("/").replace("%", "").replace(",", ".").trim().toFloatOrNull() ?: 0f
                val a = s.away.substringBefore("/").replace("%", "").replace(",", ".").trim().toFloatOrNull() ?: 0f
                val tot = (h + a).takeIf { it > 0 } ?: 1f
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.home + if (s.type == "pct" && !s.home.contains("%")) "%" else "", style = disp(18), color = if (h >= a) hc else C.Text2)
                        Text(s.label, Modifier.weight(1f), style = body(12, FontWeight.Bold, C.Text2), textAlign = TextAlign.Center)
                        Text(s.away + if (s.type == "pct" && !s.away.contains("%")) "%" else "", style = disp(18), color = if (a >= h) ac else C.Text2)
                    }
                    Row(Modifier.fillMaxWidth().height(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(3.dp)).background(C.Panel3)) {
                            Box(Modifier.align(Alignment.CenterEnd).fillMaxWidth(h / tot).fillMaxSize().background(hc))
                        }
                        Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(3.dp)).background(C.Panel3)) {
                            Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(a / tot).fillMaxSize().background(ac))
                        }
                    }
                }
            }
        }
    }
}

/* ─────────── Συνθέσεις: γήπεδο + λίστες ─────────── */

@Composable
private fun LineupsView(d: MatchDetail) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        d.pitch?.let { p -> PitchView(d, p) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TeamList(d.home, d.homeLogo, d.lineupHome, d.subsHome, d.coachHome, d.aek == "home", Modifier.weight(1f))
            TeamList(d.away, d.awayLogo, d.lineupAway, d.subsAway, d.coachAway, d.aek == "away", Modifier.weight(1f))
        }
        if (d.referee.isNotBlank()) Text("Διαιτητής: " + d.referee, style = body(13, color = C.Muted))
    }
}

@Composable
private fun PitchView(d: MatchDetail, p: gr.aekhub.app.data.Pitch) {
    val homeBg = hex(d.homeColor, C.Text2); val homeFg = hex(d.homeNum, C.Ink)
    val awayBg = hex(d.awayColor, C.Gold); val awayFg = hex(d.awayNum, C.Ink)
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(0.68f).clip(RoundedCornerShape(16.dp))) {
        val w = maxWidth; val h = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val stripes = 10
            for (i in 0 until stripes) drawRect(if (i % 2 == 0) Color(0xFF2E7D32) else Color(0xFF2A732E), Offset(0f, size.height * i / stripes), Size(size.width, size.height / stripes))
            val line = Color.White.copy(alpha = 0.55f); val sw = 2.dp.toPx(); val m = 10.dp.toPx()
            drawRect(line, Offset(m, m), Size(size.width - 2 * m, size.height - 2 * m), style = Stroke(sw))
            drawLine(line, Offset(m, size.height / 2), Offset(size.width - m, size.height / 2), sw)
            drawCircle(line, size.width * 0.15f, Offset(size.width / 2, size.height / 2), style = Stroke(sw))
            val bw = size.width * 0.56f; val bh = size.height * 0.13f
            drawRect(line, Offset((size.width - bw) / 2, m), Size(bw, bh), style = Stroke(sw))
            drawRect(line, Offset((size.width - bw) / 2, size.height - m - bh), Size(bw, bh), style = Stroke(sw))
            val gw = size.width * 0.26f; val gh = size.height * 0.05f
            drawRect(line, Offset((size.width - gw) / 2, m), Size(gw, gh), style = Stroke(sw))
            drawRect(line, Offset((size.width - gw) / 2, size.height - m - gh), Size(gw, gh), style = Stroke(sw))
        }
        // Γηπεδούχοι πάνω (καθρέφτης), φιλοξενούμενοι κάτω — όπως στο site
        p.home.forEach { pl -> Dot(pl, (100f - pl.x) / 100f * w.value, pl.y / 100f * (h.value / 2f) * 0.92f + h.value * 0.03f, homeBg, homeFg) }
        p.away.forEach { pl -> Dot(pl, pl.x / 100f * w.value, h.value - (pl.y / 100f * (h.value / 2f) * 0.92f + h.value * 0.03f), awayBg, awayFg) }
        if (p.formationHome.isNotBlank()) Text(p.formationHome, Modifier.align(Alignment.TopEnd).padding(8.dp), style = body(11, FontWeight.ExtraBold, Color.White.copy(alpha = 0.85f)))
        if (p.formationAway.isNotBlank()) Text(p.formationAway, Modifier.align(Alignment.BottomEnd).padding(8.dp), style = body(11, FontWeight.ExtraBold, Color.White.copy(alpha = 0.85f)))
    }
}

@Composable
private fun Dot(pl: PitchPlayer, cx: Float, cy: Float, bg: Color, fg: Color) {
    val boxW = 74f
    Column(
        Modifier.offset(x = (cx - boxW / 2f).dp, y = (cy - 15f).dp).width(boxW.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(Modifier.size(30.dp).clip(CircleShape).background(bg).border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape), contentAlignment = Alignment.Center) {
                Text(pl.number, style = disp(15, FontWeight.Black), color = fg)
            }
            if (pl.goals > 0) Box(Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp).size(14.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.SportsSoccer, null, tint = C.Ink, modifier = Modifier.size(12.dp))
            }
            if (pl.red || pl.yellow) Box(Modifier.align(Alignment.TopStart).offset(x = (-5).dp, y = (-3).dp).size(8.dp, 11.dp).clip(RoundedCornerShape(1.dp)).background(if (pl.red) C.Live else C.Gold))
            if (pl.out.isNotBlank()) Box(Modifier.align(Alignment.BottomEnd).offset(x = 7.dp, y = 3.dp).size(14.dp).clip(CircleShape).background(C.Loss), contentAlignment = Alignment.Center) {
                Text("↓", style = body(9, FontWeight.ExtraBold, Color.White))
            }
        }
        Text(pl.name.substringAfterLast(' ').ifBlank { pl.name }, Modifier.padding(top = 2.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 4.dp),
            style = body(10, FontWeight.ExtraBold, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TeamList(name: String, logo: String, xi: List<Player>, subs: List<Player>, coach: String, aek: Boolean, modifier: Modifier) =
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(C.Panel).padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Crest(logo, name, 20.dp)
            Text(name.upperGr(), style = disp(17), color = if (aek) C.Gold else C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(if (aek) C.Gold else C.Line2))
        xi.forEach { PlayerLine(it, aek) }
        if (subs.isNotEmpty()) { Spacer(Modifier.height(4.dp)); SectionLabel("ΑΝΑΠΛΗΡΩΜΑΤΙΚΟΙ"); subs.forEach { PlayerLine(it, aek) } }
        if (coach.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text("Προπ.: $coach", style = body(12, color = C.Muted)) }
    }

@Composable
private fun PlayerLine(p: Player, aek: Boolean) = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(p.number, Modifier.width(22.dp), style = disp(16, FontWeight.Black), color = if (aek) C.Gold else C.Muted)
    Text(p.name + if (p.captain) " (Κ)" else "", style = body(13, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

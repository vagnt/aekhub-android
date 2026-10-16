package gr.aekhub.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.aekhub.app.LocalPrefs
import gr.aekhub.app.data.Api
import gr.aekhub.app.data.MatchDetail
import gr.aekhub.app.data.MatchEvent
import gr.aekhub.app.data.Player
import gr.aekhub.app.data.Settings
import gr.aekhub.app.ui.components.Crest
import gr.aekhub.app.ui.components.ErrorBox
import gr.aekhub.app.ui.components.LiveDot
import gr.aekhub.app.ui.components.Loading
import gr.aekhub.app.ui.components.SectionLabel
import gr.aekhub.app.ui.components.dayHeader
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
        add("Ροή")
        if (d.stats.isNotEmpty() || d.periods.isNotEmpty()) add("Στατιστικά")
        if (d.lineupHome.isNotEmpty() || d.lineupAway.isNotEmpty()) add("Συνθέσεις")
    }
    if (tab >= tabs.size) tab = 0

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Πίσω", tint = C.Text) }
                Text(listOf(d.sportLabel, d.competition, d.matchday).filter { it.isNotBlank() }.joinToString(" · ").upperGr(),
                    Modifier.weight(1f), style = body(12, FontWeight.Bold, C.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (d.upcoming()) IconButton(onClick = { scope.launch { prefs.toggleMatchAlert(d.id) } }) {
                    Icon(if (alert) Icons.Filled.Notifications else Icons.Outlined.NotificationsNone, if (alert) "Ειδοποίηση ενεργή" else "Ειδοποίηση για τον αγώνα", tint = C.Gold)
                }
            }
        }
        item { ScoreCard(d) }
        if (tabs.size > 1) item {
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
            "Στατιστικά" -> item { StatsView(d) }
            "Συνθέσεις" -> item { LineupsView(d) }
            else -> {
                if (d.events.isEmpty()) item {
                    Text(if (d.upcoming()) "Η ροή του αγώνα θα εμφανιστεί εδώ μόλις ξεκινήσει." else "Δεν υπάρχουν γεγονότα.",
                        Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = body(14, color = C.Muted))
                }
                items(d.events.size) { i -> EventRow(d.events[i], d, highlight = i == 0 && d.live) }
            }
        }
    }
}

private fun MatchDetail.upcoming() = status == "upcoming"

@Composable
private fun ScoreCard(d: MatchDetail) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(shape).background(C.Panel).border(1.dp, C.Line, shape).padding(vertical = 18.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            d.live -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LiveDot()
                val mn = liveMinute(d.clock, d.minute)
                Text(("LIVE" + if (mn.isNotBlank()) " · $mn" else if (d.phase.isNotBlank()) " · " + d.phase else "").upperGr(), style = body(12, FontWeight.ExtraBold, C.Live).copy(letterSpacing = 0.6.sp))
            }
            d.status == "upcoming" -> {
                val date = runCatching { LocalDate.parse(d.date) }.getOrNull()
                Text(((date?.let { dayHeader(it) } ?: d.date) + if (d.time.isNotBlank()) " · " + d.time else ""), style = body(12, FontWeight.ExtraBold, C.Gold).copy(letterSpacing = 0.6.sp))
            }
            else -> Text(d.statusLabel.upperGr(), style = body(12, FontWeight.ExtraBold, C.Muted).copy(letterSpacing = 0.6.sp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TeamCol(d.homeLogo, d.home, d.aek == "home", Modifier.weight(1f))
            if (d.status == "upcoming") Text("VS", style = disp(40, FontWeight.Black), color = C.Line2)
            else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(d.scoreHome.ifBlank { "0" }, style = disp(56, FontWeight.Black), color = if (d.aek == "home") C.Gold else C.Text)
                Text("–", style = disp(40, FontWeight.Black), color = C.Line2)
                Text(d.scoreAway.ifBlank { "0" }, style = disp(56, FontWeight.Black), color = if (d.aek == "away") C.Gold else C.Text)
            }
            TeamCol(d.awayLogo, d.away, d.aek == "away", Modifier.weight(1f))
        }
        if (d.penHome.isNotBlank() && d.penAway.isNotBlank()) Text("Πέναλτι ${d.penHome}–${d.penAway}", style = body(13, FontWeight.Bold, C.Text2))
        val sub = listOf(d.venue, d.broadcast).filter { it.isNotBlank() }.joinToString(" · ")
        if (sub.isNotBlank()) Text(sub, style = body(12, color = C.Muted), textAlign = TextAlign.Center)
    }
}

@Composable
private fun TeamCol(logo: String, name: String, aek: Boolean, modifier: Modifier) =
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Crest(logo, name, 64.dp)
        Text(name.upperGr(), style = disp(20), color = if (aek) C.Gold else C.Text, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }

private val GOAL_TYPES = setOf("goal", "penalty_goal", "own_goal", "two_pointer", "three_pointer", "free_throw", "seven_m_score", "pen_score", "pen_so_score", "set_won")
private val MARKERS = setOf("ht", "ft", "kickoff", "second_half", "q1_start", "q2_start", "q3_start", "q4_start", "ot_start", "set_start", "et1_start", "et_ht", "et2_start", "pens_start")

@Composable
private fun EventRow(e: MatchEvent, d: MatchDetail, highlight: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    if (e.type in MARKERS) {
        Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(shape).background(C.Ink2).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text((e.label + if (e.text.isNotBlank()) " · " + e.text else "").upperGr(), style = body(12, FontWeight.ExtraBold, C.Muted).copy(letterSpacing = 0.6.sp))
        }
        return
    }
    val goal = e.type in GOAL_TYPES
    val ours = (e.team == d.aek) && d.aek.isNotEmpty()
    val teamName = when (e.team) { "home" -> d.home; "away" -> d.away; else -> "" }
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(shape)
            .then(if (goal && ours || highlight) Modifier.background(C.Panel) else Modifier)
            .border(1.dp, C.Line, shape).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
    ) {
        Text(if (e.minute.isNotBlank()) e.minute + "′" else "", Modifier.width(40.dp), style = disp(20), color = if (goal) C.Gold else C.Muted)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EventMark(e.type)
                Text((e.label + if (teamName.isNotBlank()) " · " + teamName else "").let { if (goal) it.upperGr() else it },
                    style = body(15, if (goal) FontWeight.ExtraBold else FontWeight.Bold, if (ours && goal) C.Gold else C.Text))
            }
            if (e.text.isNotBlank()) Text(e.text, style = body(13, color = C.Text2))
        }
        if (e.score.isNotBlank()) Box(Modifier.clip(RoundedCornerShape(6.dp)).background(C.Gold).padding(horizontal = 8.dp, vertical = 2.dp)) {
            Text(e.score, style = disp(18), color = C.Ink)
        }
    }
}

@Composable
private fun EventMark(type: String) {
    val (color, w, h) = when (type) {
        "yellow", "sanction_yellow" -> Triple(C.Gold, 11, 15)
        "red", "sanction_red", "technical_foul", "flagrant_foul", "exclusion_perm" -> Triple(C.Live, 11, 15)
        "susp_2min", "exclusion_20s", "personal_foul" -> Triple(Color(0xFFF08A24), 11, 15)
        "sub" -> Triple(C.Win, 10, 10)
        else -> if (type in GOAL_TYPES) Triple(C.Gold, 10, 10) else Triple(C.Muted, 8, 8)
    }
    Box(Modifier.size(w.dp, h.dp).clip(RoundedCornerShape(if (w == h) 50 else 2)).background(color))
}

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
        if (d.stats.isNotEmpty()) {
            Row { Text(d.home.upperGr(), Modifier.weight(1f), style = body(12, FontWeight.ExtraBold, if (d.aek == "home") C.Gold else C.Text)); Text(d.away.upperGr(), style = body(12, FontWeight.ExtraBold, if (d.aek == "away") C.Gold else C.Text)) }
            d.stats.forEach { s ->
                val h = s.home.substringBefore("/").replace("%", "").replace(",", ".").trim().toFloatOrNull() ?: 0f
                val a = s.away.substringBefore("/").replace("%", "").replace(",", ".").trim().toFloatOrNull() ?: 0f
                val tot = (h + a).takeIf { it > 0 } ?: 1f
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.home + if (s.type == "pct" && !s.home.contains("%")) "%" else "", style = body(14, FontWeight.ExtraBold))
                        Text(s.label, Modifier.weight(1f), style = body(13, color = C.Text2), textAlign = TextAlign.Center)
                        Text(s.away + if (s.type == "pct" && !s.away.contains("%")) "%" else "", style = body(14, FontWeight.ExtraBold))
                    }
                    Row(Modifier.fillMaxWidth().height(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(3.dp)).background(C.Panel3)) {
                            Box(Modifier.align(Alignment.CenterEnd).fillMaxWidth(h / tot).fillMaxSize().background(if (d.aek == "home") C.Gold else C.Text2))
                        }
                        Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(3.dp)).background(C.Panel3)) {
                            Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(a / tot).fillMaxSize().background(if (d.aek == "away") C.Gold else C.Text2))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LineupsView(d: MatchDetail) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TeamList(d.home, d.lineupHome, d.subsHome, d.coachHome, d.aek == "home", Modifier.weight(1f))
            TeamList(d.away, d.lineupAway, d.subsAway, d.coachAway, d.aek == "away", Modifier.weight(1f))
        }
        if (d.referee.isNotBlank()) Text("Διαιτητής: " + d.referee, style = body(13, color = C.Muted))
    }
}

@Composable
private fun TeamList(name: String, xi: List<Player>, subs: List<Player>, coach: String, aek: Boolean, modifier: Modifier) =
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(C.Panel).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(name.upperGr(), style = disp(18), color = if (aek) C.Gold else C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        xi.forEach { PlayerLine(it, aek) }
        if (subs.isNotEmpty()) { Spacer(Modifier.height(4.dp)); SectionLabel("ΑΝΑΠΛΗΡΩΜΑΤΙΚΟΙ"); subs.forEach { PlayerLine(it, aek) } }
        if (coach.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text("Προπ.: $coach", style = body(12, color = C.Muted)) }
    }

@Composable
private fun PlayerLine(p: Player, aek: Boolean) = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(p.number, Modifier.width(22.dp), style = disp(16), color = if (aek) C.Gold else C.Muted)
    Text(p.name + if (p.captain) " (Κ)" else "", style = body(13, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

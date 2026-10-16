package gr.aekhub.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import gr.aekhub.app.data.Api
import gr.aekhub.app.data.Standing
import gr.aekhub.app.data.StandingRow
import gr.aekhub.app.ui.components.ChipRow
import gr.aekhub.app.ui.components.Crest
import gr.aekhub.app.ui.components.ErrorBox
import gr.aekhub.app.ui.components.Loading
import gr.aekhub.app.ui.components.ScreenTitle
import gr.aekhub.app.ui.components.hex
import gr.aekhub.app.ui.components.upperGr
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body
import gr.aekhub.app.ui.theme.disp

/** Βαθμολογίες: άθλημα → διοργάνωση (chips, όπως στο site) → ένας πίνακας τη φορά. */
@Composable
fun StandingsScreen() {
    var list by remember { mutableStateOf<List<Standing>?>(null) }
    var error by remember { mutableStateOf(false) }
    var key by remember { mutableIntStateOf(0) }
    var sport by rememberSaveable { mutableStateOf("") }
    var sel by rememberSaveable { mutableStateOf(0L) }
    LaunchedEffect(key) { error = false; try { list = Api.standings().items } catch (e: Exception) { error = true } }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Βαθμολογίες", Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp))
        val l = list
        if (l == null) { if (error) ErrorBox { key++ } else Loading(); return@Column }
        if (l.isEmpty()) { Text("Δεν υπάρχουν βαθμολογίες ακόμα.", Modifier.padding(24.dp), style = body(15, color = C.Muted)); return@Column }
        val sports = l.map { it.sport.ifBlank { "Άλλα" } }.distinct()
        if (sport !in sports) sport = sports.first()
        val ofSport = l.filter { it.sport.ifBlank { "Άλλα" } == sport }
        if (ofSport.none { it.id == sel }) sel = (ofSport.firstOrNull { t -> t.rows.any { it.aek } } ?: ofSport.first()).id
        if (sports.size > 1) ChipRow(sports.map { it to it }, sport, { sport = it }, Modifier.padding(bottom = 8.dp))
        if (ofSport.size > 1) LeagueTabs(ofSport, sel) { sel = it }
        val t = ofSport.first { it.id == sel }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp)) {
            item(key = "t" + t.id) { Table(t) }
        }
    }
}

@Composable
private fun LeagueTabs(items: List<Standing>, sel: Long, pick: (Long) -> Unit) {
    // Δεύτερη σειρά: ίδιο στυλ με τα tabs του site (κίτρινο επιλεγμένο, διάφανα τα άλλα)
    ChipRow(items.map { it.id.toString() to (it.league.ifBlank { it.title }) }, sel.toString(), { pick(it.toLong()) }, Modifier.padding(bottom = 6.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Table(s: Standing) {
    val wdl = s.style != "wl"
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Panel)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (s.leagueLogo.isNotBlank()) AsyncImage(s.leagueLogo, null, Modifier.size(34.dp), contentScale = ContentScale.Fit)
            Text(s.title.upperGr(), style = disp(22), color = C.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Header(wdl)
        s.rows.forEach { r ->
            val z = s.zones.firstOrNull { r.pos in it.from..it.to }
            Row(
                Modifier.fillMaxWidth().height(46.dp).then(if (r.aek) Modifier.background(C.Gold.copy(alpha = 0.13f)) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(4.dp).fillMaxHeight().background(z?.let { hex(it.color, C.Gold) } ?: C.Panel))
                Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(if (r.aek) C.Gold else C.Panel3),
                        contentAlignment = Alignment.Center,
                    ) { Text(r.pos.toString(), style = body(12, FontWeight.ExtraBold, if (r.aek) C.Ink else C.Text2)) }
                }
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Crest(r.logo, r.name, 24.dp)
                    Box(Modifier.width(8.dp))
                    Text(r.name.upperGr(), style = disp(16), color = if (r.aek) C.Gold else C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Num(r.played)
                Num(r.won); if (wdl) Num(r.drawn); Num(r.lost)
                Num(diff(r), color = diffColor(r), w = 34.dp)
                Text(r.points, Modifier.width(40.dp).padding(end = 10.dp), style = disp(18, FontWeight.Black), color = if (r.aek) C.Gold else C.Text, textAlign = TextAlign.End)
            }
        }
        if (s.zones.isNotEmpty()) FlowRow(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            s.zones.forEach { z ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(width = 12.dp, height = 3.dp).background(hex(z.color, C.Gold)))
                    Text(z.label, style = body(11, FontWeight.Bold, C.Text2))
                }
            }
        }
    }
}

@Composable
private fun Header(wdl: Boolean) = Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.width(4.dp))
    Text("#", Modifier.width(34.dp), style = body(10, FontWeight.ExtraBold, C.Muted), textAlign = TextAlign.Center)
    Text("ΟΜΑΔΑ", Modifier.weight(1f), style = body(10, FontWeight.ExtraBold, C.Muted))
    (listOf("ΑΓ", "Ν") + (if (wdl) listOf("Ι") else emptyList()) + "Η").forEach { Text(it, Modifier.width(26.dp), style = body(10, FontWeight.ExtraBold, C.Muted), textAlign = TextAlign.End) }
    Text("+/-", Modifier.width(34.dp), style = body(10, FontWeight.ExtraBold, C.Muted), textAlign = TextAlign.End)
    Text("Β", Modifier.width(40.dp).padding(end = 10.dp), style = body(10, FontWeight.ExtraBold, C.Gold), textAlign = TextAlign.End)
}

@Composable
private fun Num(v: String, color: androidx.compose.ui.graphics.Color = C.Text2, w: Dp = 26.dp) =
    Text(v, Modifier.width(w), style = body(13, FontWeight.Bold, color), textAlign = TextAlign.End)

private fun diff(r: StandingRow): String {
    val f = r.gf.toIntOrNull() ?: return ""; val a = r.ga.toIntOrNull() ?: return ""
    val d = f - a
    return if (d > 0) "+$d" else d.toString()
}
private fun diffColor(r: StandingRow) = when {
    diff(r).startsWith("+") -> C.Win
    diff(r).startsWith("-") -> C.Loss
    else -> C.Text2
}

package gr.aekhub.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gr.aekhub.app.data.Api
import gr.aekhub.app.data.Standing
import gr.aekhub.app.ui.components.ChipRow
import gr.aekhub.app.ui.components.Crest
import gr.aekhub.app.ui.components.ErrorBox
import gr.aekhub.app.ui.components.Loading
import gr.aekhub.app.ui.components.ScreenTitle
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body
import gr.aekhub.app.ui.theme.disp

@Composable
fun StandingsScreen() {
    var list by remember { mutableStateOf<List<Standing>?>(null) }
    var error by remember { mutableStateOf(false) }
    var key by remember { mutableIntStateOf(0) }
    var sport by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(key) { error = false; try { list = Api.standings().items } catch (e: Exception) { error = true } }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Βαθμολογίες", Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp))
        val l = list
        if (l == null) { if (error) ErrorBox { key++ } else Loading(); return@Column }
        val sports = l.map { it.sport }.filter { it.isNotBlank() }.distinct()
        if (sport.isEmpty() && sports.isNotEmpty()) sport = sports.first()
        ChipRow(sports.map { it to it }, sport, { sport = it }, Modifier.padding(bottom = 10.dp))
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            l.filter { it.sport == sport || sports.isEmpty() }.forEach { s -> item(key = s.id) { Table(s) } }
        }
    }
}

@Composable
private fun Table(s: Standing) {
    val wdl = s.style != "wl"
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Panel).padding(vertical = 12.dp)) {
        Text(s.title.uppercase(), Modifier.padding(horizontal = 14.dp, vertical = 4.dp), style = disp(20), color = C.Text)
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
            Text("#", Modifier.width(24.dp), style = body(11, FontWeight.ExtraBold, C.Muted))
            Text("ΟΜΑΔΑ", Modifier.weight(1f), style = body(11, FontWeight.ExtraBold, C.Muted))
            listOf("ΑΓ", "Ν") .plus(if (wdl) listOf("Ι") else emptyList()).plus(listOf("Η", "ΒΑΘ")).forEach {
                Text(it, Modifier.width(if (it == "ΒΑΘ") 38.dp else 28.dp), style = body(11, FontWeight.ExtraBold, C.Muted), textAlign = TextAlign.End)
            }
        }
        s.rows.forEach { r ->
            Row(
                Modifier.fillMaxWidth().then(if (r.aek) Modifier.background(C.Gold.copy(alpha = 0.12f)) else Modifier).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(r.pos.toString(), Modifier.width(24.dp), style = body(13, FontWeight.ExtraBold, if (r.aek) C.Gold else C.Text2))
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Crest(r.logo, r.name, 22.dp)
                    Box(Modifier.width(8.dp))
                    Text(r.name, style = body(14, if (r.aek) FontWeight.ExtraBold else FontWeight.Bold, if (r.aek) C.Gold else C.Text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                listOf(r.played, r.won).plus(if (wdl) listOf(r.drawn) else emptyList()).plus(listOf(r.lost)).forEach {
                    Text(it, Modifier.width(28.dp), style = body(13, color = C.Text2), textAlign = TextAlign.End)
                }
                Text(r.points, Modifier.width(38.dp), style = disp(18, FontWeight.Black), color = if (r.aek) C.Gold else C.Text, textAlign = TextAlign.End)
            }
        }
    }
}

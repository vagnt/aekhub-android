package gr.aekhub.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import gr.aekhub.app.data.BoxRow
import gr.aekhub.app.data.MatchDetail
import gr.aekhub.app.data.MatchExtra
import gr.aekhub.app.ui.components.Crest
import gr.aekhub.app.ui.components.hex
import gr.aekhub.app.ui.components.upperGr
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body
import gr.aekhub.app.ui.theme.disp
import kotlinx.coroutines.launch
import java.util.Locale

/*
 * 📊 Ανάλυση αγώνα — ίδια εμφάνιση με το site (Match Center 1.100): τίτλος ενότητας + πάνελ με κεφαλίδα ομάδας.
 * Βαθμολογία παικτών ανά ομάδα, μπάσκετ box score.
 * Τα ονόματα παικτών έρχονται ήδη από τον server όπως είναι στη σύνθεση του αγώνα.
 */

/** Υπάρχει κάτι να δείξει η καρτέλα «Ανάλυση»; */
fun MatchExtra.hasAnalysis(): Boolean = !resultOnly && (!ratings?.home.isNullOrEmpty() || !ratings?.away.isNullOrEmpty() || box != null)

private class Cols(val h: Color, val a: Color)
private val W = Color(0xFF22C38E); private val D = Color(0xFF9A968A); private val L = Color(0xFFF0525A)
private fun src(k: String) = when (k) { "fm" -> "FotMob"; "fs" -> "Flashscore"; "ss" -> "Sofascore"; "as" -> "API-Sports"; "db" -> "AEK Hub"; else -> "" }
private fun f1(v: Float) = String.format(Locale.US, "%.1f", v)

@Composable
fun AnalysisView(d: MatchDetail, x: MatchExtra) {
    val cols = Cols(hex(x.colorHome, C.Gold), hex(x.colorAway, C.Text2))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        x.ratings?.let { r -> TopCard(d, true, r.home, x.potm, cols); TopCard(d, false, r.away, x.potm, cols) }
        x.box?.let { BoxCard(d, it, cols) }
    }
}

/* ─────────── κοινά: ίδια εμφάνιση με Συνθέσεις/Πορεία — τίτλος ενότητας (κίτρινος κύκλος) + πάνελ με σκούρα κεφαλίδα ομάδας ─────────── */

private val PanelShape = RoundedCornerShape(16.dp)

/** Τίτλος ενότητας όπως στο site/στο app: κίτρινος κύκλος › + condensed τίτλος */
@Composable
private fun AxTitle(t: String) = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    Box(Modifier.size(26.dp).clip(CircleShape).background(C.Gold), contentAlignment = Alignment.Center) { Text("›", style = disp(20, FontWeight.Black), color = C.Ink) }
    Text(t.upperGr(), style = disp(24), color = C.Text)
    DashLine(Modifier.weight(1f))
}

/** Διακεκομμένη γραμμή (το χαρακτηριστικό του brand) */
@Composable
private fun DashLine(modifier: Modifier = Modifier) = Canvas(modifier.height(2.dp)) {
    drawLine(C.Line2, Offset(0f, 1f), Offset(size.width, 1f), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 9f)))
}

/** Τίτλος + πάνελ: $head = κεφαλίδα (ομάδες), $tag = πηγή δεδομένων */
@Composable
private fun Section(title: String, tag: String = "", head: @Composable () -> Unit, content: @Composable () -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    if (title.isNotBlank()) AxTitle(title)
    Column(Modifier.fillMaxWidth().clip(PanelShape).background(C.Panel).border(1.dp, C.Line, PanelShape)) {
        Row(Modifier.fillMaxWidth().background(C.Ink2).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { head() }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
            if (tag.isNotBlank()) Text("Δεδομένα: $tag", Modifier.fillMaxWidth(), style = body(10, color = C.Muted), textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun Note(t: String) = Text(t, Modifier.fillMaxWidth(), style = body(12, color = C.Muted), textAlign = TextAlign.Center)

@Composable
private fun Divider() = DashLine(Modifier.fillMaxWidth())

/** Η ομάδα όπως στις Συνθέσεις: χρώμα · λογότυπο · όνομα (condensed κεφαλαία) */
@Composable
private fun TeamHead(logo: String, name: String, c: Color, end: Boolean = false, modifier: Modifier = Modifier) = Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    if (!end) Box(Modifier.size(10.dp).clip(CircleShape).background(c))
    Crest(logo, name, 26.dp)
    Text(name.upperGr(), style = disp(18), color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
    if (end) Box(Modifier.size(10.dp).clip(CircleShape).background(c))
}

/** Κεφαλίδα με τις δύο ομάδες (πίεση, σουτ) */
@Composable
private fun ChartBox(content: @Composable () -> Unit) = Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.Ink2).border(1.dp, C.Line, RoundedCornerShape(10.dp)).padding(10.dp),
) { content() }

fun ratingColor(r: Float): Color = when {
    r >= 8f -> Color(0xFF3D9BFF); r >= 7f -> Color(0xFF22C38E); r >= 6f -> Color(0xFFF2C230); else -> Color(0xFFF0703A)
}

@Composable
fun RatingBadge(r: Float, big: Boolean = false, best: Boolean = false) = Box(
    Modifier.clip(RoundedCornerShape(99.dp)).background(if (best) C.Gold else Color(0xFF0B0B0A)).border(1.5.dp, if (best) C.Gold else ratingColor(r), RoundedCornerShape(99.dp))
        .padding(horizontal = if (big) 12.dp else 8.dp, vertical = if (big) 5.dp else 2.dp),
    contentAlignment = Alignment.Center,
) { Text(f1(r), style = body(if (big) 18 else 12, FontWeight.ExtraBold, if (best) C.Ink else ratingColor(r))) }

/* ─────────── Βαθμολογία παικτών: ξεχωριστό πάνελ για κάθε ομάδα (5 πρώτοι, οι υπόλοιποι με πάτημα) ─────────── */

@Composable
private fun TopCard(d: MatchDetail, home: Boolean, list: List<gr.aekhub.app.data.RatingRow>, potm: gr.aekhub.app.data.Potm?, cols: Cols) {
    val l = list.filter { it.name.isNotBlank() }
    if (l.isEmpty()) return
    val side = if (home) "home" else "away"
    var all by remember { mutableStateOf(false) }
    Section(if (home) "Βαθμολογία παικτών" else "", head = { TeamHead(if (home) d.homeLogo else d.awayLogo, if (home) d.home else d.away, if (home) cols.h else cols.a) }) {
        Column {
            val shown = if (all) l else l.take(5)
            shown.forEachIndexed { i, r ->
                val best = potm != null && potm.side == side && potm.number == r.number
                Row(Modifier.fillMaxWidth().then(if (best) Modifier.background(C.Gold.copy(alpha = 0.10f)) else Modifier).padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${i + 1}", Modifier.width(24.dp), style = disp(19, FontWeight.Black), color = C.Gold, textAlign = TextAlign.Center)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(r.name, Modifier.weight(1f, false), style = body(14, if (best) FontWeight.ExtraBold else FontWeight.SemiBold, if (best) C.Text else C.Text2), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (r.number.isNotBlank()) Text("#${r.number}", style = body(11, FontWeight.Bold, C.Muted))
                        }
                        if (best) Text("ΠΑΙΚΤΗΣ ΑΓΩΝΑ", style = body(9, FontWeight.Black, C.Gold))
                    }
                    RatingBadge(r.rating, best = best)
                }
                if (i < shown.lastIndex) DashLine(Modifier.fillMaxWidth())
            }
            if (l.size > 5) Text(if (all) "Λιγότεροι" else "Όλοι οι παίκτες (${l.size})", Modifier.fillMaxWidth().clickable { all = !all }.padding(top = 10.dp, bottom = 2.dp), style = body(12, FontWeight.ExtraBold, C.Muted), textAlign = TextAlign.Center)
        }
    }
}

/* ─────────── 🏀 Διακριθέντες & box score (ξεχωριστό πάνελ ανά ομάδα) ─────────── */

@Composable
private fun BoxCard(d: MatchDetail, b: gr.aekhub.app.data.BoxScore, cols: Cols) {
    listOf(Triple(true, d.home to d.homeLogo, b.home), Triple(false, d.away to d.awayLogo, b.away)).filter { it.third.isNotEmpty() }.forEachIndexed { idx, (home, tl, list) ->
        Section(if (idx == 0) "Διακριθέντες" else "", src(b.src), head = { TeamHead(tl.second, tl.first, if (home) cols.h else cols.a) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                list.sortedByDescending { it.pts }.take(3).forEachIndexed { i, r ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (i == 0) C.Gold.copy(alpha = 0.12f) else C.Ink2)
                        .border(1.dp, if (i == 0) C.Gold.copy(alpha = 0.35f) else C.Line, RoundedCornerShape(10.dp)).padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${r.pts}", style = disp(36, FontWeight.Black), color = if (i == 0) C.Gold else C.Text)
                        Text(r.name, style = body(12, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${r.reb} ριμπ. · ${r.ast} ασίστ", style = body(10, color = C.Muted), textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, C.Line, RoundedCornerShape(10.dp))) {
                Row(Modifier.fillMaxWidth().background(C.Ink2).padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text("ΠΑΙΚΤΗΣ", Modifier.weight(1f), style = body(10, FontWeight.ExtraBold, C.Muted)); listOf("ΛΕΠ", "ΠΟΝ", "ΡΙΜ", "ΑΣ").forEach { Text(it, Modifier.width(36.dp), style = body(10, FontWeight.ExtraBold, C.Muted), textAlign = TextAlign.End) }
                }
                val best = listOf(list.maxOf { it.pts }, list.maxOf { it.reb }, list.maxOf { it.ast })
                list.forEach { r: BoxRow ->
                    DashLine(Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(r.number, Modifier.width(26.dp), style = disp(16, FontWeight.Black), color = C.Gold)
                        Text(r.name, Modifier.weight(1f), style = body(13, if (r.starter) FontWeight.ExtraBold else FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${r.min}′", Modifier.width(36.dp), style = body(13, color = C.Muted), textAlign = TextAlign.End)
                        listOf(r.pts, r.reb, r.ast).forEachIndexed { k, v ->
                            val lead = v == best[k] && v > 0
                            Text("$v", Modifier.width(36.dp), style = body(13, if (lead) FontWeight.ExtraBold else FontWeight.Medium, if (lead) C.Gold else C.Text), textAlign = TextAlign.End)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().background(C.Ink2).padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text("Σύνολο", Modifier.weight(1f), style = body(13, FontWeight.ExtraBold, C.Text2)); Spacer(Modifier.width(36.dp))
                    listOf(list.sumOf { it.pts }, list.sumOf { it.reb }, list.sumOf { it.ast }).forEach { Text("$it", Modifier.width(36.dp), style = body(13, FontWeight.ExtraBold, C.Text2), textAlign = TextAlign.End) }
                }
            }
            Note("Με έντονο: η πρώτη πεντάδα · κίτρινο: ο καλύτερος της ομάδας")
        }
    }
}

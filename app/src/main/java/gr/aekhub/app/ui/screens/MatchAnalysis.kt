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
 * Διακριθέντες ανά ομάδα (βαθμοί), πίεση, χάρτης σουτ, μπάσκετ box score.
 * Τα ονόματα παικτών έρχονται ήδη από τον server όπως είναι στη σύνθεση του αγώνα.
 */

/** Υπάρχει κάτι να δείξει η καρτέλα «Ανάλυση»; */
fun MatchExtra.hasAnalysis(): Boolean = !resultOnly && (!ratings?.home.isNullOrEmpty() || !ratings?.away.isNullOrEmpty() || box != null || momentum != null || !shots.isNullOrEmpty())

private class Cols(val h: Color, val a: Color)
private val W = Color(0xFF22C38E); private val D = Color(0xFF9A968A); private val L = Color(0xFFF0525A)
private fun src(k: String) = when (k) { "fm" -> "FotMob"; "fs" -> "Flashscore"; "ss" -> "Sofascore"; "as" -> "API-Sports"; "db" -> "AEK Hub"; else -> "" }
private fun f1(v: Float) = String.format(Locale.US, "%.1f", v)
private fun f2(v: Double) = String.format(Locale.US, "%.2f", v)

@Composable
fun AnalysisView(d: MatchDetail, x: MatchExtra) {
    val cols = Cols(hex(x.colorHome, C.Gold), hex(x.colorAway, C.Text2))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        x.ratings?.let { r -> TopCard(d, true, r.home, x.potm, cols); TopCard(d, false, r.away, x.potm, cols) }
        x.momentum?.takeIf { it.points.isNotEmpty() }?.let { MomentumCard(d, it, cols) }
        x.shots?.takeIf { it.isNotEmpty() }?.let { ShotsCard(d, it, cols) }
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
private fun VsHead(d: MatchDetail, cols: Cols) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    TeamHead(d.homeLogo, d.home, cols.h, false, Modifier.weight(1f))
    TeamHead(d.awayLogo, d.away, cols.a, true, Modifier.weight(1f).padding(start = 0.dp))
}

@Composable
private fun ChartBox(content: @Composable () -> Unit) = Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.Ink2).border(1.dp, C.Line, RoundedCornerShape(10.dp)).padding(10.dp),
) { content() }

@Composable
private fun Ticks(vararg t: String) = Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
    t.forEachIndexed { i, s -> if (i > 0) Spacer(Modifier.weight(1f)); Text(s, style = body(11, color = C.Muted)) }
}

fun ratingColor(r: Float): Color = when {
    r >= 8f -> Color(0xFF3D9BFF); r >= 7f -> Color(0xFF22C38E); r >= 6f -> Color(0xFFF2C230); else -> Color(0xFFF0703A)
}

@Composable
fun RatingBadge(r: Float, big: Boolean = false) = Box(
    Modifier.clip(RoundedCornerShape(if (big) 10.dp else 6.dp)).background(ratingColor(r)).padding(horizontal = if (big) 10.dp else 6.dp, vertical = if (big) 5.dp else 1.dp),
    contentAlignment = Alignment.Center,
) { Text(f1(r), style = body(if (big) 18 else 11, FontWeight.ExtraBold, if (r >= 8f) Color.White else C.Ink)) }

/* ─────────── ⭐ Διακριθέντες: ξεχωριστό πάνελ για κάθε ομάδα ─────────── */

@Composable
private fun TopCard(d: MatchDetail, home: Boolean, list: List<gr.aekhub.app.data.RatingRow>, potm: gr.aekhub.app.data.Potm?, cols: Cols) {
    val l = list.filter { it.name.isNotBlank() }.take(5)
    if (l.isEmpty()) return
    val side = if (home) "home" else "away"
    Section(if (home) "Διακριθέντες" else "", head = { TeamHead(if (home) d.homeLogo else d.awayLogo, if (home) d.home else d.away, if (home) cols.h else cols.a) }) {
        Column {
            l.forEachIndexed { i, r ->
                val best = potm != null && potm.side == side && potm.number == r.number
                Row(Modifier.fillMaxWidth().then(if (best) Modifier.background(C.Gold.copy(alpha = 0.10f)) else Modifier).padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (best) "⭐" else "${i + 1}", Modifier.width(24.dp), style = disp(19, FontWeight.Black), color = C.Gold, textAlign = TextAlign.Center)
                    Text(r.name, Modifier.weight(1f, false), style = body(14, if (best) FontWeight.ExtraBold else FontWeight.SemiBold, if (best) C.Text else C.Text2), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (r.number.isNotBlank()) Text("#${r.number}", style = body(11, FontWeight.Bold, C.Muted))
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.width(54.dp).height(6.dp).clip(RoundedCornerShape(99.dp)).background(C.Ink2)) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth((r.rating / 10f).coerceIn(0.05f, 1f)).clip(RoundedCornerShape(99.dp)).background(ratingColor(r.rating)))
                    }
                    RatingBadge(r.rating)
                }
                if (i < l.lastIndex) DashLine(Modifier.fillMaxWidth())
            }
        }
        if (potm != null && potm.side == side) Note("⭐ Παίκτης του αγώνα")
    }
}

/* ─────────── 🔥 Πίεση αγώνα ─────────── */

@Composable
private fun MomentumCard(d: MatchDetail, m: gr.aekhub.app.data.Momentum, cols: Cols) = Section("Πίεση αγώνα", head = { VsHead(d, cols) }) {
    val total = (m.periodLength * m.periods).coerceAtLeast(1).toFloat()
    val maxMin = maxOf(total, m.points.maxOf { it.minute })
    ChartBox {
        Canvas(Modifier.fillMaxWidth().height(112.dp)) {
            val mid = size.height / 2f
            val bw = (size.width / maxMin * 0.82f).coerceAtLeast(1.5f)
            for (k in 1 until m.periods) {
                val xx = size.width * (m.periodLength * k) / maxMin
                drawLine(C.Muted.copy(alpha = 0.6f), Offset(xx, 0f), Offset(xx, size.height), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
            m.points.forEach { p ->
                val xx = size.width * (p.minute - 1).coerceAtLeast(0f) / maxMin
                val hgt = (p.value.coerceIn(-100, 100) / 100f) * (mid - 4f)
                if (hgt > 0) drawRoundRect(cols.h, Offset(xx, mid - hgt), Size(bw, hgt), CornerRadius(2f))
                else if (hgt < 0) drawRoundRect(cols.a, Offset(xx, mid), Size(bw, -hgt), CornerRadius(2f))
            }
            drawLine(C.Line2, Offset(0f, mid), Offset(size.width, mid), 1f)
        }
        Ticks("0′", "${m.periodLength}′", "${total.toInt()}′")
    }
    Note("Πάνω: πιέζει η ${d.home} · κάτω: η ${d.away}")
}

/* ─────────── 🎯 Χάρτης σουτ (γηπεδούχος επιτίθεται δεξιά) ─────────── */

@Composable
private fun ShotsCard(d: MatchDetail, shots: List<gr.aekhub.app.data.Shot>, cols: Cols) = Section("Χάρτης σουτ", head = { VsHead(d, cols) }) {
    Row { Text("← επιτίθεται η ${d.away}", style = body(10, FontWeight.Bold, C.Muted)); Spacer(Modifier.weight(1f)); Text("επιτίθεται η ${d.home} →", style = body(10, FontWeight.Bold, C.Muted)) }
    var sel by remember(shots) { mutableStateOf<gr.aekhub.app.data.Shot?>(null) }
    val line = Color.White.copy(alpha = 0.5f)
    Box(Modifier.fillMaxWidth().aspectRatio(105f / 68f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1D3A24))) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width; val h = size.height; val st = Stroke(1.5f)
            for (i in 1 until 10 step 2) drawRect(Color(0xFF214229), Offset(w * i / 10f, 0f), Size(w / 10f, h)) // ρίγες χλοοτάπητα
            drawRect(line, Offset(3f, 3f), Size(w - 6f, h - 6f), style = st)
            drawLine(line, Offset(w / 2, 3f), Offset(w / 2, h - 3f), 1.5f)
            drawCircle(line, h * 0.135f, Offset(w / 2, h / 2), style = st)
            val pbW = w * 16.5f / 105f; val pbH = h * 40.3f / 68f; val gaW = w * 5.5f / 105f; val gaH = h * 18.3f / 68f
            drawRect(line, Offset(3f, (h - pbH) / 2), Size(pbW, pbH), style = st); drawRect(line, Offset(w - 3f - pbW, (h - pbH) / 2), Size(pbW, pbH), style = st)
            drawRect(line, Offset(3f, (h - gaH) / 2), Size(gaW, gaH), style = st); drawRect(line, Offset(w - 3f - gaW, (h - gaH) / 2), Size(gaW, gaH), style = st)
            shots.forEach { s ->
                val c = if (s.side == "home") cols.h else cols.a
                val r = (4f + s.xg.coerceIn(0f, 1f) * 14f) * density / 2.2f
                val o = Offset(w * s.x / 100f, h * s.y / 100f)
                when (s.result) {
                    "goal" -> { drawCircle(Color.White, r * 1.3f, o); drawCircle(C.Gold, r * 1.3f, o, style = Stroke(3f)) }
                    "saved", "post" -> drawCircle(c, r, o)
                    "blocked" -> drawCircle(c.copy(alpha = 0.4f), r, o)
                    else -> drawCircle(c, r, o, style = Stroke(2.5f))
                }
                if (s == sel) drawCircle(Color.White, r * 2f, o, style = Stroke(2f))
            }
        }
        Box(Modifier.matchParentSize().pointerInput(shots) {
            detectTapGestures { o ->
                val fx = o.x / size.width * 100f; val fy = o.y / size.height * 100f
                sel = shots.minByOrNull { (it.x - fx) * (it.x - fx) + (it.y - fy) * (it.y - fy) }
            }
        })
    }
    val info = sel?.let { s ->
        val res = mapOf("goal" to "⚽ Γκολ", "saved" to "Στην εστία", "miss" to "Άουτ", "blocked" to "Μπλοκ", "post" to "Δοκάρι")[s.result] ?: s.result
        "${s.minute}′ · ${s.player.ifBlank { "—" }} · $res" + (if (s.penalty) " (πέν.)" else "") + (if (s.xg > 0) " · xG " + f2(s.xg.toDouble()) else "")
    } ?: "👆 Πάτα σε ένα σουτ για λεπτομέρειες"
    Text(info, Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.Ink2).border(1.dp, C.Line, RoundedCornerShape(10.dp)).padding(9.dp),
        style = body(13, FontWeight.Bold, if (sel != null) C.Gold else C.Text2), textAlign = TextAlign.Center)
    Note("⚪ γκολ · ● στην εστία · ○ άουτ/δοκάρι · αχνό: μπλοκ · μεγάλος κύκλος = μεγάλη ευκαιρία (xG)")
    Divider()
    val hs = shots.filter { it.side == "home" }; val aw = shots.filter { it.side == "away" }
    fun on(l: List<gr.aekhub.app.data.Shot>) = l.count { it.result == "goal" || it.result == "saved" }
    Compare("Σουτ", hs.size.toDouble(), aw.size.toDouble(), false, cols)
    Compare("Στην εστία", on(hs).toDouble(), on(aw).toDouble(), false, cols)
    Compare("Γκολ", hs.count { it.result == "goal" }.toDouble(), aw.count { it.result == "goal" }.toDouble(), false, cols)
    if (shots.any { it.xg > 0 }) Compare("xG", hs.sumOf { it.xg.toDouble() }, aw.sumOf { it.xg.toDouble() }, true, cols)
}

@Composable
private fun Compare(label: String, h: Double, a: Double, dec: Boolean, cols: Cols) = Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    fun f(v: Double) = if (dec) f2(v) else v.toInt().toString()
    Text(f(h), Modifier.width(44.dp), style = disp(20), color = if (h >= a) C.Text else C.Text2)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, Modifier.fillMaxWidth(), style = body(11, FontWeight.Bold, C.Muted), textAlign = TextAlign.Center)
        val t = (h + a).takeIf { it > 0 } ?: 1.0
        Row(Modifier.fillMaxWidth().height(6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (h > 0) Box(Modifier.weight((h / t).toFloat()).fillMaxHeight().clip(RoundedCornerShape(99.dp)).background(cols.h))
            if (a > 0) Box(Modifier.weight((a / t).toFloat()).fillMaxHeight().clip(RoundedCornerShape(99.dp)).background(cols.a))
            if (h <= 0 && a <= 0) Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(99.dp)).background(C.Ink2))
        }
    }
    Text(f(a), Modifier.width(44.dp), style = disp(20), color = if (a >= h) C.Text else C.Text2, textAlign = TextAlign.End)
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

package gr.aekhub.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gr.aekhub.app.data.Api
import gr.aekhub.app.data.BoxRow
import gr.aekhub.app.data.FormItem
import gr.aekhub.app.data.MatchDetail
import gr.aekhub.app.data.MatchExtra
import gr.aekhub.app.data.Predict
import gr.aekhub.app.ui.components.SectionLabel
import gr.aekhub.app.ui.components.hex
import gr.aekhub.app.ui.components.upperGr
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body
import gr.aekhub.app.ui.theme.disp
import kotlinx.coroutines.launch

/*
 * 📊 Ανάλυση αγώνα — ίδιες ενότητες με το site (Match Center 1.97):
 * πρόβλεψη σκορ, παίκτης του αγώνα & βαθμοί, πίεση, χάρτης σουτ, ροή σκορ, σκόρερ, box score, φόρμα & συναντήσεις.
 * Τα ονόματα παικτών έρχονται ήδη από τον server όπως είναι στη σύνθεση του αγώνα.
 */

/** Υπάρχει κάτι να δείξει η καρτέλα «Ανάλυση»; */
fun MatchExtra.hasAnalysis(): Boolean = !resultOnly && (predict != null || potm != null || !ratings?.home.isNullOrEmpty() || !ratings?.away.isNullOrEmpty() ||
    flow != null || scorers != null || box != null || h2h != null || momentum != null || !shots.isNullOrEmpty())

private class Cols(val h: Color, val a: Color)

@Composable
fun AnalysisView(d: MatchDetail, x: MatchExtra, onVoted: (Predict) -> Unit) {
    val cols = Cols(hex(x.colorHome, C.Gold), hex(x.colorAway, C.Text2))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        x.predict?.let { PredictCard(d, it, onVoted) }
        x.potm?.let { PotmCard(it, x) }
        x.momentum?.takeIf { it.points.isNotEmpty() }?.let { MomentumCard(d, it, cols) }
        x.shots?.takeIf { it.isNotEmpty() }?.let { ShotsCard(d, it, cols) }
        x.flow?.let { FlowCard(d, it, cols) }
        x.scorers?.let { ScorersCard(d, it) }
        x.box?.let { BoxCard(d, it) }
        x.h2h?.let { H2HCard(d, it) }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SectionLabel(title.upperGr())
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Panel).border(1.dp, C.Line, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
}

@Composable
private fun Src(t: String) = Text(t, Modifier.fillMaxWidth(), style = body(11, color = C.Muted), textAlign = TextAlign.End)

@Composable
private fun Legend(d: MatchDetail, cols: Cols) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(cols.h)); Spacer(Modifier.width(6.dp))
    Text(d.home, Modifier.weight(1f), style = body(12, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(d.away, style = body(12, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis); Spacer(Modifier.width(6.dp))
    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(cols.a))
}

/* ─────────── 🔮 Πρόβλεψε το σκορ ─────────── */

@Composable
private fun PredictCard(d: MatchDetail, p: Predict, onVoted: (Predict) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sp = remember { ctx.getSharedPreferences("aekhub_pred", android.content.Context.MODE_PRIVATE) }
    var mine by remember(d.id) { mutableStateOf(sp.getString(d.id.toString(), "") ?: "") }
    var h by remember { mutableIntStateOf(0) }
    var a by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    fun label(k: String) = when (p.mode) { "win" -> "Νίκη " + (if (k == "H") d.home else d.away); else -> k.replace("-", "–") }
    fun vote(k: String) {
        if (busy) return
        busy = true; err = ""
        scope.launch {
            runCatching { Api.predict(ctx, d.id, k) }
                .onSuccess { mine = k; sp.edit().putString(d.id.toString(), k).apply(); onVoted(it) }
                .onFailure { err = it.message ?: "Δεν στάλθηκε. Δοκίμασε ξανά." }
            busy = false
        }
    }
    Card("🔮 Πρόβλεψε το σκορ") {
        if (p.open && mine.isEmpty()) {
            when (p.mode) {
                "score" -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Stepper(d.home, h, Modifier.weight(1f)) { h = it }
                        Text("–", Modifier.padding(horizontal = 8.dp), style = disp(24), color = C.Muted)
                        Stepper(d.away, a, Modifier.weight(1f)) { a = it }
                    }
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Gold).clickable { vote("$h-$a") }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text(if (busy) "…" else "🔮 Ψήφισε", style = body(15, FontWeight.ExtraBold, C.Ink))
                    }
                }
                else -> FlowRowOpts(p.options.map { it to label(it) }) { vote(it) }
            }
            Text("Κλείνει με τη σέντρα · ${p.total} ψήφοι μέχρι τώρα", style = body(12, color = C.Muted))
        } else {
            if (mine.isNotEmpty()) Text("✓ Η πρόβλεψή σου: " + label(mine), style = body(13, FontWeight.ExtraBold, C.Gold))
            p.top.forEach { t ->
                val hit = t.key == p.actual && p.actual.isNotEmpty()
                Box(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(8.dp)).background(C.Panel2)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth((t.pct.coerceAtLeast(4)) / 100f).background(if (hit) C.Win.copy(alpha = 0.7f) else C.Gold.copy(alpha = 0.45f)))
                    Row(Modifier.fillMaxWidth().align(Alignment.Center).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t.label.ifBlank { label(t.key) } + if (hit) " ✓" else "", Modifier.weight(1f), style = body(13, FontWeight.Bold))
                        Text("${t.pct}%", style = body(13, FontWeight.ExtraBold))
                    }
                }
            }
            Text("${p.total} ψήφοι" + when {
                p.correctPct != null -> " · το βρήκε το ${p.correctPct}%"
                p.open -> " · τα αποτελέσματα μετά τη λήξη"
                else -> " · η ψηφοφορία έκλεισε με τη σέντρα"
            }, style = body(12, color = C.Muted))
        }
        if (err.isNotEmpty()) Text(err, style = body(12, FontWeight.Bold, C.Live))
    }
}

@Composable
private fun Stepper(team: String, v: Int, modifier: Modifier, set: (Int) -> Unit) = Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(team, style = body(13, FontWeight.ExtraBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StepBtn("−") { set((v - 1).coerceAtLeast(0)) }
        Text(v.toString(), Modifier.widthIn(min = 30.dp), style = disp(30, FontWeight.Black), color = C.Gold, textAlign = TextAlign.Center)
        StepBtn("+") { set((v + 1).coerceAtMost(59)) }
    }
}

@Composable
private fun StepBtn(t: String, onClick: () -> Unit) = Box(
    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(C.Panel2).border(1.dp, C.Line2, RoundedCornerShape(10.dp)).clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
) { Text(t, style = body(18, FontWeight.ExtraBold)) }

@Composable
private fun FlowRowOpts(opts: List<Pair<String, String>>, pick: (String) -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    opts.chunked(if (opts.size > 2) 3 else 2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (k, l) ->
                Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(C.Panel2).border(1.dp, C.Gold.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).clickable { pick(k) }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center) { Text(l, style = body(14, FontWeight.ExtraBold), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/* ─────────── ⭐ Παίκτης του αγώνα & βαθμοί ─────────── */

fun ratingColor(r: Float): Color = when {
    r >= 8f -> Color(0xFF1E88E5); r >= 7f -> Color(0xFF2BB673); r >= 6f -> Color(0xFFF2C230); else -> Color(0xFFE8673A)
}

@Composable
fun RatingBadge(r: Float, big: Boolean = false) = Box(
    Modifier.clip(RoundedCornerShape(if (big) 10.dp else 5.dp)).background(ratingColor(r)).padding(horizontal = if (big) 10.dp else 5.dp, vertical = if (big) 5.dp else 1.dp),
    contentAlignment = Alignment.Center,
) { Text(String.format(java.util.Locale.US, "%.1f", r), style = body(if (big) 18 else 11, FontWeight.ExtraBold, if (r >= 8f) Color.White else C.Ink)) }

@Composable
private fun PotmCard(p: gr.aekhub.app.data.Potm, x: MatchExtra) = Card("⭐ Παίκτης του αγώνα") {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("⭐", style = body(26))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = body(17, FontWeight.ExtraBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text((if (p.number.isNotBlank()) "#${p.number} · " else "") + p.team, style = body(12, color = C.Muted))
        }
        p.rating.toFloatOrNull()?.let { RatingBadge(it, big = true) }
    }
    val r = x.ratings
    if (r != null) {
        val all = (r.home.map { it to true } + r.away.map { it to false }).filter { it.first.name.isNotBlank() && it.first.name != p.name }.sortedByDescending { it.first.rating }.take(4)
        if (all.isNotEmpty()) {
            SectionLabel("ΟΙ ΚΑΛΥΤΕΡΟΙ ΒΑΘΜΟΙ")
            all.forEach { (row, _) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.name, Modifier.weight(1f), style = body(13, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    RatingBadge(row.rating)
                }
            }
        }
    }
}

/* ─────────── 📈 Πίεση αγώνα ─────────── */

@Composable
private fun MomentumCard(d: MatchDetail, m: gr.aekhub.app.data.Momentum, cols: Cols) = Card("📈 Πίεση αγώνα") {
    Legend(d, cols)
    val total = (m.periodLength * m.periods).coerceAtLeast(1).toFloat()
    val maxMin = maxOf(total, m.points.maxOf { it.minute })
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val mid = size.height / 2f
        val bw = (size.width / maxMin).coerceAtLeast(1.5f)
        drawLine(C.Line2, Offset(0f, mid), Offset(size.width, mid), 1f)
        for (k in 1 until m.periods) {
            val xx = size.width * (m.periodLength * k) / maxMin
            drawLine(C.Muted, Offset(xx, 0f), Offset(xx, size.height), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        }
        m.points.forEach { p ->
            val xx = size.width * p.minute / maxMin
            val hgt = (p.value.coerceIn(-100, 100) / 100f) * (mid - 2f)
            if (hgt > 0) drawRect(cols.h, Offset(xx, mid - hgt), Size(bw, hgt))
            else if (hgt < 0) drawRect(cols.a, Offset(xx, mid), Size(bw, -hgt))
        }
    }
    Row { Text("0′", style = body(11, color = C.Muted)); Spacer(Modifier.weight(1f)); Text("${m.periodLength}′", style = body(11, color = C.Muted)); Spacer(Modifier.weight(1f)); Text("${total.toInt()}′", style = body(11, color = C.Muted)) }
    Text("Πάνω: πίεση ${d.home} · κάτω: ${d.away}", style = body(11, color = C.Muted))
}

/* ─────────── 🎯 Χάρτης σουτ (γηπεδούχος επιτίθεται δεξιά) ─────────── */

@Composable
private fun ShotsCard(d: MatchDetail, shots: List<gr.aekhub.app.data.Shot>, cols: Cols) = Card("🎯 Χάρτης σουτ") {
    Legend(d, cols)
    var sel by remember(shots) { mutableStateOf<gr.aekhub.app.data.Shot?>(null) }
    val pitch = Color(0xFF1F5A35); val line = Color.White.copy(alpha = 0.55f)
    Box(Modifier.fillMaxWidth().aspectRatio(105f / 68f).clip(RoundedCornerShape(10.dp)).background(pitch)) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width; val h = size.height; val st = Stroke(2f)
            drawRect(line, Offset(3f, 3f), Size(w - 6f, h - 6f), style = st)
            drawLine(line, Offset(w / 2, 3f), Offset(w / 2, h - 3f), 2f)
            drawCircle(line, h * 0.135f, Offset(w / 2, h / 2), style = st)
            val pbW = w * 16.5f / 105f; val pbH = h * 40.3f / 68f; val gaW = w * 5.5f / 105f; val gaH = h * 18.3f / 68f
            drawRect(line, Offset(3f, (h - pbH) / 2), Size(pbW, pbH), style = st); drawRect(line, Offset(w - 3f - pbW, (h - pbH) / 2), Size(pbW, pbH), style = st)
            drawRect(line, Offset(3f, (h - gaH) / 2), Size(gaW, gaH), style = st); drawRect(line, Offset(w - 3f - gaW, (h - gaH) / 2), Size(gaW, gaH), style = st)
            shots.forEach { s ->
                val c = if (s.side == "home") cols.h else cols.a
                val r = (5f + s.xg.coerceIn(0f, 1f) * 16f) * density / 2.2f
                val o = Offset(w * s.x / 100f, h * s.y / 100f)
                if (s.result == "goal") { drawCircle(c, r * 1.25f, o); drawCircle(Color.White, r * 1.25f, o, style = Stroke(3f)) }
                else if (s.result == "saved" || s.result == "post") drawCircle(c.copy(alpha = 0.85f), r, o)
                else drawCircle(c, r, o, style = Stroke(3f))
                if (s == sel) drawCircle(Color.White, r * 1.9f, o, style = Stroke(2f))
            }
        }
        // Πάτημα: ο πιο κοντινός πόντος
        Box(Modifier.matchParentSize().pointerInput(shots) {
            detectTapGestures { o ->
                val fx = o.x / size.width * 100f; val fy = o.y / size.height * 100f
                sel = shots.minByOrNull { (it.x - fx) * (it.x - fx) + (it.y - fy) * (it.y - fy) }
            }
        })
    }
    sel?.let { s ->
        val res = mapOf("goal" to "⚽ Γκολ", "saved" to "Απόκρουση", "miss" to "Άουτ", "blocked" to "Κόφτηκε", "post" to "Δοκάρι")[s.result] ?: s.result
        Text("${s.minute}′ · ${s.player.ifBlank { "—" }} · $res" + (if (s.penalty) " (πέν.)" else "") + (if (s.xg > 0) " · xG " + String.format(java.util.Locale.US, "%.2f", s.xg) else ""),
            style = body(13, FontWeight.Bold, C.Gold))
    }
    val hs = shots.filter { it.side == "home" }; val aw = shots.filter { it.side == "away" }
    StatLine("Σουτ", hs.size.toString(), aw.size.toString())
    StatLine("Στην εστία", hs.count { it.result == "goal" || it.result == "saved" }.toString(), aw.count { it.result == "goal" || it.result == "saved" }.toString())
    if (shots.any { it.xg > 0 }) StatLine("xG", String.format(java.util.Locale.US, "%.2f", hs.sumOf { it.xg.toDouble() }), String.format(java.util.Locale.US, "%.2f", aw.sumOf { it.xg.toDouble() }))
    Text("Γεμάτος κύκλος: στην εστία · με λευκό: γκολ · μέγεθος: xG. Πάτα σε ένα σουτ.", style = body(11, color = C.Muted))
}

@Composable
private fun StatLine(label: String, h: String, a: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Text(h, Modifier.width(56.dp), style = disp(17), color = C.Text)
    Text(label, Modifier.weight(1f), style = body(12, FontWeight.Bold, C.Text2), textAlign = TextAlign.Center)
    Text(a, Modifier.width(56.dp), style = disp(17), color = C.Text, textAlign = TextAlign.End)
}

/* ─────────── Ροή σκορ ─────────── */

@Composable
private fun FlowCard(d: MatchDetail, f: gr.aekhub.app.data.Flow, cols: Cols) = Card(if (f.sets) "Σετ προς σετ" else "Ροή σκορ") {
    Legend(d, cols)
    if (f.type == "min" && f.goals.isNotEmpty()) {
        val len = f.len.coerceAtLeast(1).toFloat()
        val mx = maxOf(1, f.goals.maxOf { kotlin.math.abs(it.home - it.away) }).toFloat()
        Canvas(Modifier.fillMaxWidth().height(100.dp)) {
            val mid = size.height / 2f; val sc = (mid - 10f) / mx
            drawLine(C.Line2, Offset(0f, mid), Offset(size.width, mid), 1f)
            drawLine(C.Muted, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            var px = 0f; var diff = 0
            (f.goals + gr.aekhub.app.data.FlowGoal(f.now.coerceAtLeast(f.goals.last().minute), f.goals.last().home, f.goals.last().away)).forEach { g ->
                val x0 = size.width * px / len; val x1 = size.width * g.minute.coerceAtMost(len) / len
                if (diff > 0) drawRect(cols.h, Offset(x0, mid - diff * sc), Size(x1 - x0, diff * sc))
                else if (diff < 0) drawRect(cols.a, Offset(x0, mid), Size(x1 - x0, -diff * sc))
                px = g.minute.coerceAtMost(len); diff = g.home - g.away
            }
            f.goals.forEach { g ->
                val gx = size.width * g.minute.coerceAtMost(len) / len
                drawCircle(Color.White, 3.5f * density / 2.2f + 2f, Offset(gx, if (g.note == "a") size.height - 6f else 6f))
            }
        }
        Row { Text("0′", style = body(11, color = C.Muted)); Spacer(Modifier.weight(1f)); Text("${f.len / 2}′", style = body(11, color = C.Muted)); Spacer(Modifier.weight(1f)); Text("${f.len}′", style = body(11, color = C.Muted)) }
        val parts = buildList {
            if (f.lead.h > 0) add("${d.home} μπροστά ${f.lead.h}′")
            if (f.lead.a > 0) add("${d.away} μπροστά ${f.lead.a}′")
        }
        if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = body(12, color = C.Text2), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    } else if (f.per.isNotEmpty()) {
        val mx = f.per.maxOf { maxOf(it.h, it.a) }.coerceAtLeast(1).toFloat()
        var sh = 0; var sa = 0
        f.per.forEach { p ->
            if (f.sets) { if (p.h > p.a) sh++ else if (p.a > p.h) sa++ } else { sh += p.h - p.a }
            val run = if (f.sets) "$sh–$sa" else (if (sh > 0) "+$sh" else "$sh")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(p.label, Modifier.width(34.dp), style = body(12, FontWeight.ExtraBold, C.Muted), maxLines = 1)
                PerBar(p.h, p.h / mx, cols.h, p.h > p.a, Modifier.weight(1f))
                PerBar(p.a, p.a / mx, cols.a, p.a > p.h, Modifier.weight(1f))
                Text(run, Modifier.width(40.dp), style = body(12, FontWeight.ExtraBold, C.Text2), textAlign = TextAlign.End)
            }
        }
        Text(if (f.sets) "Δεξιά: τα σετ μετά από κάθε σετ" else "Δεξιά: η διαφορά μετά από κάθε περίοδο (+ υπέρ ${d.home})", style = body(11, color = C.Muted))
    }
}

@Composable
private fun PerBar(v: Int, frac: Float, c: Color, win: Boolean, modifier: Modifier) = Box(modifier.height(24.dp).clip(RoundedCornerShape(6.dp)).background(C.Panel2)) {
    Box(Modifier.fillMaxHeight().fillMaxWidth(frac.coerceIn(0.04f, 1f)).background(c.copy(alpha = if (win) 0.95f else 0.5f)))
    Text(v.toString(), Modifier.align(Alignment.CenterStart).padding(start = 4.dp).clip(RoundedCornerShape(4.dp)).background(C.Ink.copy(alpha = 0.55f)).padding(horizontal = 5.dp),
        style = body(12, FontWeight.ExtraBold, Color.White))
}

/* ─────────── Σκόρερ (χάντμπολ / πόλο / futsal) ─────────── */

@Composable
private fun ScorersCard(d: MatchDetail, s: gr.aekhub.app.data.Scorers) = Card("Σκόρερ") {
    listOf(d.home to s.home, d.away to s.away).filter { it.second.isNotEmpty() }.forEach { (team, list) ->
        SectionLabel(team.upperGr())
        list.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.name, Modifier.weight(1f), style = body(14, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(r.goals.toString(), style = disp(18), color = C.Gold)
            }
        }
    }
}

/* ─────────── 🏀 Box score ─────────── */

@Composable
private fun BoxCard(d: MatchDetail, b: gr.aekhub.app.data.BoxScore) = Card("🏀 Box score") {
    listOf(d.home to b.home, d.away to b.away).filter { it.second.isNotEmpty() }.forEach { (team, list) ->
        SectionLabel(team.upperGr())
        Row { Text("Παίκτης", Modifier.weight(1f), style = body(11, FontWeight.ExtraBold, C.Muted)); listOf("Λ", "Π", "Ρ", "Α").forEach { Text(it, Modifier.width(32.dp), style = body(11, FontWeight.ExtraBold, C.Muted), textAlign = TextAlign.End) } }
        val top = list.maxOf { it.pts }
        list.forEach { r: BoxRow ->
            val hi = r.pts == top && top > 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text((if (r.number.isNotBlank()) r.number + " " else "") + r.name, Modifier.weight(1f), style = body(13, FontWeight.Bold, if (hi) C.Gold else C.Text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                listOf(r.min, r.pts, r.reb, r.ast).forEachIndexed { i, v -> Text(v.toString(), Modifier.width(32.dp), style = body(13, if (i == 1) FontWeight.ExtraBold else FontWeight.Medium, if (hi) C.Gold else C.Text), textAlign = TextAlign.End) }
            }
        }
    }
    Src("Δεδομένα: " + if (b.src == "ss") "Sofascore" else "API-Sports")
}

/* ─────────── Φόρμα & προηγούμενες συναντήσεις ─────────── */

@Composable
private fun H2HCard(d: MatchDetail, h: gr.aekhub.app.data.H2H) = Card("Φόρμα & συναντήσεις") {
    if (h.formHome.isNotEmpty() || h.formAway.isNotEmpty()) {
        SectionLabel("ΦΟΡΜΑ (ΠΙΟ ΠΡΟΣΦΑΤΟΣ ΔΕΞΙΑ)")
        FormRow(d.home, h.formHome); FormRow(d.away, h.formAway)
    }
    if (h.meetings.isNotEmpty()) {
        SectionLabel("ΠΡΟΗΓΟΥΜΕΝΕΣ ΣΥΝΑΝΤΗΣΕΙΣ")
        if (h.summary.size == 3) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(h.summary[0] to d.home, h.summary[1] to "Ισοπαλίες", h.summary[2] to d.away).forEach { (n, l) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(C.Panel2).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(n.toString(), style = disp(22), color = C.Text); Text(l, style = body(11, color = C.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        h.meetings.forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(shortDate(m.d), Modifier.width(56.dp), style = body(11, color = C.Muted))
                Text(m.home, Modifier.weight(1f), style = body(13, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${m.sh} – ${m.sa}" + if (m.pen.isNotBlank()) "\n(πέν. ${m.pen})" else "", style = body(13, FontWeight.ExtraBold), textAlign = TextAlign.Center)
                Text(m.away, Modifier.weight(1f), style = body(13, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
            }
        }
    }
    Src("Δεδομένα: " + when (h.src) { "fm" -> "FotMob"; "fs" -> "Flashscore"; else -> "AEK Hub" })
}

@Composable
private fun FormRow(team: String, f: List<FormItem>) {
    if (f.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(team, Modifier.weight(1f), style = body(13, FontWeight.ExtraBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        f.reversed().forEach { x ->
            val (t, c) = when (x.r) { "W" -> "Ν" to C.Win; "L" -> "Η" to C.Loss; else -> "Ι" to C.Muted }
            Box(Modifier.padding(start = 4.dp).size(24.dp).clip(RoundedCornerShape(6.dp)).background(c), contentAlignment = Alignment.Center) { Text(t, style = body(12, FontWeight.ExtraBold, Color.White)) }
        }
    }
}

private fun shortDate(iso: String): String = runCatching { java.time.LocalDate.parse(iso).let { "${it.dayOfMonth}/${it.monthValue}/${it.year % 100}" } }.getOrDefault(iso)

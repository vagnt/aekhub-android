package gr.aekhub.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import gr.aekhub.app.data.Api
import gr.aekhub.app.data.BoxRow
import gr.aekhub.app.data.FormItem
import gr.aekhub.app.data.MatchDetail
import gr.aekhub.app.data.MatchExtra
import gr.aekhub.app.data.Predict
import gr.aekhub.app.ui.components.Crest
import gr.aekhub.app.ui.components.hex
import gr.aekhub.app.ui.components.upperGr
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body
import gr.aekhub.app.ui.theme.disp
import kotlinx.coroutines.launch
import java.util.Locale

/*
 * 📊 Ανάλυση αγώνα — ίδια εμφάνιση με το site (Match Center 1.98): κάρτες με εικονίδιο/τίτλο/πηγή,
 * πρόβλεψη σκορ, παίκτης του αγώνα & βαθμοί, πίεση, χάρτης σουτ, ροή σκορ, σκόρερ, box score, φόρμα (πάτημα → ο αγώνας), συναντήσεις.
 * Τα ονόματα παικτών έρχονται ήδη από τον server όπως είναι στη σύνθεση του αγώνα.
 */

/** Υπάρχει κάτι να δείξει η καρτέλα «Ανάλυση»; */
fun MatchExtra.hasAnalysis(): Boolean = !resultOnly && (predict != null || potm != null || !ratings?.home.isNullOrEmpty() || !ratings?.away.isNullOrEmpty() ||
    flow != null || scorers != null || box != null || h2h != null || momentum != null || !shots.isNullOrEmpty())

private class Cols(val h: Color, val a: Color)
private val W = Color(0xFF22C38E); private val D = Color(0xFF9A968A); private val L = Color(0xFFF0525A)
private fun src(k: String) = when (k) { "fm" -> "FotMob"; "fs" -> "Flashscore"; "ss" -> "Sofascore"; "as" -> "API-Sports"; "db" -> "AEK Hub"; else -> "" }
private fun f1(v: Float) = String.format(Locale.US, "%.1f", v)
private fun f2(v: Double) = String.format(Locale.US, "%.2f", v)

@Composable
fun AnalysisView(d: MatchDetail, x: MatchExtra, onVoted: (Predict) -> Unit) {
    val cols = Cols(hex(x.colorHome, C.Gold), hex(x.colorAway, C.Text2))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        x.predict?.let { PredictCard(d, it, onVoted) }
        x.potm?.let { PotmCard(d, it, x) }
        x.momentum?.takeIf { it.points.isNotEmpty() }?.let { MomentumCard(d, it, cols) }
        x.shots?.takeIf { it.isNotEmpty() }?.let { ShotsCard(d, it, cols) }
        x.flow?.let { FlowCard(d, it, cols) }
        x.scorers?.let { ScorersCard(d, it, cols) }
        x.box?.let { BoxCard(d, it) }
        x.h2h?.let { h ->
            if (h.formHome.isNotEmpty() || h.formAway.isNotEmpty()) FormCard(d, h)
            if (h.meetings.isNotEmpty()) MeetingsCard(d, h, cols)
        }
    }
}

/* ─────────── κοινά ─────────── */

private val CardShape = RoundedCornerShape(18.dp)

@Composable
private fun Card(icon: String, title: String, tag: String = "", glow: Float = 0.07f, content: @Composable () -> Unit) = Column(
    Modifier.fillMaxWidth().clip(CardShape)
        .background(C.Panel)
        .background(Brush.radialGradient(listOf(C.Gold.copy(alpha = glow), Color.Transparent), center = Offset(0f, 0f), radius = 900f))
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.01f))))
        .border(1.dp, Color.White.copy(alpha = 0.07f), CardShape).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(C.Gold.copy(alpha = 0.22f), C.Gold.copy(alpha = 0.06f))))
            .border(1.dp, C.Gold.copy(alpha = 0.28f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Text(icon, style = body(15)) }
        Text(title.upperGr(), Modifier.weight(1f), style = disp(22), color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (tag.isNotBlank()) Text(tag, Modifier.clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = 0.06f)).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(99.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp), style = body(10, FontWeight.ExtraBold, C.Text2))
    }
    content()
}

@Composable
private fun Label(t: String) = Text(t.upperGr(), style = body(11, FontWeight.ExtraBold, C.Muted))

@Composable
private fun Note(t: String) = Text(t, Modifier.fillMaxWidth(), style = body(12, color = C.Muted), textAlign = TextAlign.Center)

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))

/** Οι δύο ομάδες με λογότυπα και χρώμα από κάτω */
@Composable
private fun Vs(d: MatchDetail, cols: Cols) = Row(verticalAlignment = Alignment.CenterVertically) {
    TeamTag(d.homeLogo, d.home, cols.h, Modifier.weight(1f), end = false)
    Spacer(Modifier.width(12.dp))
    TeamTag(d.awayLogo, d.away, cols.a, Modifier.weight(1f), end = true)
}

@Composable
private fun TeamTag(logo: String, name: String, c: Color, modifier: Modifier, end: Boolean) = Column(modifier, horizontalAlignment = if (end) Alignment.End else Alignment.Start) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!end) Crest(logo, name, 24.dp)
        Text(name, style = body(13, FontWeight.ExtraBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
        if (end) Crest(logo, name, 24.dp)
    }
    Box(Modifier.padding(top = 6.dp).width(56.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(c))
}

/** Μπάρα τριών κομματιών (γηπεδούχος / ισοπαλία / φιλοξενούμενος) */
@Composable
private fun TriBar(h: Float, dr: Float, a: Float, cols: Cols) {
    val t = (h + dr + a).takeIf { it > 0f } ?: 1f
    Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = 0.06f)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (h > 0f) Box(Modifier.weight(h / t).fillMaxHeight().background(cols.h))
        if (dr > 0f) Box(Modifier.weight(dr / t).fillMaxHeight().background(D.copy(alpha = 0.55f)))
        if (a > 0f) Box(Modifier.weight(a / t).fillMaxHeight().background(cols.a))
    }
}

@Composable
private fun Tile(big: String, small: String, modifier: Modifier) = Column(
    modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.04f)).padding(vertical = 9.dp, horizontal = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Text(big, style = body(19, FontWeight.ExtraBold))
    Text(small, style = body(11, color = C.Muted), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun ChartBox(content: @Composable () -> Unit) = Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.22f)).border(1.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp)).padding(10.dp),
) { content() }

@Composable
private fun Ticks(vararg t: String) = Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
    t.forEachIndexed { i, s -> if (i > 0) Spacer(Modifier.weight(1f)); Text(s, style = body(11, color = C.Muted)) }
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
    val left = remember(p.closesAt) {
        val s = p.closesAt - System.currentTimeMillis() / 1000
        when { s <= 0 -> ""; s > 86400 -> "${s / 86400} μέρ. ${s % 86400 / 3600} ώρ."; s > 3600 -> "${s / 3600} ώρ. ${s % 3600 / 60} λεπ."; else -> "${(s / 60).coerceAtLeast(1)} λεπ." }
    }
    Card("🔮", "Πρόβλεψε το σκορ") {
        if (p.open && mine.isEmpty()) {
            when (p.mode) {
                "score" -> {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Stepper(d.homeLogo, d.home, h, Modifier.weight(1f)) { h = it }
                        Text(":", Modifier.padding(horizontal = 4.dp, vertical = 12.dp), style = disp(28), color = C.Muted)
                        Stepper(d.awayLogo, d.away, a, Modifier.weight(1f)) { a = it }
                    }
                    GoldButton(if (busy) "…" else "🔮 Κατέθεσε την πρόβλεψή σου") { vote("$h-$a") }
                }
                "sets" -> OptGrid(p.options.map { k -> Triple(k, k.replace("-", "–"), if ((k.firstOrNull()?.digitToIntOrNull() ?: 0) > (k.lastOrNull()?.digitToIntOrNull() ?: 0)) d.home else d.away) }, 3) { vote(it) }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Triple("H", d.home, d.homeLogo), Triple("A", d.away, d.awayLogo)).forEach { (k, n, lg) ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.05f)).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                            .clickable { vote(k) }.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Crest(lg, n, 40.dp); Text("Νίκη $n", style = body(13, FontWeight.ExtraBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Row { Text("👥 ${p.total} ψήφοι", style = body(12, color = C.Muted)); Spacer(Modifier.weight(1f)); if (left.isNotEmpty()) Text("⏳ Κλείνει σε $left", style = body(12, color = C.Muted)) }
        } else {
            if (mine.isNotEmpty()) Text("✓ Η πρόβλεψή σου: " + label(mine), Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Gold.copy(alpha = 0.08f))
                .border(1.dp, C.Gold.copy(alpha = 0.25f), RoundedCornerShape(12.dp)).padding(10.dp), style = body(13, FontWeight.ExtraBold, C.Gold), textAlign = TextAlign.Center)
            p.top.forEachIndexed { i, t ->
                val hit = t.key == p.actual && p.actual.isNotEmpty(); val me = t.key == mine
                Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.04f))
                    .then(if (me) Modifier.border(1.5.dp, C.Gold, RoundedCornerShape(12.dp)) else Modifier)) {
                    val fill = if (hit) W else C.Gold
                    Box(Modifier.fillMaxHeight().fillMaxWidth(t.pct.coerceAtLeast(3) / 100f).clip(RoundedCornerShape(12.dp))
                        .background(Brush.horizontalGradient(listOf(fill.copy(alpha = if (i == 0 || hit) 0.25f else 0.12f), fill.copy(alpha = if (i == 0 || hit) 0.5f else 0.28f)))))
                    Row(Modifier.fillMaxWidth().align(Alignment.Center).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t.label.ifBlank { label(t.key) }, style = body(14, FontWeight.Bold))
                        if (hit) Pill("✓ σωστό", W) else if (me) Pill("εσύ", C.Gold)
                        Spacer(Modifier.weight(1f))
                        Text("${t.pct}%", style = body(14, FontWeight.ExtraBold))
                    }
                }
            }
            Row {
                Text("👥 ${p.total} ψήφοι", style = body(12, color = C.Muted)); Spacer(Modifier.weight(1f))
                Text(when { p.correctPct != null -> "🎯 Το βρήκε το ${p.correctPct}%"; p.open -> "Αποτελέσματα μετά τη λήξη"; else -> "🔒 Έκλεισε με τη σέντρα" }, style = body(12, FontWeight.Bold, C.Text2))
            }
        }
        if (err.isNotEmpty()) Text(err, style = body(12, FontWeight.Bold, C.Live))
    }
}

@Composable
private fun Pill(t: String, c: Color) = Text(t, Modifier.padding(start = 6.dp).clip(RoundedCornerShape(99.dp)).background(Color.Black.copy(alpha = 0.4f)).padding(horizontal = 7.dp, vertical = 1.dp),
    style = body(10, FontWeight.ExtraBold, c))

@Composable
private fun GoldButton(t: String, onClick: () -> Unit) = Box(
    Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFFFFE066), C.Gold, Color(0xFFE0B10F)))).clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
) { Text(t, style = body(15, FontWeight.ExtraBold, C.Ink)) }

@Composable
private fun Stepper(logo: String, team: String, v: Int, modifier: Modifier, set: (Int) -> Unit) = Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Crest(logo, team, 44.dp)
    Text(team, style = body(13, FontWeight.ExtraBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StepBtn("−") { set((v - 1).coerceAtLeast(0)) }
        Box(Modifier.size(width = 46.dp, height = 54.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.35f)).border(1.dp, C.Gold.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center) { Text(v.toString(), style = disp(32, FontWeight.Black), color = C.Gold) }
        StepBtn("+") { set((v + 1).coerceAtMost(59)) }
    }
}

@Composable
private fun StepBtn(t: String, onClick: () -> Unit) = Box(
    Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.06f)).border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape).clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
) { Text(t, style = body(19, FontWeight.ExtraBold)) }

@Composable
private fun OptGrid(opts: List<Triple<String, String, String>>, perRow: Int, pick: (String) -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    opts.chunked(perRow).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (k, l, sub) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.05f)).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                    .clickable { pick(k) }.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(sub, style = body(10, FontWeight.Bold, C.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(l, style = body(20, FontWeight.ExtraBold))
                }
            }
            repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/* ─────────── ⭐ Παίκτης του αγώνα & βαθμοί ─────────── */

fun ratingColor(r: Float): Color = when {
    r >= 8f -> Color(0xFF3D9BFF); r >= 7f -> Color(0xFF22C38E); r >= 6f -> Color(0xFFF2C230); else -> Color(0xFFF0703A)
}

@Composable
fun RatingBadge(r: Float, big: Boolean = false) = Box(
    Modifier.clip(RoundedCornerShape(if (big) 10.dp else 6.dp)).background(ratingColor(r)).padding(horizontal = if (big) 10.dp else 6.dp, vertical = if (big) 5.dp else 1.dp),
    contentAlignment = Alignment.Center,
) { Text(f1(r), style = body(if (big) 18 else 11, FontWeight.ExtraBold, if (r >= 8f) Color.White else C.Ink)) }

@Composable
private fun RatingRing(r: Float, size: Dp) = Box(Modifier.size(size), contentAlignment = Alignment.Center) {
    val c = ratingColor(r)
    Canvas(Modifier.matchParentSize()) {
        val sw = 6.dp.toPx(); val inset = sw / 2
        drawArc(Color.White.copy(alpha = 0.08f), 0f, 360f, false, Offset(inset, inset), Size(this.size.width - sw, this.size.height - sw), style = Stroke(sw))
        drawArc(c, -90f, 360f * (r.coerceIn(0f, 10f) / 10f), false, Offset(inset, inset), Size(this.size.width - sw, this.size.height - sw), style = Stroke(sw, cap = StrokeCap.Round))
    }
    Text(f1(r), style = body(22, FontWeight.ExtraBold))
}

@Composable
private fun PotmCard(d: MatchDetail, p: gr.aekhub.app.data.Potm, x: MatchExtra) = Card("⭐", "Οι καλύτεροι του αγώνα", glow = 0.18f) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        p.rating.toFloatOrNull()?.let { RatingRing(it, 76.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("⭐ ΠΑΙΚΤΗΣ ΤΟΥ ΑΓΩΝΑ", style = body(11, FontWeight.ExtraBold, C.Gold))
            Text(p.name, style = disp(30), color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Crest(if (p.side == "away") d.awayLogo else d.homeLogo, p.team, 18.dp)
                Text((if (p.number.isNotBlank()) "#${p.number} · " else "") + p.team, style = body(12, FontWeight.Bold, C.Text2))
            }
        }
    }
    val r = x.ratings
    if (r != null) {
        val all = (r.home.map { it to true } + r.away.map { it to false }).filter { it.first.name.isNotBlank() && it.first.name != p.name }.sortedByDescending { it.first.rating }.take(5)
        if (all.isNotEmpty()) {
            Divider(); Label("Κορυφαίοι βαθμοί")
            all.forEachIndexed { i, (row, home) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${i + 2}", Modifier.width(16.dp), style = body(11, FontWeight.ExtraBold, C.Muted), textAlign = TextAlign.Center)
                    Crest(if (home) d.homeLogo else d.awayLogo, if (home) d.home else d.away, 20.dp)
                    Text(row.name, Modifier.weight(1f), style = body(13, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (row.number.isNotBlank()) Text("#${row.number}", style = body(11, FontWeight.Bold, C.Muted))
                    RatingBadge(row.rating)
                }
            }
        }
    }
}

/* ─────────── 🔥 Πίεση αγώνα ─────────── */

@Composable
private fun MomentumCard(d: MatchDetail, m: gr.aekhub.app.data.Momentum, cols: Cols) = Card("🔥", "Πίεση αγώνα") {
    Vs(d, cols)
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
private fun ShotsCard(d: MatchDetail, shots: List<gr.aekhub.app.data.Shot>, cols: Cols) = Card("🎯", "Χάρτης σουτ") {
    Vs(d, cols)
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
    Text(info, Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.25f)).padding(9.dp),
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
    Text(f(h), Modifier.width(44.dp), style = body(15, if (h >= a) FontWeight.ExtraBold else FontWeight.Medium, if (h >= a) C.Text else C.Text2))
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, Modifier.fillMaxWidth(), style = body(11, FontWeight.Bold, C.Muted), textAlign = TextAlign.Center)
        val t = (h + a).takeIf { it > 0 } ?: 1.0
        Row(Modifier.fillMaxWidth().height(6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (h > 0) Box(Modifier.weight((h / t).toFloat()).fillMaxHeight().clip(RoundedCornerShape(99.dp)).background(cols.h))
            if (a > 0) Box(Modifier.weight((a / t).toFloat()).fillMaxHeight().clip(RoundedCornerShape(99.dp)).background(cols.a))
            if (h <= 0 && a <= 0) Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = 0.06f)))
        }
    }
    Text(f(a), Modifier.width(44.dp), style = body(15, if (a >= h) FontWeight.ExtraBold else FontWeight.Medium, if (a >= h) C.Text else C.Text2), textAlign = TextAlign.End)
}

/* ─────────── Ροή σκορ ─────────── */

@Composable
private fun FlowCard(d: MatchDetail, f: gr.aekhub.app.data.Flow, cols: Cols) {
    if (f.type == "min" && f.goals.isNotEmpty()) Card("📈", "Ροή σκορ") {
        Vs(d, cols)
        val len = f.len.coerceAtLeast(1).toFloat()
        val mx = maxOf(2, f.goals.maxOf { kotlin.math.abs(it.home - it.away) }).toFloat()
        ChartBox {
            Canvas(Modifier.fillMaxWidth().height(112.dp)) {
                val mid = size.height / 2f; val sc = (mid - 14f) / mx
                for (k in 1 until mx.toInt()) {
                    drawLine(Color.White.copy(alpha = 0.05f), Offset(0f, mid - k * sc), Offset(size.width, mid - k * sc), 1f)
                    drawLine(Color.White.copy(alpha = 0.05f), Offset(0f, mid + k * sc), Offset(size.width, mid + k * sc), 1f)
                }
                if (d.sport != "waterpolo") drawLine(C.Muted.copy(alpha = 0.6f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                var px = 0f; var diff = 0
                (f.goals + gr.aekhub.app.data.FlowGoal(f.now.coerceAtLeast(f.goals.last().minute), 0, 0)).forEach { g ->
                    val x0 = size.width * px / len; val x1 = size.width * g.minute.coerceAtMost(len) / len
                    if (diff > 0) drawRoundRect(Brush.verticalGradient(listOf(cols.h, cols.h.copy(alpha = 0.35f)), startY = mid - diff * sc, endY = mid), Offset(x0, mid - diff * sc), Size(x1 - x0, diff * sc), CornerRadius(3f))
                    else if (diff < 0) drawRoundRect(Brush.verticalGradient(listOf(cols.a.copy(alpha = 0.35f), cols.a), startY = mid, endY = mid - diff * sc), Offset(x0, mid), Size(x1 - x0, -diff * sc), CornerRadius(3f))
                    px = g.minute.coerceAtMost(len); diff = g.home - g.away
                }
                drawLine(C.Line2, Offset(0f, mid), Offset(size.width, mid), 1f)
                if (f.now < len) drawLine(C.Live, Offset(size.width * f.now / len, 0f), Offset(size.width * f.now / len, size.height), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 5f)))
                f.goals.forEach { g ->
                    val gx = size.width * g.minute.coerceAtMost(len) / len
                    drawCircle(if (g.note == "a") cols.a else cols.h, 4.dp.toPx(), Offset(gx, if (g.note == "a") size.height - 6f else 6f))
                }
            }
            Ticks("0′", "${f.len / 2}′", "${f.len}′")
        }
        if (f.goals.size <= 14) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            f.goals.forEach { g ->
                Row(Modifier.clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = 0.05f)).border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(99.dp)).padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (g.note == "a") cols.a else cols.h))
                    Text("${g.minute.toInt()}′", style = body(10, FontWeight.Bold, C.Muted)); Text("${g.home}–${g.away}", style = body(12, FontWeight.ExtraBold))
                }
            }
        }
        val tot = f.now.toInt().coerceAtLeast(1); val dr = (tot - f.lead.h - f.lead.a).coerceAtLeast(0)
        TriBar(f.lead.h.toFloat(), dr.toFloat(), f.lead.a.toFloat(), cols)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("${f.lead.h}′", "${d.home} μπροστά", Modifier.weight(1f)); Tile("$dr′", "Ισόπαλο", Modifier.weight(1f)); Tile("${f.lead.a}′", "${d.away} μπροστά", Modifier.weight(1f))
        }
        if (maxOf(f.max.h, f.max.a) >= 2) Note("Μεγαλύτερη διαφορά: " + if (f.max.h >= f.max.a) "${d.home} +${f.max.h}" else "${d.away} +${f.max.a}")
    } else if (f.per.isNotEmpty()) Card(if (f.sets) "🏐" else "📊", if (f.sets) "Σετ προς σετ" else "Σκορ ανά περίοδο") {
        Vs(d, cols)
        val mx = f.per.maxOf { maxOf(it.h, it.a) }.coerceAtLeast(1).toFloat()
        var sh = 0; var sa = 0
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            f.per.forEach { p ->
                if (f.sets) { if (p.h > p.a) sh++ else if (p.a > p.h) sa++ } else { sh += p.h; sa += p.a }
                val wh = p.h > p.a; val wa = p.a > p.h
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(p.label, Modifier.width(32.dp).clip(RoundedCornerShape(7.dp)).background(Color.White.copy(alpha = 0.05f)).padding(vertical = 3.dp),
                        style = body(11, FontWeight.ExtraBold, C.Text2), textAlign = TextAlign.Center, maxLines = 1)
                    Text("${p.h}", Modifier.width(26.dp), style = body(14, if (wh) FontWeight.ExtraBold else FontWeight.Medium, if (wh) C.Text else C.Muted), textAlign = TextAlign.Center)
                    Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = 0.05f)), contentAlignment = Alignment.CenterEnd) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(p.h / mx).clip(RoundedCornerShape(99.dp)).background(cols.h.copy(alpha = if (wh) 1f else 0.45f)))
                    }
                    Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = 0.05f)), contentAlignment = Alignment.CenterStart) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(p.a / mx).clip(RoundedCornerShape(99.dp)).background(cols.a.copy(alpha = if (wa) 1f else 0.45f)))
                    }
                    Text("${p.a}", Modifier.width(26.dp), style = body(14, if (wa) FontWeight.ExtraBold else FontWeight.Medium, if (wa) C.Text else C.Muted), textAlign = TextAlign.Center)
                    Text("$sh–$sa", Modifier.widthIn(min = 46.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 6.dp, vertical = 3.dp),
                        style = body(12, FontWeight.ExtraBold), textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
        Note(if (f.sets) "Δεξιά: τα σετ μέχρι εκείνη τη στιγμή" else "Δεξιά: το σκορ στο τέλος κάθε περιόδου")
    }
}

/* ─────────── 🥅 Σκόρερ (χάντμπολ / πόλο / futsal) ─────────── */

@Composable
private fun ScorersCard(d: MatchDetail, s: gr.aekhub.app.data.Scorers, cols: Cols) = Card("🥅", "Σκόρερ") {
    val mx = (s.home + s.away).maxOfOrNull { it.goals }?.coerceAtLeast(1) ?: 1
    listOf(Triple(d.home, d.homeLogo, s.home to cols.h), Triple(d.away, d.awayLogo, s.away to cols.a)).filter { it.third.first.isNotEmpty() }.forEachIndexed { i, (team, logo, lc) ->
        if (i > 0) Divider()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Crest(logo, team, 22.dp); Text(team, style = body(14, FontWeight.ExtraBold)) }
        lc.first.forEachIndexed { k, r ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${k + 1}", Modifier.width(16.dp), style = body(11, FontWeight.ExtraBold, C.Muted), textAlign = TextAlign.Center)
                Text(r.name, Modifier.weight(1f), style = body(14, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.width(54.dp).height(6.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = 0.06f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(r.goals / mx.toFloat()).clip(RoundedCornerShape(99.dp)).background(lc.second))
                }
                Text(r.goals.toString(), Modifier.width(22.dp), style = body(15, FontWeight.ExtraBold, C.Gold), textAlign = TextAlign.End)
            }
        }
    }
}

/* ─────────── 🏀 Box score ─────────── */

@Composable
private fun BoxCard(d: MatchDetail, b: gr.aekhub.app.data.BoxScore) = Card("🏀", "Box score", src(b.src)) {
    listOf(Triple(d.home, d.homeLogo, b.home), Triple(d.away, d.awayLogo, b.away)).filter { it.third.isNotEmpty() }.forEachIndexed { i, (team, logo, list) ->
        if (i > 0) Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Crest(logo, team, 22.dp); Text(team, style = body(14, FontWeight.ExtraBold)) }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))) {
            val cols = listOf("ΛΕΠ", "ΠΟΝ", "ΡΙΜ", "ΑΣ")
            Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.04f)).padding(horizontal = 10.dp, vertical = 7.dp)) {
                Text("ΠΑΙΚΤΗΣ", Modifier.weight(1f), style = body(10, FontWeight.ExtraBold, C.Muted)); cols.forEach { Text(it, Modifier.width(36.dp), style = body(10, FontWeight.ExtraBold, C.Muted), textAlign = TextAlign.End) }
            }
            val best = listOf(list.maxOf { it.pts }, list.maxOf { it.reb }, list.maxOf { it.ast })
            list.forEach { r: BoxRow ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.05f)))
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.number, Modifier.width(24.dp), style = body(11, FontWeight.ExtraBold, C.Muted))
                    Text(r.name, Modifier.weight(1f), style = body(13, if (r.starter) FontWeight.ExtraBold else FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${r.min}′", Modifier.width(36.dp), style = body(13, color = C.Muted), textAlign = TextAlign.End)
                    listOf(r.pts, r.reb, r.ast).forEachIndexed { k, v ->
                        val lead = v == best[k] && v > 0
                        Text("$v", Modifier.width(36.dp), style = body(13, if (lead) FontWeight.ExtraBold else FontWeight.Medium, if (lead) C.Gold else C.Text), textAlign = TextAlign.End)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.03f)).padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text("Σύνολο", Modifier.weight(1f), style = body(13, FontWeight.ExtraBold, C.Text2)); Spacer(Modifier.width(36.dp))
                listOf(list.sumOf { it.pts }, list.sumOf { it.reb }, list.sumOf { it.ast }).forEach { Text("$it", Modifier.width(36.dp), style = body(13, FontWeight.ExtraBold, C.Text2), textAlign = TextAlign.End) }
            }
        }
    }
    Note("Με έντονο: η πρώτη πεντάδα · κίτρινο: ο καλύτερος της ομάδας")
}

/* ─────────── 📋 Φόρμα: πάτημα σε Ν/Ι/Η → ο αγώνας ─────────── */

private fun resInfo(r: String) = when (r) { "W" -> Triple("Ν", "ΝΙΚΗ", W); "L" -> Triple("Η", "ΗΤΤΑ", L); else -> Triple("Ι", "ΙΣΟΠΑΛΙΑ", D) }

@Composable
private fun FormCard(d: MatchDetail, h: gr.aekhub.app.data.H2H) = Card("📋", "Φόρμα", src(h.src)) {
    Text("👆 Πάτα σε έναν αγώνα για σκορ & λεπτομέρειες · ● ο πιο πρόσφατος", style = body(12, color = C.Muted))
    var open by remember(h) { mutableStateOf<Pair<Boolean, Int>?>(null) }
    listOf(Triple(true, d.home, d.homeLogo) to h.formHome, Triple(false, d.away, d.awayLogo) to h.formAway).filter { it.second.isNotEmpty() }.forEachIndexed { idx, (t, list) ->
        val (home, team, logo) = t
        if (idx > 0) Divider()
        val w = list.count { it.r == "W" }; val dr = list.count { it.r == "D" }; val l = list.count { it.r == "L" }
        var gf = 0; var ga = 0
        list.forEach { x -> val sc = x.sc.split("-").map { it.trim().toIntOrNull() ?: 0 } + listOf(0, 0); if (x.ha == "h") { gf += sc[0]; ga += sc[1] } else { gf += sc[1]; ga += sc[0] } }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Crest(logo, team, 26.dp)
            Text(team, Modifier.weight(1f), style = body(14, FontWeight.ExtraBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            listOf("${w}Ν" to W, "${dr}Ι" to D, "${l}Η" to L).forEach { (s, c) ->
                Text(s, Modifier.clip(RoundedCornerShape(7.dp)).background(Color.White.copy(alpha = 0.05f)).padding(horizontal = 7.dp, vertical = 2.dp), style = body(11, FontWeight.ExtraBold, c))
            }
        }
        val rev = list.reversed()
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            rev.forEachIndexed { i, x ->
                val (letter, _, c) = resInfo(x.r)
                val sc = x.sc.split("-").map { it.trim() }
                val our = if (x.ha == "h") "${sc.getOrElse(0) { "" }}–${sc.getOrElse(1) { "" }}" else "${sc.getOrElse(1) { "" }}–${sc.getOrElse(0) { "" }}"
                val last = i == rev.lastIndex; val sel = open == (home to i)
                Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(when { sel -> Color.White.copy(alpha = 0.1f); last -> C.Gold.copy(alpha = 0.07f); else -> Color.White.copy(alpha = 0.04f) })
                    .border(1.dp, when { sel -> Color.White.copy(alpha = 0.3f); last -> C.Gold.copy(alpha = 0.45f); else -> Color.White.copy(alpha = 0.08f) }, RoundedCornerShape(12.dp))
                    .clickable { open = if (sel) null else (home to i) }.padding(vertical = 8.dp)) {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(c), contentAlignment = Alignment.Center) { Text(letter, style = body(13, FontWeight.ExtraBold, Color.White)) }
                        Text(our, style = body(12, FontWeight.ExtraBold), maxLines = 1)
                    }
                    if (last) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(6.dp).clip(CircleShape).background(C.Gold))
                }
            }
        }
        val pick = open?.takeIf { it.first == home }?.let { rev.getOrNull(it.second) }
        AnimatedVisibility(pick != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            pick?.let { FormDetail(it, team, logo) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Γκολ $gf–$ga", style = body(12, FontWeight.Bold, C.Text2))
            if (d.sport == "football" || d.sport == "women_football") Text("Βαθμοί ${w * 3 + dr}/${list.size * 3}", style = body(12, FontWeight.Bold, C.Text2))
        }
    }
}

/** Ο αγώνας της φόρμας: αποτέλεσμα · ομάδες με λογότυπα · σκορ · ημέρα, έδρα, διοργάνωση */
@Composable
private fun FormDetail(x: FormItem, team: String, teamLogo: String) {
    val (_, word, c) = resInfo(x.r)
    val home = x.ha == "h"; val sc = x.sc.split("-").map { it.trim() }
    val hn = if (home) team else x.opp; val an = if (home) x.opp else team
    val hl = if (home) teamLogo else x.logo; val al = if (home) x.logo else teamLogo
    val date = runCatching { java.time.LocalDate.parse(x.d.take(10)) }.getOrNull()
    val meta = listOfNotNull(
        date?.let { listOf("Δευ", "Τρί", "Τετ", "Πέμ", "Παρ", "Σάβ", "Κυρ")[it.dayOfWeek.value - 1] + " ${it.dayOfMonth}/${it.monthValue}/${it.year % 100}" },
        if (home) "Εντός έδρας" else "Εκτός έδρας", x.comp.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xF7181714)).border(1.dp, c.copy(alpha = 0.5f), RoundedCornerShape(14.dp)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(word, Modifier.clip(RoundedCornerShape(99.dp)).background(c).padding(horizontal = 10.dp, vertical = 2.dp), style = body(11, FontWeight.ExtraBold, Color.White))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Crest(hl, hn, 32.dp); Text(hn, style = body(12, FontWeight.ExtraBold), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("${sc.getOrElse(0) { "" }} – ${sc.getOrElse(1) { "" }}", Modifier.padding(horizontal = 8.dp), style = body(24, FontWeight.ExtraBold))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Crest(al, an, 32.dp); Text(an, style = body(12, FontWeight.ExtraBold), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (meta.isNotBlank()) Text(meta, style = body(11, color = C.Muted), textAlign = TextAlign.Center)
    }
}

/* ─────────── 🆚 Προηγούμενες συναντήσεις ─────────── */

@Composable
private fun MeetingsCard(d: MatchDetail, h: gr.aekhub.app.data.H2H, cols: Cols) = Card("🆚", "Προηγούμενες συναντήσεις", src(h.src)) {
    val (w, dr, l) = Triple(h.summary.getOrElse(0) { 0 }, h.summary.getOrElse(1) { 0 }, h.summary.getOrElse(2) { 0 })
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Crest(d.homeLogo, d.home, 28.dp); Text("$w", style = body(30, FontWeight.ExtraBold, cols.h)); Text("Νίκες", style = body(11, FontWeight.Bold, C.Muted))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("$dr", style = body(22, FontWeight.ExtraBold, C.Text2)); Text("Ισοπαλίες", style = body(11, FontWeight.Bold, C.Muted)) }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            Text("Νίκες", style = body(11, FontWeight.Bold, C.Muted)); Text("$l", style = body(30, FontWeight.ExtraBold, cols.a)); Crest(d.awayLogo, d.away, 28.dp)
        }
    }
    TriBar(w.toFloat(), dr.toFloat(), l.toFloat(), cols)
    // γκολ από τη μεριά του γηπεδούχου αυτού του αγώνα
    var gh = 0; var ga = 0
    h.meetings.forEach { m -> if (m.home == d.home || (m.away != d.home && m.home != d.away)) { gh += m.sh; ga += m.sa } else { gh += m.sa; ga += m.sh } }
    val unit = when (d.sport) { "basketball" -> "πόντοι"; "volleyball" -> "σετ"; else -> "γκολ" }
    Text("${unit.replaceFirstChar { it.uppercase() }} $gh–$ga · Μ.Ο. ${String.format(Locale.US, "%.1f", (gh + ga).toFloat() / h.meetings.size.coerceAtLeast(1)).replace('.', ',')} $unit/αγώνα · ${h.meetings.size} αγώνες",
        Modifier.fillMaxWidth(), style = body(12, FontWeight.Bold, C.Text2), textAlign = TextAlign.Center)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        h.meetings.take(6).forEach { m ->
            val hw = m.sh > m.sa; val aw = m.sa > m.sh
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.035f)).border(1.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.width(62.dp)) {
                    Text(runCatching { java.time.LocalDate.parse(m.d.take(10)).let { "${it.dayOfMonth}/${it.monthValue}/${it.year % 100}" } }.getOrDefault(m.d), style = body(11, FontWeight.Bold, C.Text2))
                    if (m.comp.isNotBlank()) Text(m.comp, style = body(10, color = C.Muted), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(m.home, Modifier.weight(1f), style = body(13, if (hw) FontWeight.ExtraBold else FontWeight.Medium, if (hw) C.Text else C.Text2), textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Column(Modifier.clip(RoundedCornerShape(9.dp)).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 8.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row {
                        Text("${m.sh}", style = body(15, if (hw) FontWeight.ExtraBold else FontWeight.Medium, if (hw) C.Gold else C.Text2))
                        Text(" – ", style = body(15, color = C.Muted))
                        Text("${m.sa}", style = body(15, if (aw) FontWeight.ExtraBold else FontWeight.Medium, if (aw) C.Gold else C.Text2))
                    }
                    if (m.pen.isNotBlank()) Text("πέν. " + m.pen.replace("-", "–"), style = body(9, color = C.Muted))
                }
                Text(m.away, Modifier.weight(1f), style = body(13, if (aw) FontWeight.ExtraBold else FontWeight.Medium, if (aw) C.Text else C.Text2), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

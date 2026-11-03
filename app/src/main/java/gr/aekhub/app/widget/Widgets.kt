package gr.aekhub.app.widget

import gr.aekhub.app.ui.components.upperGr
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import gr.aekhub.app.R
import gr.aekhub.app.data.Api
import gr.aekhub.app.data.MatchCard
import gr.aekhub.app.data.PostCard
import gr.aekhub.app.data.Standing
import gr.aekhub.app.push.Notifs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.time.LocalDate

/* ═══════════════════════════ Δεδομένα ═══════════════════════════ */

/** Ενιαία εικόνα αγώνα για όλα τα widgets (από το widget-state ή από τη λίστα αγώνων). */
internal data class MV(
    val id: Long, val home: String, val away: String, val hlogo: String, val alogo: String,
    val sh: String, val sa: String, val live: Boolean, val finished: Boolean, val minute: String, val phase: String,
    val comp: String, val start: Long, val venue: String, val time: String,
) {
    val played get() = (live || finished) && sh.isNotBlank()
}

internal fun MatchCard.mv() = MV(id, home, away, homeLogo, awayLogo, scoreHome, scoreAway, live, finished, minute,
    phase.ifBlank { statusLabel }, competition, WidgetData.start(this), venue, time.take(5))

internal fun WState.mv() = MV(id, home, away, homeLogo, awayLogo, sh, sa, live, finished, minute, phase, comp, startMs, venue, hhmm(startMs))

private suspend fun logos(ctx: Context, urls: List<String>, size: Int): Map<String, Bitmap?> = coroutineScope {
    urls.filter { it.isNotBlank() }.distinct().associateWith { u -> async(Dispatchers.IO) { Notifs.crest(ctx, u, size) } }.mapValues { it.value.await() }
}

/** Ποιο widget διάλεξε ο χρήστης (κάθε άθλημα έχει δικό του receiver). */
private fun providerName(ctx: Context, id: GlanceId): String = runCatching {
    val wid = GlanceAppWidgetManager(ctx).getAppWidgetId(id)
    AppWidgetManager.getInstance(ctx).getAppWidgetInfo(wid)?.provider?.className.orEmpty()
}.getOrDefault("")

private fun sportsOf(cn: String): Set<String>? = when {
    cn.contains("Football") -> setOf("football", "women_football")
    cn.contains("Basketball") -> setOf("basketball")
    cn.contains("Volleyball") -> setOf("volleyball")
    cn.contains("Handball") -> setOf("handball")
    else -> null
}

private fun sportTitle(s: Set<String>?): String = when {
    s == null -> "Όλα τα αθλήματα"
    "football" in s -> "Ποδόσφαιρο"
    "basketball" in s -> "Μπάσκετ"
    "volleyball" in s -> "Βόλεϊ"
    "handball" in s -> "Χάντμπολ"
    else -> ""
}

/** Λίστες για τα widgets, με αποθήκευση για όταν δεν υπάρχει δίκτυο. */
object WidgetList {
    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("widget", Context.MODE_PRIVATE)
    private val ser = ListSerializer(MatchCard.serializer())

    suspend fun matches(ctx: Context, sports: Set<String>?): List<MatchCard> = withContext(Dispatchers.IO) {
        Api.init(ctx.applicationContext)
        val key = "list_" + (sports?.firstOrNull() ?: "all")
        val today = LocalDate.now(W.ATHENS)
        val fresh = runCatching {
            val all = Api.matches(today.toString(), today.plusDays(21).toString(), null, fresh = true).items
                .filter { it.aek.isNotEmpty() && !it.postponed && (sports == null || it.sport in sports) }
            all.filter { it.live } + all.filter { it.upcoming }.sortedBy { WidgetData.start(it) } +
                all.filter { it.finished && it.date.take(10) == today.toString() }
        }.getOrNull()
        if (fresh != null) { sp(ctx).edit().putString(key, Api.json.encodeToString(ser, fresh.take(8))).apply(); fresh.take(8) }
        else sp(ctx).getString(key, null)?.let { runCatching { Api.json.decodeFromString(ser, it) }.getOrNull() }.orEmpty()
    }
}

/* ═══════════════════════════ Ένας αγώνας ═══════════════════════════ */

/** Ο αγώνας σε όλο το πλαίσιο: σήματα, ονόματα, ώρα ή σκορ, ημέρα και αντίστροφη μέτρηση. */
@Composable
internal fun ColumnScope.MatchBody(m: MV, hl: Bitmap?, al: Bitmap?) {
    val size = LocalSize.current
    val w = size.width.value; val h = size.height.value
    val compact = w < 210f
    val logo = minOf(w / 3.3f, h / 2.7f).toInt().coerceIn(30, 78)
    val nameMax = if (compact) 10 else 18
    // Κεφαλίδα
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        when {
            m.live -> Pill("LIVE" + (if (m.minute.isNotBlank()) " · " + m.minute else ""), W.RED, W.TEXT, 10.5f)
            m.finished -> Pill("ΤΕΛΙΚΟ", W.GOLD_SOFT, W.GOLD, 10.5f)
            else -> Text("ΕΠΟΜΕΝΟΣ ΑΓΩΝΑΣ", style = ts(if (compact) 10.5f else 11.5f, W.GOLD, FontWeight.Bold, cond = true), maxLines = 1)
        }
        Spacer(GlanceModifier.width(6.dp))
        if (!compact) Text(m.comp, GlanceModifier.defaultWeight(), style = ts(10.5f, W.MUTED, FontWeight.Medium, TextAlign.End), maxLines = 1)
    }
    // Ομάδες
    Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Crest(hl, logo)
            Spacer(GlanceModifier.height(5.dp))
            Text(shortName(m.home, nameMax), style = ts(if (compact) 11.5f else 13.5f, W.TEXT, FontWeight.Bold, TextAlign.Center, cond = true), maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (m.played) Text(m.sh + " – " + m.sa, style = ts(if (compact) 26f else 38f, W.GOLD, FontWeight.Bold, cond = true))
            else Text(m.time.ifBlank { "VS" }, style = ts(if (compact) 24f else 34f, W.GOLD, FontWeight.Bold, cond = true))
            if (!compact && !m.live) Text(if (m.finished) dayDate(m.start) else dayShort(m.start) + " " + dateShort(m.start), style = ts(11f, W.TEXT2, FontWeight.Medium))
            if (!compact && m.live && m.phase.isNotBlank()) Text(m.phase, style = ts(11f, W.TEXT2, FontWeight.Medium))
        }
        Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Crest(al, logo)
            Spacer(GlanceModifier.height(5.dp))
            Text(shortName(m.away, nameMax), style = ts(if (compact) 11.5f else 13.5f, W.TEXT, FontWeight.Bold, TextAlign.Center, cond = true), maxLines = 1)
        }
    }
    // Βάση
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start) {
        if (compact) {
            when {
                m.live -> Text(m.phase, style = ts(10.5f, W.TEXT2, FontWeight.Medium), maxLines = 1)
                m.finished -> Text(dayShort(m.start) + " " + dateShort(m.start), style = ts(10.5f, W.TEXT2, FontWeight.Medium))
                else -> Pill(dayShort(m.start) + " " + dateShort(m.start) + " · " + countdown(m.start), W.GOLD_SOFT, W.GOLD, 10f)
            }
        } else {
            Text(m.venue.ifBlank { m.comp }, GlanceModifier.defaultWeight(), style = ts(11f, W.MUTED, FontWeight.Medium), maxLines = 1)
            if (!m.played) Pill(countdown(m.start), W.GOLD, W.INK, 10.5f)
        }
    }
}

class MatchWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        W.FS = context.resources.configuration.fontScale
        val st = WidgetData.current(context)
        val l = st?.let { logos(context, listOf(it.homeLogo, it.awayLogo), 200) }.orEmpty()
        provideContent {
            val m = st?.mv()
            Frame(openApp(context, match = m?.id ?: 0, tab = if (m == null) "matches" else ""), live = m?.live == true) {
                if (m == null) Empty("Ο επόμενος αγώνας της ΑΕΚ θα εμφανιστεί εδώ")
                else MatchBody(m, l[m.hlogo], l[m.alogo])
            }
        }
    }
}

class MatchWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = MatchWidget() }

@Composable
private fun ColumnScope.Empty(text: String) {
    Column(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(ImageProvider(R.drawable.hub_logo), null, GlanceModifier.width(40.dp).height(40.dp))
        Spacer(GlanceModifier.height(8.dp))
        Text(text, style = ts(11.5f, W.TEXT2, FontWeight.Medium, TextAlign.Center))
    }
}

/* ═══════════════════════════ Λίστα αγώνων ═══════════════════════════ */

class MatchesWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        W.FS = context.resources.configuration.fontScale
        val sports = sportsOf(providerName(context, id))
        val list = WidgetList.matches(context, sports)
        val l = logos(context, list.take(6).flatMap { listOf(it.homeLogo, it.awayLogo) }, 120)
        provideContent {
            val size = LocalSize.current
            val cap = ((size.height.value - 34f) / 50f).toInt().coerceIn(1, 6)
            val rows = list.take(cap).map { it.mv() }
            Frame(openApp(context, tab = "matches"), live = rows.any { it.live }, pad = 10) {
                Head("ΑΓΩΝΕΣ ΑΕΚ", sportTitle(sports), small = size.width.value < 230f)
                Spacer(GlanceModifier.height(7.dp))
                when {
                    rows.isEmpty() -> Empty("Δεν υπάρχουν αγώνες τις επόμενες μέρες")
                    rows.size == 1 && size.height.value >= 140f -> MatchBody(rows[0], l[rows[0].hlogo], l[rows[0].alogo])
                    else -> rows.forEachIndexed { i, m ->
                        if (i > 0) Spacer(GlanceModifier.height(5.dp))
                        MatchRow(context, m, l, size.width.value >= 230f)
                    }
                }
            }
        }
    }
}

/** Μια γραμμή αγώνα: ημέρα | δύο ομάδες η μία κάτω από την άλλη (σήμα, όνομα, σκορ) | ώρα. */
@Composable
private fun ColumnScope.MatchRow(ctx: Context, m: MV, l: Map<String, Bitmap?>, wide: Boolean) {
    Row(GlanceModifier.fillMaxWidth().defaultWeight().background(cp(W.CARD)).cornerRadius(14.dp).padding(horizontal = 10.dp, vertical = 4.dp)
        .clickable(openApp(ctx, match = m.id)), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.width(38.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                m.live -> { Text("LIVE", style = ts(9.5f, W.RED, FontWeight.Bold)); Text(m.minute.ifBlank { "•" }, style = ts(12.5f, W.TEXT, FontWeight.Bold, cond = true)) }
                m.finished -> { Text("ΤΕΛ", style = ts(9.5f, W.MUTED, FontWeight.Bold)); Text(dateShort(m.start), style = ts(12f, W.TEXT2, FontWeight.Bold, cond = true)) }
                else -> { Text(dayShort(m.start), style = ts(9.5f, W.GOLD, FontWeight.Bold, cond = true)); Text(dateShort(m.start), style = ts(12.5f, W.TEXT, FontWeight.Bold, cond = true)) }
            }
        }
        Spacer(GlanceModifier.width(8.dp))
        Box(GlanceModifier.width(1.dp).height(30.dp).background(cp(W.LINE))) {}
        Spacer(GlanceModifier.width(9.dp))
        Column(GlanceModifier.defaultWeight()) {
            TeamLine(m.home, l[m.hlogo], if (m.played) m.sh else "", lead = m.played && (m.sh.toIntOrNull() ?: 0) > (m.sa.toIntOrNull() ?: 0))
            Spacer(GlanceModifier.height(3.dp))
            TeamLine(m.away, l[m.alogo], if (m.played) m.sa else "", lead = m.played && (m.sa.toIntOrNull() ?: 0) > (m.sh.toIntOrNull() ?: 0))
        }
        if (!m.played) {
            Spacer(GlanceModifier.width(6.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(m.time, style = ts(17f, W.GOLD, FontWeight.Bold, cond = true))
                if (wide) Text(countdown(m.start), style = ts(9.5f, W.MUTED, FontWeight.Medium), maxLines = 1)
            }
        }
    }
}

@Composable
private fun TeamLine(name: String, logo: Bitmap?, score: String, lead: Boolean) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Crest(logo, 17)
        Spacer(GlanceModifier.width(7.dp))
        Text(name.upperGr(), GlanceModifier.defaultWeight(), style = ts(13f, W.TEXT, FontWeight.Bold, cond = true), maxLines = 1)
        if (score.isNotBlank()) Text(score, style = ts(14f, if (lead) W.GOLD else W.TEXT, FontWeight.Bold, cond = true))
    }
}

class MatchesAllReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = MatchesWidget() }
class MatchesFootballReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = MatchesWidget() }
class MatchesBasketballReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = MatchesWidget() }
class MatchesVolleyballReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = MatchesWidget() }
class MatchesHandballReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = MatchesWidget() }

/* ═══════════════════════════ Βαθμολογία ═══════════════════════════ */

class StandingsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        W.FS = context.resources.configuration.fontScale
        val basket = providerName(context, id).contains("Basketball")
        val st = withContext(Dispatchers.IO) {
            Api.init(context.applicationContext)
            val sp = context.applicationContext.getSharedPreferences("widget", Context.MODE_PRIVATE)
            val key = "stand_" + (if (basket) "b" else "f")
            val ser = Standing.serializer()
            val got = runCatching {
                val all = Api.standings(fresh = true).items.filter { t -> t.rows.any { it.aek } }
                (if (basket) all.firstOrNull { it.sport == "basketball" } else all.firstOrNull { it.sport == "football" || it.sport.isBlank() }) ?: all.firstOrNull()
            }.getOrNull()
            if (got != null) { sp.edit().putString(key, Api.json.encodeToString(ser, got)).apply(); got }
            else sp.getString(key, null)?.let { runCatching { Api.json.decodeFromString(ser, it) }.getOrNull() }
        }
        val l = logos(context, st?.rows?.map { it.logo }.orEmpty().take(16), 96)
        provideContent {
            val size = LocalSize.current
            val wide = size.width.value >= 230f
            Frame(openApp(context, tab = "standings"), pad = 10) {
                Head("ΒΑΘΜΟΛΟΓΙΑ", st?.league?.ifBlank { st.title }.orEmpty().ifBlank { if (basket) "Μπάσκετ" else "Ποδόσφαιρο" }, small = !wide)
                Spacer(GlanceModifier.height(6.dp))
                if (st == null || st.rows.isEmpty()) Empty("Η βαθμολογία θα εμφανιστεί εδώ")
                else {
                    val n = ((size.height.value - 58f) / 22f).toInt().coerceIn(3, 12).coerceAtMost(st.rows.size)
                    val rows = st.rows
                    val aek = rows.indexOfFirst { it.aek }.coerceAtLeast(0)
                    val shown = if (aek < n) rows.take(n) else rows.take(n - 1) + rows[aek]
                    // κεφαλίδα στηλών
                    Row(GlanceModifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("#", GlanceModifier.width(20.dp), style = ts(9.5f, W.MUTED, FontWeight.Bold))
                        Text("ΟΜΑΔΑ", GlanceModifier.defaultWeight(), style = ts(9.5f, W.MUTED, FontWeight.Bold))
                        if (wide) { Text("ΑΓ", GlanceModifier.width(26.dp), style = ts(9.5f, W.MUTED, FontWeight.Bold, TextAlign.Center)); Text("+/−", GlanceModifier.width(32.dp), style = ts(9.5f, W.MUTED, FontWeight.Bold, TextAlign.Center)) }
                        Text("Β", GlanceModifier.width(26.dp), style = ts(9.5f, W.GOLD, FontWeight.Bold, TextAlign.End))
                    }
                    Spacer(GlanceModifier.height(2.dp))
                    shown.forEach { r ->
                        val me = r.aek
                        val gd = (r.gf.toIntOrNull() ?: 0) - (r.ga.toIntOrNull() ?: 0)
                        Row(GlanceModifier.fillMaxWidth().defaultWeight().let { if (me) it.background(cp(W.GOLD_SOFT)).cornerRadius(9.dp) else it }.padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(r.pos.toString(), GlanceModifier.width(20.dp), style = ts(11.5f, if (me) W.GOLD else W.MUTED, FontWeight.Bold))
                            Crest(l[r.logo], 16)
                            Spacer(GlanceModifier.width(7.dp))
                            Text(r.name.upperGr(), GlanceModifier.defaultWeight(), style = ts(13f, if (me) W.GOLD else W.TEXT, FontWeight.Bold, cond = true), maxLines = 1)
                            if (wide) {
                                Text(r.played, GlanceModifier.width(26.dp), style = ts(11.5f, W.TEXT2, FontWeight.Medium, TextAlign.Center))
                                Text((if (gd > 0) "+" else "") + gd, GlanceModifier.width(32.dp), style = ts(11.5f, if (gd > 0) W.GOLD else W.TEXT2, FontWeight.Medium, TextAlign.Center))
                            }
                            Text(r.points, GlanceModifier.width(26.dp), style = ts(13.5f, if (me) W.GOLD else W.TEXT, FontWeight.Bold, TextAlign.End, cond = true))
                        }
                    }
                }
            }
        }
    }
}

class StandingsFootballReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = StandingsWidget() }
class StandingsBasketballReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = StandingsWidget() }

/* ═══════════════════════════ Νέα ═══════════════════════════ */

class NewsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        W.FS = context.resources.configuration.fontScale
        val items = withContext(Dispatchers.IO) {
            Api.init(context.applicationContext)
            val sp = context.applicationContext.getSharedPreferences("widget", Context.MODE_PRIVATE)
            val ser = ListSerializer(PostCard.serializer())
            val got = runCatching { Api.feed(null, 1, fresh = true).items.take(6) }.getOrNull()
            if (!got.isNullOrEmpty()) { sp.edit().putString("news", Api.json.encodeToString(ser, got)).apply(); got }
            else sp.getString("news", null)?.let { runCatching { Api.json.decodeFromString(ser, it) }.getOrNull() }.orEmpty()
        }
        // Φωτογραφία μόνο για το πρώτο άρθρο, μικρή (για να χωράει στο όριο μνήμης των widgets)
        val thumb = items.firstOrNull()?.image?.takeIf { it.isNotBlank() }?.let { u ->
            withContext(Dispatchers.IO) { Notifs.crest(context, u, 720) }?.let { b -> Bitmap.createScaledBitmap(b, 360, (360L * b.height / b.width.coerceAtLeast(1)).toInt().coerceAtLeast(1), true) }
        }
        provideContent {
            val size = LocalSize.current
            val wide = size.width.value >= 230f
            Frame(openApp(context, tab = "home"), pad = 10) {
                Head("ΝΕΑ", "AEK Hub", small = !wide)
                Spacer(GlanceModifier.height(7.dp))
                if (items.isEmpty()) Empty("Οι ειδήσεις θα εμφανιστούν εδώ")
                else {
                    val big = size.height.value >= 150f
                    val first = items.first()
                    val restH = size.height.value - 40f - (if (big) 92f else 0f)
                    val rest = items.drop(if (big) 1 else 0).take((restH / 46f).toInt().coerceIn(if (big) 0 else 1, 5))
                    if (big) Row(GlanceModifier.fillMaxWidth().height(86.dp).clickable(openApp(context, post = first.id)), verticalAlignment = Alignment.CenterVertically) {
                        if (thumb != null) {
                            Image(ImageProvider(thumb), null, GlanceModifier.width((size.width.value * 0.36f).dp).height(86.dp).cornerRadius(14.dp), contentScale = ContentScale.Crop)
                            Spacer(GlanceModifier.width(10.dp))
                        }
                        Column(GlanceModifier.defaultWeight()) {
                            if (first.category.isNotBlank()) Text(first.category.upperGr(), style = ts(9.5f, W.GOLD, FontWeight.Bold, cond = true), maxLines = 1)
                            Text(first.title, style = ts(14f, W.TEXT, FontWeight.Bold, cond = true), maxLines = 3)
                        }
                    }
                    rest.forEach { p ->
                        Spacer(GlanceModifier.height(5.dp))
                        Column(GlanceModifier.fillMaxWidth().defaultWeight().background(cp(W.CARD)).cornerRadius(12.dp).padding(horizontal = 10.dp, vertical = 5.dp)
                            .clickable(openApp(context, post = p.id)), verticalAlignment = Alignment.CenterVertically) {
                            if (p.category.isNotBlank()) Text(p.category.upperGr(), style = ts(9f, W.GOLD, FontWeight.Bold, cond = true), maxLines = 1)
                            Text(p.title, style = ts(12.5f, W.TEXT, FontWeight.Bold), maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

class NewsWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = NewsWidget() }

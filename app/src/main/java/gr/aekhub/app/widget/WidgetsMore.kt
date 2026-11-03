package gr.aekhub.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import gr.aekhub.app.MainActivity
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val INK = Color(0xFF0B0B0A)
private val PANEL = Color(0xFF151514)
private val LINE = Color(0xFF2C2C29)
private val GOLD = Color(0xFFF9CF1C)
private val TEXT = Color(0xFFF3F1E9)
private val MUTED = Color(0xFF9A968A)
private val RED = Color(0xFFFF453A)
private val ATHENS: ZoneId = ZoneId.of("Europe/Athens")

private fun cp(c: Color) = ColorProvider(c)
private fun ts(size: Int, c: Color = TEXT, w: FontWeight = FontWeight.Bold, a: TextAlign? = null) =
    TextStyle(color = cp(c), fontSize = size.sp, fontWeight = w, textAlign = a)

/** Ποιο άθλημα δείχνει κάθε widget: από την κλάση του receiver που διάλεξε ο χρήστης. */
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
    s == null -> "ΟΛΑ ΤΑ ΑΘΛΗΜΑΤΑ"
    "football" in s -> "ΠΟΔΟΣΦΑΙΡΟ"
    "basketball" in s -> "ΜΠΑΣΚΕΤ"
    "volleyball" in s -> "ΒΟΛΕΪ"
    "handball" in s -> "ΧΑΝΤΜΠΟΛ"
    else -> ""
}

private fun openIntent(ctx: Context, match: Long = 0, tab: String = "") = actionStartActivity(Intent(ctx, MainActivity::class.java).apply {
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    if (match > 0) putExtra(MainActivity.EXTRA_MATCH, match)
    if (tab.isNotEmpty()) putExtra(MainActivity.EXTRA_TAB, tab)
})

@Composable
private fun Crest(b: Bitmap?, size: Int) {
    if (b != null) Image(ImageProvider(b), null, GlanceModifier.size(size.dp)) else Image(ImageProvider(R.drawable.hub_logo), null, GlanceModifier.size(size.dp))
}

@Composable
private fun Header(title: String, sub: String, big: Boolean) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(ImageProvider(R.drawable.hub_logo), null, GlanceModifier.size(if (big) 18.dp else 14.dp))
        Spacer(GlanceModifier.width(6.dp))
        Text(title, style = ts(if (big) 11 else 9, GOLD, FontWeight.Bold), maxLines = 1)
        if (sub.isNotBlank()) { Spacer(GlanceModifier.width(6.dp)); Text(sub, style = ts(if (big) 10 else 9, MUTED, FontWeight.Medium), maxLines = 1) }
    }
}

/* ────────────────────────── Λίστα αγώνων (ανά άθλημα) ────────────────────────── */

class MatchesWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(180.dp, 110.dp), DpSize(250.dp, 110.dp), DpSize(250.dp, 180.dp), DpSize(250.dp, 280.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val sports = sportsOf(providerName(context, id))
        val list = WidgetList.matches(context, sports)
        val logos: Map<String, Bitmap?> = coroutineScope {
            list.flatMap { listOf(it.homeLogo, it.awayLogo) }.filter { it.isNotBlank() }.distinct()
                .associateWith { u -> async(Dispatchers.IO) { Notifs.crest(context, u, 120) } }.mapValues { it.value.await() }
        }
        provideContent { MatchesContent(context, sports, list, logos) }
    }
}

private fun whenOf(m: MatchCard): String = WidgetData.start(m).takeIf { it > 0 }?.let {
    Instant.ofEpochMilli(it).atZone(ATHENS).format(DateTimeFormatter.ofPattern("EEE d/M", Locale("el"))).replaceFirstChar { c -> c.uppercase() }
}.orEmpty()

@Composable
private fun MatchesContent(ctx: Context, sports: Set<String>?, list: List<MatchCard>, logos: Map<String, Bitmap?>) {
    val size = LocalSize.current
    val rows = (((size.height.value - 40f) / 50f).toInt()).coerceIn(1, 6)
    val wide = size.width.value >= 240f
    Column(GlanceModifier.fillMaxSize().background(cp(PANEL)).cornerRadius(26.dp).padding(12.dp)) {
        Box(GlanceModifier.fillMaxWidth().clickable(openIntent(ctx, tab = "matches"))) { Header("ΑΓΩΝΕΣ", sportTitle(sports), wide) }
        Spacer(GlanceModifier.height(6.dp))
        if (list.isEmpty()) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Δεν υπάρχουν αγώνες τις επόμενες μέρες", style = ts(11, MUTED, FontWeight.Medium, TextAlign.Center))
            }
        } else list.take(rows).forEachIndexed { i, m ->
            if (i > 0) Box(GlanceModifier.fillMaxWidth().height(1.dp).background(cp(LINE))) {}
            Row(GlanceModifier.fillMaxWidth().padding(vertical = 5.dp).clickable(openIntent(ctx, match = m.id)), verticalAlignment = Alignment.CenterVertically) {
                Crest(logos[m.homeLogo], if (wide) 26 else 22)
                Spacer(GlanceModifier.width(3.dp))
                Crest(logos[m.awayLogo], if (wide) 26 else 22)
                Spacer(GlanceModifier.width(8.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(m.home + " – " + m.away, style = ts(if (wide) 12 else 11, TEXT, FontWeight.Bold), maxLines = 1)
                    Text((if (sports == null) m.sportLabel.ifBlank { "" } + " · " else "") + whenOf(m), style = ts(10, MUTED, FontWeight.Medium), maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    if ((m.live || m.finished) && m.scoreHome.isNotBlank()) Text(m.scoreHome + "–" + m.scoreAway, style = ts(if (wide) 17 else 15, GOLD, FontWeight.Bold))
                    else Text(m.time.take(5), style = ts(if (wide) 17 else 15, GOLD, FontWeight.Bold))
                    if (m.live) Text("LIVE" + (if (m.minute.isNotBlank()) " " + m.minute else ""), style = ts(9, RED, FontWeight.Bold))
                    else if (m.finished) Text("ΤΕΛΙΚΟ", style = ts(9, MUTED, FontWeight.Medium))
                }
            }
        }
    }
}

/** Λίστες αγώνων: ανάκτηση + αποθήκευση για offline. */
object WidgetList {
    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("widget", Context.MODE_PRIVATE)
    private val ser = ListSerializer(MatchCard.serializer())

    suspend fun matches(ctx: Context, sports: Set<String>?): List<MatchCard> = withContext(Dispatchers.IO) {
        Api.init(ctx.applicationContext)
        val key = "list_" + (sports?.firstOrNull() ?: "all")
        val today = LocalDate.now(ATHENS)
        val fresh = runCatching {
            Api.matches(today.toString(), today.plusDays(14).toString(), null, fresh = true).items
                .filter { it.aek.isNotEmpty() && !it.postponed && (sports == null || it.sport in sports) }
                .let { all ->
                    val live = all.filter { it.live }
                    val up = all.filter { it.upcoming }.sortedBy { WidgetData.start(it) }
                    val done = all.filter { it.finished && it.date.take(10) == today.toString() }
                    live + up + done
                }
        }.getOrNull()
        if (fresh != null) { sp(ctx).edit().putString(key, Api.json.encodeToString(ser, fresh.take(8))).apply(); fresh.take(8) }
        else sp(ctx).getString(key, null)?.let { runCatching { Api.json.decodeFromString(ser, it) }.getOrNull() }.orEmpty()
    }
}

class MatchesAllReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = MatchesWidget() }
class MatchesFootballReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = MatchesWidget() }
class MatchesBasketballReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = MatchesWidget() }
class MatchesVolleyballReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = MatchesWidget() }
class MatchesHandballReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = MatchesWidget() }

/* ────────────────────────── Βαθμολογία ────────────────────────── */

class StandingsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(180.dp, 110.dp), DpSize(250.dp, 180.dp), DpSize(250.dp, 280.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val cn = providerName(context, id)
        val basket = cn.contains("Basketball")
        val st = withContext(Dispatchers.IO) {
            Api.init(context.applicationContext)
            val sp = context.applicationContext.getSharedPreferences("widget", Context.MODE_PRIVATE)
            val key = "stand_" + (if (basket) "b" else "f")
            val ser = Standing.serializer()
            val got = runCatching {
                Api.standings(fresh = true).items.filter { t -> t.rows.any { it.aek } && (if (basket) t.sport == "basketball" else t.sport == "football" || t.sport.isBlank()) }.firstOrNull()
                    ?: Api.standings().items.firstOrNull { t -> t.rows.any { it.aek } && (if (basket) t.sport == "basketball" else true) }
            }.getOrNull()
            if (got != null) { sp.edit().putString(key, Api.json.encodeToString(ser, got)).apply(); got }
            else sp.getString(key, null)?.let { runCatching { Api.json.decodeFromString(ser, it) }.getOrNull() }
        }
        val logos: Map<String, Bitmap?> = coroutineScope {
            st?.rows?.take(10)?.map { it.logo }?.filter { it.isNotBlank() }?.distinct().orEmpty()
                .associateWith { u -> async(Dispatchers.IO) { Notifs.crest(context, u, 96) } }.mapValues { it.value.await() }
        }
        provideContent { StandingsContent(context, st, logos, if (basket) "ΜΠΑΣΚΕΤ" else "ΠΟΔΟΣΦΑΙΡΟ") }
    }
}

@Composable
private fun StandingsContent(ctx: Context, st: Standing?, logos: Map<String, Bitmap?>, sportName: String) {
    val size = LocalSize.current
    val n = (((size.height.value - 44f) / 26f).toInt()).coerceIn(3, 9)
    Column(GlanceModifier.fillMaxSize().background(cp(PANEL)).cornerRadius(26.dp).padding(12.dp).clickable(openIntent(ctx, tab = "standings"))) {
        Header("ΒΑΘΜΟΛΟΓΙΑ", st?.league?.ifBlank { sportName } ?: sportName, size.width.value >= 240f)
        Spacer(GlanceModifier.height(6.dp))
        if (st == null || st.rows.isEmpty()) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Η βαθμολογία θα εμφανιστεί εδώ", style = ts(11, MUTED, FontWeight.Medium, TextAlign.Center)) }
        } else {
            val rows = st.rows
            val aekIdx = rows.indexOfFirst { it.aek }.coerceAtLeast(0)
            // Πρώτες θέσεις· αν η ΑΕΚ είναι πιο κάτω, μπαίνει αυτή στην τελευταία γραμμή
            val shown = if (aekIdx < n) rows.take(n) else rows.take(n - 1) + rows[aekIdx]
            shown.forEach { r ->
                val me = r.aek
                Row(GlanceModifier.fillMaxWidth().height(24.dp).let { if (me) it.background(cp(Color(0x33F9CF1C))).cornerRadius(8.dp) else it }.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.pos.toString(), GlanceModifier.width(18.dp), style = ts(11, if (me) GOLD else MUTED, FontWeight.Bold))
                    Crest(logos[r.logo], 16)
                    Spacer(GlanceModifier.width(6.dp))
                    Text(r.name, GlanceModifier.defaultWeight(), style = ts(12, if (me) GOLD else TEXT, FontWeight.Bold), maxLines = 1)
                    Text(r.points, style = ts(13, if (me) GOLD else TEXT, FontWeight.Bold))
                }
            }
        }
    }
}

class StandingsFootballReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = StandingsWidget() }
class StandingsBasketballReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = StandingsWidget() }

/* ────────────────────────── Νέα ────────────────────────── */

class NewsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(180.dp, 110.dp), DpSize(250.dp, 180.dp), DpSize(250.dp, 280.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val items = withContext(Dispatchers.IO) {
            Api.init(context.applicationContext)
            val sp = context.applicationContext.getSharedPreferences("widget", Context.MODE_PRIVATE)
            val ser = ListSerializer(PostCard.serializer())
            val got = runCatching { Api.feed(null, 1, fresh = true).items.take(6) }.getOrNull()
            if (!got.isNullOrEmpty()) { sp.edit().putString("news", Api.json.encodeToString(ser, got)).apply(); got }
            else sp.getString("news", null)?.let { runCatching { Api.json.decodeFromString(ser, it) }.getOrNull() }.orEmpty()
        }
        provideContent { NewsContent(context, items) }
    }
}

@Composable
private fun NewsContent(ctx: Context, items: List<PostCard>) {
    val size = LocalSize.current
    val n = (((size.height.value - 40f) / 52f).toInt()).coerceIn(1, 5)
    Column(GlanceModifier.fillMaxSize().background(cp(PANEL)).cornerRadius(26.dp).padding(12.dp)) {
        Box(GlanceModifier.fillMaxWidth().clickable(openIntent(ctx, tab = "home"))) { Header("ΝΕΑ", "AEK Hub", size.width.value >= 240f) }
        Spacer(GlanceModifier.height(6.dp))
        if (items.isEmpty()) Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Τα νέα θα εμφανιστούν εδώ", style = ts(11, MUTED, FontWeight.Medium)) }
        else items.take(n).forEachIndexed { i, p ->
            if (i > 0) Box(GlanceModifier.fillMaxWidth().height(1.dp).background(cp(LINE))) {}
            Column(GlanceModifier.fillMaxWidth().padding(vertical = 5.dp).clickable(actionStartActivity(Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP; putExtra(MainActivity.EXTRA_POST, p.id)
            }))) {
                if (p.category.isNotBlank()) Text(p.category.uppercase(), style = ts(9, GOLD, FontWeight.Bold), maxLines = 1)
                Text(p.title, style = ts(12, TEXT, FontWeight.Bold), maxLines = 2)
            }
        }
    }
}

class NewsWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = NewsWidget() }

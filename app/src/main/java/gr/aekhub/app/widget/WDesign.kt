package gr.aekhub.app.widget

import gr.aekhub.app.ui.components.upperGr
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
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
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import gr.aekhub.app.MainActivity
import gr.aekhub.app.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Κοινό «σύστημα» των widgets: χρώματα, γραμματοσειρές, φόντο, κεφαλίδα, ετικέτες. */
internal object W {
    val INK = Color(0xFF0B0B0A)
    val CARD = Color(0x14FFFFFF)
    val LINE = Color(0x22FFFFFF)
    val GOLD = Color(0xFFF9CF1C)
    val GOLD_SOFT = Color(0x33F9CF1C)
    val TEXT = Color(0xFFF3F1E9)
    val TEXT2 = Color(0xFFC8C5BA)
    val MUTED = Color(0xFF9A968A)
    val RED = Color(0xFFFF453A)
    val ATHENS: ZoneId = ZoneId.of("Europe/Athens")
    val COND = FontFamily("sans-serif-condensed")
    /** Μέγεθος γραμματοσειράς συστήματος: τα widgets μένουν στο 100% ώστε να μη σπάνε (μπαίνει σε κάθε provideGlance). */
    @Volatile var FS = 1f
}

internal fun cp(c: Color) = ColorProvider(c)

internal fun ts(size: Float, c: Color = W.TEXT, w: FontWeight = FontWeight.Bold, a: TextAlign? = null, cond: Boolean = false) =
    TextStyle(color = cp(c), fontSize = (size / W.FS.coerceAtLeast(1f)).sp, fontWeight = w, textAlign = a, fontFamily = if (cond) W.COND else null)

internal fun openApp(ctx: Context, match: Long = 0, post: Long = 0, tab: String = ""): Action = actionStartActivity(Intent(ctx, MainActivity::class.java).apply {
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    if (match > 0) putExtra(MainActivity.EXTRA_MATCH, match)
    if (post > 0) putExtra(MainActivity.EXTRA_POST, post)
    if (tab.isNotEmpty()) putExtra(MainActivity.EXTRA_TAB, tab)
})

/** Πλαίσιο κάθε widget: σκούρο φόντο με χρυσή λάμψη (κόκκινη όταν είναι live), στρογγυλές γωνίες. */
@Composable
internal fun Frame(onClick: Action, live: Boolean = false, pad: Int = 12, content: @Composable ColumnScope.() -> Unit) {
    Box(GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(cp(W.INK)).clickable(onClick)) {
        Image(ImageProvider(if (live) R.drawable.w_bg_live else R.drawable.w_bg), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Column(GlanceModifier.fillMaxSize().padding(pad.dp)) { content() }
    }
}

/** Κεφαλίδα: λογότυπο hub + τίτλος (κίτρινο) + υπότιτλος. */
@Composable
internal fun Head(title: String, sub: String = "", right: String = "", small: Boolean = false) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(ImageProvider(R.drawable.hub_logo), null, GlanceModifier.size(if (small) 15.dp else 18.dp))
        Spacer(GlanceModifier.width(6.dp))
        Text(title, style = ts(if (small) 11f else 12.5f, W.GOLD, FontWeight.Bold, cond = true), maxLines = 1)
        if (sub.isNotBlank()) {
            Spacer(GlanceModifier.width(6.dp))
            Text(sub, GlanceModifier.defaultWeight(), style = ts(if (small) 10f else 11f, W.MUTED, FontWeight.Medium), maxLines = 1)
        } else Spacer(GlanceModifier.defaultWeight())
        if (right.isNotBlank()) Text(right, style = ts(10.5f, W.MUTED, FontWeight.Medium), maxLines = 1)
    }
}

@Composable
internal fun Pill(text: String, bg: Color, fg: Color, size: Float = 10f) {
    Box(GlanceModifier.background(cp(bg)).cornerRadius(9.dp).padding(horizontal = 7.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
        Text(text, style = ts(size, fg, FontWeight.Bold), maxLines = 1)
    }
}

@Composable
internal fun Crest(b: Bitmap?, size: Int) {
    if (b != null) Image(ImageProvider(b), null, GlanceModifier.size(size.dp))
    else Box(GlanceModifier.size(size.dp).background(cp(W.CARD)).cornerRadius((size / 2).dp)) {}
}

internal fun dayShort(ms: Long): String = if (ms <= 0) "" else
    Instant.ofEpochMilli(ms).atZone(W.ATHENS).format(DateTimeFormatter.ofPattern("EEE", Locale("el"))).upperGr().replace(".", "")
internal fun dateShort(ms: Long): String = if (ms <= 0) "" else
    Instant.ofEpochMilli(ms).atZone(W.ATHENS).format(DateTimeFormatter.ofPattern("d/M"))
internal fun dayDate(ms: Long): String = if (ms <= 0) "" else
    Instant.ofEpochMilli(ms).atZone(W.ATHENS).format(DateTimeFormatter.ofPattern("EEEE d/M", Locale("el"))).replaceFirstChar { it.uppercase() }
internal fun hhmm(ms: Long): String = if (ms <= 0) "" else
    Instant.ofEpochMilli(ms).atZone(W.ATHENS).format(DateTimeFormatter.ofPattern("HH:mm"))

/** «σε 45′», «σε 5 ώρες», «αύριο», «σε 3 μέρες». */
internal fun countdown(ms: Long): String {
    if (ms <= 0) return ""
    val mins = (ms - System.currentTimeMillis()) / 60_000
    val today = java.time.LocalDate.now(W.ATHENS)
    val day = Instant.ofEpochMilli(ms).atZone(W.ATHENS).toLocalDate()
    return when {
        mins <= 0 -> "ξεκινά τώρα"
        mins < 60 -> "σε ${mins}′"
        day == today -> "σε ${mins / 60} ώρ${if (mins / 60 == 1L) "α" else "ες"}"
        day == today.plusDays(1) -> "αύριο"
        else -> "σε ${java.time.temporal.ChronoUnit.DAYS.between(today, day)} μέρες"
    }
}

/** Μικρό όνομα ομάδας για στενά widgets («Falco Szombathely» → «Falco»). */
internal fun shortName(n: String, max: Int): String {
    if (n.length <= max) return n
    val first = n.split(' ').firstOrNull().orEmpty()
    return if (first.length in 3..max) first else n.take(max - 1) + "…"
}

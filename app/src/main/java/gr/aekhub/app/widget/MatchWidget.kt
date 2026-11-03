package gr.aekhub.app.widget

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
import gr.aekhub.app.push.Notifs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val INK = Color(0xFF0B0B0A)
private val PANEL = Color(0xFF151514)
private val GOLD = Color(0xFFF9CF1C)
private val TEXT = Color(0xFFF3F1E9)
private val MUTED = Color(0xFF9A968A)
private val RED = Color(0xFFFF453A)

private val SMALL = DpSize(110.dp, 110.dp)
private val WIDE = DpSize(250.dp, 110.dp)
private val TALL = DpSize(250.dp, 250.dp)

/**
 * Widget «ΑΕΚ»: ο ζωντανός αγώνας (σκορ + λεπτό), αλλιώς ο επόμενος (ημέρα, ώρα), αλλιώς το τελευταίο αποτέλεσμα.
 * Μικρό (2×2): σήματα + σκορ/ώρα. Μεσαίο/μεγάλο: και ονόματα, διοργάνωση, γήπεδο.
 * Ενημερώνεται: κάθε 30′, από κάθε ειδοποίηση αγώνα, και όταν ανοίγεις το app.
 */
class MatchWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val st = WidgetData.current(context)
        val (hl, al) = withContext(Dispatchers.IO) {
            st?.let { Notifs.crest(context, it.homeLogo, 160) to Notifs.crest(context, it.awayLogo, 160) } ?: (null to null)
        }
        provideContent { Content(context, st, hl, al) }
    }
}

class MatchWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MatchWidget()
}

private fun cp(c: Color) = ColorProvider(c)
private fun ts(size: Int, c: Color = TEXT, w: FontWeight = FontWeight.Bold, a: TextAlign? = null) =
    TextStyle(color = cp(c), fontSize = size.sp, fontWeight = w, textAlign = a)

@Composable
private fun Crest(b: Bitmap?, size: Int) {
    if (b != null) Image(ImageProvider(b), null, GlanceModifier.size(size.dp))
    else Image(ImageProvider(R.drawable.hub_logo), null, GlanceModifier.size(size.dp))
}

private fun whenText(st: WState): String = if (st.startMs <= 0) "" else
    Instant.ofEpochMilli(st.startMs).atZone(ZoneId.of("Europe/Athens")).format(DateTimeFormatter.ofPattern("EEE d/M", Locale("el"))).replaceFirstChar { it.uppercase() }

private fun timeText(st: WState): String = if (st.startMs <= 0) "" else
    Instant.ofEpochMilli(st.startMs).atZone(ZoneId.of("Europe/Athens")).format(DateTimeFormatter.ofPattern("HH:mm"))

private fun inText(st: WState): String {
    if (st.startMs <= 0) return ""
    val mins = (st.startMs - System.currentTimeMillis()) / 60_000
    return when {
        mins <= 0 -> ""
        mins < 90 -> "σε ${mins}′"
        mins < 36 * 60 -> "σε ${mins / 60} ώρ."
        else -> "σε ${mins / 1440} μέρ."
    }
}

@Composable
private fun Content(ctx: Context, st: WState?, hl: Bitmap?, al: Bitmap?) {
    val size = LocalSize.current
    val big = size.width >= WIDE.width
    val tall = size.height >= TALL.height
    val open = actionStartActivity(Intent(ctx, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        if (st != null && st.id > 0) putExtra(MainActivity.EXTRA_MATCH, st.id) else putExtra(MainActivity.EXTRA_TAB, "matches")
    })
    Box(GlanceModifier.fillMaxSize().background(cp(PANEL)).cornerRadius(26.dp).clickable(open).padding(if (big) 14.dp else 10.dp)) {
        if (st == null) {
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                Image(ImageProvider(R.drawable.hub_logo), null, GlanceModifier.size(if (big) 44.dp else 34.dp))
                Spacer(GlanceModifier.height(6.dp))
                Text("Ο επόμενος αγώνας θα εμφανιστεί εδώ", style = ts(11, MUTED, FontWeight.Medium, TextAlign.Center))
            }
        } else {
            val head = when {
                st.live -> "LIVE" + (if (st.minute.isNotBlank()) " · ${st.minute}" else "")
                st.finished -> "ΤΕΛΙΚΟ"
                else -> "ΕΠΟΜΕΝΟΣ ΑΓΩΝΑΣ"
            }
            Column(GlanceModifier.fillMaxSize()) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(head, style = ts(if (big) 11 else 9, if (st.live) RED else GOLD, FontWeight.Bold))
                    if (big && st.comp.isNotBlank()) { Spacer(GlanceModifier.width(8.dp)); Text(st.comp, style = ts(10, MUTED, FontWeight.Medium), maxLines = 1) }
                }
                Spacer(GlanceModifier.defaultWeight())
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Crest(hl, if (big) 52 else 38)
                        if (big) { Spacer(GlanceModifier.height(4.dp)); Text(st.home, style = ts(12, TEXT, FontWeight.Bold, TextAlign.Center), maxLines = 1) }
                    }
                    Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (st.hasScore && (st.live || st.finished)) Text(st.sh + "–" + st.sa, style = ts(if (big) 34 else 24, GOLD, FontWeight.Bold))
                        else {
                            Text(timeText(st).ifBlank { "VS" }, style = ts(if (big) 30 else 22, GOLD, FontWeight.Bold))
                        }
                    }
                    Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Crest(al, if (big) 52 else 38)
                        if (big) { Spacer(GlanceModifier.height(4.dp)); Text(st.away, style = ts(12, TEXT, FontWeight.Bold, TextAlign.Center), maxLines = 1) }
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
                val foot = when {
                    st.live -> st.phase
                    st.finished -> whenText(st)
                    else -> listOf(whenText(st), inText(st)).filter { it.isNotBlank() }.joinToString(" · ")
                }
                if (foot.isNotBlank()) Text(foot, GlanceModifier.fillMaxWidth(), style = ts(if (big) 12 else 10, MUTED, FontWeight.Medium, TextAlign.Center), maxLines = 1)
                if (tall && st.venue.isNotBlank()) Text(st.venue, GlanceModifier.fillMaxWidth().padding(top = 4.dp), style = ts(11, MUTED, FontWeight.Medium, TextAlign.Center), maxLines = 1)
            }
        }
    }
}

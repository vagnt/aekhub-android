package gr.aekhub.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.aekhub.app.data.Api
import gr.aekhub.app.data.MatchCard
import gr.aekhub.app.live.Live
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body
import gr.aekhub.app.ui.theme.disp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

private data class Flash(val m: MatchCard, val title: String, val aekGoal: Boolean, val at: Long = System.nanoTime())

/** Αθλήματα όπου κάθε σκορ είναι «γεγονός» (στο μπάσκετ θα ήταν συνέχεια). */
private val GOAL_SPORTS = setOf("football", "women_football", "futsal", "waterpolo")

/**
 * Banner πάνω-πάνω, σε όποια οθόνη κι αν είσαι (και μέσα σε άρθρο):
 * γκολ, έναρξη και τελικό στους αγώνες της ΑΕΚ, μόλις συμβούν.
 */
@Composable
fun LiveScoreBanner(openMatch: (Long) -> Unit) {
    var flash by remember { mutableStateOf<Flash?>(null) }
    LaunchedEffect(Unit) {
        val prev = HashMap<Long, Pair<String, String>>() // id → (σκορ, κατάσταση)
        var first = true
        var fresh = false
        while (true) {
            val today = LocalDate.now(ATHENS).toString()
            val list = runCatching { Api.matches(today, today, null, fresh).items.filter { it.aek.isNotEmpty() } }.getOrNull()
            if (list != null) {
                for (m in list) {
                    val score = "${m.scoreHome}-${m.scoreAway}"
                    val old = prev[m.id]
                    prev[m.id] = score to m.status
                    if (first || old == null) continue
                    val (oldScore, oldStatus) = old
                    val oh = oldScore.substringBefore('-').toIntOrNull() ?: 0; val oa = oldScore.substringAfter('-').toIntOrNull() ?: 0
                    val nh = m.scoreHome.toIntOrNull() ?: 0; val na = m.scoreAway.toIntOrNull() ?: 0
                    val f = when {
                        m.sport in GOAL_SPORTS && (nh > oh || na > oa) -> {
                            val aekScored = (m.aek == "home" && nh > oh) || (m.aek == "away" && na > oa)
                            Flash(m, if (aekScored) "ΓΚΟΛ ΤΗΣ ΑΕΚ!" else "ΓΚΟΛ", aekScored)
                        }
                        m.sport in GOAL_SPORTS && (nh < oh || na < oa) -> Flash(m, "ΑΚΥΡΩΘΗΚΕ ΓΚΟΛ", false)
                        oldStatus != "finished" && m.status == "finished" -> Flash(m, "ΤΕΛΙΚΟ", m.aekWon == true)
                        (oldStatus == "upcoming" || oldStatus == "prematch") && m.live && m.status != "prematch" -> Flash(m, "ΞΕΚΙΝΗΣΕ!", false)
                        else -> null
                    }
                    if (f != null) flash = f
                }
                first = false
            }
            val anyLive = list?.any { it.live || it.status == "prematch" } == true
            // Περιμένει το «σήμα» του server ή, για σιγουριά, 15″ (live) / 90″
            fresh = withTimeoutOrNull(if (anyLive) 15_000L else 90_000L) { Live.ping.first() } != null
        }
    }
    LaunchedEffect(flash?.at) { if (flash != null) { delay(7_000); flash = null } }

    val f = flash
    AnimatedVisibility(
        visible = f != null,
        enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut(),
        modifier = Modifier.statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        val x = f ?: return@AnimatedVisibility
        val m = x.m
        val bg = if (x.aekGoal) Brush.horizontalGradient(listOf(C.Gold, Color(0xFFE0BA19))) else Brush.horizontalGradient(listOf(Color(0xFF2A1513), C.Panel))
        val fg = if (x.aekGoal) C.Ink else C.Text
        Row(
            Modifier.fillMaxWidth().shadow(16.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(bg)
                .border(1.5.dp, if (x.aekGoal) C.Ink.copy(alpha = 0.2f) else C.Live, RoundedCornerShape(18.dp))
                .clickable { flash = null; openMatch(m.id) }.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!x.aekGoal) LiveDot()
                    Text(x.title, style = disp(18, FontWeight.Black), color = fg)
                    val mn = liveMinute(m.clock, m.minute)
                    if (mn.isNotBlank()) Text(mn, style = body(12, FontWeight.ExtraBold, if (x.aekGoal) C.Ink else C.Gold))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Crest(m.homeLogo, m.home, 22.dp)
                    Text(m.home.upperGr(), Modifier.weight(1f, false), style = disp(15), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${m.scoreHome.ifBlank { "0" }} - ${m.scoreAway.ifBlank { "0" }}", style = disp(20, FontWeight.Black), color = fg)
                    Text(m.away.upperGr(), Modifier.weight(1f, false), style = disp(15), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Crest(m.awayLogo, m.away, 22.dp)
                }
                Text(matchMeta(m), style = body(10, FontWeight.ExtraBold, if (x.aekGoal) C.Ink.copy(alpha = 0.7f) else C.Muted).copy(letterSpacing = 0.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.size(36.dp).clip(CircleShape).clickable { flash = null }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, "Κλείσιμο", tint = fg, modifier = Modifier.size(18.dp))
            }
        }
    }
}

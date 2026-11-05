package gr.aekhub.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import gr.aekhub.app.data.Api
import gr.aekhub.app.data.MatchCard
import gr.aekhub.app.ui.components.Crest
import gr.aekhub.app.ui.components.FitText
import gr.aekhub.app.ui.components.upperGr
import gr.aekhub.app.ui.theme.AekTheme
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body
import gr.aekhub.app.ui.theme.disp
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 📌 «Ποιον αγώνα δείχνει το widget»: ανοίγει όταν προσθέτεις το widget «ΑΕΚ» ή με παρατεταμένο πάτημα → «Ρύθμιση».
 * «Αυτόματα» = live αγώνας, αλλιώς ο επόμενος· ή ένας συγκεκριμένος αγώνας (οποιουδήποτε αθλήματος, ως 90 μέρες μπροστά).
 */
class MatchWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val wid = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (wid == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, wid))
        Api.init(applicationContext)
        setContent { AekTheme { Screen(wid) } }
    }

    private fun done(wid: Int, matchId: Long, scope: kotlinx.coroutines.CoroutineScope) {
        WidgetData.setPin(this, wid, matchId)
        scope.launch {
            runCatching { MatchWidget().updateAll(applicationContext) }
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, wid))
            finish()
        }
    }

    @Composable
    private fun Screen(wid: Int) {
        val scope = rememberCoroutineScope()
        var list by remember { mutableStateOf<List<MatchCard>?>(null) }
        val cur = remember { WidgetData.pin(this, wid) }
        LaunchedEffect(Unit) {
            val today = LocalDate.now(W.ATHENS)
            list = runCatching {
                val all = Api.matches(today.toString(), today.plusDays(90).toString(), null).items.filter { it.aek.isNotEmpty() && !it.postponed && !it.finished }
                all.filter { it.live } + all.filter { !it.live }.sortedBy { WidgetData.start(it) }
            }.getOrDefault(emptyList())
        }
        Column(Modifier.fillMaxSize().background(C.Ink).safeDrawingPadding()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("ΠΟΙΟΝ ΑΓΩΝΑ ΔΕΙΧΝΕΙ ΤΟ WIDGET;", style = disp(22), color = C.Text)
                Text("Αλλάζει όποτε θες: κράτα πατημένο το widget → «Ρύθμιση».", style = body(13, color = C.Muted))
            }
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Option(cur == 0L, "⚡ Αυτόματα", "Ο αγώνας της ΑΕΚ που παίζεται τώρα, αλλιώς ο επόμενος. Αλλάζει μόνο του.") { done(wid, 0L, scope) }
                }
                val l = list
                if (l == null) item { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = C.Gold) } }
                else if (l.isEmpty()) item { Text("Δεν βρέθηκαν επόμενοι αγώνες.", Modifier.padding(16.dp), style = body(14, color = C.Muted)) }
                else items(l, key = { it.id }) { m -> MatchOption(m, cur == m.id) { done(wid, m.id, scope) } }
            }
        }
    }

    @Composable
    private fun Option(on: Boolean, title: String, sub: String, onClick: () -> Unit) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Panel).border(if (on) 2.dp else 1.dp, if (on) C.Gold else C.Line, RoundedCornerShape(16.dp))
                .clickable(onClick = onClick).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = body(16, FontWeight.ExtraBold, if (on) C.Gold else C.Text))
            Text(sub, style = body(12, color = C.Muted))
        }
    }

    @Composable
    private fun MatchOption(m: MatchCard, on: Boolean, onClick: () -> Unit) {
        val ms = WidgetData.start(m)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Panel).border(if (on) 2.dp else 1.dp, if (on) C.Gold else C.Line, RoundedCornerShape(16.dp))
                .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(58.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (m.live) "LIVE" else dayShort(ms), style = body(11, FontWeight.ExtraBold, if (m.live) C.Live else C.Gold))
                Text(if (m.live) m.minute.ifBlank { "•" } else dateShort(ms), style = body(15, FontWeight.ExtraBold, C.Text))
                if (!m.live) Text(m.time.take(5), style = body(11, FontWeight.Bold, C.Muted))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(listOf(m.sportLabel, m.competition).filter { it.isNotBlank() }.joinToString(" · ").upperGr(), style = body(10, FontWeight.ExtraBold, C.Gold), maxLines = 1)
                Team(m.homeLogo, m.home, m.aek == "home")
                Team(m.awayLogo, m.away, m.aek == "away")
            }
            if (on) Text("✓", style = body(20, FontWeight.ExtraBold, C.Gold))
        }
    }

    @Composable
    private fun Team(logo: String, name: String, aek: Boolean) = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Crest(logo, name, 20.dp)
        FitText(name.upperGr(), style = disp(15), color = if (aek) C.Gold else C.Text, minSize = 11f)
    }
}

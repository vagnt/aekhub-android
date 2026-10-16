package gr.aekhub.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import gr.aekhub.app.LocalPrefs
import gr.aekhub.app.data.Api
import gr.aekhub.app.ads.BannerAd
import gr.aekhub.app.data.AppConfig
import gr.aekhub.app.data.MatchCard
import gr.aekhub.app.data.Settings
import gr.aekhub.app.ui.components.ChipRow
import gr.aekhub.app.ui.components.ErrorBox
import gr.aekhub.app.ui.components.Loading
import gr.aekhub.app.ui.components.MatchRow
import gr.aekhub.app.ui.components.ScreenTitle
import gr.aekhub.app.ui.components.SectionLabel
import gr.aekhub.app.ui.components.dayHeader
import gr.aekhub.app.ui.components.dayShort
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun MatchesScreen(openMatch: (Long) -> Unit) {
    val prefs = LocalPrefs.current
    val settings by prefs.settings.collectAsState(initial = Settings())
    var config by remember { mutableStateOf<AppConfig?>(null) }
    var sport by rememberSaveable { mutableStateOf("") }
    var list by remember { mutableStateOf<List<MatchCard>?>(null) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    var error by remember { mutableStateOf(false) }
    var key by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { runCatching { config = Api.config() } }
    LaunchedEffect(sport, key) {
        list = null; error = false
        var first = true
        while (true) {
            try {
                val r = Api.matches(sport = sport.ifEmpty { null })
                list = r.items; error = false
                runCatching { today = LocalDate.parse(r.today) }
                if (first) {
                    first = false
                    // Ξεκινάει από σήμερα (ή την επόμενη μέρα με αγώνα)
                    val days = r.items.map { it.date }.distinct()
                    val idx = days.indexOfFirst { it >= r.today }
                    if (idx > 0) { var pos = 0; for (i in 0 until idx) pos += 1 + r.items.count { it.date == days[i] }; listState.scrollToItem(pos) }
                }
            } catch (e: Exception) { if (list == null) error = true }
            delay(if (list?.any { it.live } == true) 20_000 else 120_000)
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Αγώνες", Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp))
        val chips = listOf("" to "Όλα") + (config?.sports?.map { it.key to it.name } ?: emptyList())
        ChipRow(chips, sport, { sport = it }, Modifier.padding(bottom = 10.dp))

        val items = list
        when {
            items == null && error -> ErrorBox { key++ }
            items == null -> Loading()
            items.isEmpty() -> Text("Δεν υπάρχουν αγώνες αυτές τις μέρες.", Modifier.padding(24.dp), style = body(15, color = C.Muted))
            else -> {
                val byDay = items.groupBy { it.date }
                val dayIndex = buildMap { var pos = 0; byDay.forEach { (d, ms) -> put(d, pos); pos += 1 + ms.size } }
                // Λωρίδα ημερών
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
                    state = rememberLazyListState(initialFirstVisibleItemIndex = (byDay.keys.indexOfFirst { it >= today.toString() } - 2).coerceAtLeast(0)),
                ) {
                    items(byDay.keys.toList()) { d ->
                        val date = runCatching { LocalDate.parse(d) }.getOrNull() ?: return@items
                        val isToday = date == today
                        Column(
                            Modifier.width(50.dp).clip(RoundedCornerShape(12.dp)).background(if (isToday) C.Gold else C.Ink)
                                .clickable { scope.launch { listState.animateScrollToItem(dayIndex[d] ?: 0) } }.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(if (isToday) "ΣΗΜΕΡΑ" else dayShort(date), style = body(10, FontWeight.ExtraBold, if (isToday) C.Ink else C.Muted))
                            Text(date.dayOfMonth.toString(), style = body(17, FontWeight.ExtraBold, if (isToday) C.Ink else C.Text2))
                            Box(Modifier.size(4.dp).clip(CircleShape).background(if (isToday) C.Ink else if (byDay[d]!!.any { it.aek.isNotEmpty() }) C.Gold else C.Ink))
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(1.dp).background(C.Line))
                LazyColumn(state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    var di = 0
                    byDay.forEach { (d, ms) ->
                        val showAd = di++ == 1 && config?.ads?.matches == true
                        if (showAd) item(key = "ad") { BannerAd(config!!.ads) }
                        item(key = "d$d") {
                            val date = runCatching { LocalDate.parse(d) }.getOrNull()
                            val past = date != null && date < today
                            SectionLabel((date?.let { dayHeader(it) } ?: d) + if (past && ms.all { it.finished }) " · ΑΠΟΤΕΛΕΣΜΑΤΑ" else "", Modifier.padding(top = 6.dp))
                        }
                        items(ms, key = { it.id }) { m ->
                            MatchRow(m, settings.matchAlerts.contains(m.id.toString()), { scope.launch { prefs.toggleMatchAlert(m.id) } }) { openMatch(m.id) }
                        }
                    }
                }
            }
        }
    }
}

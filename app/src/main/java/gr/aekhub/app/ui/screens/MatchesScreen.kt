package gr.aekhub.app.ui.screens

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import gr.aekhub.app.LocalPrefs
import gr.aekhub.app.ads.BannerAd
import gr.aekhub.app.data.Api
import gr.aekhub.app.data.AppConfig
import gr.aekhub.app.data.MatchCard
import gr.aekhub.app.data.Settings
import gr.aekhub.app.ui.components.AekDropdown
import gr.aekhub.app.ui.components.ErrorBox
import gr.aekhub.app.ui.components.Loading
import gr.aekhub.app.ui.components.MatchCardView
import gr.aekhub.app.ui.components.ScreenTitle
import gr.aekhub.app.ui.components.dayHeader
import gr.aekhub.app.ui.components.dayShort
import gr.aekhub.app.ui.components.monthShort
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body
import gr.aekhub.app.ui.theme.disp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private const val BACK_DAYS = 45L
private const val FWD_DAYS = 90L

/** Αγώνες μέρα-μέρα: σύρε ή πάτα μέρα· όταν δεν είσαι στο «σήμερα», φαίνεται κουμπί επιστροφής. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MatchesScreen(openMatch: (Long) -> Unit) {
    val prefs = LocalPrefs.current
    val settings by prefs.settings.collectAsState(initial = Settings())
    var config by remember { mutableStateOf(Api.configNow()) }
    var sport by rememberSaveable { mutableStateOf("") }
    var list by remember { mutableStateOf<List<MatchCard>?>(null) }
    var error by remember { mutableStateOf(false) }
    var key by remember { mutableIntStateOf(0) }
    val today = remember { LocalDate.now(gr.aekhub.app.ui.components.ATHENS) }
    val start = remember { today.minusDays(BACK_DAYS) }
    val days = remember { (0..(BACK_DAYS + FWD_DAYS)).map { start.plusDays(it) } }
    val todayIdx = BACK_DAYS.toInt()
    val pager = rememberPagerState(initialPage = todayIdx) { days.size }
    val strip = rememberLazyListState(initialFirstVisibleItemIndex = (todayIdx - 3).coerceAtLeast(0))
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { runCatching { config = Api.config() } }
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(sport, key) {
        // Αμέσως ό,τι ξέρουμε από την προηγούμενη φορά, μετά φρέσκα
        list = Api.peek<gr.aekhub.app.data.MatchList>(Api.pMatches(start.toString(), days.last().toString(), sport.ifEmpty { null }))?.items; error = false
        while (true) {
            try { list = Api.matches(start.toString(), days.last().toString(), sport.ifEmpty { null }).items; error = false }
            catch (e: Exception) { if (list == null) error = true }
            delay(if (list?.any { it.live } == true) 20_000 else 120_000)
        }
    }
    LaunchedEffect(sport) { gr.aekhub.app.live.Live.ping.collect { runCatching { list = Api.matches(start.toString(), days.last().toString(), sport.ifEmpty { null }, fresh = true).items } } }
    // Η λωρίδα ημερών ακολουθεί τη σελίδα
    LaunchedEffect(pager.currentPage) { strip.animateScrollToItem((pager.currentPage - 3).coerceAtLeast(0)) }

    val byDay = remember(list) { list?.groupBy { it.date } ?: emptyMap() }
    fun go(i: Int) = scope.launch { pager.animateScrollToPage(i.coerceIn(0, days.lastIndex)) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Αγώνες", Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp))
        val opts = listOf("" to "Όλα τα αθλήματα") + (config?.sports?.map { it.key to it.name } ?: emptyList())
        AekDropdown("ΑΘΛΗΜΑ", opts.firstOrNull { it.first == sport }?.second ?: "Όλα τα αθλήματα", opts, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp).fillMaxWidth()) { sport = it }

        // Λωρίδα ημερών
        LazyRow(state = strip, contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(days.size) { i ->
                val d = days[i]
                val on = i == pager.currentPage
                val isToday = d == today
                val has = byDay[d.toString()]
                Column(
                    Modifier.width(50.dp).clip(RoundedCornerShape(12.dp))
                        .then(if (on) Modifier.background(C.Gold) else if (isToday) Modifier.border(1.dp, C.Gold, RoundedCornerShape(12.dp)) else Modifier)
                        .clickable { go(i) }.padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(if (isToday) "ΣΗΜ." else dayShort(d), style = body(10, FontWeight.ExtraBold, if (on) C.Ink else if (isToday) C.Gold else C.Muted))
                    Text(d.dayOfMonth.toString(), style = body(17, FontWeight.ExtraBold, if (on) C.Ink else C.Text2))
                    Box(Modifier.size(5.dp).clip(CircleShape).background(when {
                        has == null -> androidx.compose.ui.graphics.Color.Transparent
                        has.any { it.live } -> C.Live
                        on -> C.Ink
                        else -> C.Gold
                    }))
                }
            }
        }

        // Επικεφαλίδα ημέρας: ‹ ημερομηνία ›  +  «Σήμερα» όταν έχεις φύγει από σήμερα
        val cur = days[pager.currentPage]
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { go(pager.currentPage - 1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Προηγούμενη μέρα", tint = C.Text) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val rel = ChronoUnit.DAYS.between(today, cur)
                Text(when (rel) { 0L -> "ΣΗΜΕΡΑ"; 1L -> "ΑΥΡΙΟ"; -1L -> "ΧΘΕΣ"; else -> if (rel > 0) "ΣΕ $rel ΜΕΡΕΣ" else "ΠΡΙΝ ${-rel} ΜΕΡΕΣ" },
                    style = body(10, FontWeight.ExtraBold, if (rel == 0L) C.Gold else C.Muted))
                Text(dayHeader(cur), style = disp(20), color = C.Text, textAlign = TextAlign.Center)
            }
            IconButton(onClick = { go(pager.currentPage + 1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Επόμενη μέρα", tint = C.Text) }
        }
        if (cur != today) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Row(
                Modifier.clip(RoundedCornerShape(999.dp)).background(C.Gold).clickable { go(todayIdx) }.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Filled.Replay, null, tint = C.Ink, modifier = Modifier.size(16.dp))
                Text("Επιστροφή στο σήμερα", style = body(13, FontWeight.ExtraBold, C.Ink))
            }
        }

        val items = list
        when {
            items == null && error -> ErrorBox { key++ }
            items == null -> Loading()
            else -> androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                isRefreshing = refreshing, modifier = Modifier.fillMaxSize(),
                onRefresh = { scope.launch { refreshing = true; runCatching { list = Api.matches(start.toString(), days.last().toString(), sport.ifEmpty { null }, fresh = true).items }; refreshing = false } },
            ) { HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                val d = days[page]
                val ms = byDay[d.toString()] ?: emptyList()
                if (ms.isEmpty()) {
                    val next = days.indices.firstOrNull { it > page && byDay.containsKey(days[it].toString()) }
                    val prev = days.indices.lastOrNull { it < page && byDay.containsKey(days[it].toString()) }
                    Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(30.dp))
                        Text("Δεν υπάρχουν αγώνες αυτή τη μέρα.", style = body(15, FontWeight.Bold, C.Text2), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        val target = next ?: prev
                        if (target != null) Row(
                            Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, C.Gold, RoundedCornerShape(12.dp)).clickable { go(target) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            val t = days[target]
                            Text((if (target == next) "Επόμενος αγώνας: " else "Τελευταίος αγώνας: ") + dayShort(t) + " " + t.dayOfMonth + " " + monthShort(t) + if (target == next) " ›" else "", style = body(14, FontWeight.ExtraBold, C.Gold))
                        }
                    }
                } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(ms, key = { it.id }) { m ->
                        MatchCardView(m, settings.matchAlerts.contains(m.id.toString()), { scope.launch { prefs.toggleMatchAlert(m.id) } }) { openMatch(m.id) }
                    }
                    val ads = config?.ads
                    if (ads != null && ads.enabled && ads.matches) item(key = "ad") { BannerAd(ads) }
                }
            } }
        }
    }
}

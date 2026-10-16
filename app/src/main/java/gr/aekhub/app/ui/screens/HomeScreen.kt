package gr.aekhub.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import gr.aekhub.app.R
import gr.aekhub.app.ads.BannerAd
import gr.aekhub.app.data.AdsConfig
import gr.aekhub.app.data.Api
import gr.aekhub.app.data.AppConfig
import gr.aekhub.app.data.HomeData
import gr.aekhub.app.data.MatchCard
import gr.aekhub.app.data.PostCard
import gr.aekhub.app.ui.components.CategoryMenu
import gr.aekhub.app.ui.components.SocialSection
import gr.aekhub.app.ui.components.SocialStrip
import gr.aekhub.app.ui.components.catIcon
import gr.aekhub.app.ui.components.LiveMatchHero
import gr.aekhub.app.ui.components.LiveEventHero
import gr.aekhub.app.ui.components.ErrorBox
import gr.aekhub.app.ui.components.FeaturedCard
import gr.aekhub.app.ui.components.GoldHeader
import gr.aekhub.app.ui.components.LiveBar
import gr.aekhub.app.ui.components.Loading
import gr.aekhub.app.ui.components.MatchMini
import gr.aekhub.app.ui.components.PostCardMedium
import gr.aekhub.app.ui.components.PostRow
import gr.aekhub.app.ui.components.ScreenTitle
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Αρχική: ενότητες ανά κατηγορία, όπως το site. Τα chips ανοίγουν τη ροή μιας κατηγορίας. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(openPost: (Long) -> Unit, openMatch: (Long) -> Unit, openSearch: () -> Unit, openSettings: () -> Unit, openFeed: () -> Unit, openMatches: () -> Unit, openCat: (String, String) -> Unit, openEvent: (Long) -> Unit) {
    var config by remember { mutableStateOf(Api.configNow()) }
    var home by remember { mutableStateOf(Api.peek<HomeData>(Api.pHome())) }
    var error by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf<List<MatchCard>>(emptyList()) }
    var key by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    suspend fun load(fresh: Boolean = false) {
        try { home = Api.home(fresh); error = false } catch (e: Exception) { if (home == null) error = true }
        if (fresh) runCatching { live = Api.matches(fresh = true).items.filter { it.live && it.aek.isNotEmpty() } }
    }
    LaunchedEffect(Unit) { runCatching { config = Api.config() } }
    LaunchedEffect(key) { load() }
    // Άμεση ανανέωση όταν αλλάξει κάτι στο site + κάθε 60″ για σιγουριά
    LaunchedEffect(Unit) { gr.aekhub.app.live.Live.ping.collect { load(fresh = true) } }
    LaunchedEffect(Unit) { while (true) { delay(60_000); load(fresh = true) } }
    LaunchedEffect(key) {
        while (true) { runCatching { live = Api.matches().items.filter { it.live && it.aek.isNotEmpty() } }; delay(30_000) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.logo), "AEK Hub", Modifier.height(30.dp))
            Box(Modifier.weight(1f))
            IconButton(onClick = openFeed) { Icon(Icons.Outlined.History, "Ροή ειδήσεων", tint = C.Text) }
            IconButton(onClick = openSearch) { Icon(Icons.Outlined.Search, "Αναζήτηση", tint = C.Text) }
            IconButton(onClick = openSettings) { Icon(Icons.Outlined.NotificationsNone, "Ειδοποιήσεις", tint = C.Text) }
        }
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { refreshing = true; load(fresh = true); refreshing = false } }, modifier = Modifier.fillMaxSize()) {
            val h = home
            when {
                h == null && error -> ErrorBox { key++ }
                h == null -> Loading()
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (h.socials.isNotEmpty()) item(key = "soctop") { SocialStrip(h.socials) }
                    // Μενού κατηγοριών (πλακίδια με τα εικονίδια του site)
                    val cats = config?.categories ?: emptyList()
                    if (cats.isNotEmpty()) item(key = "menu") {
                        Box(Modifier.padding(horizontal = 0.dp)) {
                            CategoryMenu(cats.map { Triple(it.slug, it.name, it.icon) },
                                { slug, name -> openCat(slug, name) }, Modifier.padding(bottom = 2.dp).padding(horizontal = 0.dp))
                        }
                    }
                    // Όπως στο site: όσο τρέχει live αγώνας/γεγονός με «Hero», μπαίνει στη θέση του κύριου θέματος
                    val lh = h.liveHero
                    val heroMatchId = lh?.takeIf { it.type == "match" }?.id
                    items(live.filter { it.id != heroMatchId }, key = { "live" + it.id }) { m -> LiveBar(m) { openMatch(m.id) } }
                    when {
                        lh != null && lh.type == "match" && lh.match != null -> item(key = "lh") { LiveMatchHero(lh.match, lh.image) { openMatch(lh.id) } }
                        lh != null && lh.type == "event" -> item(key = "lh") { LiveEventHero(lh.title, lh.image) { openEvent(lh.id) } }
                        else -> h.featured?.let { f -> item(key = "feat") { FeaturedCard(f) { openPost(f.id) } } }
                    }
                    if (h.top.isNotEmpty()) {
                        item(key = "toph") { GoldHeader("Τελευταία", Modifier.padding(top = 8.dp), "Ροή ειδήσεων", openFeed) }
                        items(h.top, key = { "t" + it.id }) { p -> PostRow(p) { openPost(p.id) } }
                    }
                    val upcoming = h.matches.filter { !it.live }
                    if (upcoming.isNotEmpty()) {
                        item(key = "mh") { GoldHeader("Επόμενοι αγώνες", Modifier.padding(top = 10.dp), "Όλοι", openMatches) }
                        item(key = "mr") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(upcoming, key = { it.id }) { m -> MatchMini(m) { openMatch(m.id) } } }
                        }
                    }
                    h.sections.forEachIndexed { si, sec ->
                        item(key = "sh" + sec.slug) { GoldHeader(sec.name, Modifier.padding(top = 14.dp), "Όλα") { openCat(sec.slug, sec.name) } }
                        sec.items.firstOrNull()?.let { first -> item(key = "s" + sec.slug + first.id) { PostCardMedium(first) { openPost(first.id) } } }
                        items(sec.items.drop(1).take(3), key = { "s" + sec.slug + it.id }) { p -> PostRow(p) { openPost(p.id) } }
                        val ads = config?.ads
                        if (ads != null && ads.enabled && si % 2 == 1) item(key = "ad$si") { BannerAd(ads) }
                    }
                    if (h.socials.isNotEmpty()) item(key = "soc") { Box(Modifier.padding(top = 16.dp)) { SocialSection(h.socials) } }
                }
            }
        }
    }
}

/** Μία κατηγορία: δική της σελίδα, με το εικονίδιό της. */
@Composable
fun CategoryScreen(slug: String, name: String, back: () -> Unit, openPost: (Long) -> Unit) {
    var cfg by remember { mutableStateOf<AppConfig?>(null) }
    LaunchedEffect(Unit) { runCatching { cfg = Api.config() } }
    val icon = cfg?.categories?.firstOrNull { it.slug == slug }?.icon ?: "eagle"
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, end = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Πίσω", tint = C.Text) }
            Image(painterResource(catIcon(icon)), null, Modifier.size(34.dp))
            Spacer(Modifier.size(10.dp))
            Column {
                Text("ΚΑΤΗΓΟΡΙΑ", style = body(10, FontWeight.ExtraBold, C.Gold))
                ScreenTitle(name)
            }
        }
        NewsList(slug, openPost, cfg?.ads)
    }
}

/** Ροή ειδήσεων: όλα τα άρθρα με τη σειρά (ή μιας κατηγορίας). */
@Composable
fun FeedScreen(back: () -> Unit, openPost: (Long) -> Unit) {
    var ads by remember { mutableStateOf<AdsConfig?>(null) }
    LaunchedEffect(Unit) { runCatching { ads = Api.config().ads } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Πίσω", tint = C.Text) }
            ScreenTitle("Ροή ειδήσεων")
        }
        NewsList(null, openPost, ads)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewsList(cat: String?, openPost: (Long) -> Unit, ads: AdsConfig?) {
    val items = remember(cat) {
        mutableStateListOf<PostCard>().apply {
            Api.peek<gr.aekhub.app.data.Feed>(Api.pFeed(cat))?.let { f -> addAll(((listOfNotNull(f.featured)) + f.items).distinctBy { it.id }.sortedByDescending { it.date }) }
        }
    }
    var page by remember(cat) { mutableIntStateOf(0) }
    var pages by remember(cat) { mutableIntStateOf(1) }
    var loading by remember(cat) { mutableStateOf(false) }
    var error by remember(cat) { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var key by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    suspend fun load(reset: Boolean, fresh: Boolean = false) {
        if (loading) return
        loading = true; error = false
        try {
            val f = Api.feed(cat, if (reset) 1 else page + 1, fresh)
            if (reset) items.clear()
            // Η ροή δείχνει και το κύριο θέμα με τη σειρά του
            val all = (if (reset && f.featured != null) listOf(f.featured) else emptyList()) + f.items
            all.sortedByDescending { it.date }.forEach { n -> if (items.none { it.id == n.id }) items.add(n) }
            page = f.page; pages = f.pages
        } catch (e: Exception) { if (reset) error = true }
        loading = false
    }
    LaunchedEffect(cat, key) { load(true) }
    LaunchedEffect(cat) { gr.aekhub.app.live.Live.ping.collect { if (page <= 1) load(true, fresh = true) } }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { refreshing = true; load(true, fresh = true); refreshing = false } }, modifier = Modifier.fillMaxSize()) {
        when {
            items.isEmpty() && error -> ErrorBox { key++ }
            items.isEmpty() -> Loading()
            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(items, key = { _, p -> p.id }) { i, p ->
                    Column {
                        if (i == 0 && cat != null) PostCardMedium(p) { openPost(p.id) } else PostRow(p) { openPost(p.id) }
                        if (ads != null && ads.enabled && (i + 1) % ads.everyN.coerceAtLeast(4) == 0) BannerAd(ads)
                    }
                }
                // Όχι ατελείωτο scroll: κουμπί για τα επόμενα
                if (page < pages) item(key = "more") {
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                        if (loading) Box(Modifier.height(48.dp)) { Loading() }
                        else Box(Modifier.clip(RoundedCornerShape(12.dp)).background(C.Gold).clickable { scope.launch { load(false) } }.padding(horizontal = 22.dp, vertical = 12.dp)) {
                            Text("ΠΕΡΙΣΣΟΤΕΡΑ ΑΡΘΡΑ", style = body(14, FontWeight.ExtraBold, C.Ink))
                        }
                    }
                }
            }
        }
    }
}

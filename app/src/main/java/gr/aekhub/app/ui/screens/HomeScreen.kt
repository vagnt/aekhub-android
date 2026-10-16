package gr.aekhub.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import gr.aekhub.app.ads.BannerAd
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import gr.aekhub.app.R
import gr.aekhub.app.data.Api
import gr.aekhub.app.data.AppConfig
import gr.aekhub.app.data.MatchCard
import gr.aekhub.app.data.PostCard
import gr.aekhub.app.ui.components.ChipRow
import gr.aekhub.app.ui.components.ErrorBox
import gr.aekhub.app.ui.components.FeaturedCard
import gr.aekhub.app.ui.components.LiveBar
import gr.aekhub.app.ui.components.Loading
import gr.aekhub.app.ui.components.PostRow
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.disp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(openPost: (Long) -> Unit, openMatch: (Long) -> Unit, openSearch: () -> Unit, openSettings: () -> Unit) {
    var config by remember { mutableStateOf<AppConfig?>(null) }
    var cat by rememberSaveable { mutableStateOf("") }
    var featured by remember { mutableStateOf<PostCard?>(null) }
    val items = remember { mutableStateListOf<PostCard>() }
    var page by remember { mutableIntStateOf(0) }
    var pages by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf<List<MatchCard>>(emptyList()) }
    var reloadKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    suspend fun load(reset: Boolean) {
        if (loading) return
        loading = true; error = false
        try {
            val next = if (reset) 1 else page + 1
            val f = Api.feed(cat.ifEmpty { null }, next)
            if (reset) { items.clear(); featured = f.featured }
            items.addAll(f.items.filter { n -> items.none { it.id == n.id } })
            page = f.page; pages = f.pages
        } catch (e: Exception) { if (reset) error = true }
        loading = false
    }

    LaunchedEffect(Unit) { runCatching { config = Api.config() } }
    LaunchedEffect(cat, reloadKey) { load(reset = true); listState.scrollToItem(0) }
    // Ζωντανός αγώνας της ΑΕΚ: ελέγχει κάθε 30″
    LaunchedEffect(reloadKey) {
        while (true) {
            runCatching { live = Api.matches().items.filter { it.live && it.aek.isNotEmpty() } }
            delay(30_000)
        }
    }
    val nearEnd by remember { derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= listState.layoutInfo.totalItemsCount - 4 } }
    LaunchedEffect(nearEnd, page) { if (nearEnd && page in 1 until pages) load(reset = false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.logo), "AEK Hub", Modifier.height(30.dp))
            Box(Modifier.weight(1f))
            IconButton(onClick = openSearch) { Icon(Icons.Outlined.Search, "Αναζήτηση", tint = C.Text) }
            IconButton(onClick = openSettings) { Icon(Icons.Outlined.NotificationsNone, "Ειδοποιήσεις", tint = C.Text) }
        }
        val chips = listOf("" to "Όλα") + (config?.categories?.map { it.slug to it.name } ?: emptyList())
        ChipRow(chips, cat, { cat = it }, Modifier.padding(bottom = 10.dp))

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; load(reset = true); refreshing = false } },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                items.isEmpty() && featured == null && error -> ErrorBox { reloadKey++ }
                items.isEmpty() && featured == null -> Loading()
                else -> LazyColumn(
                    state = listState, modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (cat.isEmpty()) items(live, key = { "live" + it.id }) { m -> LiveBar(m) { openMatch(m.id) } }
                    featured?.let { f -> item(key = "feat") { Box(Modifier.padding(top = 4.dp, bottom = 6.dp)) { FeaturedCard(f) { openPost(f.id) } } } }
                    item(key = "hdr") { Text(if (cat.isEmpty()) "ΤΕΛΕΥΤΑΙΑ" else (chips.firstOrNull { it.first == cat }?.second ?: "").uppercase(), style = disp(22), color = C.Text, modifier = Modifier.padding(top = 6.dp)) }
                    itemsIndexed(items, key = { _, p -> p.id }) { i, p ->
                        Column {
                            PostRow(p) { openPost(p.id) }
                            val ads = config?.ads
                            if (ads != null && ads.enabled && (i + 1) % ads.everyN.coerceAtLeast(4) == 0) BannerAd(ads)
                        }
                    }
                    if (page < pages) item(key = "more") { Box(Modifier.fillMaxWidth().height(64.dp)) { Loading() } }
                }
            }
        }
    }
}

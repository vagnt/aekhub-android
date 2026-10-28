package gr.aekhub.app.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import gr.aekhub.app.LocalPrefs
import gr.aekhub.app.data.Api
import gr.aekhub.app.data.AdsConfig
import gr.aekhub.app.ads.BannerAd
import gr.aekhub.app.ui.components.SocialRow
import gr.aekhub.app.data.Post
import gr.aekhub.app.data.Settings
import gr.aekhub.app.ui.components.Block
import gr.aekhub.app.ui.components.BlockView
import gr.aekhub.app.ui.components.ErrorBox
import gr.aekhub.app.ui.components.Html
import gr.aekhub.app.ui.components.Loading
import gr.aekhub.app.ui.components.Tag
import gr.aekhub.app.ui.components.postDate
import gr.aekhub.app.ui.components.upperGr
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body
import gr.aekhub.app.ui.theme.disp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ArticleScreen(id: Long, back: () -> Unit, openPost: (Long) -> Unit = {}) {
    val prefs = LocalPrefs.current
    val settings by prefs.settings.collectAsState(initial = Settings())
    var post by remember { mutableStateOf<Post?>(null) }
    var blocks by remember { mutableStateOf<List<Block>>(emptyList()) }
    var error by remember { mutableStateOf(false) }
    var key by remember { mutableIntStateOf(0) }
    var ads by remember { mutableStateOf<AdsConfig?>(null) }
    var socials by remember { mutableStateOf<List<gr.aekhub.app.data.Social>>(emptyList()) }
    LaunchedEffect(Unit) { runCatching { val c = Api.config(); ads = c.ads; socials = c.socials } }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    var refreshing by remember { mutableStateOf(false) }
    var fresh by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf<List<gr.aekhub.app.data.PostCard>>(emptyList()) }
    LaunchedEffect(id) { runCatching { more = Api.feed(null, 1).items.filter { it.id != id }.take(5) } }
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    // Κλείνει το άρθρο → ίσως fullscreen (με τη συχνότητα του wp-admin)
    androidx.compose.runtime.DisposableEffect(id) { onDispose { gr.aekhub.app.ads.Ads.onArticleClosed() } }
    LaunchedEffect(id, key) {
        error = false
        // Αν το άρθρο έχει ξανανοίξει, φαίνεται αμέσως από την αποθήκευση
        if (post == null) Api.peek<Post>(Api.pPost(id))?.let { c -> blocks = withContext(Dispatchers.Default) { Html.parse(c.html, c.link) }; post = c }
        try {
            val p = Api.post(id, fresh); fresh = false
            blocks = withContext(Dispatchers.Default) { Html.parse(p.html, p.link) }
            post = p
        } catch (e: Exception) { if (post == null) error = true }
        refreshing = false
    }

    LaunchedEffect(post?.id) { post?.let { gr.aekhub.app.data.Analytics.event(ctx, "article_view", "item_id" to it.id, "item_name" to it.title, "category" to it.category) } }
    val p = post
    if (p == null) { if (error) ErrorBox { key++ } else Loading(); return }
    val saved = settings.saved.any { it.id == p.id }
    val scale = settings.textScale
    fun share() = gr.aekhub.app.data.Analytics.event(ctx, "share", "content_type" to "article", "item_id" to p.id).also { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, p.title); putExtra(Intent.EXTRA_TEXT, p.title + "\n" + p.link)
    }, "Κοινοποίηση")) }

    var showComments by androidx.compose.runtime.remember(p.id) { androidx.compose.runtime.mutableStateOf(false) }
    if (showComments) gr.aekhub.app.ui.components.CommentsSheet(p.id, p.title) { showComments = false } // 💬 σχόλια σε pop up, στυλ chat

    Box(Modifier.fillMaxSize()) {
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true; fresh = true; key++ }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "hero") {
                val v = p.videoInfo
                if (v != null && (v.embed.isNotBlank() || v.html.isNotBlank())) {
                    // Video post: το βίντεο στην κορυφή, παίζει εδώ
                    Column(Modifier.fillMaxWidth().background(Color.Black).padding(top = 64.dp)) {
                        gr.aekhub.app.ui.components.InlineVideo(v.embed, v.thumb.ifBlank { p.image }, Modifier, v.html, "▶ ΔΕΣ ΤΟ ΒΙΝΤΕΟ")
                    }
                } else Box(Modifier.fillMaxWidth().height(320.dp).background(C.Panel)) {
                    if (p.image.isNotBlank()) AsyncImage(p.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to C.Ink.copy(alpha = 0.6f), 0.3f to Color.Transparent, 0.55f to Color.Transparent, 1f to C.Ink)))
                }
            }
            item(key = "head") {
                val off = if (p.videoInfo != null) 0.dp else (-56).dp
                Column(Modifier.offset(y = off).padding(horizontal = 20.dp).padding(top = if (p.videoInfo != null) 16.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (p.category.isNotBlank()) Tag(p.category)
                        Text("⏱ " + p.minutes + "′ ανάγνωση", Modifier.clip(RoundedCornerShape(6.dp)).background(C.Panel2).padding(horizontal = 8.dp, vertical = 4.dp), style = body(11, FontWeight.ExtraBold, C.Text2))
                    }
                    Text(p.title.upperGr(), style = disp((34 * scale).toInt()).copy(lineHeight = (34 * scale * 1.0f).sp), color = C.Text)
                    if (p.excerpt.isNotBlank()) Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                        Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(C.Gold))
                        Text(p.excerpt, Modifier.padding(start = 12.dp), style = body((17 * scale).toInt(), FontWeight.SemiBold, C.Text).copy(lineHeight = (17 * scale * 1.5f).sp))
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Panel).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (p.authorAvatar.isNotBlank()) AsyncImage(p.authorAvatar, p.author, Modifier.size(46.dp).clip(CircleShape).background(C.Panel2), contentScale = ContentScale.Crop)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(p.author.ifBlank { "AEK Hub" }, style = body(15, FontWeight.ExtraBold))
                            Text(postDate(p.date), style = body(12, color = C.Muted))
                        }
                        Box(Modifier.size(40.dp).clip(CircleShape).background(C.Panel2).clickable { share() }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Share, "Κοινοποίηση", tint = C.Gold, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (p.imageCaption.isNotBlank() && p.videoInfo == null) Text("📷 " + p.imageCaption, style = body(11, color = C.Muted))
                }
            }
            items(blocks.size) { i ->
                Column(Modifier.offset(y = if (p.videoInfo != null) 0.dp else (-40).dp)) {
                    BlockView(blocks[i], scale)
                    val a = ads
                    if (a != null && a.article && (i == 2 && blocks.size > 5 || i == blocks.size - 1)) BannerAd(a, medium = true, modifier = Modifier.padding(top = 16.dp), place = "article")
                }
            }
            if (socials.isNotEmpty()) item(key = "socials") {
                Box(Modifier.padding(horizontal = 16.dp)) { SocialRow(socials, "Συζήτησέ το μαζί μας") }
            }
            if (more.isNotEmpty()) {
                item(key = "moreh") {
                    Row(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.width(4.dp).height(22.dp).background(C.Gold))
                        Text("ΔΙΑΒΑΣΕ ΕΠΙΣΗΣ", style = disp(22), color = C.Text)
                    }
                }
                items(more, key = { "more" + it.id }) { c -> Box(Modifier.padding(horizontal = 16.dp)) { gr.aekhub.app.ui.components.PostRow(c) { openPost(c.id) } } }
            }
        }
        }

        // Πρόοδος ανάγνωσης (χρυσή γραμμή πάνω-πάνω)
        val prog by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf {
            val total = list.layoutInfo.totalItemsCount.coerceAtLeast(1)
            ((list.firstVisibleItemIndex + 1).toFloat() / total).coerceIn(0f, 1f)
        } }
        Box(Modifier.fillMaxWidth().height(3.dp).background(C.Line)) {
            Box(Modifier.fillMaxWidth(prog).fillMaxHeight().background(C.Gold))
        }
        // Πάνω κουμπιά
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundBtn(Icons.AutoMirrored.Filled.ArrowBack, "Πίσω", C.Text, back)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(C.Ink.copy(alpha = 0.6f))
                    .clickable { scope.launch { prefs.setScale(when { scale < 0.95f -> 1f; scale < 1.1f -> 1.15f; scale < 1.25f -> 1.3f; else -> 0.9f }) } },
                contentAlignment = Alignment.Center,
            ) { Text("Aa", style = body(15, FontWeight.ExtraBold)) }
            RoundBtn(if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, if (saved) "Αφαίρεση από αποθηκευμένα" else "Αποθήκευση", C.Gold) {
                scope.launch { prefs.toggleSaved(p.card()) }
            }
            RoundBtn(Icons.Filled.Share, "Κοινοποίηση", C.Text) { share() }
        }

        // Κάτω κουμπιά: 💬 Σχόλια (ανοίγει pop up) + Κοινοποίηση
        Row(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            gr.aekhub.app.ui.components.CommentsButton(p.id) { showComments = true }
            Row(
                Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(C.Gold).clickable { share() }.padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Share, null, tint = C.Ink, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text("Κοινοποίηση", style = body(15, FontWeight.ExtraBold, C.Ink))
            }
        }
    }
}

@Composable
private fun RoundBtn(icon: ImageVector, desc: String, tint: Color, onClick: () -> Unit) =
    Box(Modifier.size(44.dp).clip(CircleShape).background(C.Ink.copy(alpha = 0.6f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(22.dp))
    }

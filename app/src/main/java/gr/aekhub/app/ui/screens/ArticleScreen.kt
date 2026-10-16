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

@Composable
fun ArticleScreen(id: Long, back: () -> Unit) {
    val prefs = LocalPrefs.current
    val settings by prefs.settings.collectAsState(initial = Settings())
    var post by remember { mutableStateOf<Post?>(null) }
    var blocks by remember { mutableStateOf<List<Block>>(emptyList()) }
    var error by remember { mutableStateOf(false) }
    var key by remember { mutableIntStateOf(0) }
    var ads by remember { mutableStateOf<AdsConfig?>(null) }
    LaunchedEffect(Unit) { runCatching { ads = Api.config().ads } }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    LaunchedEffect(id, key) {
        error = false
        try {
            val p = Api.post(id)
            blocks = withContext(Dispatchers.Default) { Html.parse(p.html, p.link) }
            post = p
        } catch (e: Exception) { error = true }
    }

    val p = post
    if (p == null) { if (error) ErrorBox { key++ } else Loading(); return }
    val saved = settings.saved.any { it.id == p.id }
    val scale = settings.textScale
    fun share() = ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, p.title); putExtra(Intent.EXTRA_TEXT, p.title + "\n" + p.link)
    }, "Κοινοποίηση"))

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "hero") {
                Box(Modifier.fillMaxWidth().height(280.dp).background(C.Panel)) {
                    if (p.image.isNotBlank()) AsyncImage(p.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to C.Ink.copy(alpha = 0.55f), 0.35f to Color.Transparent, 0.6f to Color.Transparent, 1f to C.Ink)))
                }
            }
            item(key = "head") {
                Column(Modifier.offset(y = (-48).dp).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (p.category.isNotBlank()) Tag(p.category)
                    Text(p.title.upperGr(), style = disp((36 * scale).toInt()).copy(lineHeight = (36 * scale * 0.98f).sp), color = C.Text)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (p.author.isNotBlank()) Text(p.author, style = body(13, FontWeight.Bold))
                        Text(postDate(p.date) + " · " + p.minutes + " λεπτά ανάγνωση", style = body(12, color = C.Muted))
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
                }
            }
            items(blocks.size) { i ->
                Column(Modifier.offset(y = (-48).dp)) {
                    BlockView(blocks[i], scale)
                    val a = ads
                    if (a != null && a.article && (i == 2 && blocks.size > 5 || i == blocks.size - 1)) BannerAd(a, medium = true, modifier = Modifier.padding(top = 16.dp))
                }
            }
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

        // Κάτω κουμπί κοινοποίησης
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth().height(50.dp)
                .clip(RoundedCornerShape(14.dp)).background(C.Gold).clickable { share() },
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Share, null, tint = C.Ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text("Κοινοποίηση", style = body(15, FontWeight.ExtraBold, C.Ink))
        }
    }
}

@Composable
private fun RoundBtn(icon: ImageVector, desc: String, tint: Color, onClick: () -> Unit) =
    Box(Modifier.size(44.dp).clip(CircleShape).background(C.Ink.copy(alpha = 0.6f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(22.dp))
    }

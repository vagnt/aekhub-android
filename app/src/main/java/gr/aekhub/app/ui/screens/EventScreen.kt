package gr.aekhub.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import gr.aekhub.app.data.Api
import gr.aekhub.app.data.EventDetail
import gr.aekhub.app.data.EventUpdate
import gr.aekhub.app.ui.components.ErrorBox
import gr.aekhub.app.ui.components.LiveDot
import gr.aekhub.app.ui.components.Loading
import gr.aekhub.app.ui.components.VideoDialog
import gr.aekhub.app.ui.components.openUrl
import gr.aekhub.app.ui.components.upperGr
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body
import gr.aekhub.app.ui.theme.disp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Live γεγονός (Live Ticker): συνέντευξη, παρουσίαση, κλήρωση… ανανεώνεται μόνο του. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EventScreen(id: Long, back: () -> Unit) {
    var ev by remember { mutableStateOf<EventDetail?>(null) }
    var error by remember { mutableStateOf(false) }
    var key by remember { mutableIntStateOf(0) }
    var popup by remember { mutableStateOf<EventUpdate?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val ctx = LocalContext.current
    LaunchedEffect(id, key) {
        while (true) {
            try { ev = Api.event(id); error = false } catch (e: Exception) { if (ev == null) error = true }
            delay(if (ev?.live == true) 10_000 else 120_000)
        }
    }
    val e = ev
    if (e == null) { if (error) ErrorBox { key++ } else Loading(); return }
    popup?.let { u -> VideoDialog(u.title.ifBlank { e.title }, u.embed) { popup = null } }

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(isRefreshing = refreshing, modifier = Modifier.fillMaxSize(),
        onRefresh = { scope.launch { refreshing = true; runCatching { ev = Api.event(id, fresh = true) }; refreshing = false } }) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(230.dp)) {
                if (e.image.isNotBlank()) AsyncImage(e.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(gr.aekhub.app.R.drawable.match_hero_bg), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to C.Ink.copy(alpha = 0.5f), 1f to C.Ink)))
                IconButton(onClick = back, modifier = Modifier.padding(6.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Πίσω", tint = C.Text) }
                Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.clip(RoundedCornerShape(6.dp)).background(if (e.live) C.Live else C.Panel3).padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (e.live) LiveDot(Color.White)
                        Text(if (e.live) "LIVE" else e.statusLabel.upperGr(), style = body(11, FontWeight.ExtraBold, Color.White))
                    }
                    Text(e.title.upperGr(), style = disp(28), color = C.Text)
                }
            }
        }
        if (e.updates.isEmpty()) item { Text("Δεν υπάρχουν ακόμα ενημερώσεις.", Modifier.padding(20.dp), style = body(14, color = C.Muted)) }
        items(e.updates, key = { it.id }) { u ->
            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Panel)
                .border(1.dp, if (u.highlight) C.Gold else C.Line, RoundedCornerShape(16.dp)).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.padding(top = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(u.minute.ifBlank { u.time }, style = disp(18), color = C.Gold)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (u.typeLabel.isNotBlank()) Text(u.typeLabel.upperGr(), style = body(10, FontWeight.ExtraBold, C.Muted).copy(letterSpacing = 0.8.sp))
                    if (u.title.isNotBlank()) Text(u.title, style = body(16, FontWeight.ExtraBold))
                    if (u.text.isNotBlank()) Text(u.text, style = body(14, color = C.Text2).copy(lineHeight = 21.sp))
                    if (u.embed.isNotBlank()) Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(C.Panel2).clickable { popup = u }, contentAlignment = Alignment.Center) {
                        if (u.thumb.isNotBlank()) AsyncImage(u.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(Modifier.size(52.dp).clip(CircleShape).background(C.Gold), contentAlignment = Alignment.Center) { Icon(Icons.Filled.PlayArrow, "Αναπαραγωγή", tint = C.Ink) }
                    }
                    if (u.link.isNotBlank()) Text("Άνοιγμα ›", Modifier.clickable { openUrl(ctx, u.link) }, style = body(13, FontWeight.ExtraBold, C.Gold))
                }
            }
        }
    }
    }
}

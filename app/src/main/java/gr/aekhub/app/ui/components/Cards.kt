package gr.aekhub.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import gr.aekhub.app.data.MatchCard
import gr.aekhub.app.data.PostCard
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body
import gr.aekhub.app.ui.theme.disp

@Composable
fun FeaturedCard(p: PostCard, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(20.dp)).background(C.Panel).clickable(onClick = onClick)) {
        if (p.image.isNotBlank()) AsyncImage(p.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color.Transparent, 0.7f to C.Ink.copy(alpha = 0.92f), 1f to C.Ink)))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (p.category.isNotBlank()) Tag(p.category)
            Text(p.title.upperGr(), style = disp(30).copy(lineHeight = 30.sp), color = C.Text, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Text(postDate(p.date), style = body(12, color = C.Text2))
        }
    }
}

@Composable
fun PostRow(p: PostCard, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(112.dp).height(78.dp).clip(RoundedCornerShape(12.dp)).background(C.Panel2)) {
            if (p.image.isNotBlank()) AsyncImage(p.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (p.category.isNotBlank()) Text(p.category.upperGr(), style = body(11, FontWeight.ExtraBold, C.Gold).copy(letterSpacing = 0.5.sp))
            Text(p.title, style = body(15, FontWeight.Bold).copy(lineHeight = 20.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(postDate(p.date, withYear = false), style = body(12, color = C.Muted))
        }
    }
}

/** Μπάρα ζωντανού αγώνα στην αρχική. */
@Composable
fun LiveBar(m: MatchCard, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Panel).border(1.dp, C.Line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LiveDot()
            Text(liveMinute(m.clock, m.minute.ifBlank { "LIVE" }).let { if (it == "LIVE") it else "LIVE $it" }, style = body(11, FontWeight.ExtraBold, C.Live))
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(m.home.upperGr(), style = disp(20), color = if (m.aek == "home") C.Gold else C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
            Box(Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(8.dp)).background(C.Gold).padding(horizontal = 10.dp, vertical = 2.dp)) {
                Text("${m.scoreHome.ifBlank { "0" }} – ${m.scoreAway.ifBlank { "0" }}", style = disp(20), color = C.Ink)
            }
            Text(m.away.upperGr(), style = disp(20), color = if (m.aek == "away") C.Gold else C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = C.Muted)
    }
}

/** Μεσαία κάρτα: πρώτο άρθρο κάθε ενότητας στην αρχική. */
@Composable
fun PostCardMedium(p: PostCard, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(16.dp)).background(C.Panel2)) {
            if (p.image.isNotBlank()) AsyncImage(p.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Text(p.title, style = body(18, FontWeight.ExtraBold).copy(lineHeight = 23.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(postDate(p.date, withYear = false), style = body(12, color = C.Muted))
    }
}

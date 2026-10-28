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
import androidx.compose.material.icons.filled.PlayArrow
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
        if (p.video) PlayBadge(Modifier.align(Alignment.TopEnd).padding(14.dp), 46.dp)
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
            if (p.video) PlayBadge(Modifier.align(Alignment.Center), 30.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (p.category.isNotBlank()) Text(p.category.upperGr(), style = body(11, FontWeight.ExtraBold, C.Gold).copy(letterSpacing = 0.5.sp))
            Text(p.title, style = body(15, FontWeight.Bold).copy(lineHeight = 20.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(postDate(p.date, withYear = false), style = body(12, color = C.Muted))
        }
    }
}

/** Ζωντανός αγώνας στην αρχική: σήματα, σκορ, λεπτό/δεκάλεπτο, άθλημα και διοργάνωση. */
@Composable
fun LiveBar(m: MatchCard, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Brush.verticalGradient(listOf(Color(0xFF2A1513), C.Panel))).border(1.5.dp, C.Live, shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.clip(RoundedCornerShape(6.dp)).background(C.Live).padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                LiveDot(Color.White)
                val mn = liveMinute(m.clock, m.minute)
                Text(("LIVE · " + mn.ifBlank { m.phase.ifBlank { m.statusLabel } }).upperGr().trimEnd(' ', '·'), style = body(11, FontWeight.ExtraBold, Color.White))
            }
            Text(matchMeta(m), Modifier.weight(1f), style = body(11, FontWeight.ExtraBold, C.Gold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = C.Muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Crest(m.homeLogo, m.home, 40.dp)
                Text(m.home.upperGr(), style = disp(18), color = if (m.aek == "home") C.Gold else C.Text, maxLines = 2, softWrap = m.home.trim().contains(' '), overflow = TextOverflow.Ellipsis)
            }
            Text("${m.scoreHome.ifBlank { "0" }} : ${m.scoreAway.ifBlank { "0" }}", Modifier.padding(horizontal = 8.dp), style = disp(36, FontWeight.Black), color = C.Text)
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                Text(m.away.upperGr(), style = disp(18), color = if (m.aek == "away") C.Gold else C.Text, maxLines = 2, softWrap = m.away.trim().contains(' '), overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                Crest(m.awayLogo, m.away, 40.dp)
            }
        }
    }
}

/** Μεσαία κάρτα: πρώτο άρθρο κάθε ενότητας στην αρχική. */
@Composable
fun PostCardMedium(p: PostCard, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(16.dp)).background(C.Panel2)) {
            if (p.image.isNotBlank()) AsyncImage(p.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (p.video) PlayBadge(Modifier.align(Alignment.Center), 52.dp)
        }
        Text(p.title, style = body(18, FontWeight.ExtraBold).copy(lineHeight = 23.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(postDate(p.date, withYear = false), style = body(12, color = C.Muted))
    }
}

/** Live αγώνας στη θέση του κύριου θέματος (όπως στο site όσο τρέχει το ματς). */
@Composable
fun LiveMatchHero(m: MatchCard, image: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(20.dp)).background(C.Panel).border(1.5.dp, C.Live, RoundedCornerShape(20.dp)).clickable(onClick = onClick)) {
        if (image.isNotBlank()) AsyncImage(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(gr.aekhub.app.R.drawable.match_hero_bg), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to C.Ink.copy(alpha = 0.55f), 1f to C.Ink.copy(alpha = 0.95f))))
        Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.clip(RoundedCornerShape(6.dp)).background(C.Live).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    LiveDot(Color.White)
                    val mn = liveMinute(m.clock, m.minute)
                    Text("LIVE" + if (mn.isNotBlank()) " · $mn" else "", style = body(11, FontWeight.ExtraBold, Color.White))
                }
                Spacer(Modifier.weight(1f))
                Text(matchMeta(m), style = body(10, FontWeight.ExtraBold, C.Gold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Crest(m.homeLogo, m.home, 58.dp); Text(m.home.upperGr(), style = disp(17), color = if (m.aek == "home") C.Gold else C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("${m.scoreHome.ifBlank { "0" }} : ${m.scoreAway.ifBlank { "0" }}", style = disp(52, FontWeight.Black), color = C.Text)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Crest(m.awayLogo, m.away, 58.dp); Text(m.away.upperGr(), style = disp(17), color = if (m.aek == "away") C.Gold else C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text("ΠΑΤΑ ΓΙΑ ΤΟ MATCH CENTER ›", style = body(11, FontWeight.ExtraBold, C.Gold).copy(letterSpacing = 1.sp))
        }
    }
}

/** Live γεγονός (συνέντευξη, παρουσίαση…) στη θέση του κύριου θέματος. */
@Composable
fun LiveEventHero(title: String, image: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(20.dp)).background(C.Panel).border(1.5.dp, C.Live, RoundedCornerShape(20.dp)).clickable(onClick = onClick)) {
        if (image.isNotBlank()) AsyncImage(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(gr.aekhub.app.R.drawable.match_hero_bg), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.2f to Color.Transparent, 1f to C.Ink)))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.clip(RoundedCornerShape(6.dp)).background(C.Live).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                LiveDot(Color.White); Text("LIVE ΤΩΡΑ", style = body(11, FontWeight.ExtraBold, Color.White))
            }
            Text(title.upperGr(), style = disp(28), color = C.Text, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Σήμα ▶ πάνω στις κάρτες των video άρθρων. */
@Composable
fun PlayBadge(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 40.dp) =
    Box(modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape).background(C.Gold), contentAlignment = Alignment.Center) {
        Icon(androidx.compose.material.icons.Icons.Filled.PlayArrow, "Βίντεο", tint = C.Ink, modifier = Modifier.size(size * 0.62f))
    }

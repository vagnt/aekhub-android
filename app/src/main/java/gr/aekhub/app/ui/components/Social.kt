package gr.aekhub.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gr.aekhub.app.R
import gr.aekhub.app.data.Social
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body
import gr.aekhub.app.ui.theme.disp

private fun socialIcon(key: String) = when (key) {
    "facebook" -> R.drawable.ic_soc_facebook
    "instagram" -> R.drawable.ic_soc_instagram
    "youtube" -> R.drawable.ic_soc_youtube
    "tiktok" -> R.drawable.ic_soc_tiktok
    else -> R.drawable.ic_soc_x
}

private fun socialBrush(key: String): Brush = when (key) {
    "facebook" -> Brush.linearGradient(listOf(Color(0xFF1877F2), Color(0xFF0E5FD0)))
    "instagram" -> Brush.linearGradient(listOf(Color(0xFFFEDA75), Color(0xFFFA7E1E), Color(0xFFD62976), Color(0xFF962FBF), Color(0xFF4F5BD5)))
    "youtube" -> Brush.linearGradient(listOf(Color(0xFFFF0000), Color(0xFFC4302B)))
    "tiktok" -> Brush.linearGradient(listOf(Color(0xFF25F4EE), Color(0xFF000000), Color(0xFFFE2C55)))
    else -> Brush.linearGradient(listOf(Color(0xFF2A2A2A), Color(0xFF000000)))
}

private fun socialCta(key: String) = when (key) {
    "instagram" -> "Stories & reels από κάθε ματς"
    "youtube" -> "Βίντεο, συνεντεύξεις, στιγμιότυπα"
    "tiktok" -> "Τα πιο viral της ΑΕΚ"
    "facebook" -> "Συζήτηση με την κερκίδα"
    else -> "Πρώτοι στα έκτακτα"
}

/** Ανοίγει το προφίλ στην εφαρμογή του δικτύου (αν υπάρχει), αλλιώς όπου μπορεί. */
fun openUrl(ctx: android.content.Context, url: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

/** Μεγάλη ενότητα για την αρχική: κάρτες ανά δίκτυο. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SocialSection(list: List<Social>) {
    if (list.isEmpty()) return
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Panel).border(1.dp, C.Line, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.nav_eagle), null, Modifier.size(40.dp), contentScale = ContentScale.Fit)
            Column {
                Text("ΑΚΟΛΟΥΘΗΣΕ ΤΟ AEK HUB", style = disp(22), color = C.Text)
                Text("Ό,τι γίνεται στην ΑΕΚ, πρώτα στα social μας", style = body(12, color = C.Muted))
            }
        }
        list.forEach { s ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(socialBrush(s.key)).clickable { openUrl(ctx, s.url) }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                    Icon(painterResource(socialIcon(s.key)), s.label, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(s.label + if (s.handle.isNotBlank()) "  " + s.handle else "", style = body(15, FontWeight.ExtraBold, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(socialCta(s.key), style = body(12, FontWeight.Bold, Color.White.copy(alpha = 0.85f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text("Follow", style = body(12, FontWeight.ExtraBold, Color.Black))
                }
            }
        }
    }
}

/** Συμπαγής σειρά με στρογγυλά εικονίδια (τέλος άρθρου, «Εγώ»). */
@Composable
fun SocialRow(list: List<Social>, title: String = "Ακολούθησε το AEK Hub", modifier: Modifier = Modifier) {
    if (list.isEmpty()) return
    val ctx = LocalContext.current
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Panel).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title.upperGr(), style = disp(18), color = C.Text)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            list.forEach { s ->
                Box(Modifier.size(46.dp).clip(CircleShape).background(socialBrush(s.key)).clickable { openUrl(ctx, s.url) }, contentAlignment = Alignment.Center) {
                    Icon(painterResource(socialIcon(s.key)), s.label, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

/** Εικονίδιο κατηγορίας (ίδια με τη μπάρα του site). */
fun catIcon(key: String) = when (key) {
    "football" -> R.drawable.nav_football
    "basketball" -> R.drawable.nav_basketball
    "erasitechniki" -> R.drawable.nav_erasitechniki
    "hubplus" -> R.drawable.nav_hubplus
    "programma" -> R.drawable.nav_programma
    "home" -> R.drawable.nav_home
    else -> R.drawable.nav_eagle
}

/** Μενού κατηγοριών: πλακίδια με τα εικονίδια του site. */
@Composable
fun CategoryMenu(items: List<Triple<String, String, String>>, onPick: (String, String) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.lazy.LazyRow(modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items.size) { i ->
            val (slug, name, icon) = items[i]
            Column(
                Modifier.size(width = 84.dp, height = 92.dp).clip(RoundedCornerShape(16.dp)).background(C.Panel).border(1.dp, C.Line, RoundedCornerShape(16.dp))
                    .clickable { onPick(slug, name) }.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            ) {
                Image(painterResource(catIcon(icon)), null, Modifier.size(38.dp), contentScale = ContentScale.Fit)
                FitText(name, style = body(11, FontWeight.ExtraBold, C.Text), textAlign = androidx.compose.ui.text.style.TextAlign.Center, minSize = 8f)
            }
        }
    }
}


/** Λεπτή λωρίδα στην κορυφή της αρχικής: «Ακολούθησέ μας» + εικονίδια. */
@Composable
fun SocialStrip(list: List<Social>) {
    if (list.isEmpty()) return
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Panel).border(1.dp, C.Line, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            FitText("ΑΚΟΛΟΥΘΗΣΕ ΜΑΣ", style = disp(16), color = C.Gold, minSize = 11f)
            Text(list.firstOrNull { it.handle.isNotBlank() }?.handle ?: "AEK Hub", style = body(11, FontWeight.Bold, C.Muted), maxLines = 1)
        }
        list.forEach { s ->
            Box(Modifier.size(36.dp).clip(CircleShape).background(socialBrush(s.key)).clickable { openUrl(ctx, s.url) }, contentAlignment = Alignment.Center) {
                Icon(painterResource(socialIcon(s.key)), s.label, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

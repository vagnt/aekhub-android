package gr.aekhub.app.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import gr.aekhub.app.BuildConfig
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body
import kotlinx.coroutines.delay

/**
 * Βίντεο μέσα στο άρθρο: εικόνα + κουμπί ▶. Με το πάτημα παίζει ΕΚΕΙ, μέσα στο app
 * (YouTube / Vimeo / Facebook / mp4 / embed παρόχου), με πλήρη οθόνη από τον player.
 */
@Composable
fun InlineVideo(embed: String, thumb: String, modifier: Modifier = Modifier, rawHtml: String = "", label: String = "") {
    var play by rememberSaveable(embed + rawHtml) { mutableStateOf(false) }
    Box(modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Color.Black)) {
        if (play) VideoPlayer(embed, Modifier.fillMaxSize(), rawHtml)
        else Box(Modifier.fillMaxSize().clickable { play = true }, contentAlignment = Alignment.Center) {
            if (thumb.isNotBlank()) AsyncImage(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
            Box(Modifier.size(68.dp).clip(CircleShape).background(C.Gold), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, "Αναπαραγωγή", tint = C.Ink, modifier = Modifier.size(42.dp))
            }
            if (label.isNotBlank()) Text(label, Modifier.align(Alignment.BottomStart).padding(12.dp), style = body(12, FontWeight.ExtraBold, Color.White))
        }
    }
}

/** Τι είδους social post είναι (για τον επίσημο τρόπο ενσωμάτωσης κάθε πλατφόρμας). */
fun socialKind(url: String): String? = when {
    Regex("""instagram\.com/(p|reel|tv)/""").containsMatchIn(url) -> "instagram"
    Regex("""tiktok\.com/.+/video/\d+|vm\.tiktok\.com/|tiktok\.com/t/""").containsMatchIn(url) -> "tiktok"
    Regex("""(twitter|x)\.com/[^/]+/status/\d+""").containsMatchIn(url) -> "x"
    // Facebook: posts, βίντεο, reels, φωτογραφίες, share links, permalink/story.php
    Regex("""(facebook\.com|fb\.com)/(.+/(posts|videos|photos|reel|reels)/|share/|reel/|watch|permalink\.php|story\.php|photo\.php|photo/|groups/.+/(posts|permalink)/|[^/?]+/(posts|videos)/)|fb\.watch/""").containsMatchIn(url) -> "facebook"
    else -> null
}

private fun socialHtml(kind: String, url: String): String {
    val u = url.replace("\"", "&quot;")
    return when (kind) {
        "instagram" -> """<blockquote class="instagram-media" data-instgrm-permalink="$u" data-instgrm-version="14" data-instgrm-captioned style="width:100%;min-width:0;margin:0;border:0"></blockquote><script async src="https://www.instagram.com/embed.js"></script>"""
        "tiktok" -> {
            val id = Regex("""/video/(\d+)""").find(url)?.groupValues?.get(1) ?: ""
            """<blockquote class="tiktok-embed" cite="$u" data-video-id="$id" style="max-width:100%;min-width:0;margin:0"><section></section></blockquote><script async src="https://www.tiktok.com/embed.js"></script>"""
        }
        "x" -> """<blockquote class="twitter-tweet" data-theme="dark" data-dnt="true" lang="el"><a href="${u.replace("x.com/", "twitter.com/")}"></a></blockquote><script async src="https://platform.twitter.com/widgets.js"></script>"""
        // Facebook: το επίσημο SDK ζωγραφίζει το post και του δίνει το σωστό ύψος
        else -> {
            val video = Regex("""/videos/|/reel|fb\.watch|/watch""").containsMatchIn(url)
            """<div id="fb-root"></div><div class="${if (video) "fb-video" else "fb-post"}" data-href="$u" data-width="auto" data-show-text="true" data-allowfullscreen="true"></div><script async defer crossorigin="anonymous" src="https://connect.facebook.net/el_GR/sdk.js#xfbml=1&version=v19.0"></script>"""
        }
    }
}

/**
 * Social post όπως στο site (Instagram, TikTok, X, Facebook): ο επίσημος κώδικας της κάθε
 * πλατφόρμας μέσα σε μικρό «παράθυρο» που παίρνει μόνο του το ύψος του post.
 * Ό,τι πατηθεί μέσα του (προφίλ, link) ανοίγει στην αντίστοιχη εφαρμογή.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SocialEmbed(url: String, kind: String, modifier: Modifier = Modifier) {
    var h by remember(url) { mutableIntStateOf(if (kind == "tiktok") 720 else if (kind == "x") 420 else 560) }
    var web by remember { mutableStateOf<WebView?>(null) }
    // Το post «ζωγραφίζεται» σε λίγα δευτερόλεπτα: μετράμε το ύψος του μέχρι να σταθεροποιηθεί
    LaunchedEffect(web) {
        val w = web ?: return@LaunchedEffect
        repeat(30) {
            delay(500)
            w.evaluateJavascript("(function(){var b=document.body,e=document.documentElement;return Math.max(b.scrollHeight,b.offsetHeight,e.scrollHeight)})()") { v ->
                v?.toDoubleOrNull()?.toInt()?.takeIf { it in 120..2400 }?.let { if (kotlin.math.abs(it - h) > 4) h = it }
            }
        }
    }
    val html = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>html,body{margin:0;padding:0;background:#141413;color:#eee;overflow:hidden}body>*{margin:0 auto!important}</style></head>
        <body>${socialHtml(kind, url)}</body></html>"""
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(h.dp).clip(RoundedCornerShape(14.dp)).background(C.Panel),
            factory = { c ->
                WebView(c).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(android.graphics.Color.parseColor("#141413"))
                    isVerticalScrollBarEnabled = false
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                            if (!req.isForMainFrame || !req.hasGesture()) return false
                            runCatching { c.startActivity(Intent(Intent.ACTION_VIEW, req.url)) }; return true
                        }
                    }
                    loadDataWithBaseURL(BuildConfig.SITE + "/", html, "text/html", "utf-8", null)
                    web = this
                }
            },
            onRelease = { it.stopLoading(); it.destroy() },
        )
    }
}

package gr.aekhub.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body
import gr.aekhub.app.ui.theme.disp
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** Το άρθρο γίνεται «κομμάτια» που ζωγραφίζει το ίδιο το app — χωρίς browser. */
sealed interface Block {
    data class Para(val text: AnnotatedString) : Block
    data class Heading(val text: String, val level: Int) : Block
    data class Img(val url: String, val caption: String) : Block
    data class Quote(val text: AnnotatedString) : Block
    data class Bullets(val items: List<AnnotatedString>, val ordered: Boolean) : Block
    data class Video(val youtubeId: String) : Block
    data class Embed(val url: String, val label: String) : Block
    data object Rule : Block
}

object Html {
    private val linkStyle = TextLinkStyles(SpanStyle(color = C.Gold, textDecoration = TextDecoration.Underline))

    fun parse(html: String, base: String): List<Block> {
        val doc = Jsoup.parseBodyFragment(html, base)
        val out = mutableListOf<Block>()
        doc.body().children().forEach { walk(it, out) }
        return out.filterNot { it is Block.Para && it.text.isBlank() }
    }

    private fun ytId(src: String): String? =
        Regex("""(?:youtube(?:-nocookie)?\.com/(?:embed/|watch\?v=|shorts/|live/)|youtu\.be/)([A-Za-z0-9_-]{6,})""").find(src)?.groupValues?.get(1)

    private fun imgSrc(e: Element): String {
        val cands = listOf("data-src", "data-lazy-src", "src").map { e.absUrl(it) }.filter { it.isNotBlank() && !it.startsWith("data:") }
        val srcset = (e.attr("data-srcset").ifBlank { e.attr("srcset") }).split(",").map { it.trim().split(" ") }
            .filter { it.size >= 2 }.maxByOrNull { it[1].removeSuffix("w").toIntOrNull() ?: 0 }?.get(0)
        return srcset?.takeIf { it.startsWith("http") } ?: cands.firstOrNull() ?: ""
    }

    private fun embedLabel(url: String) = when {
        "twitter.com" in url || "x.com" in url -> "Δες το post στο X"
        "instagram.com" in url -> "Δες το post στο Instagram"
        "facebook.com" in url -> "Δες το post στο Facebook"
        "tiktok.com" in url -> "Δες το βίντεο στο TikTok"
        else -> "Άνοιγμα συνδέσμου"
    }

    private fun walk(e: Element, out: MutableList<Block>) {
        when (e.tagName()) {
            "script", "style", "noscript", "form", "button", "svg" -> return
            "p" -> {
                val imgs = e.select("img"); val ifr = e.selectFirst("iframe")
                if (ifr != null) { iframe(ifr, out) }
                if (imgs.isNotEmpty() && e.text().isBlank()) { imgs.forEach { out += Block.Img(imgSrc(it), "") }; return }
                out += Block.Para(inline(e))
            }
            "h1", "h2", "h3", "h4", "h5", "h6" -> if (e.text().isNotBlank()) out += Block.Heading(e.text(), e.tagName()[1].digitToInt())
            "img" -> out += Block.Img(imgSrc(e), "")
            "figure" -> {
                val cap = e.selectFirst("figcaption")?.text() ?: ""
                val ifr = e.selectFirst("iframe"); val bq = e.selectFirst("blockquote"); val img = e.selectFirst("img")
                when {
                    ifr != null -> iframe(ifr, out)
                    bq != null -> walk(bq, out)
                    img != null -> out += Block.Img(imgSrc(img), cap)
                    else -> {
                        // WordPress embed χωρίς iframe: το URL είναι σκέτο κείμενο μέσα στο wrapper
                        val url = e.selectFirst("a[href]")?.absUrl("href") ?: e.text().trim().takeIf { it.startsWith("http") }
                        if (url != null) { val y = ytId(url); out += if (y != null) Block.Video(y) else Block.Embed(url, embedLabel(url)) }
                    }
                }
            }
            "iframe" -> iframe(e, out)
            "blockquote" -> {
                val cls = e.className()
                if ("twitter-tweet" in cls || "instagram-media" in cls || "tiktok-embed" in cls || e.hasAttr("data-instgrm-permalink")) {
                    val url = e.attr("data-instgrm-permalink").ifBlank { e.attr("cite") }.ifBlank { e.select("a[href]").lastOrNull()?.absUrl("href") ?: "" }
                    if (url.isNotBlank()) out += Block.Embed(url, embedLabel(url))
                } else if (e.text().isNotBlank()) out += Block.Quote(inline(e))
            }
            "ul", "ol" -> {
                val items = e.children().filter { it.tagName() == "li" }.map { inline(it) }
                if (items.isNotEmpty()) out += Block.Bullets(items, e.tagName() == "ol")
            }
            "hr" -> out += Block.Rule
            "table" -> e.select("tr").forEach { tr ->
                val t = tr.children().joinToString("  ·  ") { it.text() }
                if (t.isNotBlank()) out += Block.Para(AnnotatedString(t))
            }
            else -> {
                if (e.children().isEmpty()) { if (e.text().isNotBlank()) out += Block.Para(inline(e)) }
                else {
                    // div με κείμενο ανάμεσα στα παιδιά: κείμενο + παιδιά με τη σειρά
                    val hasLooseText = e.textNodes().any { it.text().isNotBlank() }
                    if (hasLooseText && e.select("p,div,figure,img,iframe,ul,ol,blockquote,h2,h3").isEmpty()) out += Block.Para(inline(e))
                    else e.children().forEach { walk(it, out) }
                }
            }
        }
    }

    private fun iframe(e: Element, out: MutableList<Block>) {
        val src = e.absUrl("src").ifBlank { e.absUrl("data-src") }
        val y = ytId(src)
        if (y != null) out += Block.Video(y) else if (src.isNotBlank()) out += Block.Embed(src, embedLabel(src))
    }

    fun inline(root: Element): AnnotatedString = buildAnnotatedString {
        fun rec(n: Node) {
            when (n) {
                is TextNode -> append(n.text().replace(Regex("\\s+"), " "))
                is Element -> when (n.tagName()) {
                    "br" -> append("\n")
                    "strong", "b" -> withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = C.Text)) { n.childNodes().forEach { rec(it) } }
                    "em", "i" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { n.childNodes().forEach { rec(it) } }
                    "a" -> {
                        val href = n.absUrl("href")
                        if (href.isNotBlank()) withLink(LinkAnnotation.Url(href, linkStyle)) { n.childNodes().forEach { rec(it) } }
                        else n.childNodes().forEach { rec(it) }
                    }
                    "img", "script", "style", "iframe" -> {}
                    else -> n.childNodes().forEach { rec(it) }
                }
            }
        }
        root.childNodes().forEach { rec(it) }
    }.let { s -> if (s.text.isNotEmpty() && s.text.first() == ' ') s.subSequence(1, s.length) else s }
}

@Composable
fun BlockView(b: Block, scale: Float) {
    val ctx = LocalContext.current
    fun open(url: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    val pad = Modifier.padding(horizontal = 20.dp)
    when (b) {
        is Block.Para -> Text(b.text, pad, style = body((16 * scale).toInt(), color = C.Text2).copy(lineHeight = (16 * scale * 1.7f).sp))
        is Block.Heading -> Text(b.text, pad.padding(top = 6.dp), style = if (b.level <= 2) disp((26 * scale).toInt()) else body((18 * scale).toInt(), FontWeight.ExtraBold), color = C.Text)
        is Block.Quote -> Row(pad.padding(vertical = 4.dp).height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(C.Gold))
            Text(
                b.text, Modifier.padding(start = 14.dp),
                style = disp((24 * scale).toInt(), FontWeight.Bold).copy(lineHeight = (24 * scale * 1.12f).sp), color = C.Text,
            )
        }
        is Block.Bullets -> Column(pad, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            b.items.forEachIndexed { i, t ->
                Row {
                    Text(if (b.ordered) "${i + 1}." else "•", Modifier.width(22.dp), style = body((16 * scale).toInt(), FontWeight.ExtraBold, C.Gold))
                    Text(t, style = body((16 * scale).toInt(), color = C.Text2).copy(lineHeight = (16 * scale * 1.6f).sp))
                }
            }
        }
        is Block.Img -> if (b.url.isNotBlank()) Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            AsyncImage(b.url, null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
            if (b.caption.isNotBlank()) Text(b.caption, pad.padding(top = 6.dp), style = body(12, color = C.Muted))
        }
        is Block.Video -> Box(
            pad.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).clickable { open("https://www.youtube.com/watch?v=" + b.youtubeId) },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage("https://img.youtube.com/vi/${b.youtubeId}/hqdefault.jpg", null, Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentScale = ContentScale.Crop)
            Box(Modifier.size(64.dp).clip(CircleShape).background(C.Gold), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, "Αναπαραγωγή", tint = C.Ink, modifier = Modifier.size(38.dp))
            }
        }
        is Block.Embed -> Row(
            pad.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Panel).border(1.dp, C.Line, RoundedCornerShape(14.dp))
                .clickable { open(b.url) }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = C.Gold)
            Text(b.label, style = body(15, FontWeight.Bold))
        }
        Block.Rule -> Box(pad.fillMaxWidth().height(1.dp).background(C.Line))
    }
}

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
import gr.aekhub.app.data.Standing
import gr.aekhub.app.data.StandingRow
import gr.aekhub.app.data.Zone
import gr.aekhub.app.ui.screens.StandingTable
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
    data class Video(val embed: String, val thumb: String) : Block
    data class Social(val kind: String, val url: String) : Block
    data class Embed(val url: String, val label: String) : Block
    data class Table(val standing: Standing) : Block
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

    /** Link βίντεο → (embed για τον player, εικόνα). null αν δεν είναι βίντεο. */
    private fun video(src: String, poster: String = ""): Block.Video? {
        ytId(src)?.let { return Block.Video("https://www.youtube.com/embed/$it?autoplay=1&playsinline=1&rel=0&modestbranding=1", "https://i.ytimg.com/vi/$it/hqdefault.jpg") }
        Regex("""vimeo\.com/(?:video/)?(\d+)""").find(src)?.let { return Block.Video("https://player.vimeo.com/video/${it.groupValues[1]}?autoplay=1", poster) }
        Regex("""dailymotion\.com/(?:embed/)?video/([a-zA-Z0-9]+)|dai\.ly/([a-zA-Z0-9]+)""").find(src)?.let { m ->
            val id = m.groupValues[1].ifBlank { m.groupValues[2] }
            return Block.Video("https://www.dailymotion.com/embed/video/$id?autoplay=1", "https://www.dailymotion.com/thumbnail/video/$id")
        }
        if ("facebook.com/plugins/video" in src) return Block.Video(src, poster)
        if (Regex("""(facebook\.com/.+/videos/|fb\.watch/)""").containsMatchIn(src)) return Block.Video("https://www.facebook.com/plugins/video.php?href=" + java.net.URLEncoder.encode(src, "UTF-8") + "&show_text=false&autoplay=true", poster)
        if (Regex("""\.(mp4|webm|m4v|mov|m3u8)(\?|$)""", RegexOption.IGNORE_CASE).containsMatchIn(src)) return Block.Video(src, poster)
        return null
    }

    /** Οποιοσδήποτε σύνδεσμος ενσωμάτωσης → το σωστό κομμάτι: βίντεο, social post ή κάρτα. */
    private fun media(url: String, out: MutableList<Block>, poster: String = "") {
        if (url.isBlank()) return
        val sk = socialKind(url)
        when {
            sk != null && !("facebook" == sk && "/videos/" in url) -> out += Block.Social(sk, url)
            else -> out += video(url, poster) ?: Block.Embed(url, embedLabel(url))
        }
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
                // Σκέτο link βίντεο/post σε δική του γραμμή (όπως το «μετατρέπει» το WordPress)
                val only = e.text().trim()
                if (ifr == null && imgs.isEmpty() && only.startsWith("http") && ' ' !in only && (video(only) != null || socialKind(only) != null)) { media(only, out); return }
                // Παράγραφος που είναι μόνο ένα link σε post (το WordPress δεν «ενσωματώνει» πια Facebook): γίνεται το ίδιο το post
                val a1 = e.children().singleOrNull()?.takeIf { it.tagName() == "a" && e.ownText().isBlank() }?.absUrl("href")
                if (a1 != null && socialKind(a1) != null) { media(a1, out); return }
                if (imgs.isNotEmpty() && e.text().isBlank()) { imgs.forEach { out += Block.Img(imgSrc(it), "") }; return }
                out += Block.Para(inline(e))
            }
            "h1", "h2", "h3", "h4", "h5", "h6" -> if (e.text().isNotBlank()) out += Block.Heading(e.text(), e.tagName()[1].digitToInt())
            "img" -> out += Block.Img(imgSrc(e), "")
            "figure" -> {
                val cap = e.selectFirst("figcaption")?.text() ?: ""
                val ifr = e.selectFirst("iframe"); val bq = e.selectFirst("blockquote"); val img = e.selectFirst("img"); val vid = e.selectFirst("video")
                when {
                    vid != null -> videoTag(vid, out)
                    ifr != null -> iframe(ifr, out)
                    bq != null -> walk(bq, out)
                    img != null && !e.hasClass("wp-block-embed") -> out += Block.Img(imgSrc(img), cap)
                    else -> {
                        // WordPress embed χωρίς iframe: το URL είναι σκέτο κείμενο μέσα στο wrapper
                        val url = e.selectFirst(".wp-block-embed__wrapper")?.text()?.trim()?.takeIf { it.startsWith("http") }
                            ?: e.selectFirst("a[href]")?.absUrl("href") ?: e.text().trim().takeIf { it.startsWith("http") }
                        if (url != null) media(url, out)
                    }
                }
            }
            "iframe" -> iframe(e, out)
            "video" -> videoTag(e, out)
            // Facebook (Jetpack / SDK): <fb:post href>, <div class="fb-post|fb-video" data-href>
            "fb:post", "fb:video" -> media(e.absUrl("href").ifBlank { e.attr("data-href") }, out)
            "blockquote" -> {
                val cls = e.className()
                if ("twitter-tweet" in cls || "instagram-media" in cls || "tiktok-embed" in cls || e.hasAttr("data-instgrm-permalink")) {
                    val url = e.attr("data-instgrm-permalink").ifBlank { e.attr("cite") }.ifBlank { e.select("a[href]").lastOrNull()?.absUrl("href") ?: "" }
                    if (url.isNotBlank()) media(url.substringBefore("?utm"), out)
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
                // Shortcode βαθμολογίας του site → ο ίδιος πίνακας με την οθόνη «Βαθμολογίες»
                if (e.hasClass("aekstd-table-wrap")) { standing(e)?.let { out += Block.Table(it) }; return }
                val dh = e.attr("data-href").ifBlank { e.attr("data-instgrm-permalink") }
                if (dh.startsWith("http") && (socialKind(dh) != null || e.hasClass("fb-post") || e.hasClass("fb-video"))) { media(dh, out); return }
                // Σκέτος σύνδεσμος σε social post/βίντεο, μόνος του μέσα σε div
                val lone = e.children().singleOrNull()?.takeIf { it.tagName() == "a" && e.ownText().isBlank() }?.absUrl("href")
                if (lone != null && (socialKind(lone) != null || video(lone) != null)) { media(lone, out); return }
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

    /** [aekhub_standings]: τίτλος, γραμμές με θέση/σήμα/αριθμούς ανά στήλη, χρώματα ζωνών και υπόμνημα. */
    private fun standing(e: Element): Standing? {
        val heads = e.select("thead th").map { it.text().trim() }
        fun col(tr: Element, h: String) = heads.indexOf(h).takeIf { it >= 0 }?.let { tr.children().getOrNull(it)?.text()?.trim() } ?: ""
        val zoneOf = mutableMapOf<Int, Pair<String, String>>()
        val rows = e.select("tbody tr").mapNotNull { tr ->
            val pos = tr.selectFirst(".aekstd-pos")?.text()?.trim()?.toIntOrNull() ?: return@mapNotNull null
            val cell = tr.selectFirst("td.aekstd-th-pos")
            Regex("""--z:\s*(#[0-9A-Fa-f]{3,8})""").find(cell?.attr("style") ?: "")?.let { zoneOf[pos] = it.groupValues[1] to cell!!.attr("title") }
            val goals = col(tr, "Γκολ").split("-", ":").map { it.trim() }
            StandingRow(
                pos = pos, name = tr.selectFirst(".aekstd-team-name")?.text()?.trim() ?: "",
                logo = tr.selectFirst("img.aekstd-logo")?.let { imgSrc(it) } ?: "",
                played = col(tr, "Αγ"), won = col(tr, "Ν"), drawn = col(tr, "Ι"), lost = col(tr, "Η"),
                gf = goals.getOrNull(0) ?: "", ga = goals.getOrNull(1) ?: "",
                points = col(tr, "Β"), aek = tr.hasClass("aekstd-highlight"),
            )
        }
        if (rows.isEmpty()) return null
        // Υπόμνημα του site («1-4 Play-off τίτλου»). Αλλιώς οι ζώνες από τα χρώματα των γραμμών.
        val legend = e.select(".aekstd-legend > span").mapNotNull { sp ->
            val color = Regex("""background:\s*(#[0-9A-Fa-f]{3,8})""").find(sp.selectFirst("i")?.attr("style") ?: "")?.groupValues?.get(1) ?: return@mapNotNull null
            val range = sp.selectFirst("b")?.text()?.split("-")?.mapNotNull { it.trim().toIntOrNull() } ?: return@mapNotNull null
            val from = range.firstOrNull() ?: return@mapNotNull null
            Zone(from, range.getOrElse(1) { from }, sp.ownText().trim(), color)
        }
        val zones = legend.ifEmpty {
            zoneOf.toSortedMap().entries.fold(mutableListOf<Zone>()) { acc, (p, z) ->
                val last = acc.lastOrNull()
                if (last != null && last.color == z.first && last.label == z.second && last.to == p - 1) acc[acc.size - 1] = last.copy(to = p)
                else acc += Zone(p, p, z.second, z.first)
                acc
            }
        }
        return Standing(
            id = 0, title = e.selectFirst(".aekstd-title-txt")?.text()?.trim() ?: "",
            style = if ("Ι" in heads) "wdl" else "wl",
            leagueLogo = e.selectFirst("img.aekstd-title-logo")?.let { imgSrc(it) } ?: "",
            zones = zones, rows = rows,
        )
    }

    private fun iframe(e: Element, out: MutableList<Block>) {
        val src = e.absUrl("src").ifBlank { e.absUrl("data-src") }.ifBlank { e.absUrl("data-lazy-src") }
        if (src.isBlank()) return
        // iframe ενσωμάτωσης social → το ίδιο το post
        val ig = Regex("""instagram\.com/(p|reel|tv)/([^/?]+)""").find(src)
        if (ig != null) { out += Block.Social("instagram", "https://www.instagram.com/${ig.groupValues[1]}/${ig.groupValues[2]}/"); return }
        Regex("""tiktok\.com/embed(?:/v2)?/(\d+)""").find(src)?.let { out += Block.Social("tiktok", "https://www.tiktok.com/@/video/${it.groupValues[1]}"); return }
        Regex("""[?&]id=(\d+)""").find(src)?.takeIf { "platform.twitter.com" in src }?.let { out += Block.Social("x", "https://twitter.com/i/status/${it.groupValues[1]}"); return }
        if ("facebook.com/plugins/post" in src) Regex("""href=([^&]+)""").find(src)?.let { out += Block.Social("facebook", java.net.URLDecoder.decode(it.groupValues[1], "UTF-8")); return }
        // κάθε άλλο iframe (βίντεο οποιουδήποτε παρόχου) παίζει μέσα στο app
        out += video(src) ?: Block.Video(src, "")
    }

    private fun videoTag(e: Element, out: MutableList<Block>) {
        val src = e.absUrl("src").ifBlank { e.selectFirst("source[src]")?.absUrl("src") ?: "" }
        if (src.isNotBlank()) out += Block.Video(src, e.absUrl("poster"))
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
        is Block.Para -> Text(b.text, pad, style = body((17 * scale).toInt(), color = C.Text.copy(alpha = 0.88f)).copy(lineHeight = (17 * scale * 1.72f).sp))
        is Block.Heading -> Row(pad.padding(top = 10.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(C.Gold))
            Text(b.text.upperGr(), style = if (b.level <= 2) disp((26 * scale).toInt()) else disp((22 * scale).toInt()), color = C.Text)
        }
        is Block.Quote -> Column(pad.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Panel).padding(18.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("“", style = disp(56, FontWeight.Black).copy(lineHeight = 40.sp), color = C.Gold)
            Text(b.text, style = disp((23 * scale).toInt(), FontWeight.Bold).copy(lineHeight = (23 * scale * 1.15f).sp), color = C.Text)
        }
        is Block.Bullets -> Column(pad, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            b.items.forEachIndexed { i, t ->
                Row {
                    Text(if (b.ordered) "${i + 1}." else "•", Modifier.width(22.dp), style = body((16 * scale).toInt(), FontWeight.ExtraBold, C.Gold))
                    Text(t, style = body((16 * scale).toInt(), color = C.Text2).copy(lineHeight = (16 * scale * 1.6f).sp))
                }
            }
        }
        is Block.Img -> if (b.url.isNotBlank()) Column(pad.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AsyncImage(b.url, null, Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Panel), contentScale = ContentScale.FillWidth)
            if (b.caption.isNotBlank()) Text(b.caption, style = body(12, color = C.Muted).copy(fontStyle = FontStyle.Italic))
        }
        is Block.Video -> InlineVideo(b.embed, b.thumb, pad)
        is Block.Social -> SocialEmbed(b.url, b.kind, pad)
        is Block.Embed -> Row(
            pad.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Panel).border(1.dp, C.Line, RoundedCornerShape(14.dp))
                .clickable { open(b.url) }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = C.Gold)
            Text(b.label, style = body(15, FontWeight.Bold))
        }
        is Block.Table -> Box(pad.fillMaxWidth()) { StandingTable(b.standing) }
        Block.Rule -> Box(pad.fillMaxWidth().height(1.dp).background(C.Line))
    }
}

package gr.aekhub.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.aekhub.app.data.CItem
import gr.aekhub.app.data.CommentsApi
import gr.aekhub.app.data.Device
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body
import gr.aekhub.app.ui.theme.disp
import kotlinx.coroutines.launch

/**
 * 💬 Σχόλια άρθρου — ΤΑ ΙΔΙΑ με το site (ένα κοινό σύστημα): ό,τι γράφεται εδώ φαίνεται στο aekhub.gr και αντίστροφα.
 * Μόνο όνομα (χωρίς εγγραφή), likes, απαντήσεις, αναφορά, έγκριση/αντι-spam από τον server όπως στο site.
 */
@Composable
fun CommentsSection(postId: Long) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember(postId) { mutableStateOf<List<CItem>>(emptyList()) }
    var page by remember(postId) { mutableStateOf(1) }
    var more by remember(postId) { mutableStateOf(false) }
    var total by remember(postId) { mutableStateOf(0) }
    var open by remember(postId) { mutableStateOf(true) }
    var likesOn by remember(postId) { mutableStateOf(true) }
    var reportsOn by remember(postId) { mutableStateOf(true) }
    var maxLen by remember(postId) { mutableStateOf(0) }
    var sort by remember(postId) { mutableStateOf("new") }
    var loading by remember(postId) { mutableStateOf(true) }
    var failed by remember(postId) { mutableStateOf(false) }
    var name by remember { mutableStateOf(Device.name(ctx)) }
    var text by remember(postId) { mutableStateOf("") }
    var replyTo by remember(postId) { mutableStateOf<CItem?>(null) }
    var sending by remember(postId) { mutableStateOf(false) }
    var notice by remember(postId) { mutableStateOf("") }
    val opened = remember(postId) { System.currentTimeMillis() }

    fun load(reset: Boolean) {
        scope.launch {
            loading = true; failed = false
            try {
                val pg = if (reset) 1 else page + 1
                val r = CommentsApi.list(ctx, postId, sort, pg)
                items = if (reset) r.items else items + r.items.filter { n -> items.none { it.id == n.id } }
                page = pg; more = r.more; total = r.total; open = r.open; likesOn = r.likes; reportsOn = r.reports; maxLen = r.max
            } catch (e: Exception) { failed = true }
            loading = false
        }
    }
    LaunchedEffect(postId, sort) { load(true) }

    fun patch(id: Long, f: (CItem) -> CItem) { items = items.map { r -> if (r.id == id) f(r) else r.copy(replies = r.replies.map { if (it.id == id) f(it) else it }) } }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.width(4.dp).height(22.dp).background(C.Gold))
            Text("ΣΧΟΛΙΑ" + if (total > 0) " ($total)" else "", style = disp(22), color = C.Text)
        }
        // Ταξινόμηση
        if (total > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("new" to "Νεότερα", "old" to "Παλιότερα", "top" to "Δημοφιλή").forEach { (k, l) ->
                Text(l, Modifier.clip(RoundedCornerShape(99.dp)).background(if (sort == k) C.Gold else C.Panel2).border(1.dp, if (sort == k) C.Gold else C.Line2, RoundedCornerShape(99.dp))
                    .clickable { if (sort != k) { sort = k } }.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = body(12, FontWeight.ExtraBold, if (sort == k) C.Ink else C.Text2))
            }
        }
        // Γράψε σχόλιο
        if (open) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Panel).border(1.dp, C.Line, RoundedCornerShape(16.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val rt = replyTo
            if (rt != null) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.Panel2).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("↩ Απάντηση σε " + rt.name, Modifier.weight(1f), style = body(12, FontWeight.Bold, C.Gold))
                Icon(Icons.Filled.Close, "Ακύρωση απάντησης", tint = C.Muted, modifier = Modifier.size(18.dp).clickable { replyTo = null })
            }
            OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Το όνομά σου", style = body(14, color = C.Muted)) },
                textStyle = body(14), shape = RoundedCornerShape(12.dp), colors = fieldColors())
            OutlinedTextField(text, { text = if (maxLen > 0) it.take(maxLen) else it }, Modifier.fillMaxWidth(), minLines = 3, maxLines = 8,
                placeholder = { Text(if (rt != null) "Γράψε την απάντησή σου…" else "Γράψε το σχόλιό σου…", style = body(14, color = C.Muted)) },
                textStyle = body(14), shape = RoundedCornerShape(12.dp), colors = fieldColors())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (notice.isNotBlank()) notice else "Μόνο όνομα, χωρίς εγγραφή. Τα σχόλια φαίνονται και στο aekhub.gr.", Modifier.weight(1f),
                    style = body(11, color = if (notice.isNotBlank()) C.Gold else C.Muted).copy(lineHeight = 15.sp))
                val can = !sending && name.trim().length >= 2 && text.trim().length >= 2
                Row(Modifier.clip(RoundedCornerShape(12.dp)).background(if (can) C.Gold else C.Panel3).clickable(enabled = can) {
                    sending = true; notice = ""
                    scope.launch {
                        Device.setName(ctx, name.trim())
                        val r = CommentsApi.send(ctx, postId, name.trim(), text.trim(), replyTo?.id ?: 0L, System.currentTimeMillis() - opened)
                        sending = false
                        if (r.ok) { gr.aekhub.app.data.Analytics.event(ctx, "comment_post", "item_id" to postId); text = ""; replyTo = null; notice = if (r.approved) "✓ Δημοσιεύτηκε" else r.message.ifBlank { "Θα εμφανιστεί μόλις εγκριθεί." }; load(true) }
                        else notice = "⚠ " + r.message.ifBlank { "Δεν στάλθηκε" }
                    }
                }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, tint = if (can) C.Ink else C.Muted, modifier = Modifier.size(18.dp))
                    Text(if (sending) "Στέλνω…" else "Δημοσίευση", style = body(14, FontWeight.ExtraBold, if (can) C.Ink else C.Muted))
                }
            }
        } else if (total > 0) Text("Τα σχόλια έκλεισαν για αυτό το άρθρο.", style = body(13, color = C.Muted))

        // Λίστα
        items.forEach { c ->
            CommentCard(c, likesOn, reportsOn, open, { id, on -> scope.launch { patch(id) { it.copy(liked = on, likes = (it.likes + if (on) 1 else -1).coerceAtLeast(0)) }; val r = CommentsApi.like(ctx, id, on); if (r.ok) patch(id) { it.copy(liked = r.liked, likes = r.likes) } else patch(id) { it.copy(liked = !on, likes = (it.likes + if (on) -1 else 1).coerceAtLeast(0)) } } },
                { id -> scope.launch { val r = CommentsApi.report(ctx, id); notice = if (r.ok) "✓ Η αναφορά στάλθηκε — ευχαριστούμε." else "⚠ " + r.message; if (r.hidden) load(true) } }) { replyTo = it; notice = "" }
        }
        if (loading) Text("Φορτώνει σχόλια…", style = body(13, color = C.Muted))
        if (failed && !loading) Text("Δεν φορτώθηκαν τα σχόλια. Πάτα για να δοκιμάσεις ξανά.", Modifier.clickable { load(true) }, style = body(13, FontWeight.Bold, C.Gold))
        if (!loading && !failed && items.isEmpty() && open) Text("Κανένα σχόλιο ακόμα — γράψε το πρώτο!", style = body(14, color = C.Muted))
        if (more && !loading) Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Panel2).clickable { load(false) }.padding(12.dp), contentAlignment = Alignment.Center) {
            Text("Περισσότερα σχόλια", style = body(14, FontWeight.ExtraBold, C.Gold))
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = C.Text, unfocusedTextColor = C.Text, cursorColor = C.Gold, focusedBorderColor = C.Gold, unfocusedBorderColor = C.Line2,
    focusedContainerColor = C.Panel2, unfocusedContainerColor = C.Panel2,
)

@Composable
private fun CommentCard(c: CItem, likesOn: Boolean, reportsOn: Boolean, open: Boolean, like: (Long, Boolean) -> Unit, report: (Long) -> Unit, reply: (CItem) -> Unit) {
    var expanded by remember(c.id) { mutableStateOf(false) }
    val shown = if (expanded || c.replies.size <= 2) c.replies else c.replies.take(2)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Panel).border(1.dp, if (c.pinned) C.Gold.copy(alpha = 0.6f) else C.Line, RoundedCornerShape(16.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CommentBody(c, false, likesOn, reportsOn, open, like, report, reply)
        if (c.replies.isNotEmpty()) Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.padding(start = 18.dp).width(2.dp).fillMaxHeight().background(C.Line2))
            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                shown.forEach { r -> CommentBody(r, true, likesOn, reportsOn, open, like, report, reply) }
                if (c.replies.size > 2 && !expanded) Text("Δες " + (c.replies.size - 2) + (if (c.replies.size - 2 == 1) " ακόμη απάντηση" else " ακόμη απαντήσεις"), Modifier.clickable { expanded = true }, style = body(12, FontWeight.ExtraBold, C.Gold))
            }
        }
    }
}

@Composable
private fun CommentBody(c: CItem, isReply: Boolean, likesOn: Boolean, reportsOn: Boolean, open: Boolean, like: (Long, Boolean) -> Unit, report: (Long) -> Unit, reply: (CItem) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val size = if (isReply) 32.dp else 40.dp
    val col = runCatching { Color(android.graphics.Color.parseColor(c.color)) }.getOrDefault(C.Muted)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(size).clip(CircleShape).background(col), contentAlignment = Alignment.Center) { Text(c.ini, style = body(if (isReply) 12 else 14, FontWeight.ExtraBold, Color.White)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(c.name, style = body(14, FontWeight.ExtraBold), maxLines = 1)
                if (c.staff) Text("ΣΥΝΤΑΚΤΗΣ", Modifier.clip(RoundedCornerShape(5.dp)).background(C.Gold).padding(horizontal = 6.dp, vertical = 2.dp), style = body(9, FontWeight.ExtraBold, C.Ink))
                if (c.pinned) Icon(Icons.Filled.PushPin, "Καρφιτσωμένο", tint = C.Gold, modifier = Modifier.size(14.dp))
            }
            Text(c.date, style = body(11, color = C.Muted))
            Text((if (c.replyTo.isNotBlank()) "@" + c.replyTo + " " else "") + c.text, style = body(14, color = C.Text).copy(lineHeight = 20.sp))
            if (c.pending) Text("Περιμένει έγκριση — το βλέπεις μόνο εσύ.", style = body(11, color = C.Gold))
            else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (likesOn) Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable { like(c.id, !c.liked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(if (c.liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, "Μου αρέσει", tint = if (c.liked) C.Gold else C.Muted, modifier = Modifier.size(18.dp))
                    Text(if (c.likes > 0) c.likes.toString() else "", style = body(12, FontWeight.Bold, if (c.liked) C.Gold else C.Muted))
                }
                if (open) Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable { reply(c) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Reply, "Απάντηση", tint = C.Muted, modifier = Modifier.size(18.dp))
                    Text("Απάντηση", style = body(12, FontWeight.Bold, C.Muted))
                }
                if (reportsOn && !c.staff) Box {
                    Icon(Icons.Filled.MoreVert, "Περισσότερα", tint = C.Muted, modifier = Modifier.size(20.dp).clickable { menu = true })
                    DropdownMenu(menu, { menu = false }) { DropdownMenuItem(text = { Text("Αναφορά σχολίου", style = body(13)) }, onClick = { menu = false; report(c.id) }) }
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

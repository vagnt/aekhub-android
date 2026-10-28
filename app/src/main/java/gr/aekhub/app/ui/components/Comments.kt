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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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

/** Κουμπί «💬 Σχόλια (N)» δίπλα στην κοινοποίηση. Ο αριθμός φορτώνει μόνος του (αν αποτύχει, δεν φαίνεται). */
@Composable
fun CommentsButton(postId: Long, onClick: () -> Unit) {
    val ctx = LocalContext.current
    var total by remember(postId) { mutableStateOf(-1) }
    LaunchedEffect(postId) { runCatching { total = CommentsApi.list(ctx, postId, "new", 1).total } }
    Row(
        Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(C.Panel2).border(1.dp, C.Gold.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.ChatBubbleOutline, null, tint = C.Gold, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text("Σχόλια" + if (total > 0) " ($total)" else "", style = body(15, FontWeight.ExtraBold, C.Text))
    }
}

/**
 * 💬 Σχόλια άρθρου σε παράθυρο (pop up από κάτω) με στυλ chat — ΤΑ ΙΔΙΑ σχόλια με το site (κοινό σύστημα):
 * ό,τι γράφεται εδώ φαίνεται στο aekhub.gr και αντίστροφα. Μόνο όνομα (χωρίς εγγραφή), likes, απαντήσεις, αναφορά, έγκριση/αντι-spam από τον server.
 * Τα νεότερα είναι κάτω (σαν συνομιλία)· πάνω-πάνω «Παλιότερα σχόλια». Τα δικά σου μηνύματα πάνε δεξιά.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(postId: Long, title: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var items by remember(postId) { mutableStateOf<List<CItem>>(emptyList()) } // νεότερα → παλιότερα, όπως τα δίνει ο server
    var page by remember(postId) { mutableStateOf(1) }
    var more by remember(postId) { mutableStateOf(false) }
    var total by remember(postId) { mutableStateOf(0) }
    var open by remember(postId) { mutableStateOf(true) }
    var likesOn by remember(postId) { mutableStateOf(true) }
    var reportsOn by remember(postId) { mutableStateOf(true) }
    var maxLen by remember(postId) { mutableStateOf(0) }
    var loading by remember(postId) { mutableStateOf(true) }
    var failed by remember(postId) { mutableStateOf(false) }
    var name by remember { mutableStateOf(Device.name(ctx)) }
    var editName by remember { mutableStateOf(Device.name(ctx).length < 2) }
    var text by remember(postId) { mutableStateOf("") }
    var replyTo by remember(postId) { mutableStateOf<CItem?>(null) }
    var sending by remember(postId) { mutableStateOf(false) }
    var notice by remember(postId) { mutableStateOf("") }
    var mine by remember(postId) { mutableStateOf(setOf<Long>()) }  // σχόλια που έγραψα σε αυτή τη συνεδρία
    val opened = remember(postId) { System.currentTimeMillis() }
    val list = rememberLazyListState()

    fun load(reset: Boolean, toBottom: Boolean = false) {
        scope.launch {
            loading = true; failed = false
            try {
                val pg = if (reset) 1 else page + 1
                val r = CommentsApi.list(ctx, postId, "new", pg)
                items = if (reset) r.items else items + r.items.filter { n -> items.none { it.id == n.id } }
                page = pg; more = r.more; total = r.total; open = r.open; likesOn = r.likes; reportsOn = r.reports; maxLen = r.max
            } catch (e: Exception) { failed = true }
            loading = false
            if (toBottom) list.scrollToItem((list.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
        }
    }
    LaunchedEffect(postId) {
        load(true)
    }
    // Μόλις φορτώσουν τα πρώτα, πήγαινε στο τέλος (τα νεότερα)
    var firstScroll by remember(postId) { mutableStateOf(true) }
    LaunchedEffect(items.size, loading) { if (firstScroll && !loading && items.isNotEmpty()) { firstScroll = false; list.scrollToItem((list.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) } }

    fun patch(id: Long, f: (CItem) -> CItem) { items = items.map { r -> if (r.id == id) f(r) else r.copy(replies = r.replies.map { if (it.id == id) f(it) else it }) } }
    fun doLike(id: Long, on: Boolean) = scope.launch {
        patch(id) { it.copy(liked = on, likes = (it.likes + if (on) 1 else -1).coerceAtLeast(0)) }
        val r = CommentsApi.like(ctx, id, on)
        if (r.ok) patch(id) { it.copy(liked = r.liked, likes = r.likes) } else patch(id) { it.copy(liked = !on, likes = (it.likes + if (on) -1 else 1).coerceAtLeast(0)) }
    }
    fun doReport(id: Long) = scope.launch {
        val r = CommentsApi.report(ctx, id); notice = if (r.ok) "✓ Η αναφορά στάλθηκε — ευχαριστούμε." else "⚠ " + r.message; if (r.hidden) load(true)
    }
    fun send() {
        sending = true; notice = ""
        scope.launch {
            Device.setName(ctx, name.trim())
            val r = CommentsApi.send(ctx, postId, name.trim(), text.trim(), replyTo?.id ?: 0L, System.currentTimeMillis() - opened)
            sending = false
            if (r.ok) {
                gr.aekhub.app.data.Analytics.event(ctx, "comment_post", "item_id" to postId)
                r.item?.let { mine = mine + it.id }
                text = ""; replyTo = null; editName = false
                notice = if (r.approved) "" else r.message.ifBlank { "Θα εμφανιστεί μόλις εγκριθεί." }
                load(true, toBottom = true)
            } else { notice = "⚠ " + r.message.ifBlank { "Δεν στάλθηκε" }; load(true) } // ξαναφορτώνει: αν το σχόλιο μπήκε παρόλα αυτά, θα φανεί
        }
    }

    // Επίπεδη σειρά chat: παλιότερα πάνω → νεότερα κάτω· κάθε σχόλιο ακολουθείται από τις απαντήσεις του
    val flat = items.asReversed().flatMap { listOf(it) + it.replies }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = C.Ink, contentColor = C.Text) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).imePadding()) {
            // Κεφαλίδα
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(width = 4.dp, height = 22.dp).background(C.Gold))
                Column(Modifier.weight(1f)) {
                    Text("ΣΧΟΛΙΑ" + if (total > 0) " ($total)" else "", style = disp(22), color = C.Text)
                    Text(title, style = body(11, color = C.Muted), maxLines = 1)
                }
                Icon(Icons.Filled.Close, "Κλείσιμο", tint = C.Muted, modifier = Modifier.size(26.dp).clickable { scope.launch { sheet.hide(); onDismiss() } })
            }
            // Μηνύματα
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (more) item(key = "older") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(if (loading) "Φορτώνει…" else "↑ Παλιότερα σχόλια", Modifier.clip(RoundedCornerShape(99.dp)).background(C.Panel2).clickable(enabled = !loading) { load(false) }.padding(horizontal = 14.dp, vertical = 7.dp), style = body(12, FontWeight.ExtraBold, C.Gold))
                    }
                }
                if (loading && items.isEmpty()) item(key = "ld") { Text("Φορτώνει σχόλια…", style = body(13, color = C.Muted)) }
                if (failed && !loading) item(key = "fail") { Text("Δεν φορτώθηκαν τα σχόλια. Πάτα για να δοκιμάσεις ξανά.", Modifier.clickable { load(true) }, style = body(13, FontWeight.Bold, C.Gold)) }
                if (!loading && !failed && items.isEmpty() && open) item(key = "empty") { Text("Κανένα σχόλιο ακόμα — γράψε το πρώτο! 👇", style = body(14, color = C.Muted)) }
                if (!open && total > 0) item(key = "closed") { Text("Τα σχόλια έκλεισαν για αυτό το άρθρο.", style = body(13, color = C.Muted)) }
                items(flat.size, key = { flat[it].id }) { i ->
                    val c = flat[i]
                    Bubble(c, mine = c.id in mine, likesOn = likesOn, reportsOn = reportsOn, open = open, like = { doLike(c.id, !c.liked) }, report = { doReport(c.id) }) { replyTo = c; notice = "" }
                }
            }
            // Πλαίσιο γραφής
            if (open) Column(Modifier.fillMaxWidth().background(C.Panel).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val rt = replyTo
                if (rt != null) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.Panel2).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("↩ Απάντηση σε " + rt.name + ": " + rt.text.take(40), Modifier.weight(1f), style = body(12, FontWeight.Bold, C.Gold), maxLines = 1)
                    Icon(Icons.Filled.Close, "Ακύρωση απάντησης", tint = C.Muted, modifier = Modifier.size(18.dp).clickable { replyTo = null })
                }
                if (editName) OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Το όνομά σου (χωρίς εγγραφή)", style = body(14, color = C.Muted)) },
                    textStyle = body(14), shape = RoundedCornerShape(12.dp), colors = fieldColors())
                else Text("Γράφεις ως " + name + "  ·  αλλαγή", Modifier.clickable { editName = true }, style = body(11, FontWeight.Bold, C.Muted))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(text, { text = if (maxLen > 0) it.take(maxLen) else it }, Modifier.weight(1f), minLines = 1, maxLines = 5,
                        placeholder = { Text(if (rt != null) "Γράψε την απάντησή σου…" else "Γράψε ένα σχόλιο…", style = body(14, color = C.Muted)) },
                        textStyle = body(14), shape = RoundedCornerShape(22.dp), colors = fieldColors())
                    val can = !sending && name.trim().length >= 2 && text.trim().length >= 2
                    Box(Modifier.size(48.dp).clip(CircleShape).background(if (can) C.Gold else C.Panel3).clickable(enabled = can) { send() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Αποστολή", tint = if (can) C.Ink else C.Muted, modifier = Modifier.size(22.dp))
                    }
                }
                Text(if (notice.isNotBlank()) notice else "Τα σχόλια φαίνονται και στο aekhub.gr.", style = body(11, color = if (notice.isNotBlank()) C.Gold else C.Muted).copy(lineHeight = 15.sp))
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = C.Text, unfocusedTextColor = C.Text, cursorColor = C.Gold, focusedBorderColor = C.Gold, unfocusedBorderColor = C.Line2,
    focusedContainerColor = C.Panel2, unfocusedContainerColor = C.Panel2,
)

/** Ένα μήνυμα chat: των άλλων αριστερά με avatar, δικό σου δεξιά (χρυσή φούσκα). */
@Composable
private fun Bubble(c: CItem, mine: Boolean, likesOn: Boolean, reportsOn: Boolean, open: Boolean, like: () -> Unit, report: () -> Unit, reply: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val col = runCatching { Color(android.graphics.Color.parseColor(c.color)) }.getOrDefault(C.Muted)
    val shape = if (mine) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
        if (!mine) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(col), contentAlignment = Alignment.Center) { Text(c.ini, style = body(13, FontWeight.ExtraBold, Color.White)) }
            Spacer(Modifier.size(8.dp))
        }
        Column(
            Modifier.widthIn(max = 300.dp).clip(shape).background(if (mine) C.Gold.copy(alpha = 0.18f) else C.Panel)
                .border(1.dp, if (c.pinned) C.Gold.copy(alpha = 0.7f) else if (mine) C.Gold.copy(alpha = 0.45f) else C.Line, shape).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (mine) "Εσύ" else c.name, style = body(13, FontWeight.ExtraBold, if (mine) C.Gold else col.copy(alpha = 1f)), maxLines = 1)
                if (c.staff) Text("ΣΥΝΤΑΚΤΗΣ", Modifier.clip(RoundedCornerShape(5.dp)).background(C.Gold).padding(horizontal = 6.dp, vertical = 2.dp), style = body(9, FontWeight.ExtraBold, C.Ink))
                if (c.pinned) Icon(Icons.Filled.PushPin, "Καρφιτσωμένο", tint = C.Gold, modifier = Modifier.size(13.dp))
            }
            if (c.replyTo.isNotBlank()) Text("↩ " + c.replyTo, style = body(11, FontWeight.Bold, C.Muted))
            Text(c.text, style = body(14, color = C.Text).copy(lineHeight = 20.sp))
            if (c.pending) Text("Περιμένει έγκριση — το βλέπεις μόνο εσύ.", style = body(11, color = C.Gold))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(c.date, style = body(10, color = C.Muted))
                if (!c.pending) {
                    if (likesOn) Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = like).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(if (c.liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, "Μου αρέσει", tint = if (c.liked) C.Gold else C.Muted, modifier = Modifier.size(15.dp))
                        if (c.likes > 0) Text(c.likes.toString(), style = body(11, FontWeight.Bold, if (c.liked) C.Gold else C.Muted))
                    }
                    if (open) Text("Απάντηση", Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = reply).padding(vertical = 2.dp), style = body(11, FontWeight.Bold, C.Muted))
                    if (reportsOn && !c.staff && !mine) Box {
                        Text("⋯", Modifier.clickable { menu = true }.padding(horizontal = 4.dp), style = body(15, FontWeight.ExtraBold, C.Muted))
                        DropdownMenu(menu, { menu = false }) { DropdownMenuItem(text = { Text("Αναφορά σχολίου", style = body(13)) }, onClick = { menu = false; report() }) }
                    }
                }
            }
        }
    }
}

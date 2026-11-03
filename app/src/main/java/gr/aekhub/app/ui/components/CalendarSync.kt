package gr.aekhub.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import gr.aekhub.app.data.CalConfig
import gr.aekhub.app.data.CalGroup
import gr.aekhub.app.data.SportItem
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body
import gr.aekhub.app.ui.theme.disp

/** Κουμπί «📅 Στο ημερολόγιό μου» (καρτέλα Αγώνες). */
@Composable
fun CalendarButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(999.dp)).background(C.Gold).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Outlined.CalendarMonth, null, tint = C.Ink, modifier = Modifier.size(17.dp))
        Text("Στο ημερολόγιο", style = body(13, FontWeight.ExtraBold, C.Ink), maxLines = 1)
    }
}

/**
 * Συνδρομή στο πρόγραμμα της ΑΕΚ — ίδιο με το «Στο κινητό μου» του ημερολογίου στο site:
 * αθλήματα + υπενθύμιση → Google Calendar / άλλη εφαρμογή (webcal) / Outlook / αντιγραφή link.
 * Μία φορά και τέλος: νέοι αγώνες, αλλαγές ώρας και σκορ περνάνε μόνα τους (το ημερολόγιο ξαναδιαβάζει το link).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CalendarSheet(cal: CalConfig?, sports: List<SportItem>, site: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val groups = cal?.groups?.takeIf { it.isNotEmpty() } ?: sports.map { CalGroup(it.key, it.name) }
    val feed = cal?.feed?.ifBlank { null } ?: (site.ifBlank { "https://aekhub.gr/" }.trimEnd('/') + "/?amc_ics=1")
    var sel by remember { mutableStateOf(setOf<String>()) }
    var alarm by remember { mutableStateOf("60") }
    val u = feed + (if (sel.isNotEmpty()) "&s=" + groups.map { it.k }.filter { it in sel }.joinToString(",") else "") + (if (alarm != "0") "&a=$alarm" else "")
    val w = u.replaceFirst(Regex("^https?://"), "webcal://")
    val name = (cal?.name ?: "AEK Hub") + if (sel.isNotEmpty()) " · " + groups.filter { it.k in sel }.joinToString(" + ") { it.l } else ""

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = C.Ink2) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).navigationBarsPadding().padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FitText("ΤΟ ΠΡΟΓΡΑΜΜΑ ΤΗΣ ΑΕΚ ΣΤΟ ΗΜΕΡΟΛΟΓΙΟ ΣΟΥ", style = disp(20), color = C.Text, minSize = 13f)
                Text("Μία φορά και τέλος: νέοι αγώνες, αλλαγές ώρας και τελικά σκορ περνάνε μόνα τους.", style = body(13, color = C.Text2))
            }
            Label("ΑΘΛΗΜΑΤΑ")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Όλα", sel.isEmpty(), null) { sel = emptySet() }
                groups.forEach { g -> Chip(g.l, g.k in sel, g.c) { sel = if (g.k in sel) sel - g.k else sel + g.k } }
            }
            Label("ΥΠΕΝΘΥΜΙΣΗ")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("0" to "Καμία", "30" to "30′ πριν", "60" to "1 ώρα", "180" to "3 ώρες", "1440" to "1 μέρα").forEach { (v, l) -> Chip(l, alarm == v, null) { alarm = v } }
            }
            Spacer(Modifier.height(2.dp))
            Go(Icons.Outlined.Event, "Google Calendar", "Android · εμφανίζεται σε λίγα λεπτά στο κινητό") {
                open(ctx, "https://calendar.google.com/calendar/r?cid=" + Uri.encode(w))
            }
            Go(Icons.Outlined.CalendarMonth, "Άλλη εφαρμογή ημερολογίου", "Samsung, Xiaomi, iPhone…") {
                if (!open(ctx, w, quiet = true)) { copy(ctx, u); Toast.makeText(ctx, "Δεν βρέθηκε εφαρμογή για συνδρομή — το link αντιγράφηκε. Βάλ' το στο ημερολόγιό σου («Προσθήκη από URL»).", Toast.LENGTH_LONG).show() }
            }
            Go(Icons.Outlined.OpenInNew, "Outlook", "Microsoft") {
                open(ctx, "https://outlook.live.com/calendar/0/addfromweb?url=" + Uri.encode(u) + "&name=" + Uri.encode(name))
            }
            Go(Icons.Outlined.ContentCopy, "Αντιγραφή link", "για οποιαδήποτε άλλη εφαρμογή") {
                copy(ctx, u); Toast.makeText(ctx, "Το link αντιγράφηκε ✓", Toast.LENGTH_SHORT).show()
            }
            Text("Στο Google Calendar πατάς «Προσθήκη». Για να το βγάλεις, το διαγράφεις από τις ρυθμίσεις του ημερολογίου σου.", style = body(12, color = C.Muted))
        }
    }
}

@Composable
private fun Label(t: String) = Text(t, style = body(11, FontWeight.ExtraBold, C.Gold))

@Composable
private fun Chip(t: String, on: Boolean, color: String?, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(999.dp)).background(if (on) C.Gold else C.Panel2).border(1.dp, if (on) C.Gold else C.Line, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!color.isNullOrBlank()) Box(Modifier.size(8.dp).clip(CircleShape).background(hex(color, C.Gold)))
        Text(t, style = body(13, if (on) FontWeight.ExtraBold else FontWeight.Bold, if (on) C.Ink else C.Text), maxLines = 1)
    }
}

@Composable
private fun Go(icon: ImageVector, t: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Panel).border(1.dp, C.Line, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(C.Panel3), contentAlignment = Alignment.Center) { Icon(icon, null, tint = C.Gold, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f)) {
            Text(t, style = body(14, FontWeight.ExtraBold, C.Text), maxLines = 1)
            Text(sub, style = body(12, color = C.Muted), maxLines = 1)
        }
    }
}

private fun open(ctx: Context, url: String, quiet: Boolean = false): Boolean = runCatching {
    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
}.getOrElse { if (!quiet) Toast.makeText(ctx, "Δεν άνοιξε. Δοκίμασε «Αντιγραφή link».", Toast.LENGTH_SHORT).show(); false }

private fun copy(ctx: Context, s: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("AEK Hub", s))
}

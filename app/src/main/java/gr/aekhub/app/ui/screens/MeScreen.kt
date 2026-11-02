package gr.aekhub.app.ui.screens

import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import gr.aekhub.app.ads.Ads
import gr.aekhub.app.ui.components.SocialRow
import gr.aekhub.app.push.Push
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import gr.aekhub.app.BuildConfig
import gr.aekhub.app.LocalPrefs
import gr.aekhub.app.data.Api
import gr.aekhub.app.data.AppConfig
import gr.aekhub.app.data.Settings as AppSettings
import gr.aekhub.app.ui.components.PostRow
import gr.aekhub.app.ui.components.ScreenTitle
import gr.aekhub.app.ui.components.SectionLabel
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body
import kotlinx.coroutines.launch
import gr.aekhub.app.data.Kinds
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.Icons

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeScreen(openPost: (Long) -> Unit) {
    val prefs = LocalPrefs.current
    val s by prefs.settings.collectAsState(initial = AppSettings())
    var config by remember { mutableStateOf<AppConfig?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { runCatching { config = Api.config() } }
    val ctx = LocalContext.current
    val pushOn = remember { Push.available(ctx) }
    var allowed by remember { mutableStateOf(Push.allowed(ctx)) }
    var askedOnce by remember { mutableStateOf(false) }
    val askPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    val privacy by Ads.privacyButton.collectAsState()
    LifecycleResumeEffect(Unit) { allowed = Push.allowed(ctx); onPauseOrDispose { } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = gr.aekhub.app.ui.sidePad(680.dp).let { sp -> PaddingValues(start = sp, end = sp, bottom = 32.dp) }, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { ScreenTitle("Ειδοποιήσεις", Modifier.padding(top = 14.dp)) }
        item {
            val msg = when {
                !pushOn -> "Το app δεν έχει ακόμη συνδεθεί με την υπηρεσία ειδοποιήσεων. Οι επιλογές σου αποθηκεύονται και ενεργοποιούνται μόλις συνδεθεί."
                !allowed -> "Οι ειδοποιήσεις είναι κλειστές για το AEK Hub στο κινητό σου."
                else -> null
            }
            if (msg != null) Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Panel2).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(msg, style = body(13, color = C.Text2))
                if (pushOn && !allowed) Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(C.Gold).clickable {
                        if (Build.VERSION.SDK_INT >= 33 && !askedOnce) { askedOnce = true; askPerm.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        else ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                    }.padding(horizontal = 16.dp, vertical = 10.dp)
                ) { Text("Άνοιξέ τες", style = body(14, FontWeight.ExtraBold, C.Ink)) }
            }
        }
        // Γενικός διακόπτης
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (s.nAll) C.Panel else C.Panel2)
                    .border(1.5.dp, if (s.nAll) C.Gold else C.Line, RoundedCornerShape(20.dp))
                    .clickable { scope.launch { prefs.setFlag("all", !s.nAll) } }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(if (s.nAll) C.Gold else C.Panel3), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(if (s.nAll) Icons.Filled.Notifications else Icons.Filled.NotificationsOff, null, tint = if (s.nAll) C.Ink else C.Muted)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Ειδοποιήσεις AEK Hub", style = body(16, FontWeight.ExtraBold))
                    Text(if (s.nAll) "Ενεργές, με τις επιλογές σου παρακάτω" else "Κλειστές", style = body(12, color = if (s.nAll) C.Gold else C.Muted))
                }
                Switch(s.nAll, { scope.launch { prefs.setFlag("all", it) } }, colors = sw())
            }
        }
        if (!s.nAll) item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Panel2).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Δεν θα λαμβάνεις καμία ειδοποίηση", style = body(15, FontWeight.ExtraBold))
                Text("Ούτε γκολ, ούτε έκτακτα, ούτε υπενθυμίσεις αγώνων. Οι επιλογές σου ανά άθλημα μένουν αποθηκευμένες: άνοιξε τον διακόπτη και ξαναρχίζουν όπως ήταν.",
                    style = body(13, color = C.Text2).copy(lineHeight = 19.sp))
            }
        }
        if (s.nAll) {
            item { SectionLabel("ΑΝΑ ΑΘΛΗΜΑ") }
            val list = config?.sports?.map { it.key to it.name } ?: Kinds.SPORTS.map { it to sportName(it) }
            item {
                val allOn = list.all { (k, _) -> k in s.sports && Kinds.ALL.all { "$k:$it" in s.kinds } }
                NewsChip(if (allOn) "✓ Όλα επιλεγμένα — πάτα για να τα ξεχωρίσεις" else "Επιλογή όλων", allOn) { scope.launch { prefs.selectAllSports(!allOn, list.map { it.first }) } }
            }
            items(list, key = { "sp" + it.first }) { (key, name) ->
                SportCard(key, name, s, { scope.launch { prefs.toggleSport(key) } }) { kind, on -> scope.launch { prefs.setKind(key, kind, on) } }
            }
            item {
                Group("ΕΙΔΗΣΕΙΣ") {
                    Toggle(Icons.Filled.Bolt, "Έκτακτα", "Μεταγραφές, ανακοινώσεις και τα σημαντικά θέματα της ημέρας, με ετικέτα όπως «ΕΠΙΣΗΜΟ».",
                        "ΕΠΙΣΗΜΟ · Ανακοίνωση της ΠΑΕ ΑΕΚ", s.nBreaking, last = !(s.nBreaking && !config?.categories.isNullOrEmpty())) { scope.launch { prefs.setFlag("breaking", it) } }
                    // Ανά κατηγορία: καμία επιλεγμένη = όλες
                    val cats = config?.categories.orEmpty()
                    if (s.nBreaking && cats.isNotEmpty()) Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (s.newsCats.isEmpty()) "Από όλες τις κατηγορίες. Πάτα για να διαλέξεις μόνο κάποιες." else "Μόνο από: " + s.newsCats.joinToString(", "), style = body(12, color = C.Muted).copy(lineHeight = 17.sp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            NewsChip("Όλες", s.newsCats.isEmpty()) { scope.launch { prefs.clearNewsCats() } }
                            cats.forEach { c -> NewsChip(c.name, c.name in s.newsCats) { scope.launch { prefs.toggleNewsCat(c.name) } } }
                        }
                    }
                }
            }
            item {
                Group("ΓΕΝΙΚΑ") {
                    Toggle(Icons.Filled.Bedtime, "Ήσυχες ώρες", "Από 01:00 έως 08:00 το κινητό δεν χτυπάει. Εξαίρεση: τα γκολ της ΑΕΚ.", null, s.nQuiet) { scope.launch { prefs.setFlag("quiet", it) } }
                    HorizontalDivider(Modifier.padding(start = 68.dp, end = 16.dp), thickness = 1.dp, color = C.Line)
                    Toggle(Icons.Filled.Insights, "Ανώνυμα στατιστικά", "Βοηθούν να βελτιώνουμε το AEK Hub (ποια άρθρα και αγώνες διαβάζονται). Χωρίς προσωπικά στοιχεία, μόνο αν έχεις δώσει συναίνεση στη φόρμα απορρήτου — και μπορείς να τα κλείσεις.", null, s.nStats) { scope.launch { prefs.setFlag("stats", it) } }
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            runCatching { ctx.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, gr.aekhub.app.push.Notifs.CH_GOAL)) }
                        }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IconBox(Icons.Filled.MusicNote, true)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Ήχος γκολ", style = body(15, FontWeight.ExtraBold))
                            Text("Ο ήχος του AEK Hub. Πάτα για να τον αλλάξεις ή να τον κλείσεις.", style = body(12, color = C.Muted).copy(lineHeight = 17.sp))
                        }
                        Text("Αλλαγή", style = body(13, FontWeight.ExtraBold, C.Gold))
                    }
                }
            }
        }
        item { SectionLabel("ΑΠΟΘΗΚΕΥΜΕΝΑ ΑΡΘΡΑ") }
        if (s.saved.isEmpty()) item { Text("Πάτα το σημαδάκι σε ένα άρθρο για να το βρίσκεις εδώ.", style = body(14, color = C.Muted)) }
        items(s.saved, key = { "s" + it.id }) { p -> PostRow(p) { openPost(p.id) } }
        val rw = config?.ads?.reward
        if (rw != null && rw.on && config?.ads?.enabled == true) item {
            // Rewarded: ο αναγνώστης βλέπει ένα βίντεο → χωρίς διαφημίσεις για Χ ώρες
            val free by Ads.adFreeUntil.collectAsState()
            var busy by remember { mutableStateOf(false) }
            val isFree = System.currentTimeMillis() < free
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Panel).border(1.dp, if (isFree) C.Gold else C.Line, RoundedCornerShape(18.dp))
                    .clickable(enabled = !isFree && !busy) {
                        val a = ctx as? Activity ?: return@clickable
                        busy = true; Ads.showReward(a, rw.hours) { busy = false }
                    }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(if (isFree) "✅" else "🎬", style = body(24))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(if (isFree) "Χωρίς διαφημίσεις" else "Χωρίς διαφημίσεις για ${rw.hours} ώρες", style = body(15, FontWeight.ExtraBold))
                    Text(
                        if (isFree) "Μέχρι " + java.text.SimpleDateFormat("d/M HH:mm", java.util.Locale.ROOT).format(java.util.Date(free)) + ". Ευχαριστούμε που στηρίζεις το AEK Hub!"
                        else if (busy) "Φορτώνει…" else "Δες ένα σύντομο βίντεο και κλείνουν οι διαφημίσεις.",
                        style = body(12, color = C.Muted),
                    )
                }
            }
        }
        if (privacy) item {
            Text("Ρυθμίσεις απορρήτου (διαφημίσεις & στατιστικά)", Modifier.clickable { (ctx as? Activity)?.let { Ads.showPrivacy(it) } }.padding(vertical = 8.dp),
                style = body(14, FontWeight.Bold, C.Gold))
        }
        // Απαίτηση Google Play: η πολιτική απορρήτου και μέσα στην εφαρμογή
        item {
            Text("Πολιτική απορρήτου", Modifier.clickable { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(BuildConfig.SITE + "/politiki-aporritou/"))) } }.padding(vertical = 8.dp),
                style = body(14, FontWeight.Bold, C.Gold))
        }
        if ((config?.socials ?: emptyList()).isNotEmpty()) item { SocialRow(config!!.socials) }
        item { Text("AEK Hub " + BuildConfig.VERSION_NAME, Modifier.padding(top = 12.dp), style = body(12, color = C.Muted)) }
        item {
            // Διάγνωση σύνδεσης με το aekhub.gr (ανανεώνεται κάθε 2″)
            var tick by remember { mutableStateOf(0) }
            LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(2_000); tick++ } }
            tick
            val f = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT)
            val ok = if (Api.lastOk > 0) "τελευταία επιτυχία " + f.format(java.util.Date(Api.lastOk)) else "καμία επιτυχής σύνδεση ακόμη"
            val err = if (Api.lastErrAt > 0) "\nσφάλμα " + f.format(java.util.Date(Api.lastErrAt)) + ": " + Api.lastErr else ""
            Text("Server: $ok$err", style = body(11, color = C.Muted))
        }
    }
}

@Composable
private fun NewsChip(text: String, on: Boolean, onClick: () -> Unit) = Text(
    text, Modifier.clip(RoundedCornerShape(99.dp)).background(if (on) C.Gold else C.Panel2).border(1.dp, if (on) C.Gold else C.Line2, RoundedCornerShape(99.dp)).clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 7.dp),
    style = body(13, FontWeight.ExtraBold, if (on) C.Ink else C.Text2),
)

@Composable
private fun Group(title: String, content: @Composable () -> Unit) =
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Panel).border(1.dp, C.Line, RoundedCornerShape(18.dp))) {
        SectionLabel(title, Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp))
        content()
    }

@Composable
private fun sw() = SwitchDefaults.colors(checkedThumbColor = C.Ink, checkedTrackColor = C.Gold, uncheckedThumbColor = C.Muted, uncheckedTrackColor = C.Line2, uncheckedBorderColor = C.Line2)

@Composable
private fun IconBox(icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean) =
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (on) C.Gold.copy(alpha = 0.16f) else C.Panel2), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Icon(icon, null, tint = if (on) C.Gold else C.Muted, modifier = Modifier.size(22.dp))
    }

@Composable
private fun Toggle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, example: String?, on: Boolean, last: Boolean = false, set: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { set(!on) }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconBox(icon, on)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = body(15, FontWeight.ExtraBold, if (on) C.Text else C.Text2))
            Text(sub, style = body(12, color = C.Muted).copy(lineHeight = 17.sp))
            if (example != null) Text(
                example, Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(C.Ink).border(1.dp, C.Line, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
                style = body(12, FontWeight.Bold, if (on) C.Text else C.Muted), maxLines = 2,
            )
        }
        Switch(on, set, colors = sw())
    }
    if (!last) HorizontalDivider(Modifier.padding(start = 68.dp, end = 16.dp), thickness = 1.dp, color = C.Line)
}

private fun sportName(k: String) = when (k) {
    "football" -> "Ποδόσφαιρο"; "basketball" -> "Μπάσκετ"; "volleyball" -> "Βόλεϊ"; "handball" -> "Χάντμπολ"
    "women_football" -> "Γυναικείο ποδόσφαιρο"; "futsal" -> "Futsal"; "waterpolo" -> "Πόλο"; else -> k
}
private fun sportIcon(k: String) = when (k) {
    "basketball" -> Icons.Filled.SportsBasketball; "volleyball" -> Icons.Filled.SportsVolleyball; "handball" -> Icons.Filled.SportsHandball
    "waterpolo" -> Icons.Filled.Pool; else -> Icons.Filled.SportsSoccer
}

/** Οι επιλογές κάθε αθλήματος, με τη γλώσσα του (γκολ/σετ, ενδεκάδα/πεντάδα, κάρτες/αποβολές). */
private data class KindRow(val kind: String, val title: String, val sub: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private fun kindRows(sp: String): List<KindRow> = buildList {
    when (sp) {
        "volleyball" -> add(KindRow(Kinds.GOALS, "Κάθε σετ", "Μόλις τελειώνει σετ — για όποιον κι αν το κερδίσει.", Icons.Filled.EmojiEvents))
        in Kinds.NO_GOALS -> {}
        else -> add(KindRow(Kinds.GOALS, "Γκολ", "Τη στιγμή που μπαίνει — για την ΑΕΚ και για τον αντίπαλο (και αν ακυρωθεί).", Icons.Filled.EmojiEvents))
    }
    add(KindRow(Kinds.STARTEND, when (sp) { "basketball" -> "Τζάμπολ, δεκάλεπτα, τελικό"; "volleyball" -> "Έναρξη, κάθε σετ, τελικό"; "waterpolo" -> "Έναρξη, περίοδοι, τελικό"; else -> "Σέντρα, ημίχρονο, τελικό" },
        "Το τελικό έρχεται με σκόρερ και κουμπί για στιγμιότυπα και βαθμολογία.", Icons.Filled.Flag))
    add(KindRow(Kinds.REMINDER, "Υπενθύμιση 30′ πριν", "Με την ώρα, το γήπεδο και πού μεταδίδεται.", Icons.Filled.Alarm))
    add(KindRow(Kinds.CARDS, when (sp) { "basketball" -> "Τεχνικές ποινές και αποβολές"; "handball", "waterpolo" -> "Αποβολές"; "volleyball" -> "Αλλαγές"; else -> "Κάρτες και αλλαγές" },
        "Για όσους θέλουν να ξέρουν τα πάντα.", Icons.Filled.Style))
}

@Composable
private fun SportCard(key: String, name: String, s: AppSettings, toggle: () -> Unit, set: (String, Boolean) -> Unit) {
    val follow = key in s.sports
    var open by androidx.compose.runtime.saveable.rememberSaveable(key) { mutableStateOf(false) }
    val rows = kindRows(key)
    val onCount = rows.count { "$key:${it.kind}" in s.kinds }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Panel).border(1.dp, if (follow) C.Gold.copy(alpha = 0.45f) else C.Line, RoundedCornerShape(18.dp))) {
        Row(Modifier.fillMaxWidth().clickable { if (follow) open = !open else { toggle(); open = true } }.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBox(sportIcon(key), follow)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = body(16, FontWeight.ExtraBold, if (follow) C.Text else C.Text2))
                Text(if (follow) "Ακολουθείς · $onCount από ${rows.size} ειδοποιήσεις" else "Καμία ειδοποίηση από αυτό το τμήμα", style = body(12, color = if (follow) C.Gold else C.Muted))
            }
            if (follow) androidx.compose.material3.Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (open) "Σύμπτυξη" else "Ανάπτυξη", tint = C.Text2, modifier = Modifier.size(24.dp))
            Switch(follow, { on -> toggle(); open = on }, colors = sw())
        }
        AnimatedVisibility(follow && open) {
            Column {
                HorizontalDivider(thickness = 1.dp, color = C.Line)
                rows.forEachIndexed { i, r ->
                    val on = "$key:${r.kind}" in s.kinds
                    Row(Modifier.fillMaxWidth().clickable { set(r.kind, !on) }.padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        androidx.compose.material3.Icon(r.icon, null, tint = if (on) C.Gold else C.Muted, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(r.title, style = body(14, FontWeight.Bold, if (on) C.Text else C.Text2))
                            Text(r.sub, style = body(11, color = C.Muted).copy(lineHeight = 15.sp))
                        }
                        Switch(on, { set(r.kind, it) }, colors = sw())
                    }
                    if (i < rows.lastIndex) HorizontalDivider(Modifier.padding(start = 48.dp, end = 12.dp), thickness = 1.dp, color = C.Line)
                }
            }
        }
    }
}

package gr.aekhub.app.ui.screens

import android.Manifest
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

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
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
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("ΤΜΗΜΑΤΑ ΠΟΥ ΑΚΟΛΟΥΘΩ")
                Text("Ειδοποιήσεις αγώνων έρχονται μόνο για τα τμήματα που τσεκάρεις.", style = body(13, color = C.Muted))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Panel).border(1.dp, C.Line, RoundedCornerShape(18.dp))) {
                    val list = config?.sports ?: emptyList()
                    list.forEachIndexed { idx, sp ->
                        val on = sp.key in s.sports
                        Row(
                            Modifier.fillMaxWidth().clickable { scope.launch { prefs.toggleSport(sp.key) } }.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.Checkbox(
                                checked = on, onCheckedChange = { scope.launch { prefs.toggleSport(sp.key) } },
                                colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = C.Gold, checkmarkColor = C.Ink, uncheckedColor = C.Line2),
                            )
                            Text(sportEmoji(sp.key) + "  " + sp.name, style = body(15, if (on) FontWeight.ExtraBold else FontWeight.Bold, if (on) C.Text else C.Text2))
                        }
                        if (idx < list.lastIndex) HorizontalDivider(Modifier.padding(start = 56.dp, end = 16.dp), thickness = 1.dp, color = C.Line)
                    }
                }
            }
        }
        item {
            Group("ΣΤΟΥΣ ΑΓΩΝΕΣ") {
                Toggle("⚽", "Γκολ", "Κάθε γκολ της ΑΕΚ και του αντιπάλου, τη στιγμή που μπαίνει, με το νέο σκορ. Στο μπάσκετ και το βόλεϊ δεν χτυπάει σε κάθε πόντο.",
                    "⚽ 🟡 ΓΚΟΛ! ΑΕΚ [1]-0 Ολυμπιακός — 23′", s.nGoals) { scope.launch { prefs.setFlag("goals", it) } }
                Toggle("🏁", "Έναρξη, ημίχρονο & τελικό", "Πότε ξεκινά ο αγώνας, το ημίχρονο ή τα δεκάλεπτα/σετ και το τελικό αποτέλεσμα.",
                    "⚽ 🏁 Τελικό · ΑΕΚ 2-0 Ολυμπιακός", s.nStartEnd) { scope.launch { prefs.setFlag("startEnd", it) } }
                Toggle("📋", "Ενδεκάδα", "Η αρχική σύνθεση της ΑΕΚ μόλις ανακοινωθεί, περίπου μία ώρα πριν τη σέντρα.",
                    "📋 Η ενδεκάδα της ΑΕΚ · ΑΕΚ - Ολυμπιακός", s.nLineup) { scope.launch { prefs.setFlag("lineup", it) } }
                Toggle("🟨", "Κάρτες & αλλαγές", "Κίτρινες, κόκκινες, αλλαγές και αποβολές. Για όσους θέλουν να ξέρουν τα πάντα.",
                    "🟨 Κίτρινη 37′ · ΑΕΚ", s.nCards, last = true) { scope.launch { prefs.setFlag("cards", it) } }
            }
        }
        item {
            Group("ΕΙΔΗΣΕΙΣ") {
                Toggle("⚡", "Έκτακτα", "Μεταγραφές, ανακοινώσεις και τα σημαντικά θέματα της ημέρας. Μόνο όσα αξίζει να μάθεις αμέσως.",
                    "⚡ Ανακοίνωση: νέος προπονητής στην ΑΕΚ", s.nBreaking) { scope.launch { prefs.setFlag("breaking", it) } }
                Toggle("🌙", "Ήσυχες ώρες", "Από 01:00 έως 08:00 το κινητό δεν χτυπάει. Εξαίρεση: τα γκολ.", null, s.nQuiet) { scope.launch { prefs.setFlag("quiet", it) } }
                Toggle("🧪", "Δοκιμαστικές ειδοποιήσεις", "Για συντάκτες: λαμβάνεις και τις ειδοποιήσεις των δοκιμαστικών αγώνων. Το κοινό δεν τις βλέπει ποτέ.", null, s.nTest, last = true) { scope.launch { prefs.setFlag("test", it) } }
            }
        }
        item { SectionLabel("ΑΠΟΘΗΚΕΥΜΕΝΑ ΑΡΘΡΑ") }
        if (s.saved.isEmpty()) item { Text("Πάτα το σημαδάκι σε ένα άρθρο για να το βρίσκεις εδώ.", style = body(14, color = C.Muted)) }
        items(s.saved, key = { "s" + it.id }) { p -> PostRow(p) { openPost(p.id) } }
        if (privacy) item {
            Text("Ρυθμίσεις απορρήτου διαφημίσεων", Modifier.clickable { (ctx as? Activity)?.let { Ads.showPrivacy(it) } }.padding(vertical = 8.dp),
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
private fun Group(title: String, content: @Composable () -> Unit) =
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Panel).border(1.dp, C.Line, RoundedCornerShape(18.dp))) {
        SectionLabel(title, Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp))
        content()
    }

@Composable
private fun Toggle(icon: String, title: String, sub: String, example: String?, on: Boolean, last: Boolean = false, set: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { set(!on) }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (on) C.Gold.copy(alpha = 0.16f) else C.Panel2), contentAlignment = Alignment.Center) {
            Text(icon, style = body(19))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = body(15, FontWeight.ExtraBold, if (on) C.Text else C.Text2))
            Text(sub, style = body(12, color = C.Muted).copy(lineHeight = 17.sp))
            if (example != null) Text(
                example, Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(C.Ink).border(1.dp, C.Line, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
                style = body(12, FontWeight.Bold, if (on) C.Text else C.Muted), maxLines = 2,
            )
        }
        Switch(on, set, colors = SwitchDefaults.colors(checkedThumbColor = C.Ink, checkedTrackColor = C.Gold, uncheckedThumbColor = C.Muted, uncheckedTrackColor = C.Line2, uncheckedBorderColor = C.Line2))
    }
    if (!last) HorizontalDivider(Modifier.padding(start = 68.dp, end = 16.dp), thickness = 1.dp, color = C.Line)
}

private fun sportEmoji(k: String) = when (k) {
    "football", "women_football" -> "⚽"; "basketball" -> "🏀"; "volleyball" -> "🏐"; "handball" -> "🤾"
    "futsal" -> "🥅"; "waterpolo" -> "🤽"; else -> "🏅"
}

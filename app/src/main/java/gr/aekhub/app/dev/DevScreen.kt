package gr.aekhub.app.dev

import android.Manifest
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import gr.aekhub.app.data.Settings
import gr.aekhub.app.push.Notifs
import gr.aekhub.app.push.Push
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body
import gr.aekhub.app.widget.AekTileService
import gr.aekhub.app.widget.MatchWidgetReceiver
import gr.aekhub.app.widget.WidgetData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Γραμμή στο «Εγώ» (μόνο στο APK του dev) που ανοίγει τα εργαλεία προσομοίωσης. */
@Composable
fun DevEntry() {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Panel).border(1.5.dp, C.Gold, RoundedCornerShape(18.dp))
            .clickable { open = true }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Εργαλεία dev · προσομοίωση αγώνα", style = body(15, FontWeight.ExtraBold, C.Gold))
            Text("Δοκίμασε ειδοποιήσεις, Live Update και widget χωρίς πραγματικό ματς.", style = body(13, color = C.Text2))
        }
        Text("›", style = body(22, FontWeight.ExtraBold, C.Gold))
    }
    if (open) Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(C.Ink).statusBarsPadding()) { DevPanel { open = false } }
    }
}

@Composable
private fun Btn(text: String, modifier: Modifier = Modifier, primary: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(if (primary) C.Gold else C.Panel2).border(1.dp, if (primary) C.Gold else C.Line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = body(13, FontWeight.ExtraBold, if (primary) C.Ink else C.Text)) }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) = Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Panel).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
) {
    Text(title.uppercase(), style = body(11, FontWeight.ExtraBold, C.Gold))
    content()
}

private val SPORTS = listOf("football" to "Ποδόσφαιρο", "basketball" to "Μπάσκετ", "volleyball" to "Βόλεϊ", "handball" to "Χάντμπολ")

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DevPanel(close: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sim = remember { Sim(ctx.applicationContext) }
    val log = remember { mutableStateListOf<String>() }
    var ver by remember { mutableStateOf(0) }
    var auto by remember { mutableStateOf<Job?>(null) }
    var perm by remember { mutableStateOf(Push.allowed(ctx)) }
    val askPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { perm = it }
    sim.onLog = { log.add(0, it); if (log.size > 40) log.removeAt(log.lastIndex) }
    sim.onChange = { ver++ }
    // Αγώνας από το site (σήματα, ονόματα, διοργάνωση): όταν υπάρχει επόμενος αγώνας της ΑΕΚ
    androidx.compose.runtime.LaunchedEffect(Unit) { sim.loadReal() }

    // Διαβάζουμε το «ver» ώστε η οθόνη να ξαναζωγραφίζεται κάθε φορά που αλλάζει ο αγώνας
    val statusText = remember(ver) { sim.status() }
    val matchLabel = remember(ver) { sim.label() }
    val sport = remember(ver) { sim.sport }
    val aekGoal = when (sport) { "basketball" -> "+2 ΑΕΚ"; "volleyball" -> "Πόντος ΑΕΚ"; else -> "Γκολ ΑΕΚ" }
    val oppGoal = when (sport) { "basketball" -> "+2 αντιπάλου"; "volleyball" -> "Πόντος αντιπάλου"; else -> "Γκολ αντιπάλου" }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Εργαλεία dev", Modifier.weight(1f), style = body(22, FontWeight.ExtraBold, C.Gold))
                Btn("Κλείσιμο") { auto?.cancel(); close() }
            }
        }
        item {
            Card("Κατάσταση συσκευής") {
                val promo = promotedOk(ctx)
                Text("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}", style = body(13, color = C.Text2))
                Text("Άδεια ειδοποιήσεων: " + (if (perm) "ναι" else "ΟΧΙ"), style = body(13, FontWeight.Normal, if (perm) C.Text2 else C.Gold))
                Text("Live Update (Android 16+): " + when { Build.VERSION.SDK_INT < 36 -> "δεν υποστηρίζεται σε αυτό το Android (θα φαίνεται κανονική ζωντανή ειδοποίηση)"; promo == true -> "επιτρέπεται"; promo == false -> "ΑΠΕΝΕΡΓΟΠΟΙΗΜΕΝΟ για το app (ρυθμίσεις Android → Ειδοποιήσεις → Live Updates)"; else -> "άγνωστο" }, style = body(13, color = C.Text2))
                Text("Widget στην αρχική: " + widgetCount(ctx) + " · Αγώνας: " + matchLabel, style = body(13, color = C.Text2))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!perm && Build.VERSION.SDK_INT >= 33) Btn("Ζήτα άδεια ειδοποιήσεων", primary = true) { askPerm.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    if (Build.VERSION.SDK_INT >= 36) Btn("Ρυθμίσεις Live Update") {
                        runCatching { ctx.startActivity(Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS").putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            .onFailure { ctx.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                    Btn("Ρυθμίσεις ειδοποιήσεων") { ctx.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
        }
        item {
            Card("Άθλημα") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SPORTS.forEach { (k, n) -> Btn(n, primary = sport == k) { auto?.cancel(); sim.reset(k) } }
                }
                Text(statusText, style = body(14, FontWeight.Bold, C.Text))
            }
        }
        item {
            Card("Χειροκίνητα — ό,τι κάνεις στέλνει ειδοποίηση σαν να ήταν ζωντανός αγώνας") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Έναρξη", primary = true) { sim.start() }
                    Btn(aekGoal, primary = true) { sim.score("home", 1) }
                    Btn(oppGoal) { sim.score("away", 1) }
                    if (sport == "basketball") { Btn("+3 ΑΕΚ", primary = true) { sim.score("home", 3) }; Btn("+3 αντιπάλου") { sim.score("away", 3) } }
                    if (sport == "volleyball") { Btn("Σετ ΑΕΚ", primary = true) { sim.setWon("home") }; Btn("Σετ αντιπάλου") { sim.setWon("away") } }
                    if (sport != "volleyball") Btn("Ακύρωση τελευταίου") { sim.cancelLast() }
                    if (sport == "football" || sport == "handball") { Btn("+10′") { sim.advance(10) }; Btn("Ημίχρονο") { sim.halftime() }; Btn("Β΄ ημίχρονο") { sim.secondHalf() } }
                    if (sport == "basketball") { Btn("Τέλος δεκάλεπτου") { sim.endQuarter() }; Btn("Επόμενο δεκάλεπτο") { sim.nextQuarter() } }
                    Btn("Τελικό") { auto?.cancel(); sim.finish() }
                }
            }
        }
        item {
            Card("Αυτόματος αγώνας") {
                Text("Παίζει ολόκληρο αγώνα μόνος του, με γκολ/σκορ, ημίχρονο και τελικό. Κλείσε την οθόνη για να δεις τη ζωντανή στην οθόνη κλειδώματος.", style = body(13, color = C.Text2))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("▶ Γρήγορος (≈40″)", primary = true) { auto?.cancel(); auto = scope.launch { sim.autoPlay(450) } }
                    Btn("▶ Αργός (≈2′)") { auto?.cancel(); auto = scope.launch { sim.autoPlay(1300) } }
                    Btn("■ Στοπ") { auto?.cancel() }
                }
            }
        }
        item {
            Card("Άλλες δοκιμές") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Υπενθύμιση 30′ πριν") { sim.reminder() }
                    Btn("Έκτακτο νέο") { sim.breaking() }
                    Btn("Προσθήκη widget") { pinWidget(ctx) }
                    Btn("Ανανέωση widget") { WidgetData.refreshAsync(ctx); sim.log("Ζητήθηκε ανανέωση widget και πλακιδίου") }
                    Btn("Σκούπισμα ειδοποιήσεων") { auto?.cancel(); sim.clearAll() }
                }
                Text("Πλακίδιο γρήγορων ρυθμίσεων: τράβηξε τη γραμμή πάνω δύο φορές, πάτα το μολυβάκι και πρόσθεσε «ΑΕΚ».", style = body(13, color = C.Text2))
            }
        }
        item {
            Card("Τι στάλθηκε") {
                if (log.isEmpty()) Text("Τίποτα ακόμη.", style = body(13, color = C.Muted))
                log.forEach { Text(it, style = body(12, color = C.Text2)) }
            }
        }
    }
}

private fun promotedOk(ctx: Context): Boolean? = runCatching {
    val nm = ctx.getSystemService(NotificationManager::class.java)
    nm.javaClass.getMethod("canPostPromotedNotifications").invoke(nm) as Boolean
}.getOrNull()

private fun widgetCount(ctx: Context): Int = runCatching {
    AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, MatchWidgetReceiver::class.java)).size
}.getOrDefault(0)

private fun pinWidget(ctx: Context) {
    val m = AppWidgetManager.getInstance(ctx)
    if (Build.VERSION.SDK_INT >= 26 && m.isRequestPinAppWidgetSupported) m.requestPinAppWidget(ComponentName(ctx, MatchWidgetReceiver::class.java), null, null)
    else android.widget.Toast.makeText(ctx, "Πάτα παρατεταμένα την αρχική οθόνη → Widgets → ΑΕΚ.", android.widget.Toast.LENGTH_LONG).show()
}

/** Ο dev μπορεί να δει και το πλακίδιο (η κλάση χρησιμοποιείται από το σύστημα). */
@Suppress("unused") private val tileClass = AekTileService::class.java

internal fun devSettings() = Settings(nLive = true)
internal fun openUri(ctx: Context, u: String) = ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

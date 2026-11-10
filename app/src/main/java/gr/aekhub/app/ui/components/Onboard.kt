package gr.aekhub.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import gr.aekhub.app.data.Api
import gr.aekhub.app.data.Kinds
import gr.aekhub.app.data.Settings
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body
import gr.aekhub.app.ui.theme.disp
import kotlinx.coroutines.launch

/**
 * 🔔 Νέος χρήστης, μόλις δεχτεί τις ειδοποιήσεις (ζήτησε ο χρήστης, 0.17.0): «Τι θέλεις να μαθαίνεις;»
 * 3 βήματα: τμήματα → τι από τους αγώνες → ειδήσεις. Όλα αλλάζουν μετά από «Εγώ».
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun OnboardDialog(done: () -> Unit) {
    val prefs = gr.aekhub.app.LocalPrefs.current
    val scope = rememberCoroutineScope()
    val cfg = remember { Api.configNow() }
    val sportList = cfg?.sports?.takeIf { it.isNotEmpty() }?.map { it.key to it.name }
        ?: Kinds.SPORTS.map { it to sportName(it) }
    var step by remember { mutableIntStateOf(0) }
    var sports by remember { mutableStateOf(Settings().sports) }
    var kinds by remember { mutableStateOf(setOf(Kinds.GOALS, Kinds.STARTEND, Kinds.REMINDER)) }
    var news by remember { mutableStateOf(true) }
    var live by remember { mutableStateOf(true) }
    var cats by remember { mutableStateOf(setOf<String>()) }

    fun finish() {
        scope.launch {
            prefs.setOnboard(sports, kinds, news, live)
            prefs.clearNewsCats(); cats.forEach { prefs.toggleNewsCat(it) }
            done()
        }
    }

    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(C.Ink).background(Brush.verticalGradient(listOf(C.Gold.copy(alpha = 0.16f), Color.Transparent, Color.Transparent))).systemBarsPadding()) {
            Column(Modifier.fillMaxSize().widthIn(max = 560.dp).align(Alignment.TopCenter)) {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) { i -> Box(Modifier.weight(1f).height(4.dp).background(if (i <= step) C.Gold else Color.White.copy(alpha = 0.14f), CircleShape)) }
                }
                AnimatedContent(step, Modifier.weight(1f), transitionSpec = {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
                }, label = "onboard") { st ->
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        when (st) {
                            0 -> {
                                Title("Ποια ΑΕΚ ακολουθείς;", "Διάλεξε τα τμήματα για τα οποία θέλεις ειδοποιήσεις.")
                                sportList.chunked(2).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        row.forEach { (k, n) ->
                                            val on = k in sports
                                            Column(
                                                Modifier.weight(1f).heightIn(min = 104.dp)
                                                    .background(if (on) C.Gold.copy(alpha = 0.14f) else C.Panel, RoundedCornerShape(20.dp))
                                                    .border(if (on) 1.5.dp else 1.dp, if (on) C.Gold else C.Line, RoundedCornerShape(20.dp))
                                                    .clickable { sports = if (on) sports - k else sports + k }.padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(emoji(k), style = body(28))
                                                    Spacer(Modifier.weight(1f))
                                                    Check(on)
                                                }
                                                Text(n, style = body(15, FontWeight.ExtraBold), maxLines = 2)
                                            }
                                        }
                                        if (row.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                                val all = sportList.map { it.first }.toSet()
                                Text(if (sports.containsAll(all)) "Καθαρισμός" else "Όλα τα τμήματα", style = body(14, FontWeight.ExtraBold, C.Gold),
                                    modifier = Modifier.clickable { sports = if (sports.containsAll(all)) emptySet() else all }.padding(vertical = 6.dp))
                            }
                            1 -> {
                                Title("Τι θέλεις να μαθαίνεις;", "Για τους αγώνες των τμημάτων που διάλεξες.")
                                Column(Modifier.background(C.Panel, RoundedCornerShape(20.dp))) {
                                    listOf(
                                        Triple(Kinds.GOALS, "⚽  Γκολ & σκορ", "Κάθε γκολ στο ποδόσφαιρο, κάθε σετ στο βόλεϊ."),
                                        Triple(Kinds.STARTEND, "🏁  Σέντρα, ημίχρονο, τελικό", "Οι φάσεις του αγώνα και το τελικό σκορ."),
                                        Triple(Kinds.REMINDER, "⏰  Υπενθύμιση", "30′ πριν από την έναρξη."),
                                        Triple(Kinds.CARDS, "🟨  Κάρτες", "Κίτρινες και κόκκινες κάρτες."),
                                    ).forEachIndexed { i, (k, t, sub) ->
                                        if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(C.Line))
                                        val on = k in kinds
                                        Row(Modifier.fillMaxWidth().clickable { kinds = if (on) kinds - k else kinds + k }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(t, style = body(15, FontWeight.ExtraBold))
                                                Text(sub, style = body(12, color = C.Muted))
                                            }
                                            Check(on)
                                        }
                                    }
                                }
                                SwitchRow("Ζωντανός αγώνας στην οθόνη κλειδώματος", "Σκορ και λεπτό όσο παίζει η ΑΕΚ.", live) { live = it }
                            }
                            else -> {
                                Title("Και οι ειδήσεις;", "Ειδοποίηση για τα σημαντικά θέματα του AEK Hub.")
                                SwitchRow("Νέα άρθρα", "Έκτακτα, ανακοινώσεις, μεταγραφές.", news) { news = it }
                                val list = cfg?.categories.orEmpty()
                                if (news && list.isNotEmpty()) {
                                    Text(if (cats.isEmpty()) "Από όλες τις κατηγορίες — ή διάλεξε μόνο κάποιες:" else "Μόνο από αυτές:", style = body(13, color = C.Muted))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Chip("Όλες", cats.isEmpty()) { cats = emptySet() }
                                        list.forEach { c -> Chip(c.name, c.name in cats) { cats = if (c.name in cats) cats - c.name else cats + c.name } }
                                    }
                                }
                                Text("Όλα αλλάζουν όποτε θες από «Εγώ».", style = body(12, color = C.Muted))
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (step > 0) Box(Modifier.size(56.dp).background(Color.White.copy(alpha = 0.08f), CircleShape).clickable { step-- }, contentAlignment = Alignment.Center) {
                        Text("‹", style = body(26, FontWeight.ExtraBold))
                    }
                    Box(Modifier.weight(1f).height(56.dp).background(C.Gold, CircleShape).clickable { if (step < 2) step++ else finish() }, contentAlignment = Alignment.Center) {
                        Text(if (step < 2) "Συνέχεια" else "Έτοιμο", style = body(17, FontWeight.ExtraBold, C.Ink))
                    }
                }
                Text("Τα ρυθμίζω αργότερα", style = body(13, FontWeight.Bold, C.Muted),
                    modifier = Modifier.align(Alignment.CenterHorizontally).clickable { done() }.padding(12.dp))
            }
        }
    }
}

@Composable private fun Title(big: String, small: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(big.upperGr(), style = disp(40))
        Text(small, style = body(15, color = C.Muted))
    }
}

@Composable private fun Check(on: Boolean) {
    Icon(if (on) Icons.Filled.CheckCircle else Icons.Outlined.Circle, null, Modifier.size(24.dp), tint = if (on) C.Gold else C.Muted)
}

@Composable private fun SwitchRow(t: String, sub: String, v: Boolean, set: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().background(C.Panel, RoundedCornerShape(20.dp)).clickable { set(!v) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(t, style = body(15, FontWeight.ExtraBold))
            Text(sub, style = body(12, color = C.Muted))
        }
        Switch(v, set, colors = SwitchDefaults.colors(checkedThumbColor = C.Ink, checkedTrackColor = C.Gold))
    }
}

@Composable private fun Chip(t: String, on: Boolean, tap: () -> Unit) {
    Text(t, style = body(13, FontWeight.ExtraBold, if (on) C.Ink else C.Text),
        modifier = Modifier.background(if (on) C.Gold else C.Panel, CircleShape).border(1.dp, if (on) Color.Transparent else C.Line, CircleShape)
            .clickable(onClick = tap).padding(horizontal = 14.dp, vertical = 9.dp))
}

private fun emoji(s: String) = when (s) {
    "football", "women_football" -> "⚽"; "basketball" -> "🏀"; "volleyball" -> "🏐"
    "handball" -> "🤾"; "futsal" -> "🥅"; "waterpolo" -> "🤽"; else -> "🏟"
}

private fun sportName(s: String) = when (s) {
    "football" -> "Ποδόσφαιρο"; "women_football" -> "Ποδόσφαιρο Γυναικών"; "basketball" -> "Μπάσκετ"; "volleyball" -> "Βόλεϊ"
    "handball" -> "Χάντμπολ"; "futsal" -> "Futsal"; "waterpolo" -> "Πόλο"; else -> s
}

package gr.aekhub.app.dev

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import gr.aekhub.app.push.Notifs
import gr.aekhub.app.widget.WidgetData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Προσομοιωτής αγώνα (μόνο για το dev APK): φτιάχνει τα ΙΔΙΑ δεδομένα που στέλνει ο server (aekhub-push) και τα περνά
 * από τον ίδιο κώδικα ειδοποιήσεων του app — άρα βλέπεις ακριβώς ό,τι θα δει ο χρήστης (Live Update, γκολ, ημίχρονο,
 * τελικό, widget, πλακίδιο), χωρίς να υπάρχει ματς και χωρίς να στείλει κάτι ο server.
 */
class Sim(private val ctx: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    var onLog: (String) -> Unit = {}
    var onChange: () -> Unit = {}

    var sport = "football"; private set
    private var id = 999001L
    private var home = "ΑΕΚ"; private var away = "Αντίπαλος"
    private var hlogo = ""; private var alogo = ""
    private var comp = "Δοκιμαστικός αγώνας"
    private var aekAway = false // στον πραγματικό αγώνα η ΑΕΚ είναι φιλοξενούμενη (μέσα στην προσομοίωση η ΑΕΚ είναι πάντα το «home»)
    private var sh = 0; private var sa = 0           // σκορ (στο βόλεϊ: σετ)
    private var ph = 0; private var pa = 0           // βόλεϊ: πόντοι του σετ
    private var setNo = 1
    private var q = 1                                // μπάσκετ: δεκάλεπτο
    private var min = 0; private var base = 0       // λεπτό αγώνα, και από ποιο λεπτό ξεκίνησε η περίοδος
    private var status = "upcoming"
    private val marks = ArrayList<String>()          // γκολ πάνω στη μπάρα: "h12", "a23"
    private val scorers = ArrayList<String>()
    private val history = ArrayList<Triple<String, Int, Int>>() // για την ακύρωση: (πλευρά, πόντοι, λεπτό)

    fun log(s: String) { onLog(java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date()) + "  " + s) }

    fun label() = (if (aekAway) "$away – $home" else "$home – $away") + (if (id != 999001L) " (πραγματικός αγώνας #$id)" else " (ψεύτικος)")
    fun status(): String = when (status) {
        "upcoming" -> "Δεν έχει ξεκινήσει"
        "finished" -> "Τελικό $sh–$sa"
        else -> (if (sport == "volleyball") "Σετ $setNo · σκορ σετ $sh–$sa · πόντοι $ph–$pa" else "$sh–$sa · ${phase()}" + (if (sport == "basketball") "" else " · $min′"))
    }

    /** Αν υπάρχει επόμενος αγώνας της ΑΕΚ στο site, παίρνει τα πραγματικά ονόματα και σήματα (για να φαίνονται σωστά). */
    suspend fun loadReal() {
        val st = runCatching { WidgetData.fetch(ctx) }.getOrNull() ?: return
        if (st.home.isBlank()) return
        home = st.home; away = st.away; hlogo = st.homeLogo; alogo = st.awayLogo; comp = st.comp.ifBlank { comp }
        aekAway = st.aek == "away"
        if (aekAway) { val t = home; home = away; away = t; val l = hlogo; hlogo = alogo; alogo = l }
        id = st.id.takeIf { it > 0 } ?: id
        sport = when (st.sport) { "basketball" -> "basketball"; "volleyball" -> "volleyball"; "handball" -> "handball"; else -> "football" } // το σωστό άθλημα του αγώνα
        onChange(); log("Φορτώθηκαν από το site: $home – $away")
    }

    fun reset(newSport: String) {
        sport = newSport; sh = 0; sa = 0; ph = 0; pa = 0; setNo = 1; q = 1; min = 0; base = 0; status = "upcoming"
        marks.clear(); scorers.clear(); history.clear()
        WidgetData.setDev(ctx, null)
        onChange(); log("Νέος αγώνας ($sport)")
    }

    private fun phase(): String = when {
        status == "finished" -> "Τελικό"
        status == "halftime" -> "Ημίχρονο"
        sport == "basketball" -> "$q" + "ο δεκάλεπτο"
        sport == "volleyball" -> "$setNo" + "ο σετ · $ph–$pa"
        base > 0 -> "Β΄ ημίχρονο"
        else -> "Α΄ ημίχρονο"
    }

    private fun half() = if (sport == "handball") 30 else 45

    private fun data(kind: String, ev: String, title: String, body: String, side: String = ""): Map<String, String> {
        val m = HashMap<String, String>()
        m["kind"] = kind; m["ev"] = ev; m["match"] = id.toString(); m["sport"] = sport
        // Στη σωστή σειρά του πραγματικού αγώνα (αν η ΑΕΚ παίζει εκτός, πρώτος ο αντίπαλος)
        val sw = aekAway
        m["home"] = if (sw) away else home; m["away"] = if (sw) home else away
        m["hlogo"] = if (sw) alogo else hlogo; m["alogo"] = if (sw) hlogo else alogo; m["aek"] = if (sw) "away" else "home"
        m["sh"] = (if (sw) sa else sh).toString(); m["sa"] = (if (sw) sh else sa).toString(); m["status"] = status; m["comp"] = comp
        m["title"] = title; m["body"] = body; m["aekScored"] = if (side == "home") "1" else "0"
        m["side"] = when (side) { "home" -> if (sw) "away" else "home"; "away" -> if (sw) "home" else "away"; else -> "" }
        m["min"] = if (sport == "volleyball") "" else "$min′"
        m["phase"] = phase(); m["statusLabel"] = phase()
        m["goals"] = marks.joinToString(",") { if (!sw) it else (if (it.startsWith("h")) "a" else "h") + it.drop(1) }; m["scorers"] = scorers.joinToString(" · ")
        if ((status == "live" || status.startsWith("q")) && sport != "volleyball") {
            m["clockB"] = base.toString(); m["clockL"] = half().toString()
            m["clockS"] = (System.currentTimeMillis() - (min - 1 - base).coerceAtLeast(0) * 60_000L).toString() // το λεπτό «τρέχει» κανονικά
        }
        return m
    }

    private fun send(kind: String, ev: String, title: String, body: String, side: String = "") {
        val d = data(kind, ev, title, body, side)
        scope.launch {
            runCatching { Notifs.handle(ctx, d, devSettings()) }.onFailure { log("ΣΦΑΛΜΑ: " + it.message) }
        }
        log("$kind/$ev · $title")
        onChange()
    }

    private fun vs() = if (aekAway) "$away $sa–$sh $home" else "$home $sh–$sa $away"
    private fun scoreTitle(prefix: String, side: String): String {
        val h = if (side == "home") "[$sh]" else "$sh"; val a = if (side == "away") "[$sa]" else "$sa"
        return if (aekAway) "$prefix · $away $a–$h $home" else "$prefix · $home $h–$a $away"
    }

    fun start() {
        if (status != "upcoming") { log("Ο αγώνας έχει ήδη ξεκινήσει"); return }
        status = if (sport == "basketball") "q1" else "live"; min = if (sport == "basketball") 1 else 1; base = 0
        send("startend", "start", (if (sport == "volleyball") "Ξεκίνησε · " else if (sport == "basketball") "Τζάμπολ · " else "Σέντρα · ") + (if (aekAway) "$away – $home" else "$home – $away"), comp)
    }

    /** Γκολ / καλάθι / πόντος. Στο ποδόσφαιρο και στα σετ του βόλεϊ: δυνατή ειδοποίηση· στο μπάσκετ/χάντμπολ: σιωπηλή ενημέρωση της ζωντανής. */
    fun score(side: String, n: Int) {
        if (status == "upcoming") start()
        if (status == "finished" || status == "halftime") { log("Όχι τώρα (${status()})"); return }
        if (sport == "volleyball") { if (side == "home") ph += n else pa += n; send("live", "live", "Πόντος", ""); return }
        if (side == "home") sh += n else sa += n
        history.add(Triple(side, n, min))
        if (min > 0) marks.add((if (side == "home") "h" else "a") + (if (sport == "basketball") (q * 10 - 5) else min))
        if (side == "home") scorers.add("$min′ ${if (n > 1) "+$n" else ""}".trim())
        val quiet = sport == "basketball" || sport == "handball"
        send(if (quiet) "live" else "goals", if (quiet) "live" else "goal", scoreTitle(if (n > 1) "Καλάθι" else "Γκολ", side), "$min′ — δοκιμαστικό γεγονός", side)
    }

    fun setWon(side: String) {
        if (sport != "volleyball") return
        if (status == "upcoming") start()
        if (side == "home") sh++ else sa++
        val t = "Τέλος σετ · " + vs()
        val pts = "$ph–$pa"
        ph = 0; pa = 0; setNo++
        if (sh == 3 || sa == 3) { finish(); return }
        send("goals", "set", t, "Σετ $pts")
    }

    fun cancelLast() {
        val last = history.removeLastOrNull() ?: run { log("Δεν υπάρχει τίποτα να ακυρωθεί"); return }
        if (last.first == "home") { sh -= last.second; scorers.removeLastOrNull() } else sa -= last.second
        marks.removeLastOrNull()
        send("goals", "cancel", "Ακυρώθηκε γκολ · " + vs(), "VAR — το σκορ διορθώθηκε")
    }

    fun advance(n: Int) { if (status == "live") { min += n; send("live", "live", vs(), ""); } else log("Μόνο όσο παίζεται") }

    fun halftime() {
        if (status != "live") { log("Μόνο όσο παίζεται"); return }
        min = half(); status = "halftime"
        send("startend", "ht", "Ημίχρονο · " + vs(), comp)
    }

    fun secondHalf() {
        if (status != "halftime") { log("Πρώτα το ημίχρονο"); return }
        status = "live"; base = half(); min = half() + 1
        send("startend", "period", "Ξεκίνησε το Β΄ ημίχρονο · " + vs(), comp)
    }

    fun endQuarter() {
        if (!status.startsWith("q")) { log("Μόνο στο μπάσκετ, όσο παίζεται"); return }
        send("startend", "period", "Τέλος ${q}ου δεκάλεπτου · " + vs(), comp)
    }

    fun nextQuarter() {
        if (!status.startsWith("q") || q >= 4) { log("Δεν υπάρχει επόμενο δεκάλεπτο"); return }
        q++; status = "q$q"; min = q * 10 - 9
        send("startend", "period", "Ξεκίνησε το ${q}ο δεκάλεπτο · " + vs(), comp)
    }

    fun finish() {
        if (status == "upcoming") { log("Ο αγώνας δεν έχει ξεκινήσει"); return }
        status = "finished"
        send("startend", "ft", "Τελικό · " + vs(), "Πάτα για στατιστικά και βαθμολογία")
    }

    fun reminder() = send("reminder", "reminder", "Σε 30′ η σέντρα · $home – $away", "$comp · AEK Arena · Magenta TV")

    fun breaking() {
        val m = hashMapOf("kind" to "breaking", "title" to "Έκτακτο: δοκιμαστική είδηση", "body" to "Έτσι φαίνεται μια έκτακτη ειδοποίηση ειδήσεων στο κινητό.", "cat" to "Ποδόσφαιρο")
        scope.launch { runCatching { Notifs.handle(ctx, m, devSettings()) } }
        log("breaking · δοκιμαστική είδηση")
    }

    fun clearAll() {
        runCatching { Notifs.cancelMatch(ctx, id) }
        runCatching { NotificationManagerCompat.from(ctx).cancelAll() }
        WidgetData.setDev(ctx, null)
        log("Καθαρίστηκαν οι ειδοποιήσεις"); onChange()
    }

    /** Ολόκληρος αγώνας μόνος του. [step] = χιλιοστά του δευτερολέπτου ανά «λεπτό» αγώνα. */
    suspend fun autoPlay(step: Long) {
        reset(sport)
        start(); delay(step * 2)
        when (sport) {
            "volleyball" -> {
                val winners = listOf("home", "away", "home", "home")
                for (w in winners) {
                    for (p in 1..25) {
                        val side = if (p % 4 == 0) (if (w == "home") "away" else "home") else w
                        score(side, 1); delay((step / 4).coerceAtLeast(60))
                    }
                    setWon(w); delay(step * 3)
                    if (status == "finished") return
                }
            }
            "basketball" -> {
                val rnd = java.util.Random(7)
                for (quarter in 1..4) {
                    for (m in 1..10) {
                        min = quarter * 10 - 10 + m
                        if (rnd.nextInt(10) < 6) score(if (rnd.nextBoolean()) "home" else "away", if (rnd.nextInt(3) == 0) 3 else 2)
                        delay(step)
                    }
                    if (quarter < 4) { endQuarter(); delay(step * 3); nextQuarter(); delay(step) }
                }
                finish()
            }
            else -> {
                val aek = setOf(12, 38, 74); val opp = setOf(23, 61)
                val total = half() * 2
                for (m in 1..total) {
                    if (status == "halftime") { secondHalf() }
                    min = m
                    if (m in aek) score("home", 1) else if (m in opp) score("away", 1)
                    else if (sport == "handball" && m % 2 == 0) score(if (m % 4 == 0) "home" else "away", 1)
                    else if (m % 3 == 0) send("live", "live", vs(), "")
                    if (m == 63 && sport == "football") cancelLast() // «VAR»: ακυρώνεται το τελευταίο γκολ αν υπάρχει
                    if (m == half()) { halftime(); delay(step * 4) } else delay(step)
                }
                finish()
            }
        }
    }
}

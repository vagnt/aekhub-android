package gr.aekhub.app.widget

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import androidx.glance.appwidget.updateAll
import gr.aekhub.app.data.Api
import gr.aekhub.app.data.MatchCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Ο αγώνας της ΑΕΚ που δείχνουν το widget και το πλακίδιο γρήγορων ρυθμίσεων. */
@Serializable
data class WState(
    val id: Long = 0,
    val sport: String = "football",
    val live: Boolean = false,
    val finished: Boolean = false,
    val home: String = "",
    val away: String = "",
    val homeLogo: String = "",
    val awayLogo: String = "",
    val sh: String = "",
    val sa: String = "",
    val minute: String = "",
    val phase: String = "",
    val comp: String = "",
    val startMs: Long = 0,
    val venue: String = "",
    val aek: String = "",
    val updated: Long = 0,
) {
    val hasScore get() = sh.isNotBlank() && sa.isNotBlank()
}

/**
 * Η «πηγή αλήθειας» για widget και πλακίδιο: διαβάζει/γράφει ένα μικρό αρχείο, ώστε να δουλεύει και χωρίς δίκτυο.
 * Ενημερώνεται (1) από το δίκτυο όταν το ζητά το widget, (2) αμέσως από κάθε ειδοποίηση αγώνα (χωρίς δίκτυο),
 * (3) από το dev εργαλείο προσομοίωσης (προτεραιότητα, μέχρι να καθαριστεί).
 */
object WidgetData {
    private val ATHENS: ZoneId = ZoneId.of("Europe/Athens")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("widget", Context.MODE_PRIVATE)

    fun read(ctx: Context): WState? {
        val sp = sp(ctx)
        val raw = sp.getString("dev", null) ?: sp.getString("state", null) ?: return null
        return runCatching { Api.json.decodeFromString(WState.serializer(), raw) }.getOrNull()
    }

    fun save(ctx: Context, s: WState) { sp(ctx).edit().putString("state", Api.json.encodeToString(WState.serializer(), s)).apply() }
    fun setDev(ctx: Context, s: WState?) {
        val e = sp(ctx).edit()
        if (s == null) e.remove("dev") else e.putString("dev", Api.json.encodeToString(WState.serializer(), s))
        e.apply()
    }
    fun hasDev(ctx: Context): Boolean = sp(ctx).contains("dev")

    fun start(m: MatchCard): Long = runCatching {
        val d = LocalDate.parse(m.date.take(10))
        val t = m.time.takeIf { it.length >= 4 && it[0].isDigit() }?.let { java.time.LocalTime.parse(it.take(5)) } ?: java.time.LocalTime.NOON
        LocalDateTime.of(d, t).atZone(ATHENS).toInstant().toEpochMilli()
    }.getOrDefault(0L)

    private fun from(m: MatchCard) = WState(
        id = m.id, sport = m.sport, live = m.live, finished = m.finished,
        home = m.home, away = m.away, homeLogo = m.homeLogo, awayLogo = m.awayLogo,
        sh = m.scoreHome, sa = m.scoreAway, minute = m.minute, phase = m.phase.ifBlank { m.statusLabel },
        comp = m.competition, startMs = start(m), venue = m.venue, aek = m.aek, updated = System.currentTimeMillis(),
    )

    /** Από το δίκτυο: ζωντανός αγώνας της ΑΕΚ, αλλιώς ο επόμενος (έως 14 μέρες), αλλιώς ο τελευταίος της σημερινής μέρας. */
    suspend fun fetch(ctx: Context): WState? = withContext(Dispatchers.IO) {
        Api.init(ctx.applicationContext)
        val today = LocalDate.now(ATHENS)
        val list = runCatching { Api.matches(today.minusDays(1).toString(), today.plusDays(14).toString(), null, fresh = true).items.filter { it.aek.isNotEmpty() && !it.postponed } }
            .getOrNull() ?: return@withContext null
        val pick = list.firstOrNull { it.live }
            ?: list.filter { it.upcoming }.minByOrNull { start(it) }
            ?: list.filter { it.finished }.maxByOrNull { start(it) }
        pick?.let { from(it) }?.also { save(ctx, it) }
    }

    /** Για το widget: dev → δίκτυο → αποθηκευμένο. */
    suspend fun current(ctx: Context): WState? {
        if (hasDev(ctx)) return read(ctx)
        return fetch(ctx) ?: read(ctx)
    }

    /** Από μια ειδοποίηση αγώνα (ίδια κλειδιά με το push του server): σκορ/λεπτό χωρίς δίκτυο. */
    fun fromPush(ctx: Context, d: Map<String, String>) {
        val id = d["match"]?.toLongOrNull() ?: return
        val old = read(ctx)?.takeIf { it.id == id }
        val status = d["status"].orEmpty()
        val finished = status == "finished" || d["ev"] == "ft"
        val live = !finished && status !in setOf("", "upcoming", "postponed")
        val st = (old ?: WState(id = id)).copy(
            sport = d["sport"] ?: old?.sport ?: "football",
            live = live, finished = finished,
            home = d["home"] ?: old?.home.orEmpty(), away = d["away"] ?: old?.away.orEmpty(),
            homeLogo = d["hlogo"] ?: old?.homeLogo.orEmpty(), awayLogo = d["alogo"] ?: old?.awayLogo.orEmpty(),
            sh = d["sh"] ?: old?.sh.orEmpty(), sa = d["sa"] ?: old?.sa.orEmpty(),
            minute = d["min"] ?: old?.minute.orEmpty(),
            phase = d["phase"]?.takeIf { it.isNotBlank() } ?: d["statusLabel"] ?: old?.phase.orEmpty(),
            comp = d["comp"] ?: old?.comp.orEmpty(), aek = d["aek"] ?: old?.aek.orEmpty(),
            updated = System.currentTimeMillis(),
        )
        if (hasDev(ctx)) setDev(ctx, null) // πραγματικός αγώνας: τέλος η προσομοίωση
        save(ctx, st)
        refreshAsync(ctx)
    }

    /** Ξαναζωγραφίζει widget και πλακίδιο (αθόρυβα, στο παρασκήνιο). */
    fun refreshAsync(ctx: Context) {
        val app = ctx.applicationContext
        scope.launch {
            runCatching { MatchWidget().updateAll(app) }
            runCatching { MatchesWidget().updateAll(app) }
            runCatching { StandingsWidget().updateAll(app) }
            runCatching { NewsWidget().updateAll(app) }
            runCatching { TileService.requestListeningState(app, ComponentName(app, AekTileService::class.java)) }
        }
    }
}

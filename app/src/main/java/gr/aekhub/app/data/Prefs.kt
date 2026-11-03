package gr.aekhub.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer

private val Context.store by preferencesDataStore("aekhub")

data class Settings(
    val textScale: Float = 1f,
    val sports: Set<String> = Kinds.HOT.toSet(), // προεπιλογή: όλα τα «hot» αθλήματα ενεργά
    val nGoals: Boolean = true,
    val nStartEnd: Boolean = true,
    val nLineup: Boolean = true,
    val nCards: Boolean = false,
    val nBreaking: Boolean = true,
    /** Έκτακτα μόνο από αυτές τις κατηγορίες (ονόματα)· άδειο = όλες. */
    val newsCats: Set<String> = emptySet(),
    val nQuiet: Boolean = false,
    /** Ζωντανός αγώνας στην οθόνη κλειδώματος (Live Update). Προεπιλογή: ανοιχτό από Android 16, κλειστό πριν. */
    val nLive: Boolean = android.os.Build.VERSION.SDK_INT >= 36,
    /** Η ζωντανή ειδοποίηση ως «πίνακας σκορ» (σήμα–σκορ–σήμα). Κλειστό = το στυλ Live Update του Android 16. */
    val nLiveBoard: Boolean = true,
    /** Γενικός διακόπτης: κλειστός = καμία ειδοποίηση από το AEK Hub. */
    val nAll: Boolean = true,
    /** «Χωρίς spoilers»: ειδοποιήσεις αγώνων χωρίς σκορ. */
    val nSpoiler: Boolean = false,
    /** Ανώνυμα στατιστικά χρήσης (Google Analytics μέσω Firebase + στατιστικά του aekhub.gr). */
    val nStats: Boolean = true,
    /** Τι θέλει ο χρήστης σε κάθε άθλημα: "football:goals", "basketball:live" κ.λπ. */
    val kinds: Set<String> = defaultKinds(),
    val matchAlerts: Set<String> = emptySet(),
    val mutedMatches: Set<String> = emptySet(),
    val saved: List<PostCard> = emptyList(),
)

/** Τα είδη ειδοποίησης ανά άθλημα (ίδια ονόματα με τα θέματα m_<άθλημα>_<είδος> του server, εκτός από το "opp"). */
object Kinds {
    const val LIVE = "live"; const val GOALS = "goals"; const val OPP = "opp"; const val STARTEND = "startend"
    const val CARDS = "cards"; const val REMINDER = "reminder"
    // Ενδεκάδες/πεντάδες: καμία ειδοποίηση (το ζήτησε ο χρήστης, 0.10.0)
    val ALL = listOf(GOALS, STARTEND, REMINDER, CARDS) // «opp» (γκολ αντιπάλου) δεν υπάρχει πια: ΑΕΚ και αντίπαλος = ένα «Γκολ»
    val SPORTS = listOf("football", "basketball", "volleyball", "handball", "women_football", "futsal", "waterpolo")
    /** Τα «hot» αθλήματα: ενεργά από την αρχή. */
    val HOT = listOf("football", "women_football", "basketball", "volleyball", "handball")
    /** Αθλήματα χωρίς ειδοποίηση σε κάθε σκορ (μόνο φάσεις και τελικό). */
    val NO_GOALS = setOf("basketball", "handball")
}
@Suppress("UNUSED_PARAMETER")
fun defaultKinds(goals: Boolean = true, startEnd: Boolean = true, lineup: Boolean = false, cards: Boolean = false): Set<String> = buildSet {
    Kinds.SPORTS.forEach { sp ->
        add("$sp:${Kinds.REMINDER}")
        if (goals) add("$sp:${Kinds.GOALS}")
        if (startEnd) add("$sp:${Kinds.STARTEND}")
        if (cards) add("$sp:${Kinds.CARDS}")
    }
}

/** Θέλει ο χρήστης αυτό το είδος σε αυτό το άθλημα; (γενικός διακόπτης + ακολουθεί το άθλημα + το είδος) */
fun Settings.wants(sport: String, kind: String) = nAll && sport in sports &&
    ("$sport:$kind" in kinds || (kind == Kinds.GOALS && "$sport:${Kinds.OPP}" in kinds)) // παλιές ρυθμίσεις με «opp» μετράνε ως «Γκολ»

/** Θα έρθουν ειδοποιήσεις για αυτόν τον αγώνα; (ακολουθείς το άθλημα ή το πάτησες, και δεν τον έχεις σιγάσει) */
fun Settings.alertOn(id: Long, sport: String, aek: Boolean): Boolean {
    if (!nAll) return false
    val k = id.toString()
    if (k in mutedMatches) return false
    return k in matchAlerts || (aek && Kinds.ALL.any { wants(sport, it) })
}

class Prefs(private val ctx: Context) {
    private object K {
        val scale = floatPreferencesKey("text_scale")
        val sports = stringSetPreferencesKey("sports")
        val goals = booleanPreferencesKey("n_goals")
        val startEnd = booleanPreferencesKey("n_start_end")
        val lineup = booleanPreferencesKey("n_lineup")
        val cards = booleanPreferencesKey("n_cards")
        val breaking = booleanPreferencesKey("n_breaking")
        val newsCats = stringSetPreferencesKey("news_cats")
        val quiet = booleanPreferencesKey("n_quiet")
        val live = booleanPreferencesKey("n_live")
        val liveBoard = booleanPreferencesKey("n_live_board")
        val all = booleanPreferencesKey("n_all")
        val spoiler = booleanPreferencesKey("n_spoiler")
        val stats = booleanPreferencesKey("n_stats")
        val kinds = stringSetPreferencesKey("sport_kinds")
        val matchAlerts = stringSetPreferencesKey("match_alerts")
        val muted = stringSetPreferencesKey("muted_matches")
        val saved = stringPreferencesKey("saved")
    }

    private val savedSer = ListSerializer(PostCard.serializer())

    val settings: Flow<Settings> = ctx.store.data.map { p ->
        val d = Settings()
        Settings(
            textScale = p[K.scale] ?: d.textScale,
            sports = p[K.sports] ?: d.sports,
            nGoals = p[K.goals] ?: d.nGoals,
            nStartEnd = p[K.startEnd] ?: d.nStartEnd,
            nLineup = p[K.lineup] ?: d.nLineup,
            nCards = p[K.cards] ?: d.nCards,
            nBreaking = p[K.breaking] ?: d.nBreaking,
            newsCats = p[K.newsCats] ?: d.newsCats,
            nQuiet = p[K.quiet] ?: d.nQuiet,
            nLive = p[K.live] ?: d.nLive,
            nLiveBoard = p[K.liveBoard] ?: d.nLiveBoard,
            nAll = p[K.all] ?: d.nAll,
            nSpoiler = p[K.spoiler] ?: d.nSpoiler,
            nStats = p[K.stats] ?: d.nStats,
            // Πρώτη φορά με τις νέες ρυθμίσεις: από τις παλιές επιλογές του χρήστη
            kinds = p[K.kinds] ?: defaultKinds(p[K.goals] ?: true, p[K.startEnd] ?: true, p[K.lineup] ?: true, p[K.cards] ?: false),
            matchAlerts = p[K.matchAlerts] ?: emptySet(),
            mutedMatches = p[K.muted] ?: emptySet(),
            saved = p[K.saved]?.let { runCatching { Api.json.decodeFromString(savedSer, it) }.getOrNull() } ?: emptyList(),
        )
    }

    suspend fun setScale(v: Float) = ctx.store.edit { it[K.scale] = v.coerceIn(0.85f, 1.4f) }

    /** Κατηγορία ειδήσεων για τα έκτακτα (καμία επιλεγμένη = όλες). */
    suspend fun toggleNewsCat(name: String) = ctx.store.edit { p ->
        val cur = p[K.newsCats] ?: emptySet()
        p[K.newsCats] = if (name in cur) cur - name else cur + name
    }
    suspend fun clearNewsCats() = ctx.store.edit { it[K.newsCats] = emptySet() }

    /** «Επιλογή όλων» / «Αποεπιλογή όλων»: όλα τα αθλήματα με όλα τα είδη ειδοποίησης. */
    suspend fun selectAllSports(all: Boolean, sports: List<String>) = ctx.store.edit { p ->
        p[K.sports] = if (all) sports.toSet() else emptySet()
        p[K.kinds] = if (all) sports.flatMap { sp -> Kinds.ALL.map { "$sp:$it" } }.toSet() else (p[K.kinds] ?: defaultKinds())
    }

    suspend fun toggleSport(key: String) = ctx.store.edit { p ->
        val cur = p[K.sports] ?: Settings().sports
        p[K.sports] = if (key in cur) cur - key else cur + key
    }

    /**
     * Καμπανάκι αγώνα. Αν είναι αναμμένο (είτε επειδή ακολουθείς το άθλημα είτε χειροκίνητα) → σβήνει
     * και ο αγώνας γίνεται «σίγαση». Αν είναι σβηστό → ανάβει για όλα τα γεγονότα του αγώνα.
     */
    suspend fun toggleMatchAlert(id: Long, on: Boolean) = ctx.store.edit { p ->
        val k = id.toString()
        val al = p[K.matchAlerts] ?: emptySet(); val mu = p[K.muted] ?: emptySet()
        if (on) { p[K.matchAlerts] = al - k; p[K.muted] = mu + k }
        else { p[K.muted] = mu - k; p[K.matchAlerts] = al + k }
    }

    suspend fun setFlag(name: String, v: Boolean) = ctx.store.edit { p ->
        val key: Preferences.Key<Boolean> = when (name) {
            "goals" -> K.goals; "startEnd" -> K.startEnd; "lineup" -> K.lineup
            "cards" -> K.cards; "breaking" -> K.breaking
            "all" -> K.all; "spoiler" -> K.spoiler; "stats" -> K.stats; "live" -> K.live; "liveBoard" -> K.liveBoard; else -> K.quiet
        }
        p[key] = v
    }

    /** Ένα είδος ειδοποίησης σε ένα άθλημα. */
    suspend fun setKind(sport: String, kind: String, on: Boolean) = ctx.store.edit { p ->
        val cur = p[K.kinds] ?: defaultKinds(p[K.goals] ?: true, p[K.startEnd] ?: true, p[K.lineup] ?: true, p[K.cards] ?: false)
        p[K.kinds] = if (on) cur + "$sport:$kind" else cur - "$sport:$kind" - (if (kind == Kinds.GOALS) "$sport:${Kinds.OPP}" else "")
    }

    /** Σίγαση αγώνα (από το κουμπί της ειδοποίησης). */
    suspend fun mute(id: Long) = ctx.store.edit { p ->
        val k = id.toString()
        p[K.muted] = (p[K.muted] ?: emptySet()) + k; p[K.matchAlerts] = (p[K.matchAlerts] ?: emptySet()) - k
    }

    suspend fun saveIfMissing(card: PostCard) = ctx.store.edit { p ->
        val cur = p[K.saved]?.let { runCatching { Api.json.decodeFromString(savedSer, it) }.getOrNull() } ?: emptyList()
        if (cur.none { it.id == card.id }) p[K.saved] = Api.json.encodeToString(savedSer, (listOf(card) + cur).take(200))
    }

    suspend fun toggleSaved(card: PostCard) = ctx.store.edit { p ->
        val cur = p[K.saved]?.let { runCatching { Api.json.decodeFromString(savedSer, it) }.getOrNull() } ?: emptyList()
        val next = if (cur.any { it.id == card.id }) cur.filter { it.id != card.id } else listOf(card) + cur
        p[K.saved] = Api.json.encodeToString(savedSer, next.take(200))
    }
}

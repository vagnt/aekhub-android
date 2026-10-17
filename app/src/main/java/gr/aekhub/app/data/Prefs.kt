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
    val sports: Set<String> = setOf("football", "basketball"),
    val nGoals: Boolean = true,
    val nStartEnd: Boolean = true,
    val nLineup: Boolean = true,
    val nCards: Boolean = false,
    val nBreaking: Boolean = true,
    val nQuiet: Boolean = false,
    val nTest: Boolean = false,
    /** Γενικός διακόπτης: κλειστός = καμία ειδοποίηση από το AEK Hub. */
    val nAll: Boolean = true,
    /** «Χωρίς spoilers»: ειδοποιήσεις αγώνων χωρίς σκορ. */
    val nSpoiler: Boolean = false,
    /** Τι θέλει ο χρήστης σε κάθε άθλημα: "football:goals", "basketball:live" κ.λπ. */
    val kinds: Set<String> = defaultKinds(),
    val matchAlerts: Set<String> = emptySet(),
    val mutedMatches: Set<String> = emptySet(),
    val saved: List<PostCard> = emptyList(),
)

/** Τα είδη ειδοποίησης ανά άθλημα (ίδια ονόματα με τα θέματα m_<άθλημα>_<είδος> του server, εκτός από το "opp"). */
object Kinds {
    const val LIVE = "live"; const val GOALS = "goals"; const val OPP = "opp"; const val STARTEND = "startend"
    const val LINEUP = "lineup"; const val CARDS = "cards"; const val REMINDER = "reminder"
    val ALL = listOf(GOALS, OPP, STARTEND, LINEUP, REMINDER, CARDS)
    val SPORTS = listOf("football", "basketball", "volleyball", "handball", "women_football", "futsal", "waterpolo")
    /** Αθλήματα χωρίς ειδοποίηση σε κάθε σκορ (μόνο φάσεις και τελικό). */
    val NO_GOALS = setOf("basketball", "handball")
}
fun defaultKinds(goals: Boolean = true, startEnd: Boolean = true, lineup: Boolean = true, cards: Boolean = false): Set<String> = buildSet {
    Kinds.SPORTS.forEach { sp ->
        add("$sp:${Kinds.REMINDER}")
        if (goals) { add("$sp:${Kinds.GOALS}"); add("$sp:${Kinds.OPP}") }
        if (startEnd) add("$sp:${Kinds.STARTEND}")
        if (lineup) add("$sp:${Kinds.LINEUP}")
        if (cards) add("$sp:${Kinds.CARDS}")
    }
}

/** Θέλει ο χρήστης αυτό το είδος σε αυτό το άθλημα; (γενικός διακόπτης + ακολουθεί το άθλημα + το είδος) */
fun Settings.wants(sport: String, kind: String) = nAll && sport in sports && "$sport:$kind" in kinds

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
        val quiet = booleanPreferencesKey("n_quiet")
        val test = booleanPreferencesKey("n_test")
        val all = booleanPreferencesKey("n_all")
        val spoiler = booleanPreferencesKey("n_spoiler")
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
            nQuiet = p[K.quiet] ?: d.nQuiet,
            nTest = p[K.test] ?: d.nTest,
            nAll = p[K.all] ?: d.nAll,
            nSpoiler = p[K.spoiler] ?: d.nSpoiler,
            // Πρώτη φορά με τις νέες ρυθμίσεις: από τις παλιές επιλογές του χρήστη
            kinds = p[K.kinds] ?: defaultKinds(p[K.goals] ?: true, p[K.startEnd] ?: true, p[K.lineup] ?: true, p[K.cards] ?: false),
            matchAlerts = p[K.matchAlerts] ?: emptySet(),
            mutedMatches = p[K.muted] ?: emptySet(),
            saved = p[K.saved]?.let { runCatching { Api.json.decodeFromString(savedSer, it) }.getOrNull() } ?: emptyList(),
        )
    }

    suspend fun setScale(v: Float) = ctx.store.edit { it[K.scale] = v.coerceIn(0.85f, 1.4f) }

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
            "cards" -> K.cards; "breaking" -> K.breaking; "test" -> K.test
            "all" -> K.all; "spoiler" -> K.spoiler; else -> K.quiet
        }
        p[key] = v
    }

    /** Ένα είδος ειδοποίησης σε ένα άθλημα. */
    suspend fun setKind(sport: String, kind: String, on: Boolean) = ctx.store.edit { p ->
        val cur = p[K.kinds] ?: defaultKinds(p[K.goals] ?: true, p[K.startEnd] ?: true, p[K.lineup] ?: true, p[K.cards] ?: false)
        p[K.kinds] = if (on) cur + "$sport:$kind" else cur - "$sport:$kind"
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

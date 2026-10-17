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
    val matchAlerts: Set<String> = emptySet(),
    val mutedMatches: Set<String> = emptySet(),
    val saved: List<PostCard> = emptyList(),
)

/** Θα έρθουν ειδοποιήσεις για αυτόν τον αγώνα; (ακολουθείς το άθλημα ή το πάτησες, και δεν τον έχεις σιγάσει) */
fun Settings.alertOn(id: Long, sport: String, aek: Boolean): Boolean {
    val k = id.toString()
    if (k in mutedMatches) return false
    return k in matchAlerts || (aek && sport in sports && (nGoals || nStartEnd || nLineup || nCards))
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
            "cards" -> K.cards; "breaking" -> K.breaking; "test" -> K.test; else -> K.quiet
        }
        p[key] = v
    }

    suspend fun toggleSaved(card: PostCard) = ctx.store.edit { p ->
        val cur = p[K.saved]?.let { runCatching { Api.json.decodeFromString(savedSer, it) }.getOrNull() } ?: emptyList()
        val next = if (cur.any { it.id == card.id }) cur.filter { it.id != card.id } else listOf(card) + cur
        p[K.saved] = Api.json.encodeToString(savedSer, next.take(200))
    }
}

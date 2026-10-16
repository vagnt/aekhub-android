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
    val matchAlerts: Set<String> = emptySet(),
    val saved: List<PostCard> = emptyList(),
)

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
        val matchAlerts = stringSetPreferencesKey("match_alerts")
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
            matchAlerts = p[K.matchAlerts] ?: emptySet(),
            saved = p[K.saved]?.let { runCatching { Api.json.decodeFromString(savedSer, it) }.getOrNull() } ?: emptyList(),
        )
    }

    suspend fun setScale(v: Float) = ctx.store.edit { it[K.scale] = v.coerceIn(0.85f, 1.4f) }

    suspend fun toggleSport(key: String) = ctx.store.edit { p ->
        val cur = p[K.sports] ?: Settings().sports
        p[K.sports] = if (key in cur) cur - key else cur + key
    }

    suspend fun toggleMatchAlert(id: Long) = ctx.store.edit { p ->
        val cur = p[K.matchAlerts] ?: emptySet()
        val k = id.toString()
        p[K.matchAlerts] = if (k in cur) cur - k else cur + k
    }

    suspend fun setFlag(name: String, v: Boolean) = ctx.store.edit { p ->
        val key: Preferences.Key<Boolean> = when (name) {
            "goals" -> K.goals; "startEnd" -> K.startEnd; "lineup" -> K.lineup
            "cards" -> K.cards; "breaking" -> K.breaking; else -> K.quiet
        }
        p[key] = v
    }

    suspend fun toggleSaved(card: PostCard) = ctx.store.edit { p ->
        val cur = p[K.saved]?.let { runCatching { Api.json.decodeFromString(savedSer, it) }.getOrNull() } ?: emptyList()
        val next = if (cur.any { it.id == card.id }) cur.filter { it.id != card.id } else listOf(card) + cur
        p[K.saved] = Api.json.encodeToString(savedSer, next.take(200))
    }
}

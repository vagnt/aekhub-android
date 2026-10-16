package gr.aekhub.app.data

import android.content.Context
import gr.aekhub.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Όλα τα δεδομένα έρχονται από το aekhub.gr (Match Center API + WordPress). */
object Api {
    private const val BASE = BuildConfig.SITE + "/wp-json/aekhub/v1/app"

    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    lateinit var http: OkHttpClient
        private set

    private lateinit var dir: File
    private val mem = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Τελευταία απάντηση κάθε οθόνης (μνήμη + δίσκος): η οθόνη ανοίγει ΑΜΕΣΩΣ και μετά ανανεώνεται. */
    private fun key(path: String) = path.hashCode().toUInt().toString(16)
    private fun save(path: String, body: String) {
        mem[path] = body
        runCatching { File(dir, key(path) + ".json").writeText(body) }
    }
    fun peekRaw(path: String): String? = mem[path] ?: runCatching { File(dir, key(path) + ".json").takeIf { it.exists() }?.readText() }.getOrNull()?.also { mem[path] = it }
    inline fun <reified T> peek(path: String): T? = peekRaw(path)?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }

    fun init(ctx: Context) {
        if (::http.isInitialized) return
        dir = File(ctx.filesDir, "api").apply { mkdirs() }
        http = OkHttpClient.Builder()
            .cache(Cache(File(ctx.cacheDir, "http"), 20L * 1024 * 1024))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", "AEKHubApp/" + BuildConfig.VERSION_NAME + " (Android)").build())
            }
            .build()
    }

    /** fresh = τράβηγμα για ανανέωση: παρακάμπτει κάθε cache (κινητού και server). */
    private suspend fun get(path: String, fresh: Boolean = false): String = withContext(Dispatchers.IO) {
        val url = BASE + path + if (fresh) (if ('?' in path) "&" else "?") + "_=" + System.currentTimeMillis() else ""
        val req = Request.Builder().url(url).apply { if (fresh) cacheControl(okhttp3.CacheControl.FORCE_NETWORK) }.build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP " + r.code)
            (r.body?.string() ?: throw IOException("Κενή απάντηση")).also { save(path, it) }
        }
    }

    // Διαδρομές (ίδιες με τις κλήσεις) για να διαβάζουν οι οθόνες τα αποθηκευμένα
    fun pHome() = "/home"
    fun pStandings() = "/standings"
    fun pFeed(cat: String?) = "/feed?page=1" + (if (cat.isNullOrEmpty()) "" else "&cat=" + q(cat))
    fun pMatch(id: Long) = "/match/$id"
    fun pPost(id: Long) = "/post/$id"
    fun pMatches(from: String?, to: String?, sport: String?) = "/matches" + buildList {
        if (from != null) add("from=$from"); if (to != null) add("to=$to"); if (!sport.isNullOrEmpty()) add("sport=" + q(sport))
    }.joinToString("&").let { if (it.isEmpty()) "" else "?$it" }

    fun q(v: String) = java.net.URLEncoder.encode(v, "UTF-8")

    private var cfg: AppConfig? = null
    private var cfgAt = 0L
    /** Ρυθμίσεις από το wp-admin (κατηγορίες, διαφημίσεις): κρατιούνται 5′ στη μνήμη. */
    suspend fun config(): AppConfig {
        val c = cfg
        if (c != null && System.currentTimeMillis() - cfgAt < 60_000) return c
        return runCatching { json.decodeFromString<AppConfig>(get("/config")) }.getOrElse { peek<AppConfig>("/config") ?: throw it }
            .also { cfg = it; cfgAt = System.currentTimeMillis() }
    }
    /** Ρυθμίσεις χωρίς αναμονή (από την προηγούμενη φορά). */
    fun configNow(): AppConfig? = cfg ?: peek<AppConfig>("/config")
    suspend fun feed(cat: String?, page: Int, fresh: Boolean = false): Feed =
        json.decodeFromString(get("/feed?page=$page" + (if (cat.isNullOrEmpty()) "" else "&cat=" + q(cat)), fresh))
    suspend fun search(s: String, page: Int = 1): Feed = json.decodeFromString(get("/feed?page=$page&s=" + q(s)))
    suspend fun post(id: Long, fresh: Boolean = false): Post = json.decodeFromString(get("/post/$id", fresh))
    suspend fun matches(from: String? = null, to: String? = null, sport: String? = null, fresh: Boolean = false): MatchList {
        return json.decodeFromString(get(pMatches(from, to, sport), fresh))
    }
    suspend fun match(id: Long, fresh: Boolean = false): MatchDetail = json.decodeFromString(get("/match/$id", fresh))
    suspend fun home(fresh: Boolean = false): HomeData = json.decodeFromString(get("/home", fresh))
    suspend fun event(id: Long, fresh: Boolean = false): EventDetail = json.decodeFromString(get("/event/$id", fresh))
    suspend fun standings(fresh: Boolean = false): StandingsList = json.decodeFromString(get("/standings", fresh))
}

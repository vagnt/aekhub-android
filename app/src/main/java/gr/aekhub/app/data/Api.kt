package gr.aekhub.app.data

import android.content.Context
import gr.aekhub.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", "AEKHubApp/" + BuildConfig.VERSION_NAME + " (Android)").build())
            }
            .build()
    }

    /**
     * Κάθε λήψη πάει ΠΑΝΤΑ στον server, χωρίς καμία ενδιάμεση cache (κινητού ή WordPress.com),
     * ώστε σκορ και γεγονότα να είναι πάντα τα τρέχοντα. Το άμεσο άνοιγμα το δίνει η peek (δίσκος).
     */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun get(path: String, fresh: Boolean = false): String = withContext(Dispatchers.IO) {
        val url = BASE + path + (if ('?' in path) "&" else "?") + "_=" + System.currentTimeMillis()
        // POST: καμία cache (WordPress.com edge / Batcache / CDN) δεν αποθηκεύει POST → πάντα τα τρέχοντα.
        // Αν ο server είναι παλιός (δεν δέχεται POST), ξαναδοκιμάζει με GET.
        fun build(post: Boolean) = Request.Builder().url(url).cacheControl(okhttp3.CacheControl.FORCE_NETWORK).header("Cache-Control", "no-cache")
            .apply { if (post) post(ByteArray(0).toRequestBody(null)) }.build()
        try {
            var resp = http.newCall(build(usePost)).execute()
            if (usePost && (resp.code == 404 || resp.code == 405)) { resp.close(); usePost = false; resp = http.newCall(build(false)).execute() }
            resp.use { r ->
                if (!r.isSuccessful) throw IOException("HTTP " + r.code)
                val b = r.body?.string() ?: throw IOException("Κενή απάντηση")
                if (!b.trimStart().startsWith("{") && !b.trimStart().startsWith("[")) throw IOException("Όχι JSON: " + b.take(80))
                lastOk = System.currentTimeMillis()
                b.also { save(path, it) }
            }
        } catch (e: Exception) {
            lastErr = path.substringBefore('?') + " → " + (e.message ?: e.javaClass.simpleName); lastErrAt = System.currentTimeMillis()
            throw e
        }
    }

    /** Διάγνωση (οθόνη «Εγώ»): πότε μιλήσαμε τελευταία φορά με τον server και τι σφάλμα υπήρξε. */
    @Volatile private var usePost = true
    @Volatile var lastOk = 0L
    @Volatile var lastErr = ""
    @Volatile var lastErrAt = 0L

    // Διαδρομές (ίδιες με τις κλήσεις) για να διαβάζουν οι οθόνες τα αποθηκευμένα
    fun pHome() = "/home"
    fun pStandings() = "/standings"
    fun pFeed(cat: String?) = "/feed?page=1&per=12" + (if (cat.isNullOrEmpty()) "" else "&cat=" + q(cat))
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
        json.decodeFromString(get("/feed?page=$page&per=12" + (if (cat.isNullOrEmpty()) "" else "&cat=" + q(cat)), fresh))
    suspend fun search(s: String, page: Int = 1): Feed = json.decodeFromString(get("/feed?page=$page&s=" + q(s)))
    suspend fun post(id: Long, fresh: Boolean = false): Post = json.decodeFromString(get("/post/$id", fresh))
    suspend fun matches(from: String? = null, to: String? = null, sport: String? = null, fresh: Boolean = false): MatchList {
        return json.decodeFromString(get(pMatches(from, to, sport), fresh))
    }
    suspend fun match(id: Long, fresh: Boolean = false): MatchDetail = json.decodeFromString(get("/match/$id", fresh))
    suspend fun home(fresh: Boolean = false): HomeData = json.decodeFromString(get("/home", fresh))
    suspend fun event(id: Long, fresh: Boolean = false): EventDetail = json.decodeFromString(get("/event/$id", fresh))
    /** Μετρητής καμπανιών χορηγών (εμφάνιση «i» / κλικ «c»). Αθόρυβα, χωρίς να περιμένει απάντηση. */
    fun track(campaign: String, ev: String) {
        if (!::http.isInitialized) return
        val req = Request.Builder().url(BASE + "/adtrack?c=" + q(campaign) + "&e=" + ev).post(ByteArray(0).toRequestBody(null)).build()
        http.newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {}
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) { response.close() }
        })
    }
    suspend fun standings(fresh: Boolean = false): StandingsList = json.decodeFromString(get("/standings", fresh))

    /** Αναγνωριστικό συσκευής για τις ψήφους (μία ψήφος ανά συσκευή) — τυχαίο, χωρίς προσωπικά στοιχεία. */
    fun deviceId(ctx: Context): String {
        val sp = ctx.getSharedPreferences("aekhub_dev", Context.MODE_PRIVATE)
        return sp.getString("id", null) ?: java.util.UUID.randomUUID().toString().replace("-", "").also { sp.edit().putString("id", it).apply() }
    }

    /** 🔮 Πρόβλεψη σκορ: key «2-1» / «3-1» (σετ) / «H» ή «A» (νικητής). Σφάλμα → μήνυμα του server. */
    suspend fun predict(ctx: Context, id: Long, key: String): Predict = withContext(Dispatchers.IO) {
        val body = json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), kotlinx.serialization.json.buildJsonObject {
            put("device", kotlinx.serialization.json.JsonPrimitive(deviceId(ctx))); put("key", kotlinx.serialization.json.JsonPrimitive(key))
        })
        val req = Request.Builder().url("$BASE/predict/$id").post(body.toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { r ->
            val b = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException(runCatching { json.parseToJsonElement(b).jsonObject["message"]?.jsonPrimitive?.content }.getOrNull() ?: ("HTTP " + r.code))
            json.decodeFromString(Predict.serializer(), b)
        }
    }
}

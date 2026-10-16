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

    private lateinit var http: OkHttpClient

    fun init(ctx: Context) {
        if (::http.isInitialized) return
        http = OkHttpClient.Builder()
            .cache(Cache(File(ctx.cacheDir, "http"), 20L * 1024 * 1024))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", "AEKHubApp/" + BuildConfig.VERSION_NAME + " (Android)").build())
            }
            .build()
    }

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(BASE + path).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP " + r.code)
            r.body?.string() ?: throw IOException("Κενή απάντηση")
        }
    }

    private fun q(v: String) = java.net.URLEncoder.encode(v, "UTF-8")

    private var cfg: AppConfig? = null
    private var cfgAt = 0L
    /** Ρυθμίσεις από το wp-admin (κατηγορίες, διαφημίσεις): κρατιούνται 5′ στη μνήμη. */
    suspend fun config(): AppConfig {
        val c = cfg
        if (c != null && System.currentTimeMillis() - cfgAt < 300_000) return c
        return json.decodeFromString<AppConfig>(get("/config")).also { cfg = it; cfgAt = System.currentTimeMillis() }
    }
    suspend fun feed(cat: String?, page: Int): Feed =
        json.decodeFromString(get("/feed?page=$page" + (if (cat.isNullOrEmpty()) "" else "&cat=" + q(cat))))
    suspend fun search(s: String, page: Int = 1): Feed = json.decodeFromString(get("/feed?page=$page&s=" + q(s)))
    suspend fun post(id: Long): Post = json.decodeFromString(get("/post/$id"))
    suspend fun matches(from: String? = null, to: String? = null, sport: String? = null): MatchList {
        val p = buildList {
            if (from != null) add("from=$from")
            if (to != null) add("to=$to")
            if (!sport.isNullOrEmpty()) add("sport=" + q(sport))
        }.joinToString("&")
        return json.decodeFromString(get("/matches" + if (p.isEmpty()) "" else "?$p"))
    }
    suspend fun match(id: Long): MatchDetail = json.decodeFromString(get("/match/$id"))
    suspend fun standings(): StandingsList = json.decodeFromString(get("/standings"))
}

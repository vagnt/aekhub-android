package gr.aekhub.app.data

import android.content.Context
import gr.aekhub.app.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID

/** Ανώνυμο «αποτύπωμα συσκευής»: τυχαίος κωδικός που φτιάχνεται μία φορά (δεν συνδέεται με τίποτα προσωπικό). */
object Device {
    @Volatile private var cached: String? = null
    fun id(ctx: Context): String = cached ?: synchronized(this) {
        cached ?: ctx.applicationContext.getSharedPreferences("aek_dev", Context.MODE_PRIVATE).let { sp ->
            (sp.getString("id", null) ?: UUID.randomUUID().toString().also { sp.edit().putString("id", it).apply() }).also { cached = it }
        }
    }
    /** Το όνομα που έγραψε ο χρήστης στα σχόλια (θυμάται για την επόμενη φορά). */
    fun name(ctx: Context): String = ctx.applicationContext.getSharedPreferences("aek_dev", Context.MODE_PRIVATE).getString("name", "").orEmpty()
    fun setName(ctx: Context, n: String) { ctx.applicationContext.getSharedPreferences("aek_dev", Context.MODE_PRIVATE).edit().putString("name", n).apply() }
}

/**
 * Στατιστικά χρήσης (ανώνυμα): κάθε οθόνη που ανοίγει στέλνεται (1) στο Google Analytics μέσω Firebase και (2) στα δικά μας
 * αναλυτικά του site (πηγή «Εφαρμογή Android», φαίνεται στο Κέντρο Ελέγχου). Ο χρήστης μπορεί να τα κλείσει από «Εγώ».
 * kind (αναλυτικά site): 1 άρθρο · 2 αρχική · 3 κατηγορία · 4 αγώνας · 5 σελίδα · 6 άλλο
 */
object Analytics {
    private val seen = HashMap<String, Long>()
    @Volatile var enabled = true

    private fun fb(ctx: Context): com.google.firebase.analytics.FirebaseAnalytics? =
        if (com.google.firebase.FirebaseApp.getApps(ctx).isNotEmpty()) runCatching { com.google.firebase.analytics.FirebaseAnalytics.getInstance(ctx.applicationContext) }.getOrNull() else null

    /** Ο διακόπτης «Ανώνυμα στατιστικά» της οθόνης «Εγώ»: κλείνει ΚΑΙ το Google Analytics ΚΑΙ τα δικά μας. */
    fun setEnabled(ctx: Context, on: Boolean) { enabled = on; fb(ctx)?.setAnalyticsCollectionEnabled(on) }

    /** Άνοιξε οθόνη: screen_view στο GA + hit στο site. */
    fun screen(ctx: Context, name: String, kind: Int, oid: Long = 0L) {
        if (!enabled) return
        val key = "$kind:$oid:$name"
        synchronized(seen) { val t = seen[key]; if (t != null && System.currentTimeMillis() - t < 20_000) return; seen[key] = System.currentTimeMillis() } // όχι διπλή καταγραφή σε περιστροφή/επιστροφή
        fb(ctx)?.logEvent(com.google.firebase.analytics.FirebaseAnalytics.Event.SCREEN_VIEW, android.os.Bundle().apply {
            putString(com.google.firebase.analytics.FirebaseAnalytics.Param.SCREEN_NAME, name)
            putString(com.google.firebase.analytics.FirebaseAnalytics.Param.SCREEN_CLASS, "MainActivity")
            if (oid > 0) putLong("item_id", oid)
        })
        siteHit(ctx, kind, oid)
    }

    /** Γεγονός στο GA: π.χ. article_view, comment_post, share. */
    fun event(ctx: Context, name: String, vararg params: Pair<String, Any>) {
        if (!enabled) return
        fb(ctx)?.logEvent(name, android.os.Bundle().apply { params.forEach { (k, v) -> when (v) { is Long -> putLong(k, v); is Int -> putLong(k, v.toLong()); else -> putString(k, v.toString().take(100)) } } })
    }

    private fun siteHit(ctx: Context, kind: Int, oid: Long) {
        if (!Api.isReady()) return
        val tablet = ctx.resources.configuration.smallestScreenWidthDp >= 600
        val body = """{"k":$kind,"o":$oid,"s":14,"v":${if (tablet) 2 else 1},"d":"${Device.id(ctx)}"}"""
        val req = Request.Builder().url(BuildConfig.SITE + "/wp-json/aekhub/v1/hit").post(body.toRequestBody("text/plain".toMediaType())).build()
        Api.http.newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {}
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) { response.close() }
        })
    }
}

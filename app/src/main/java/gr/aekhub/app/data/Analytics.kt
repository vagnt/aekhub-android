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
    /** Ενεργό μόνο όταν υπάρχει συναίνεση (GDPR) ΚΑΙ δεν το έκλεισε ο χρήστης από «Εγώ». Μέχρι να ξέρουμε τη συναίνεση: κλειστό. */
    @Volatile var enabled = false
    @Volatile private var consentOk = false
    @Volatile private var userOn = true

    private fun fb(ctx: Context): com.google.firebase.analytics.FirebaseAnalytics? =
        if (com.google.firebase.FirebaseApp.getApps(ctx).isNotEmpty()) runCatching { com.google.firebase.analytics.FirebaseAnalytics.getInstance(ctx.applicationContext) }.getOrNull() else null

    /** Ο διακόπτης «Ανώνυμα στατιστικά» της οθόνης «Εγώ»: κλείνει ΚΑΙ το Google Analytics ΚΑΙ τα δικά μας. */
    fun setEnabled(ctx: Context, on: Boolean) { userOn = on; apply(ctx) }

    /** Η συναίνεση από το Google UMP (η φόρμα του AdMob): ΕΕ/Ελλάδα → πρέπει να έχει δοθεί· αλλού → δεν απαιτείται. */
    fun setConsent(ctx: Context, ok: Boolean) { consentOk = ok; apply(ctx) }

    private fun apply(ctx: Context) {
        val on = consentOk && userOn; enabled = on
        fb(ctx)?.let { f ->
            f.setConsent(mapOf(com.google.firebase.analytics.FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to
                (if (on) com.google.firebase.analytics.FirebaseAnalytics.ConsentStatus.GRANTED else com.google.firebase.analytics.FirebaseAnalytics.ConsentStatus.DENIED)))
            f.setAnalyticsCollectionEnabled(on)
        }
    }

    /** Άνοιξε οθόνη: screen_view στο GA + hit στο site. */
    fun screen(ctx: Context, name: String, kind: Int, oid: Long = 0L) {
        curKind = kind; curOid = oid
        if (!enabled) return
        val key = "$kind:$oid:$name"
        // Όπως στο site, όπου μετράει μόνο το άνοιγμα μιας σελίδας: εδώ οι καρτέλες (Αρχική, Αγώνες, Βαθμολογίες, Εγώ)
        // ανοίγουν ξανά και ξανά καθώς πηγαινοέρχεσαι, άρα μετράνε μία φορά ανά 30′· άρθρα/αγώνες ανά 5′, κατηγορίες ανά 10′.
        val win = when (kind) { 1, 4 -> 5 * 60_000L; 3 -> 10 * 60_000L; else -> 30 * 60_000L }
        synchronized(seen) { val t = seen[key]; if (t != null && System.currentTimeMillis() - t < win) return; seen[key] = System.currentTimeMillis() }
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

    @Volatile private var curKind = 2
    @Volatile private var curOid = 0L

    /**
     * 🔴 «Live τώρα» στα στατιστικά του site: όσο η εφαρμογή είναι ανοιχτή, ένας μικρός παλμός κάθε 60″ με την οθόνη που βλέπει
     * (όπως κάνει και το site)· όταν βγει από την εφαρμογή (leave) φεύγει αμέσως από το «μέσα τώρα».
     */
    fun ping(ctx: Context, leave: Boolean = false) {
        if (!enabled || !Api.isReady() || gr.aekhub.app.BuildConfig.DEV) return
        val body = "{\"k\":$curKind,\"o\":$curOid,\"d\":\"${Device.id(ctx)}\"" + (if (leave) ",\"x\":1}" else "}")
        val req = Request.Builder().url(BuildConfig.SITE + "/wp-json/aekhub-an/v1/ping").post(body.toRequestBody("text/plain".toMediaType())).build()
        Api.http.newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {}
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) { response.close() }
        })
    }

    private fun siteHit(ctx: Context, kind: Int, oid: Long) {
        if (!Api.isReady() || gr.aekhub.app.BuildConfig.DEV) return // το dev APK δεν μετράει στα στατιστικά του site
        val tablet = ctx.resources.configuration.smallestScreenWidthDp >= 600
        val body = """{"k":$kind,"o":$oid,"s":14,"v":${if (tablet) 2 else 1},"d":"${Device.id(ctx)}"}"""
        val req = Request.Builder().url(BuildConfig.SITE + "/wp-json/aekhub/v1/hit").post(body.toRequestBody("text/plain".toMediaType())).build()
        Api.http.newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {}
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) { response.close() }
        })
    }
}

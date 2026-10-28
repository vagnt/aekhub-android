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
 * Στατιστικά χρήσης: κάθε οθόνη που ανοίγει στέλνει ένα αθόρυβο «hit» στα αναλυτικά του site (πηγή «Εφαρμογή Android»),
 * ώστε να φαίνεται στο Κέντρο Ελέγχου πόσοι διαβάζουν και τι. Χωρίς cookies, χωρίς τρίτους, ανώνυμα.
 * kind: 1 άρθρο · 2 αρχική · 3 κατηγορία · 4 αγώνας · 5 σελίδα · 6 άλλο
 */
object Analytics {
    private val seen = HashMap<String, Long>()
    fun hit(ctx: Context, kind: Int, oid: Long = 0L) {
        if (!Api.isReady()) return
        val key = "$kind:$oid"
        synchronized(seen) { val t = seen[key]; if (t != null && System.currentTimeMillis() - t < 20_000) return; seen[key] = System.currentTimeMillis() } // όχι διπλή καταγραφή σε περιστροφή/επιστροφή
        val tablet = ctx.resources.configuration.smallestScreenWidthDp >= 600
        val body = """{"k":$kind,"o":$oid,"s":14,"v":${if (tablet) 2 else 1},"d":"${Device.id(ctx)}"}"""
        val req = Request.Builder().url(BuildConfig.SITE + "/wp-json/aekhub/v1/hit").post(body.toRequestBody("text/plain".toMediaType())).build()
        Api.http.newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {}
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) { response.close() }
        })
    }
}

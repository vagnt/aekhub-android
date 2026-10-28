package gr.aekhub.app.data

import android.content.Context
import gr.aekhub.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.Request

/** Σχόλιο: ίδια δεδομένα με το σύστημα σχολίων του site (μόνο όνομα, likes, αναφορές, ένα επίπεδο απαντήσεων). */
@Serializable
data class CItem(
    val id: Long,
    val name: String = "",
    val ini: String = "?",
    val color: String = "#888888",
    val staff: Boolean = false,
    val pinned: Boolean = false,
    val date: String = "",
    val text: String = "",
    val likes: Int = 0,
    val liked: Boolean = false,
    val replyTo: String = "",
    val pending: Boolean = false,
    val replies: List<CItem> = emptyList(),
)

@Serializable
data class CPage(
    val ok: Boolean = true, val items: List<CItem> = emptyList(), val more: Boolean = false, val page: Int = 1, val total: Int = 0,
    val open: Boolean = true, val likes: Boolean = true, val reports: Boolean = true, val max: Int = 0, val message: String = "",
)

@Serializable
data class CSent(val ok: Boolean = false, val approved: Boolean = false, val message: String = "", val item: CItem? = null, val total: Int = 0)

@Serializable
data class CMark(val ok: Boolean = false, val likes: Int = 0, val liked: Boolean = false, val hidden: Boolean = false, val message: String = "")

object CommentsApi {
    private fun base(path: String) = BuildConfig.SITE + "/wp-json/aekhub/v1/app/" + path

    private suspend fun call(ctx: Context, path: String, form: Map<String, String>?): String = withContext(Dispatchers.IO) {
        val b = Request.Builder().url(base(path) + (if (form == null) (if ('?' in path) "&" else "?") + "_=" + System.currentTimeMillis() else ""))
            .header("X-Aek-Device", Device.id(ctx)).header("Cache-Control", "no-cache")
        if (form != null) b.post(FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build())
        Api.http.newCall(b.build()).execute().use { r -> r.body?.string().orEmpty() }
    }

    suspend fun list(ctx: Context, postId: Long, sort: String, page: Int): CPage =
        Api.json.decodeFromString(call(ctx, "comments/$postId?sort=$sort&page=$page", null))

    suspend fun send(ctx: Context, postId: Long, author: String, text: String, parent: Long, elapsedMs: Long): CSent =
        runCatching { Api.json.decodeFromString<CSent>(call(ctx, "comments/$postId", mapOf("author" to author, "comment" to text, "parent" to parent.toString(), "el" to elapsedMs.toString()))) }
            .getOrElse { CSent(false, message = "Δεν στάλθηκε — έλεγξε τη σύνδεσή σου.") }

    suspend fun like(ctx: Context, id: Long, on: Boolean): CMark =
        runCatching { Api.json.decodeFromString<CMark>(call(ctx, "comment/$id/like", mapOf("on" to if (on) "1" else ""))) }.getOrElse { CMark(false, message = "Χωρίς σύνδεση") }

    suspend fun report(ctx: Context, id: Long): CMark =
        runCatching { Api.json.decodeFromString<CMark>(call(ctx, "comment/$id/report", mapOf("x" to "1"))) }.getOrElse { CMark(false, message = "Χωρίς σύνδεση") }
}

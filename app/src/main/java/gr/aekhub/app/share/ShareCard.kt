package gr.aekhub.app.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import gr.aekhub.app.R
import gr.aekhub.app.data.MatchDetail
import gr.aekhub.app.push.Notifs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * «Ημέρα αγώνα»: κάρτα σκορ 1080×1350 (σήματα, σκορ, διοργάνωση) για κοινοποίηση σε Instagram/WhatsApp/κ.λπ.,
 * και «Πρόσθεσε στο ημερολόγιο» για τους επόμενους αγώνες. Η εικόνα φτιάχνεται στο κινητό — δεν περνά από τον server.
 */
object ShareCard {
    private val ATHENS: ZoneId = ZoneId.of("Europe/Athens")
    private const val W = 1080
    private const val H = 1350
    private const val GOLD = 0xFFF9CF1C.toInt()
    private const val INK = 0xFF0B0B0A.toInt()
    private const val TEXT = 0xFFF3F1E9.toInt()
    private const val MUTED = 0xFF9A968A.toInt()

    private fun font(ctx: Context, id: Int): Typeface = runCatching { ResourcesCompat.getFont(ctx, id) }.getOrNull() ?: Typeface.DEFAULT_BOLD

    fun startMillis(m: MatchDetail): Long? = runCatching {
        val d = LocalDate.parse(m.date.take(10))
        val t = m.time.takeIf { it.length >= 4 && it[0].isDigit() }?.let { LocalTime.parse(it.take(5)) } ?: LocalTime.NOON
        LocalDateTime.of(d, t).atZone(ATHENS).toInstant().toEpochMilli()
    }.getOrNull()

    /** Η κάρτα ως αρχείο PNG στην cache· επιστρέφει το Uri για κοινοποίηση. */
    suspend fun build(ctx: Context, m: MatchDetail): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            c.drawColor(INK)
            // λάμψη της ΑΕΚ πάνω δεξιά και χαμηλά αριστερά
            p.shader = RadialGradient(W * 0.88f, 0f, 760f, intArrayOf(0x66F9CF1C, 0x00F9CF1C), null, Shader.TileMode.CLAMP); c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
            p.shader = RadialGradient(0f, H.toFloat(), 620f, intArrayOf(0x44805F00, 0x00805F00), null, Shader.TileMode.CLAMP); c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
            p.shader = null
            val head = font(ctx, R.font.sofia_black)
            val bold = font(ctx, R.font.manrope_extrabold)

            // κεφαλίδα: λογότυπο + διοργάνωση
            runCatching { android.graphics.BitmapFactory.decodeResource(ctx.resources, R.drawable.hub_logo) }.getOrNull()?.let {
                c.drawBitmap(Bitmap.createScaledBitmap(it, 120, 120 * it.height / it.width.coerceAtLeast(1), true), 64f, 64f, null)
            }
            p.typeface = bold; p.textSize = 38f; p.color = GOLD; p.letterSpacing = 0.08f
            c.drawText(m.competition.uppercase().take(34), 64f + 150f, 134f, p)
            p.letterSpacing = 0f

            // σήματα
            val hl = Notifs.crest(ctx, m.homeLogo, 420); val al = Notifs.crest(ctx, m.awayLogo, 420)
            val box = 330
            val cy = 420
            hl?.let { c.drawBitmap(Bitmap.createScaledBitmap(it, box, box, true), 90f, cy.toFloat(), null) }
            al?.let { c.drawBitmap(Bitmap.createScaledBitmap(it, box, box, true), (W - 90 - box).toFloat(), cy.toFloat(), null) }

            // ονόματα
            p.typeface = head; p.textSize = 52f; p.color = TEXT; p.textAlign = Paint.Align.CENTER
            c.drawText(m.home.uppercase().take(16), 90f + box / 2f, (cy + box + 90).toFloat(), p)
            c.drawText(m.away.uppercase().take(16), (W - 90 - box / 2f), (cy + box + 90).toFloat(), p)

            // κέντρο: σκορ ή ώρα
            val played = m.live || m.finished()
            p.typeface = head; p.color = GOLD
            if (played) { p.textSize = 190f; c.drawText(m.scoreHome + "–" + m.scoreAway, W / 2f, cy + box / 2f + 70f, p) }
            else { p.textSize = 120f; c.drawText(m.time.take(5), W / 2f, cy + box / 2f + 40f, p) }

            // πλαίσιο κατάστασης
            val label = when {
                m.live -> "LIVE" + (if (m.minute.isNotBlank()) " · " + m.minute else "")
                m.finished() -> "ΤΕΛΙΚΟ"
                else -> m.date.take(10).let { d -> runCatching { LocalDate.parse(d).let { ld -> ld.dayOfMonth.toString() + "/" + ld.monthValue } }.getOrDefault("") }
            }
            if (label.isNotBlank()) {
                p.textSize = 44f; p.typeface = bold
                val tw = p.measureText(label)
                val r = RectF(W / 2f - tw / 2 - 36f, cy + box / 2f + 120f, W / 2f + tw / 2 + 36f, cy + box / 2f + 196f)
                p.color = if (m.live) 0xFFFF453A.toInt() else GOLD; c.drawRoundRect(r, 38f, 38f, p)
                p.color = if (m.live) TEXT else INK; c.drawText(label, W / 2f, r.bottom - 22f, p)
            }

            // πάτωμα
            val venue = listOf(m.venue, m.broadcast).filter { it.isNotBlank() }.joinToString(" · ")
            p.textAlign = Paint.Align.CENTER; p.typeface = bold; p.textSize = 38f; p.color = MUTED
            if (venue.isNotBlank()) c.drawText(venue.take(46), W / 2f, 1130f, p)
            p.shader = LinearGradient(0f, 0f, W.toFloat(), 0f, intArrayOf(0x00F9CF1C, GOLD, 0x00F9CF1C), null, Shader.TileMode.CLAMP)
            c.drawRect(120f, 1180f, W - 120f, 1184f, p); p.shader = null
            p.typeface = head; p.textSize = 64f; p.color = GOLD; c.drawText("aekhub.gr", W / 2f, 1272f, p)

            val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
            val f = File(dir, "aekhub-" + m.id + ".png")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        }.getOrNull()
    }

    private fun MatchDetail.finished() = status == "finished"

    fun share(ctx: Context, uri: Uri, text: String) {
        val i = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Κοινοποίηση αγώνα").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** «Πρόσθεσε στο ημερολόγιο»: ανοίγει το Ημερολόγιο του κινητού με έτοιμο γεγονός (χωρίς καμία άδεια). */
    fun addToCalendar(ctx: Context, m: MatchDetail): Boolean {
        val start = startMillis(m) ?: return false
        val i = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, m.home + " – " + m.away)
            .putExtra(CalendarContract.Events.EVENT_LOCATION, m.venue)
            .putExtra(CalendarContract.Events.DESCRIPTION, listOf(m.competition, m.broadcast).filter { it.isNotBlank() }.joinToString(" · ") + "\nhttps://aekhub.gr")
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 2 * 3_600_000L)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { ctx.startActivity(i) }.isSuccess
    }
}

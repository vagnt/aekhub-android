package gr.aekhub.app.push

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import coil.request.SuccessResult
import gr.aekhub.app.MainActivity
import gr.aekhub.app.R
import gr.aekhub.app.data.Kinds
import gr.aekhub.app.data.Settings
import gr.aekhub.app.data.wants
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.time.LocalTime
import java.time.ZoneId

/**
 * Οι ειδοποιήσεις του AEK Hub, στη νέα μορφή:
 *  • μία ΖΩΝΤΑΝΗ ειδοποίηση ανά αγώνα (σκορ, λεπτό, μπάρα αγώνα, σκόρερ) που ενημερώνεται αθόρυβα·
 *    στο Android 16 προωθείται σε Live Update (σκορ στη γραμμή κατάστασης)
 *  • γκολ της ΑΕΚ: δυνατή ειδοποίηση με τον ήχο του AEK Hub· όλα τα άλλα αθόρυβα
 *  • σήματα ομάδων αντί για emoji, κουμπιά (Δες το γκολ, Match Center, Σίγαση, Αποθήκευση)
 *  • «Χωρίς spoilers»: χωρίς σκορ στις ειδοποιήσεις
 */
object Notifs {
    // Κανάλια (ο χρήστης τα ρυθμίζει και από τις ρυθμίσεις του Android)
    const val CH_LIVE = "live_match"
    const val CH_GOAL = "goal_aek"
    const val CH_MATCH = "matchday"
    const val CH_QUIET = "match_quiet"
    const val CH_NEWS = "news"

    // Θέσεις μέσα σε κάθε αγώνα (ίδιο tag "m<id>": η νέα αντικαθιστά την παλιά του ίδιου είδους)
    private const val SLOT_LIVE = 1
    private const val SLOT_GOAL = 2
    private const val SLOT_EVENT = 3
    private const val SLOT_LINEUP = 4
    private const val SLOT_REMIND = 5

    const val ACT_MUTE = "gr.aekhub.app.MUTE"
    const val ACT_SAVE = "gr.aekhub.app.SAVE"
    const val ACT_TICK = "gr.aekhub.app.TICK"

    private const val GOLD = 0xFFF9CF1C.toInt()

    fun channels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.deleteNotificationChannel("matches") } // παλιό κανάλι (έως 0.8)
        nm.createNotificationChannel(NotificationChannel(CH_LIVE, "Ζωντανός αγώνας", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Μία ειδοποίηση ανά αγώνα με σκορ και λεπτό, που ενημερώνεται αθόρυβα"
            setSound(null, null); enableVibration(false); setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_GOAL, "Γκολ της ΑΕΚ", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Με τον ήχο γκολ του AEK Hub"
            setSound(Uri.parse("android.resource://" + ctx.packageName + "/" + R.raw.aek_goal),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true); vibrationPattern = longArrayOf(0, 180, 90, 180, 90, 420)
        })
        nm.createNotificationChannel(NotificationChannel(CH_MATCH, "Ημέρα αγώνα", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Υπενθύμιση, ενδεκάδα, σέντρα, τελικό"
        })
        nm.createNotificationChannel(NotificationChannel(CH_QUIET, "Εξελίξεις αγώνα (αθόρυβα)", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Γκολ αντιπάλου, ημίχρονο, κάρτες"
        })
        nm.createNotificationChannel(NotificationChannel(CH_NEWS, "Ειδήσεις", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Έκτακτα και σημαντικά άρθρα"
        })
    }

    /* ───────── Είσοδος: ένα μήνυμα από τον server ───────── */
    fun handle(ctx: Context, d: Map<String, String>, s: Settings) {
        if (!s.nAll) return // γενικός διακόπτης
        if (!Push.allowed(ctx)) return
        channels(ctx)
        val kind = d["kind"] ?: "breaking"
        val id = d["match"]?.toLongOrNull() ?: 0L
        val test = (d["title"] ?: "").startsWith("[Δοκιμή]")

        if (kind == "breaking") { if (s.nBreaking) news(ctx, d); return }
        if (id > 0 && id.toString() in s.mutedMatches) { cancelMatch(ctx, id); return }

        val sport = d["sport"] ?: "football"
        val explicit = id > 0 && id.toString() in s.matchAlerts
        val aekScored = d["aekScored"] == "1" || d["aek"].isNullOrEmpty()
        val want = when (kind) {
            "goals" -> if (d["ev"] == "goal" || d["ev"] == "set") (if (aekScored) Kinds.GOALS else Kinds.OPP) else Kinds.GOALS
            else -> kind
        }
        val allowed = if (test) s.nTest else explicit || s.wants(sport, want)
        if (!allowed) return
        val liveOn = if (test) s.nTest else explicit || s.wants(sport, Kinds.LIVE)

        // Ήσυχες ώρες 01:00–08:00: περνάνε μόνο τα γκολ της ΑΕΚ (και η αθόρυβη ζωντανή)
        if (s.nQuiet && kind != "live" && !(kind == "goals" && aekScored && d["ev"] == "goal")) {
            if (LocalTime.now(ZoneId.of("Europe/Athens")).hour in 1..7) return
        }

        val sp = s.nSpoiler
        val ev = d["ev"] ?: ""
        val status = d["status"] ?: ""
        val liveNow = status !in setOf("", "upcoming", "finished", "postponed")
        when (kind) {
            "live" -> live(ctx, d, sp, alert = false)
            "goals" -> when {
                ev == "goal" && aekScored && d["aek"]?.isNotEmpty() == true -> goal(ctx, d, sp, loud = !sp)
                liveOn && liveNow && ev != "pen" -> {} // γκολ αντιπάλου / ακύρωση / σετ: μέσα στη ζωντανή, αθόρυβα
                else -> goal(ctx, d, sp, loud = false)
            }
            "startend" -> when {
                ev == "ft" -> final(ctx, d, sp)
                ev == "postponed" -> event(ctx, d, sp, CH_MATCH, SLOT_LIVE)
                status == "live" || status == "q1" -> event(ctx, d, sp, CH_MATCH, SLOT_LIVE) // σέντρα: μετά γίνεται η ζωντανή
                liveOn && liveNow -> {} // ημίχρονο, δεκάλεπτα: μέσα στη ζωντανή
                else -> event(ctx, d, sp, CH_QUIET, SLOT_EVENT)
            }
            "cards" -> event(ctx, d, sp, CH_QUIET, SLOT_EVENT)
            "lineup" -> lineup(ctx, d)
            "reminder" -> reminder(ctx, d)
        }
    }

    /* ───────── Βοηθητικά ───────── */
    private var imgLoader: ImageLoader? = null
    private val crestCache = HashMap<String, Bitmap>()

    /** Σήμα ομάδας ως εικόνα (και SVG). */
    fun crest(ctx: Context, url: String?, size: Int = 192): Bitmap? {
        if (url.isNullOrBlank() || !url.startsWith("http")) return null
        crestCache[url]?.let { return it }
        val il = imgLoader ?: ImageLoader.Builder(ctx.applicationContext).components { add(SvgDecoder.Factory()) }.build().also { imgLoader = it }
        val bmp = runCatching {
            runBlocking {
                withTimeoutOrNull(6_000) {
                    val r = il.execute(ImageRequest.Builder(ctx).data(url).size(size).allowHardware(false).build())
                    (r as? SuccessResult)?.drawable?.toBitmap(size, size, Bitmap.Config.ARGB_8888)
                }
            }
        }.getOrNull()
        if (bmp != null) crestCache[url] = bmp
        return bmp
    }

    private fun tag(id: Long) = "m$id"

    private fun openMatch(ctx: Context, id: Long, req: Int): PendingIntent =
        PendingIntent.getActivity(ctx, (id * 10 + req).toInt(), Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_MATCH, id)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun openTab(ctx: Context, tab: String, req: Int): PendingIntent =
        PendingIntent.getActivity(ctx, req, Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_TAB, tab)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun openPost(ctx: Context, post: Long): PendingIntent =
        PendingIntent.getActivity(ctx, post.toInt(), Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_POST, post)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun broadcast(ctx: Context, action: String, req: Int, extras: Bundle.() -> Unit): PendingIntent =
        PendingIntent.getBroadcast(ctx, req, Intent(ctx, NotifReceiver::class.java).setAction(action).putExtras(Bundle().apply(extras)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun muteAction(ctx: Context, id: Long) =
        NotificationCompat.Action(0, "Σίγαση αγώνα", broadcast(ctx, ACT_MUTE, (id * 10 + 7).toInt()) { putLong("match", id) })

    private fun base(ctx: Context, ch: String, title: String, text: String) =
        NotificationCompat.Builder(ctx, ch)
            .setSmallIcon(R.drawable.ic_stat_aek)
            .setColor(GOLD)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)

    private fun post(ctx: Context, tag: String?, slot: Int, b: NotificationCompat.Builder) {
        try { NotificationManagerCompat.from(ctx).notify(tag, slot, b.build()) } catch (_: SecurityException) {}
    }

    private fun vs(d: Map<String, String>) = (d["home"] ?: "") + " – " + (d["away"] ?: "")
    private fun line(d: Map<String, String>): String {
        val pen = if (!d["ph"].isNullOrBlank() && !d["pa"].isNullOrBlank() && (d["status"] == "penalties" || d["status"] == "finished")) " (πέν. ${d["ph"]}–${d["pa"]})" else ""
        return (d["home"] ?: "") + " " + (d["sh"] ?: "0") + "–" + (d["sa"] ?: "0") + " " + (d["away"] ?: "") + pen
    }
    private fun aekLogo(d: Map<String, String>) = if (d["aek"] == "away") d["alogo"] else d["hlogo"]

    /** Λεπτό αγώνα τώρα: από το ρολόι του Match Center, αλλιώς ό,τι έστειλε ο server. */
    private fun minuteNow(d: Map<String, String>): String {
        val b = d["clockB"]?.toDoubleOrNull(); val st = d["clockS"]?.toDoubleOrNull(); val l = d["clockL"]?.toDoubleOrNull() ?: 45.0
        if (b != null && st != null && st > 0) {
            val el = System.currentTimeMillis() - st
            if (el in 0L..3 * 3_600_000L) {
                val min = kotlin.math.floor(b + el / 60000.0).toInt() + 1
                val end = (b + l).toInt()
                return if (min > end) "$end+${min - end}′" else "$min′"
            }
        }
        return d["min"].orEmpty()
    }

    private fun spoilerTitle(d: Map<String, String>) = vs(d)

    /* ───────── Η ζωντανή ειδοποίηση ───────── */
    fun live(ctx: Context, d: Map<String, String>, spoiler: Boolean, alert: Boolean, fromTick: Boolean = false) {
        val id = d["match"]?.toLongOrNull() ?: return
        val status = d["status"] ?: ""
        val done = status == "finished" || status == "postponed"
        val min = minuteNow(d)
        val phase = d["phase"].takeUnless { it.isNullOrBlank() } ?: d["statusLabel"].orEmpty()
        val when_ = listOf(min, phase).filter { it.isNotBlank() }.distinct().joinToString(" · ")
        val title = if (spoiler) vs(d) else line(d)
        val text = if (spoiler) (if (done) "Ο αγώνας τελείωσε." else "Σε εξέλιξη" + (if (when_.isNotBlank()) " · $when_" else ""))
            else listOf(if (done) "Τελικό" else when_, d["scorers"].orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
        val b = base(ctx, CH_LIVE, title, text)
            .setSubText(d["comp"])
            .setOngoing(!done)
            .setOnlyAlertOnce(true)
            .setSilent(!alert)
            .setAutoCancel(done)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(openMatch(ctx, id, 1))
            .addAction(0, "Match Center", openMatch(ctx, id, 2))
        if (!done) b.addAction(muteAction(ctx, id))
        crest(ctx, aekLogo(d))?.let { b.setLargeIcon(it) }
        // Μπάρα αγώνα: ποδόσφαιρο/futsal/πόλο με το λεπτό, μπάσκετ με το δεκάλεπτο
        if (!done) {
            val m = Regex("""^(\d+)""").find(min)?.groupValues?.get(1)?.toIntOrNull()
            val q = Regex("""^q(\d)$""").find(status)?.groupValues?.get(1)?.toIntOrNull()
            val total = when (d["sport"]) { "futsal" -> 40; "waterpolo" -> 32; else -> 90 }
            when {
                m != null -> b.setProgress(total, m.coerceAtMost(total), false)
                q != null -> b.setProgress(4, q, false)
            }
            // Android 16: προώθηση σε Live Update, με σκορ/λεπτό στη γραμμή κατάστασης
            b.addExtras(Bundle().apply {
                putBoolean("android.requestPromotedOngoing", true)
                putString("android.shortCriticalText", if (spoiler) (min.ifBlank { "LIVE" }) else (d["sh"] ?: "0") + "–" + (d["sa"] ?: "0"))
            })
        } else b.setTimeoutAfter(3 * 3_600_000L)
        post(ctx, tag(id), SLOT_LIVE, b)
        if (done) { stopTicker(ctx, id); forget(ctx, id) } else { if (!fromTick) remember(ctx, id, d); scheduleTick(ctx, id) }
    }

    /** Γκολ (και ακύρωση, πέναλτι, σετ). Γκολ της ΑΕΚ: με ήχο. */
    private fun goal(ctx: Context, d: Map<String, String>, spoiler: Boolean, loud: Boolean) {
        val id = d["match"]?.toLongOrNull() ?: return
        val side = d["side"]
        val logo = if (side == "away") d["alogo"] else if (side == "home") d["hlogo"] else aekLogo(d)
        val title = if (spoiler) spoilerTitle(d) else d["title"].orEmpty()
        val text = if (spoiler) "Νέα εξέλιξη στον αγώνα. Πάτα για να τη δεις." else d["body"].orEmpty()
        val b = base(ctx, if (loud) CH_GOAL else CH_QUIET, title, text)
            .setSubText(d["comp"])
            .setContentIntent(openMatch(ctx, id, 3))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(if (loud) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setTimeoutAfter(20 * 60_000L)
        if (!spoiler) crest(ctx, logo)?.let { b.setLargeIcon(it) }
        if (d["hl"] == "1" && d["ev"] == "goal") b.addAction(0, "Δες το γκολ", openMatch(ctx, id, 4))
        b.addAction(0, "Match Center", openMatch(ctx, id, 2))
        post(ctx, tag(id), SLOT_GOAL, b)
    }

    /** Τελικό: αντικαθιστά τη ζωντανή με τη σύνοψη του αγώνα. */
    private fun final(ctx: Context, d: Map<String, String>, spoiler: Boolean) {
        val id = d["match"]?.toLongOrNull() ?: return
        stopTicker(ctx, id); forget(ctx, id)
        val title = if (spoiler) "Τελικό · " + vs(d) else d["title"].orEmpty()
        val text = if (spoiler) "Ο αγώνας τελείωσε. Πάτα για το αποτέλεσμα." else d["body"].orEmpty()
        val b = base(ctx, CH_MATCH, title, text)
            .setSubText(d["comp"])
            .setContentIntent(openMatch(ctx, id, 5))
            .setTimeoutAfter(12 * 3_600_000L)
        if (!spoiler) crest(ctx, aekLogo(d))?.let { b.setLargeIcon(it) }
        if (d["hl"] == "1") b.addAction(0, "Στιγμιότυπα", openMatch(ctx, id, 6))
        b.addAction(0, "Βαθμολογία", openTab(ctx, "standings", 9001))
        NotificationManagerCompat.from(ctx).cancel(tag(id), SLOT_GOAL)
        post(ctx, tag(id), SLOT_LIVE, b)
    }

    private fun event(ctx: Context, d: Map<String, String>, spoiler: Boolean, ch: String, slot: Int) {
        val id = d["match"]?.toLongOrNull() ?: return
        val reveal = d["ev"] == "card" || d["ev"] == "period" && (d["status"] == "live" || d["status"] == "q1")
        val title = if (spoiler && !reveal) spoilerTitle(d) else d["title"].orEmpty()
        val text = if (spoiler && !reveal) "Νέα εξέλιξη στον αγώνα. Πάτα για να τη δεις." else d["body"].orEmpty()
        val b = base(ctx, ch, title, text).setSubText(d["comp"]).setContentIntent(openMatch(ctx, id, 3))
            .addAction(0, "Match Center", openMatch(ctx, id, 2))
        if (!spoiler) crest(ctx, if (d["side"] == "away") d["alogo"] else if (d["side"] == "home") d["hlogo"] else aekLogo(d))?.let { b.setLargeIcon(it) }
        if (slot == SLOT_LIVE) b.addAction(muteAction(ctx, id))
        post(ctx, tag(id), slot, b)
    }

    private fun lineup(ctx: Context, d: Map<String, String>) {
        val id = d["match"]?.toLongOrNull() ?: return
        val b = base(ctx, CH_MATCH, d["title"].orEmpty(), d["body"].orEmpty())
            .setSubText(d["comp"]).setContentIntent(openMatch(ctx, id, 3))
            .addAction(0, "Συνθέσεις", openMatch(ctx, id, 8))
            .addAction(0, "Match Center", openMatch(ctx, id, 2))
        crest(ctx, aekLogo(d))?.let { b.setLargeIcon(it) }
        post(ctx, tag(id), SLOT_LINEUP, b)
    }

    private fun reminder(ctx: Context, d: Map<String, String>) {
        val id = d["match"]?.toLongOrNull() ?: return
        val b = base(ctx, CH_MATCH, d["title"].orEmpty(), d["body"].orEmpty())
            .setSubText(d["comp"]).setContentIntent(openMatch(ctx, id, 3))
            .addAction(0, "Match Center", openMatch(ctx, id, 2))
            .addAction(muteAction(ctx, id))
            .setTimeoutAfter(60 * 60_000L)
        crest(ctx, aekLogo(d))?.let { b.setLargeIcon(it) }
        post(ctx, tag(id), SLOT_REMIND, b)
    }

    /** Ειδήσεις: ετικέτα (ΕΠΙΣΗΜΟ…), τίτλος, φωτογραφία, «Διάβασε» / «Αποθήκευση». */
    private fun news(ctx: Context, d: Map<String, String>) {
        val post = d["post"]?.toLongOrNull() ?: 0L
        val title = d["title"].orEmpty(); val body = d["body"].orEmpty()
        val label = d["label"].takeUnless { it.isNullOrBlank() }
        val b = base(ctx, CH_NEWS, title, body).setSubText(label ?: d["cat"])
        val open = if (post > 0) openPost(ctx, post) else PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        b.setContentIntent(open)
        crest(ctx, d["image"], 720)?.let { img ->
            b.setLargeIcon(img).setStyle(NotificationCompat.BigPictureStyle().bigPicture(img).bigLargeIcon(null as Bitmap?).setSummaryText(body))
        }
        if (post > 0) {
            b.addAction(0, "Διάβασε", open)
            b.addAction(0, "Αποθήκευση", broadcast(ctx, ACT_SAVE, post.toInt()) {
                putLong("post", post); putString("title", title); putString("image", d["image"]); putString("link", d["url"]); putString("cat", d["cat"])
            })
        }
        post(ctx, if (post > 0) "p$post" else null, if (post > 0) 1 else (System.currentTimeMillis() % Int.MAX_VALUE).toInt(), b)
    }

    fun cancelMatch(ctx: Context, id: Long) {
        val nm = NotificationManagerCompat.from(ctx)
        listOf(SLOT_LIVE, SLOT_GOAL, SLOT_EVENT, SLOT_LINEUP, SLOT_REMIND).forEach { nm.cancel(tag(id), it) }
        stopTicker(ctx, id); forget(ctx, id)
    }

    /* ───────── Το λεπτό «τρέχει»: ξαναζωγραφίζουμε τη ζωντανή κάθε ~1′ ───────── */
    private fun store(ctx: Context) = ctx.getSharedPreferences("live_notifs", Context.MODE_PRIVATE)
    private fun remember(ctx: Context, id: Long, d: Map<String, String>) =
        store(ctx).edit().putString("m$id", JSONObject(d + ("_at" to System.currentTimeMillis().toString())).toString()).apply()
    private fun forget(ctx: Context, id: Long) = store(ctx).edit().remove("m$id").apply()

    private fun tickIntent(ctx: Context, id: Long) = broadcast(ctx, ACT_TICK, (id * 10 + 9).toInt()) { putLong("match", id) }
    private fun scheduleTick(ctx: Context, id: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.set(AlarmManager.RTC, System.currentTimeMillis() + 60_000L, tickIntent(ctx, id))
    }
    private fun stopTicker(ctx: Context, id: Long) { ctx.getSystemService(AlarmManager::class.java)?.cancel(tickIntent(ctx, id)) }

    fun tick(ctx: Context, id: Long, s: Settings) {
        val raw = store(ctx).getString("m$id", null) ?: return
        val o = JSONObject(raw)
        val d = o.keys().asSequence().associateWith { o.optString(it) }
        val age = System.currentTimeMillis() - (d["_at"]?.toLongOrNull() ?: 0L)
        if (age > 4 * 3_600_000L || !s.nAll || id.toString() in s.mutedMatches) { cancelMatch(ctx, id); return }
        // Μόνο αν η ζωντανή είναι ακόμα στην οθόνη (δεν την έκλεισε ο χρήστης)
        val active = ctx.getSystemService(NotificationManager::class.java)?.activeNotifications?.any { it.tag == tag(id) && it.id == SLOT_LIVE } ?: false
        if (!active) { forget(ctx, id); return }
        live(ctx, d - "_at", s.nSpoiler, alert = false, fromTick = true)
    }
}

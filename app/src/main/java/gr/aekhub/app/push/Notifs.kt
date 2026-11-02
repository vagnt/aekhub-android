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
 */
object Notifs {
    // Κανάλια (ο χρήστης τα ρυθμίζει και από τις ρυθμίσεις του Android)
    const val CH_LIVE = "live_match"
    const val CH_GOAL = "aek_score2" // νέα κανάλια στο 0.14.0 (νέος ήχος): ο ήχος ενός καναλιού δεν αλλάζει μετά τη δημιουργία του
    const val CH_MATCH = "matchday2"
    const val CH_QUIET = "match_quiet"
    const val CH_NEWS = "news2"

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
        for (old in listOf("aek_score", "matchday", "news")) runCatching { nm.deleteNotificationChannel(old) } // παλιά κανάλια με τον παλιό ήχο (έως 0.13)
        runCatching { nm.deleteNotificationChannel("goal_aek") } // παλιός ήχος κόρνας (0.9.0)
        runCatching { nm.deleteNotificationChannel(CH_LIVE) } // η μόνιμη ειδοποίηση αγώνα καταργήθηκε (0.9.4) — φεύγει και όποια έχει μείνει
        nm.createNotificationChannel(NotificationChannel(CH_GOAL, "Σκορ της ΑΕΚ", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Γκολ, καλάθι ή σετ της ΑΕΚ, με τον ήχο του AEK Hub"
            setSound(Uri.parse("android.resource://" + ctx.packageName + "/" + R.raw.aek_sound),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true); vibrationPattern = longArrayOf(0, 120, 80, 120)
        })
        nm.createNotificationChannel(NotificationChannel(CH_MATCH, "Ημέρα αγώνα", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Υπενθύμιση, σέντρα, ημίχρονο, τελικό"
            setSound(Uri.parse("android.resource://" + ctx.packageName + "/" + R.raw.aek_sound),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        })
        nm.createNotificationChannel(NotificationChannel(CH_QUIET, "Εξελίξεις αγώνα (αθόρυβα)", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Γκολ αντιπάλου, ημίχρονο, κάρτες"
        })
        nm.createNotificationChannel(NotificationChannel(CH_NEWS, "Ειδήσεις", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Έκτακτα και σημαντικά άρθρα"
            setSound(Uri.parse("android.resource://" + ctx.packageName + "/" + R.raw.aek_sound),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        })
    }

    /* ───────── Είσοδος: ένα μήνυμα από τον server ───────── */
    fun handle(ctx: Context, d: Map<String, String>, s: Settings) {
        if (!Push.allowed(ctx)) return
        channels(ctx)
        val kind = d["kind"] ?: "breaking"
        if (!s.nAll) return // γενικός διακόπτης
        val id = d["match"]?.toLongOrNull() ?: 0L

        if (kind == "lineup") return // ενδεκάδες/πεντάδες: καμία ειδοποίηση (και αν έρθει από παλιότερο server)
        if (kind == "breaking") {
            val cat = d["cat"].orEmpty()
            if (s.nBreaking && (s.newsCats.isEmpty() || cat.isBlank() || cat in s.newsCats)) news(ctx, d)
            return
        }
        if (id > 0 && id.toString() in s.mutedMatches) { cancelMatch(ctx, id); return }

        val sport = d["sport"] ?: "football"
        val explicit = id > 0 && id.toString() in s.matchAlerts
        val aekScored = d["aekScored"] == "1" || d["aek"].isNullOrEmpty()
        val allowed = explicit || s.wants(sport, kind) // γκολ ΑΕΚ και αντιπάλου: ένα είδος «goals»
        if (!allowed) return

        // Ήσυχες ώρες 01:00–08:00: περνάνε μόνο τα γκολ της ΑΕΚ (και η αθόρυβη ζωντανή)
        if (s.nQuiet && kind != "live" && !(kind == "goals" && aekScored && d["ev"] == "goal")) {
            if (LocalTime.now(ZoneId.of("Europe/Athens")).hour in 1..7) return
        }

        val sp = false // «Χωρίς spoilers» καταργήθηκε (0.14.0): οι ειδοποιήσεις έχουν πάντα σκορ
        val ev = d["ev"] ?: ""
        val status = d["status"] ?: ""
        val liveNow = status !in setOf("", "upcoming", "finished", "postponed")
        when (kind) {
            "live" -> {} // χωρίς μόνιμη ειδοποίηση αγώνα
            // Γκολ (ΑΕΚ ή αντιπάλου — ίδια ειδοποίηση, ίδιος ήχος). Ακύρωση, σετ, πέναλτι: κανονική ειδοποίηση (και ενημερώνεται και η ζωντανή)
            "goals" -> goal(ctx, d, sp, loud = ev == "goal" && !sp)
            "startend" -> when {
                ev == "ft" -> final(ctx, d, sp)
                ev == "postponed" -> event(ctx, d, sp, CH_MATCH, SLOT_LIVE)
                status == "live" || status == "q1" -> event(ctx, d, sp, CH_MATCH, SLOT_LIVE) // σέντρα: μετά γίνεται η ζωντανή
                else -> event(ctx, d, sp, CH_MATCH, SLOT_EVENT) // ημίχρονο, δεκάλεπτα: κανονική ειδοποίηση
            }
            "cards" -> event(ctx, d, sp, CH_QUIET, SLOT_EVENT)
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
                    (r as? SuccessResult)?.drawable?.let { dr ->
                        if (size > 300) { // φωτογραφία είδησης: με τις αναλογίες της
                            val w = dr.intrinsicWidth.coerceAtLeast(1); val h = dr.intrinsicHeight.coerceAtLeast(1)
                            dr.toBitmap(size, (size.toLong() * h / w).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                        } else fitCrest(dr, size, 0.78f)
                    }
                }
            }
        }.getOrNull()
        if (bmp != null) crestCache[url] = bmp
        return bmp
    }

    /** Το λογότυπο «hub» (έγχρωμο, κίτρινο με μαύρα γράμματα) για ειδοποιήσεις χωρίς σήμα ομάδας ή φωτογραφία. */
    private var hub: Bitmap? = null
    private fun hubLogo(ctx: Context): Bitmap? = hub ?: runCatching { android.graphics.BitmapFactory.decodeResource(ctx.resources, R.drawable.hub_logo) }.getOrNull().also { hub = it }

    /** Το σήμα ολόκληρο, με τις αναλογίες του, στο κέντρο ενός τετραγώνου με διάφανο περιθώριο (για να μην κόβεται στο στρογγύλεμα). */
    private fun fitCrest(dr: android.graphics.drawable.Drawable, size: Int, fill: Float): Bitmap {
        val w = dr.intrinsicWidth.takeIf { it > 0 } ?: size; val h = dr.intrinsicHeight.takeIf { it > 0 } ?: size
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val box = size * fill; val sc = minOf(box / w, box / h)
        val dw = (w * sc).toInt(); val dh = (h * sc).toInt()
        val l = (size - dw) / 2; val t = (size - dh) / 2
        dr.setBounds(l, t, l + dw, t + dh); dr.draw(android.graphics.Canvas(out))
        return out
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
            if (el >= 0.0 && el <= 3 * 3_600_000.0) {
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
        // Ο χρόνος του αγώνα και στην κεφαλίδα, για να φαίνεται και όταν η ειδοποίηση είναι μαζεμένη
        val head = listOf(if (done) "Τελικό" else min.ifBlank { phase }, d["comp"].orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
        val b = base(ctx, CH_LIVE, title, text)
            .setSubText(head)
            .setOngoing(!done)
            .setOnlyAlertOnce(true)
            .setSilent(!alert)
            .setAutoCancel(done)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(openMatch(ctx, id, 1))
            .addAction(0, "Match Center", openMatch(ctx, id, 2))
        if (!done) b.addAction(muteAction(ctx, id))
        b.setLargeIcon(crest(ctx, aekLogo(d)) ?: hubLogo(ctx))
        // Μπάρα αγώνα: ποδόσφαιρο/futsal/πόλο με το λεπτό, μπάσκετ με το δεκάλεπτο
        var rich: Any? = null
        if (!done) {
            val m = Regex("""^(\d+)""").find(min)?.groupValues?.get(1)?.toIntOrNull()
            val q = Regex("""^q(\d)$""").find(status)?.groupValues?.get(1)?.toIntOrNull()
            val sp = d["sport"] ?: "football"
            val segs = when (sp) { "futsal" -> listOf(20, 20); "handball" -> listOf(30, 30); "waterpolo" -> List(4) { 8 }; "basketball" -> List(4) { 10 }; else -> listOf(45, 45) }
            val total = segs.sum()
            val pos = when {
                sp == "volleyball" -> null // βόλεϊ: χωρίς χρόνο — το «3ο Σετ · 18–15» γράφεται στο κείμενο
                sp == "basketball" && q != null -> q * 10 - 5
                m != null -> m.coerceIn(0, total)
                status == "halftime" || status == "ht" -> total / 2
                else -> null
            }
            if (pos != null) {
                b.setProgress(total, pos, false)
                if (Build.VERSION.SDK_INT >= 36) rich = matchBar(ctx, d, segs, pos)
            }
            // Android 16: προώθηση σε Live Update, με σκορ/λεπτό στη γραμμή κατάστασης
            b.addExtras(Bundle().apply {
                putBoolean("android.requestPromotedOngoing", true)
                putString("android.shortCriticalText", if (spoiler) (min.ifBlank { "LIVE" }) else (d["sh"] ?: "0") + "–" + (d["sa"] ?: "0"))
            })
        } else b.setTimeoutAfter(3 * 3_600_000L)
        val n = b.build()
        val out = rich?.let { st -> runCatching { android.app.Notification.Builder.recoverBuilder(ctx, n).setStyle(st as android.app.Notification.Style).build() }.getOrNull() } ?: n
        try { NotificationManagerCompat.from(ctx).notify(tag(id), SLOT_LIVE, out) } catch (_: SecurityException) {}
        if (done) { stopTicker(ctx, id); forget(ctx, id) } else { if (!fromTick) remember(ctx, id, d); scheduleTick(ctx, id) }
    }

    /**
     * Android 16: μπάρα αγώνα με ημίχρονα/δεκάλεπτα ως κομμάτια, τα γκολ ως σημάδια πάνω της
     * (κίτρινο/μαύρο της ΑΕΚ, γκρι του αντιπάλου) και μπαλίτσα στο τωρινό λεπτό.
     * Με reflection, για να μη χρειάζεται SDK 36 για το build.
     */
    private fun matchBar(ctx: Context, d: Map<String, String>, segs: List<Int>, pos: Int): Any? = runCatching {
        val ps = Class.forName("android.app.Notification\$ProgressStyle")
        val segC = Class.forName("android.app.Notification\$ProgressStyle\$Segment")
        val ptC = Class.forName("android.app.Notification\$ProgressStyle\$Point")
        val I = Int::class.javaPrimitiveType!!
        val style = ps.getConstructor().newInstance()
        val total = segs.sum()
        val segList = segs.map { len -> segC.getConstructor(I).newInstance(len).also { segC.getMethod("setColor", I).invoke(it, GOLD) } }
        ps.getMethod("setProgressSegments", java.util.List::class.java).invoke(style, segList)
        val aek = d["aek"].orEmpty()
        val pts = d["goals"].orEmpty().split(',').mapNotNull { g ->
            val side = when (g.firstOrNull()) { 'h' -> "home"; 'a' -> "away"; else -> return@mapNotNull null }
            val at = g.drop(1).toIntOrNull()?.coerceIn(1, total - 1) ?: return@mapNotNull null
            val col = if (aek.isEmpty() || side == aek) 0xFF0B0B0A.toInt() else 0xFF8C8A83.toInt()
            ptC.getConstructor(I).newInstance(at).also { ptC.getMethod("setColor", I).invoke(it, col) }
        }
        if (pts.isNotEmpty()) ps.getMethod("setProgressPoints", java.util.List::class.java).invoke(style, pts)
        ps.getMethod("setStyledByProgress", Boolean::class.javaPrimitiveType).invoke(style, true)
        ps.getMethod("setProgress", I).invoke(style, pos.coerceIn(0, total))
        ps.getMethod("setProgressTrackerIcon", android.graphics.drawable.Icon::class.java)
            .invoke(style, android.graphics.drawable.Icon.createWithResource(ctx, R.drawable.ic_track_ball))
        style
    }.getOrNull()

    /** Γκολ (και ακύρωση, πέναλτι, σετ). Γκολ της ΑΕΚ: με ήχο. */
    private fun goal(ctx: Context, d: Map<String, String>, spoiler: Boolean, loud: Boolean) {
        val id = d["match"]?.toLongOrNull() ?: return
        val side = d["side"]
        val logo = if (side == "away") d["alogo"] else if (side == "home") d["hlogo"] else aekLogo(d)
        val title = if (spoiler) spoilerTitle(d) else d["title"].orEmpty()
        val text = if (spoiler) "Νέα εξέλιξη στον αγώνα. Πάτα για να τη δεις." else d["body"].orEmpty()
        val b = base(ctx, if (loud) CH_GOAL else CH_MATCH, title, text)
            .setSubText(d["comp"])
            .setContentIntent(openMatch(ctx, id, 3))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(if (loud) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setTimeoutAfter(20 * 60_000L)
        if (!spoiler) b.setLargeIcon(crest(ctx, logo) ?: hubLogo(ctx))
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
        if (!spoiler) b.setLargeIcon(crest(ctx, aekLogo(d)) ?: hubLogo(ctx))
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
        if (!spoiler) b.setLargeIcon(crest(ctx, if (d["side"] == "away") d["alogo"] else if (d["side"] == "home") d["hlogo"] else aekLogo(d)) ?: hubLogo(ctx))
        if (slot == SLOT_LIVE) b.addAction(muteAction(ctx, id))
        post(ctx, tag(id), slot, b)
    }

    private fun lineup(ctx: Context, d: Map<String, String>) {
        val id = d["match"]?.toLongOrNull() ?: return
        val b = base(ctx, CH_MATCH, d["title"].orEmpty(), d["body"].orEmpty())
            .setSubText(d["comp"]).setContentIntent(openMatch(ctx, id, 3))
            .addAction(0, "Συνθέσεις", openMatch(ctx, id, 8))
            .addAction(0, "Match Center", openMatch(ctx, id, 2))
        b.setLargeIcon(crest(ctx, aekLogo(d)) ?: hubLogo(ctx))
        post(ctx, tag(id), SLOT_LINEUP, b)
    }

    private fun reminder(ctx: Context, d: Map<String, String>) {
        val id = d["match"]?.toLongOrNull() ?: return
        val b = base(ctx, CH_MATCH, d["title"].orEmpty(), d["body"].orEmpty())
            .setSubText(d["comp"]).setContentIntent(openMatch(ctx, id, 3))
            .addAction(0, "Match Center", openMatch(ctx, id, 2))
            .addAction(muteAction(ctx, id))
            .setTimeoutAfter(60 * 60_000L)
        b.setLargeIcon(crest(ctx, aekLogo(d)) ?: hubLogo(ctx))
        post(ctx, tag(id), SLOT_REMIND, b)
    }

    /** Ειδήσεις: ετικέτα (ΕΠΙΣΗΜΟ…), τίτλος, φωτογραφία, «Διάβασε» / «Αποθήκευση». */
    /** Κατηγορία στην ειδοποίηση: μόνο «κανονική» — ποτέ οι σκιώδεις (Hero, Επικαιρότητα…)· αλλιώς τίποτα. */
    private fun mainCat(c: String?): String? {
        val n = c?.trim().orEmpty(); if (n.isEmpty()) return null
        val k = java.text.Normalizer.normalize(n, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").uppercase()
        return if (k in setOf("HERO", "ΕΠΙΚΑΙΡΟΤΗΤΑ", "ΑΡΘΡΟΓΡΑΦΙΑ", "ΣΑΝ ΣΗΜΕΡΑ", "ΧΩΡΙΣ ΚΑΤΗΓΟΡΙΑ", "UNCATEGORIZED", "VIDEO", "BREAKING NEWS")) null else n
    }

    private fun news(ctx: Context, d: Map<String, String>) {
        val post = d["post"]?.toLongOrNull() ?: 0L
        val title = d["title"].orEmpty(); val body = d["body"].orEmpty()
        val label = d["label"].takeUnless { it.isNullOrBlank() }
        val b = base(ctx, CH_NEWS, title, body).setSubText(label ?: mainCat(d["cat"]))
        val open = if (post > 0) openPost(ctx, post) else PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        b.setContentIntent(open)
        b.setLargeIcon(hubLogo(ctx)) // χωρίς φωτογραφία: το λογότυπο hub
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
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 60_000L, tickIntent(ctx, id))
    }
    private fun stopTicker(ctx: Context, id: Long) { ctx.getSystemService(AlarmManager::class.java)?.cancel(tickIntent(ctx, id)) }

    /** Παλιά ρολόγια της μόνιμης ειδοποίησης (έως 0.9.3): απλώς σβήνουν. */
    fun tick(ctx: Context, id: Long, @Suppress("UNUSED_PARAMETER") s: Settings) {
        stopTicker(ctx, id); forget(ctx, id)
        NotificationManagerCompat.from(ctx).cancel(tag(id), SLOT_LIVE)
    }
}

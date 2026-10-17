package gr.aekhub.app.push

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import gr.aekhub.app.MainActivity
import gr.aekhub.app.R
import gr.aekhub.app.data.Prefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalTime
import java.time.ZoneId

class AekMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) = Push.reset(applicationContext)

    override fun onMessageReceived(msg: RemoteMessage) {
        val d = msg.data
        // Κάθε μήνυμα (και το αθόρυβο «sync») ξυπνάει τις ανοιχτές οθόνες να ανανεωθούν τώρα
        gr.aekhub.app.live.Live.ping()
        if (d["kind"] == "sync") return
        val title = d["title"] ?: msg.notification?.title ?: return
        val body = d["body"] ?: msg.notification?.body ?: ""
        val kind = d["kind"] ?: "breaking"

        // Ήσυχες ώρες 01:00–08:00: περνάνε μόνο τα γκολ
        val s = runCatching { runBlocking { Prefs(applicationContext).settings.first() } }.getOrNull()
        if (s?.nQuiet == true && kind != "goals") {
            val h = LocalTime.now(ZoneId.of("Europe/Athens")).hour
            if (h in 1..7) return
        }
        if (!Push.allowed(this)) return
        // Σίγαση αγώνα από το καμπανάκι: καμία ειδοποίηση για αυτόν
        d["match"]?.takeIf { it.isNotBlank() }?.let { if (s != null && it in s.mutedMatches) return }
        Push.channels(this)

        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            d["post"]?.toLongOrNull()?.let { putExtra(MainActivity.EXTRA_POST, it) }
            d["match"]?.toLongOrNull()?.let { putExtra(MainActivity.EXTRA_MATCH, it) }
        }
        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val pi = PendingIntent.getActivity(this, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, if (kind == "breaking") "news" else "matches")
            .setSmallIcon(R.drawable.ic_stat_aek)
            .setColor(0xFFF9CF1C.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(if (kind == "breaking") NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (kind == "breaking") NotificationCompat.CATEGORY_RECOMMENDATION else NotificationCompat.CATEGORY_EVENT)

        d["image"]?.takeIf { it.startsWith("http") }?.let { url -> image(url)?.let { bmp ->
            n.setLargeIcon(bmp).setStyle(NotificationCompat.BigPictureStyle().bigPicture(bmp).bigLargeIcon(null as Bitmap?))
        } }

        // Ίδιος αγώνας → αντικαθιστά την προηγούμενη ειδοποίηση του ίδιου είδους αντί να γεμίζει τη λίστα
        val tag = d["match"]?.takeIf { it.isNotBlank() }?.let { "m$it" }
        try { NotificationManagerCompat.from(this).notify(tag, if (tag != null) kind.hashCode() else id, n.build()) } catch (_: SecurityException) {}
    }

    private fun image(url: String): Bitmap? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 5000; c.readTimeout = 5000
        c.inputStream.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}

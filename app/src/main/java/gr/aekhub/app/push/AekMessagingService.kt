package gr.aekhub.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import gr.aekhub.app.data.Prefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class AekMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) = Push.reset(applicationContext)

    override fun onMessageReceived(msg: RemoteMessage) {
        val d = msg.data
        // Κάθε μήνυμα (και το αθόρυβο «sync») ξυπνάει τις ανοιχτές οθόνες να ανανεωθούν τώρα
        gr.aekhub.app.live.Live.ping()
        if (d["kind"] == "sync") return
        val data = HashMap(d)
        if (data["title"] == null) msg.notification?.title?.let { data["title"] = it }
        if (data["body"] == null) msg.notification?.body?.let { data["body"] = it }
        val s = runCatching { runBlocking { Prefs(applicationContext).settings.first() } }.getOrNull() ?: return
        Notifs.handle(applicationContext, data, s)
    }
}

package gr.aekhub.app.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import gr.aekhub.app.data.PostCard
import gr.aekhub.app.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Κουμπιά των ειδοποιήσεων (Σίγαση αγώνα, Αποθήκευση άρθρου) και το «τικ» της ζωντανής. */
class NotifReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val pending = goAsync()
        val app = ctx.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = Prefs(app)
                when (i.action) {
                    Notifs.ACT_MUTE -> {
                        val id = i.getLongExtra("match", 0L)
                        if (id > 0) { prefs.mute(id); Notifs.cancelMatch(app, id) }
                        withContext(Dispatchers.Main) { Toast.makeText(app, "Ο αγώνας σιγάστηκε. Το αλλάζεις από το καμπανάκι του.", Toast.LENGTH_LONG).show() }
                    }
                    Notifs.ACT_SAVE -> {
                        val id = i.getLongExtra("post", 0L)
                        if (id > 0) prefs.saveIfMissing(PostCard(id, i.getStringExtra("title").orEmpty(), "", "", i.getStringExtra("image").orEmpty(), i.getStringExtra("cat").orEmpty(), i.getStringExtra("link").orEmpty()))
                        androidx.core.app.NotificationManagerCompat.from(app).cancel("p$id", 1)
                        withContext(Dispatchers.Main) { Toast.makeText(app, "Αποθηκεύτηκε στο «Εγώ».", Toast.LENGTH_SHORT).show() }
                    }
                    Notifs.ACT_TICK -> {
                        val id = i.getLongExtra("match", 0L)
                        if (id > 0) Notifs.tick(app, id, prefs.settings.first())
                    }
                }
            } finally { pending.finish() }
        }
    }
}

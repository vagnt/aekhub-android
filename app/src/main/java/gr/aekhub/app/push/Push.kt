package gr.aekhub.app.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import gr.aekhub.app.data.Settings

/**
 * Ειδοποιήσεις: το app γράφεται σε «θέματα» ανάλογα με τις επιλογές του χρήστη.
 * Ίδια ονόματα με το plugin AEKhub Push (app-push.php).
 */
object Push {
    private const val SP = "push"
    private const val KEY = "topics"

    fun available(ctx: Context) = FirebaseApp.getApps(ctx).isNotEmpty()

    fun allowed(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun channels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel("matches", "Αγώνες", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Γκολ, έναρξη και λήξη, ενδεκάδα, κάρτες"
        })
        nm.createNotificationChannel(NotificationChannel("news", "Ειδήσεις", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Έκτακτα και σημαντικά άρθρα"
        })
    }

    fun topicsFor(s: Settings): Set<String> = buildSet {
        add("app_sync") // αθόρυβη άμεση ανανέωση περιεχομένου (χωρίς ειδοποίηση)
        s.sports.forEach { sp ->
            if (s.nGoals) add("m_${sp}_goals")
            if (s.nStartEnd) add("m_${sp}_startend")
            if (s.nLineup) add("m_${sp}_lineup")
            if (s.nCards) add("m_${sp}_cards")
        }
        if (s.nBreaking) add("news_breaking")
        if (s.nTest) add("push_test") // δοκιμαστικοί αγώνες (🧪) — μόνο για όσους το ανοίξουν
        s.matchAlerts.forEach { add("match_$it") }
    }

    /** Γράφει/ξεγράφει μόνο τη διαφορά από την προηγούμενη φορά. */
    fun sync(ctx: Context, s: Settings) {
        if (!available(ctx)) return
        val sp = ctx.getSharedPreferences(SP, Context.MODE_PRIVATE)
        val cur = sp.getStringSet(KEY, emptySet()) ?: emptySet()
        val want = topicsFor(s)
        if (cur == want) return
        val fm = FirebaseMessaging.getInstance()
        val done = cur.toMutableSet()
        (want - cur).forEach { t -> fm.subscribeToTopic(t).addOnSuccessListener { done += t; sp.edit().putStringSet(KEY, done.toSet()).apply() } }
        (cur - want).forEach { t -> fm.unsubscribeFromTopic(t).addOnSuccessListener { done -= t; sp.edit().putStringSet(KEY, done.toSet()).apply() } }
    }

    /** Νέο token (π.χ. καθάρισμα δεδομένων): ξαναγράφεται σε όλα στο επόμενο άνοιγμα. */
    fun reset(ctx: Context) = ctx.getSharedPreferences(SP, Context.MODE_PRIVATE).edit().remove(KEY).apply()
}

package gr.aekhub.app.widget

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import gr.aekhub.app.MainActivity

/** Πλακίδιο γρήγορων ρυθμίσεων «ΑΕΚ»: ζωντανό σκορ ή ώρα επόμενου αγώνα· πάτημα → ανοίγει τον αγώνα. */
class AekTileService : TileService() {
    override fun onStartListening() = paint()
    override fun onTileAdded() = paint()

    private fun paint() {
        val t = qsTile ?: return
        val st = WidgetData.read(this)
        t.label = "ΑΕΚ"
        when {
            st == null -> { t.state = Tile.STATE_INACTIVE; sub(t, "Αγώνες") }
            st.live -> { t.state = Tile.STATE_ACTIVE; sub(t, (if (st.hasScore) st.sh + "–" + st.sa else "LIVE") + (if (st.minute.isNotBlank()) " · " + st.minute else "")) }
            st.finished -> { t.state = Tile.STATE_INACTIVE; sub(t, "Τελικό " + st.sh + "–" + st.sa) }
            else -> {
                t.state = Tile.STATE_INACTIVE
                val time = if (st.startMs > 0) java.time.Instant.ofEpochMilli(st.startMs).atZone(java.time.ZoneId.of("Europe/Athens")).format(java.time.format.DateTimeFormatter.ofPattern("EEE d/M HH:mm", java.util.Locale("el"))) else "Επόμενος"
                sub(t, time)
            }
        }
        t.updateTile()
    }

    private fun sub(t: Tile, s: String) { if (Build.VERSION.SDK_INT >= 29) t.subtitle = s }

    override fun onClick() {
        val st = WidgetData.read(this)
        val i = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (st != null && st.id > 0) putExtra(MainActivity.EXTRA_MATCH, st.id) else putExtra(MainActivity.EXTRA_TAB, "matches")
        }
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 77, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION") startActivityAndCollapse(i)
        }
    }
}

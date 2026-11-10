package gr.aekhub.app.ui.components

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import gr.aekhub.app.R
import kotlinx.coroutines.delay

/**
 * Άνοιγμα της εφαρμογής (ζήτησε ο χρήστης): το βίντεο του AEK Hub σε όλη την οθόνη
 * (κινητό · tablet κάθετο · tablet οριζόντιο) με δονήσεις ΣΥΓΧΡΟΝΙΣΜΕΝΕΣ με το βίντεο:
 * κύκλος hub → κύμα → κουτί → a, e → το K (δυνατό) → το σύνθημα. Πάτημα = προσπέραση.
 * 0.17.2: 5″ στο πρώτο άνοιγμα της μέρας και μετά από εγκατάσταση/ενημέρωση, 3″ (`*_s`) στα υπόλοιπα.
 * Ίδιες στιγμές με το iPhone (`IntroSplash.beats5/beats3` στο aekhub-ios).
 */
private val BEATS5 = listOf(
    650 to HapticFeedbackConstants.CLOCK_TICK,     // εμφανίζεται ο κύκλος hub
    1100 to HapticFeedbackConstants.VIRTUAL_KEY,   // ο κύκλος «σκάει» (κύμα)
    1500 to HapticFeedbackConstants.CLOCK_TICK,    // γίνεται το κουτί
    2300 to HapticFeedbackConstants.KEYBOARD_TAP,  // πέφτει το a
    2600 to HapticFeedbackConstants.KEYBOARD_TAP,  // το e
    2900 to HapticFeedbackConstants.LONG_PRESS,    // το K (το πιο δυνατό)
    3300 to HapticFeedbackConstants.CLOCK_TICK,    // «Όλη η ΑΕΚ, σε ένα σημείο!»
)
private val BEATS3 = listOf(
    300 to HapticFeedbackConstants.CLOCK_TICK,
    670 to HapticFeedbackConstants.VIRTUAL_KEY,
    900 to HapticFeedbackConstants.CLOCK_TICK,
    1400 to HapticFeedbackConstants.KEYBOARD_TAP,
    1600 to HapticFeedbackConstants.KEYBOARD_TAP,
    1800 to HapticFeedbackConstants.LONG_PRESS,
    2100 to HapticFeedbackConstants.CLOCK_TICK,
)

/** Μεγάλο (5″) στο πρώτο άνοιγμα της μέρας ή μετά από εγκατάσταση/ενημέρωση· γράφει ότι παίχτηκε. */
private fun longSplash(ctx: android.content.Context): Boolean = runCatching {
    val sp = ctx.getSharedPreferences("splash", android.content.Context.MODE_PRIVATE)
    val day = java.time.LocalDate.now().toString()
    val upd = ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime
    val l = sp.getString("day", null) != day || sp.getLong("upd", 0L) != upd
    sp.edit().putString("day", day).putLong("upd", upd).apply()
    l
}.getOrDefault(true)

@Composable
fun IntroSplash(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val out = remember { Animatable(0f) }
    var finishing by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var view by remember { mutableStateOf<TextureView?>(null) }
    val long = remember { longSplash(ctx) }
    val beats = if (long) BEATS5 else BEATS3
    // τα πρώτα κλάσματα του βίντεο είναι σχεδόν μαύρα → ξεκινά μόλις «ανάβει» ο δικέφαλος (όπως στο iPhone)
    val startMs = if (long) 250 else 150

    // τέλος (ή πάτημα): σβήνει μαλακά πάνω στην αρχική
    LaunchedEffect(finishing) {
        if (!finishing) return@LaunchedEffect
        out.animateTo(1f, tween(350, easing = FastOutSlowInEasing))
        onDone()
    }
    // δονήσεις στις στιγμές του βίντεο (από τη θέση του player, όχι από χρονόμετρο)
    LaunchedEffect(player) {
        val p = player ?: return@LaunchedEffect
        var i = beats.indexOfFirst { it.first > startMs }.let { if (it < 0) beats.size else it }
        while (i < beats.size && !finishing) {
            val pos = runCatching { p.currentPosition }.getOrDefault(0)
            while (i < beats.size && pos >= beats[i].first) {
                if (pos - beats[i].first < 250) view?.performHapticFeedback(beats[i].second)
                i++
            }
            delay(12)
        }
    }
    // ασφάλεια: αν για κάποιο λόγο δεν παίξει, δεν κολλάμε στο μαύρο
    LaunchedEffect(Unit) { delay(7_000); finishing = true }
    DisposableEffect(Unit) { onDispose { runCatching { player?.release() }; player = null } }

    BoxWithConstraints(
        Modifier.fillMaxSize().graphicsLayer { alpha = 1f - out.value }.background(Color.Black)
            .clickable(remember { MutableInteractionSource() }, null) { finishing = true },
    ) {
        val tablet = minOf(maxWidth.value, maxHeight.value) >= 600f
        val res = when {
            !tablet -> if (long) R.raw.splash_phone else R.raw.splash_phone_s
            maxWidth > maxHeight -> if (long) R.raw.splash_tab_l else R.raw.splash_tab_l_s
            else -> if (long) R.raw.splash_tab_p else R.raw.splash_tab_p_s
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                TextureView(c).also { tv ->
                    view = tv
                    tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            val mp = MediaPlayer()
                            runCatching {
                                mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
                                mp.setDataSource(ctx, Uri.parse("android.resource://${ctx.packageName}/$res"))
                                mp.setSurface(Surface(st))
                                mp.setVolume(0f, 0f) // το βίντεο δεν έχει ήχο· να μη σταματά τη μουσική του χρήστη
                                mp.setOnVideoSizeChangedListener { _, vw, vh -> cover(tv, vw, vh) }
                                mp.setOnCompletionListener { finishing = true }
                                mp.setOnErrorListener { _, _, _ -> finishing = true; true }
                                mp.setOnPreparedListener { it.seekTo(startMs.toLong(), MediaPlayer.SEEK_CLOSEST); it.start(); player = it }
                                mp.prepareAsync()
                            }.onFailure { finishing = true; runCatching { mp.release() } }
                        }
                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                            player?.let { cover(tv, it.videoWidth, it.videoHeight) }
                        }
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                }
            },
        )
    }
}

/** Γεμίζει την οθόνη χωρίς παραμόρφωση (κόβει λίγο στις άκρες αν διαφέρει η αναλογία), στο κέντρο. */
private fun cover(tv: TextureView, vw: Int, vh: Int) {
    val w = tv.width.toFloat(); val h = tv.height.toFloat()
    if (vw <= 0 || vh <= 0 || w <= 0f || h <= 0f) return
    val s = maxOf(w / vw, h / vh)
    val sx = vw * s / w; val sy = vh * s / h
    tv.setTransform(Matrix().apply { setScale(sx, sy, w / 2f, h / 2f) })
}

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
 * Άνοιγμα της εφαρμογής (0.17.1, ζήτησε ο χρήστης): το βίντεο του AEK Hub σε όλη την οθόνη
 * (κινητό · tablet κάθετο · tablet οριζόντιο) με δονήσεις ΣΥΓΧΡΟΝΙΣΜΕΝΕΣ με το βίντεο:
 * «σκάει» ο κύκλος hub → γίνεται κουτί → πέφτουν a, e → το K (δυνατό) → το σύνθημα. Πάτημα = προσπέραση.
 * Ίδιες στιγμές με το iPhone (`IntroSplash.beats` στο aekhub-ios).
 */
private val BEATS = listOf(
    1920 to HapticFeedbackConstants.VIRTUAL_KEY,   // ο κύκλος hub «σκάει»
    2320 to HapticFeedbackConstants.CLOCK_TICK,    // γίνεται το κουτί
    2970 to HapticFeedbackConstants.KEYBOARD_TAP,  // πέφτει το a
    3260 to HapticFeedbackConstants.KEYBOARD_TAP,  // το e
    3540 to HapticFeedbackConstants.LONG_PRESS,    // το K (το πιο δυνατό)
    4150 to HapticFeedbackConstants.CLOCK_TICK,    // «Όλη η ΑΕΚ, σε ένα σημείο!»
)

@Composable
fun IntroSplash(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val out = remember { Animatable(0f) }
    var finishing by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var view by remember { mutableStateOf<TextureView?>(null) }

    // τέλος (ή πάτημα): σβήνει μαλακά πάνω στην αρχική
    LaunchedEffect(finishing) {
        if (!finishing) return@LaunchedEffect
        out.animateTo(1f, tween(350, easing = FastOutSlowInEasing))
        onDone()
    }
    // δονήσεις στις στιγμές του βίντεο (από τη θέση του player, όχι από χρονόμετρο)
    LaunchedEffect(player) {
        val p = player ?: return@LaunchedEffect
        var i = 0
        while (i < BEATS.size && !finishing) {
            val pos = runCatching { p.currentPosition }.getOrDefault(0)
            while (i < BEATS.size && pos >= BEATS[i].first) {
                if (pos - BEATS[i].first < 250) view?.performHapticFeedback(BEATS[i].second)
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
        val res = when { !tablet -> R.raw.splash_phone; maxWidth > maxHeight -> R.raw.splash_tab_l; else -> R.raw.splash_tab_p }
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
                                mp.setOnPreparedListener { it.start(); player = it }
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

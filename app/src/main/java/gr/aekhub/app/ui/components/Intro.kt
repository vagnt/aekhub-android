package gr.aekhub.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import gr.aekhub.app.R
import gr.aekhub.app.ui.theme.C
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Άνοιγμα της εφαρμογής: το logo «χτίζεται» κομμάτι-κομμάτι
 * (a από αριστερά, e από πάνω, ο κύκλος hub γυρίζει και σκάει, το K από δεξιά),
 * ένας παλμός, και σβήνει πάνω στην αρχική. ~1,6″ — στο μεταξύ τα δεδομένα φορτώνουν από κάτω.
 */
@Composable
fun IntroSplash(onDone: () -> Unit) {
    val box = remember { Animatable(0f) }
    val a = remember { Animatable(0f) }
    val e = remember { Animatable(0f) }
    val hub = remember { Animatable(0f) }
    val k = remember { Animatable(0f) }
    val pulse = remember { Animatable(1f) }
    val out = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { box.animateTo(1f, tween(280, easing = FastOutSlowInEasing)) }
        launch { delay(120); a.animateTo(1f, tween(360, easing = FastOutSlowInEasing)) }
        launch { delay(230); e.animateTo(1f, tween(360, easing = FastOutSlowInEasing)) }
        launch { delay(340); hub.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
        launch { delay(450); k.animateTo(1f, tween(360, easing = FastOutSlowInEasing)) }
        delay(950)
        pulse.animateTo(1.07f, tween(140)); pulse.animateTo(1f, tween(180))
        delay(120)
        out.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
        onDone()
    }

    val shift = with(LocalDensity.current) { 46.dp.toPx() }
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = 1f - out.value }.background(C.Ink),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(220.dp)
                .graphicsLayer {
                    val s = (0.86f + 0.14f * box.value) * pulse.value * (1f + 0.25f * out.value)
                    scaleX = s; scaleY = s; alpha = box.value
                }
                .background(Color.Black, RoundedCornerShape(48.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column {
                Row {
                    Piece(R.drawable.intro_a) { alpha = a.value; translationX = -shift * (1f - a.value) }
                    Piece(R.drawable.intro_e) { alpha = e.value; translationY = -shift * (1f - e.value) }
                }
                Row {
                    Piece(R.drawable.intro_hub) {
                        val v = hub.value
                        alpha = v.coerceIn(0f, 1f); scaleX = v; scaleY = v; rotationZ = -200f * (1f - v)
                    }
                    Piece(R.drawable.intro_k) { alpha = k.value; translationX = shift * (1f - k.value) }
                }
            }
        }
    }
}

@Composable
private fun Piece(res: Int, block: androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit) =
    Image(painterResource(res), null, Modifier.size(110.dp).graphicsLayer(block))

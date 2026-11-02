package gr.aekhub.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Μέγεθος οθόνης (όπως τα window size classes του Android):
 * Compact = κινητό · Medium = tablet κάθετα / κινητό οριζόντια · Expanded = tablet οριζόντια.
 */
enum class Win {
    Compact, Medium, Expanded;
    val wide get() = this != Compact
}

/** Σε tablet το μενού είναι κάθετη μπάρα αριστερά (αντί για κάτω) — τόσο πλάτος τρώει. */
val RAIL = 88.dp

@Composable
fun win(): Win {
    val w = LocalConfiguration.current.screenWidthDp
    return when { w >= 840 -> Win.Expanded; w >= 600 -> Win.Medium; else -> Win.Compact }
}

/** Πλάτος για το περιεχόμενο (χωρίς την μπάρα μενού στα tablet). */
@Composable
fun contentWidth(): Dp {
    val w = LocalConfiguration.current.screenWidthDp.dp
    return if (win().wide) w - RAIL else w
}

/** Πλάγιο περιθώριο ώστε το περιεχόμενο να μένει στη μέση με μέγιστο πλάτος `max` (σε κινητό: `min`). */
@Composable
fun sidePad(max: Dp, min: Dp = 16.dp): Dp = maxOf(min, (contentWidth() - max) / 2)

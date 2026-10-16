package gr.aekhub.app.ui.components

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import gr.aekhub.app.BuildConfig
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body

/**
 * Player βίντεο μέσα στο app (YouTube / Facebook / Vimeo).
 * Τα βίντεο αυτών των υπηρεσιών παίζουν μόνο μέσω του δικού τους player (iframe),
 * οπότε εδώ «φιλοξενείται» ο επίσημος player τους, μέσα στο app, με πλήρη οθόνη.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VideoPlayer(embed: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var full by remember { mutableStateOf<View?>(null) }
    var fullCb by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    val html = """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>html,body{margin:0;height:100%;background:#000}iframe{position:absolute;inset:0;width:100%;height:100%;border:0}</style></head>
        <body><iframe src="${embed.replace("\"", "&quot;")}" allow="autoplay; encrypted-media; fullscreen; picture-in-picture" allowfullscreen></iframe></body></html>
    """.trimIndent()
    AndroidView(
        modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
        factory = { c ->
            WebView(c).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                webViewClient = WebViewClient()
                webChromeClient = object : WebChromeClient() {
                    override fun onShowCustomView(view: View, callback: CustomViewCallback) { full = view; fullCb = callback }
                    override fun onHideCustomView() { full = null; fullCb = null }
                }
                // Βάση το aekhub.gr: ο YouTube player θέλει «σελίδα προέλευσης» για να παίξει
                loadDataWithBaseURL(BuildConfig.SITE + "/", html, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.stopLoading(); it.loadUrl("about:blank"); it.destroy() },
    )
    // Πλήρης οθόνη (το κουμπί του player): οριζόντια, πάνω από όλα
    val v = full
    if (v != null) {
        val act = ctx as? Activity
        DisposableEffect(v) {
            val old = act?.requestedOrientation
            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            onDispose { if (old != null) act?.requestedOrientation = old }
        }
        Dialog(onDismissRequest = { fullCb?.onCustomViewHidden(); full = null }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            AndroidView(modifier = Modifier.fillMaxSize().background(Color.Black), factory = { c ->
                FrameLayout(c).apply { (v.parent as? ViewGroup)?.removeView(v); addView(v, FrameLayout.LayoutParams(-1, -1)) }
            })
        }
    }
}

/** Popup με βίντεο (στιγμιότυπα): ανοίγει πάνω από τη σελίδα, κλείνει με Χ ή «πίσω». */
@Composable
fun VideoDialog(title: String, embed: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth().padding(12.dp).clickable(enabled = false) {}, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = body(15, FontWeight.ExtraBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.size(40.dp).clip(CircleShape).background(C.Panel2).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, "Κλείσιμο", tint = C.Text)
                    }
                }
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))) { VideoPlayer(embed) }
            }
        }
    }
}

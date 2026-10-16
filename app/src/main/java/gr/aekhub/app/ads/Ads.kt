package gr.aekhub.app.ads

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import gr.aekhub.app.data.AdsConfig
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AdMob + συγκατάθεση (GDPR) μέσω του Google UMP:
 * πρώτα ρωτάμε τον χρήστη (μόνο όπου χρειάζεται, π.χ. ΕΕ), μετά φορτώνουμε διαφημίσεις.
 */
object Ads {
    const val TEST_BANNER = "ca-app-pub-3940256099942544/9214589741"

    val ready = MutableStateFlow(false)
    val privacyButton = MutableStateFlow(false)
    private val started = AtomicBoolean(false)
    private lateinit var consent: ConsentInformation

    fun start(activity: Activity) {
        consent = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(false).build()
        consent.requestConsentInfoUpdate(activity, params, {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ ->
                privacyButton.value = consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                if (consent.canRequestAds()) init(activity)
            }
        }, { _ -> if (consent.canRequestAds()) init(activity) })
        if (consent.canRequestAds()) init(activity)
    }

    private fun init(activity: Activity) {
        if (!started.compareAndSet(false, true)) return
        Thread { MobileAds.initialize(activity.applicationContext) { ready.value = true } }.start()
    }

    fun showPrivacy(activity: Activity) = UserMessagingPlatform.showPrivacyOptionsForm(activity) { _ ->
        privacyButton.value = consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    fun unit(cfg: AdsConfig) = cfg.banner.ifBlank { TEST_BANNER }
}

/** Banner διαφήμισης. Αν δεν φορτώσει (π.χ. καμία διαθέσιμη), δεν πιάνει καθόλου χώρο. */
@Composable
fun BannerAd(cfg: AdsConfig, medium: Boolean = false, modifier: Modifier = Modifier) {
    val ready by Ads.ready.collectAsState()
    if (!cfg.enabled || !ready) return
    var failed by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    if (failed) return
    val unit = Ads.unit(cfg)
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (loaded) Text("ΔΙΑΦΗΜΙΣΗ", style = body(9, FontWeight.ExtraBold, C.Muted).copy(letterSpacing = 1.sp), modifier = Modifier.padding(bottom = 4.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (loaded) C.Panel else C.Ink), contentAlignment = Alignment.Center) {
            val w = maxWidth.value.toInt()
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { ctx ->
                    AdView(ctx).apply {
                        setAdSize(if (medium) AdSize.MEDIUM_RECTANGLE else AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, w))
                        adUnitId = unit
                        adListener = object : AdListener() {
                            override fun onAdLoaded() { loaded = true }
                            override fun onAdFailedToLoad(e: LoadAdError) { failed = true }
                        }
                        loadAd(AdRequest.Builder().build())
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }
}

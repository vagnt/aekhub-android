package gr.aekhub.app.ads

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color as AColor
import android.graphics.Typeface
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import gr.aekhub.app.data.AdsConfig
import gr.aekhub.app.data.Api
import gr.aekhub.app.data.Campaign
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body
import gr.aekhub.app.ui.theme.disp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Διαφημίσεις του app: AdMob (banner, fullscreen, native, rewarded) + δικοί σου χορηγοί.
 * Όλες οι ρυθμίσεις έρχονται από το wp-admin (Match Center → 📢 Διαφημίσεις app), χωρίς νέα έκδοση.
 * Συγκατάθεση (GDPR) μέσω Google UMP: πρώτα ρωτάμε (όπου χρειάζεται), μετά φορτώνουμε.
 */
object Ads {
    // Δοκιμαστικά IDs της Google: όσο ένα πεδίο στο wp-admin είναι κενό
    const val TEST_BANNER = "ca-app-pub-3940256099942544/9214589741"
    const val TEST_INTER = "ca-app-pub-3940256099942544/1033173712"
    const val TEST_NATIVE = "ca-app-pub-3940256099942544/2247696110"
    const val TEST_REWARD = "ca-app-pub-3940256099942544/5224354917"

    val ready = MutableStateFlow(false)
    val privacyButton = MutableStateFlow(false)
    /** Χορηγός σε πλήρη οθόνη (splash ή fullscreen ανάμεσα σε άρθρα). */
    val overlay = MutableStateFlow<Pair<Campaign, Boolean>?>(null) // (καμπάνια, είναι splash)
    /** Μέχρι πότε δεν δείχνουμε διαφημίσεις (rewarded). */
    val adFreeUntil = MutableStateFlow(0L)
    /** Παίζει τώρα η ΑΕΚ; (το ενημερώνει το live banner) */
    @Volatile var aekLive = false

    private val started = AtomicBoolean(false)
    private lateinit var consent: ConsentInformation
    private var act = WeakReference<Activity>(null)
    private var inter: InterstitialAd? = null
    private var interLoading = false
    private var reward: RewardedAd? = null
    private var rewardLoading = false
    private var articles = 0
    private var lastFull = 0L

    fun start(activity: Activity) {
        act = WeakReference(activity)
        adFreeUntil.value = prefs(activity).getLong("free_until", 0L)
        consent = UserMessagingPlatform.getConsentInformation(activity)
        reportConsent(activity) // όσοι έχουν ήδη απαντήσει σε προηγούμενη φορά: δεν χάνεται ούτε η πρώτη οθόνη
        val params = ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(false).build()
        consent.requestConsentInfoUpdate(activity, params, {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ ->
                reportConsent(activity)
                privacyButton.value = consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                if (consent.canRequestAds()) init(activity)
            }
        }, { _ -> if (consent.canRequestAds()) init(activity) })
        if (consent.canRequestAds()) init(activity)
    }

    fun attach(activity: Activity) { act = WeakReference(activity) }

    private fun init(activity: Activity) {
        if (!started.compareAndSet(false, true)) return
        Thread { MobileAds.initialize(activity.applicationContext) { ready.value = true; preload() } }.start()
    }

    /**
     * Στα στατιστικά (Google Analytics) επιτρέπεται μόνο με συναίνεση: εκτός ΕΕ δεν απαιτείται· στην ΕΕ/Ελλάδα πρέπει να έχει δοθεί
     * η συναίνεση στη φόρμα (TCF: σκοπός 1 «αποθήκευση στη συσκευή» και ένας από 7/8/9 «μέτρηση/ανάλυση»).
     */
    private fun reportConsent(ctx: Context) {
        val st = consent.consentStatus
        val ok = st == ConsentInformation.ConsentStatus.NOT_REQUIRED || (st == ConsentInformation.ConsentStatus.OBTAINED && tcfAnalytics(ctx))
        gr.aekhub.app.data.Analytics.setConsent(ctx, ok)
    }
    private fun tcfAnalytics(ctx: Context): Boolean {
        val pc = ctx.getSharedPreferences(ctx.packageName + "_preferences", Context.MODE_PRIVATE).getString("IABTCF_PurposeConsents", "").orEmpty()
        fun p(n: Int) = pc.length >= n && pc[n - 1] == '1'
        return p(1) && (p(7) || p(8) || p(9))
    }

    fun showPrivacy(activity: Activity) = UserMessagingPlatform.showPrivacyOptionsForm(activity) { _ ->
        reportConsent(activity)
        privacyButton.value = consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    private fun prefs(c: Context) = c.getSharedPreferences("ads", Context.MODE_PRIVATE)
    fun adFree() = System.currentTimeMillis() < adFreeUntil.value
    fun unit(cfg: AdsConfig) = cfg.banner.ifBlank { TEST_BANNER }

    /** Ανοίγει το link μιας καμπάνιας και μετράει το κλικ. */
    fun click(ctx: Context, c: Campaign) {
        Api.track(c.id, "c")
        if (c.link.isNotBlank()) runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(c.link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /* ───── Fullscreen (interstitial) ───── */
    private fun preload() {
        val cfg = Api.configNow()?.ads ?: return
        val a = act.get() ?: return
        if (cfg.enabled && cfg.inter.on && inter == null && !interLoading) a.runOnUiThread {
            interLoading = true
            InterstitialAd.load(a, cfg.inter.unit.ifBlank { TEST_INTER }, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { inter = ad; interLoading = false }
                override fun onAdFailedToLoad(e: LoadAdError) { inter = null; interLoading = false }
            })
        }
        if (cfg.reward.on && reward == null && !rewardLoading) a.runOnUiThread {
            rewardLoading = true
            RewardedAd.load(a, cfg.reward.unit.ifBlank { TEST_REWARD }, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { reward = ad; rewardLoading = false }
                override fun onAdFailedToLoad(e: LoadAdError) { reward = null; rewardLoading = false }
            })
        }
    }

    /**
     * Κλείνει ένα άρθρο: κάθε Ν άρθρα (και όχι συχνότερα από Χ λεπτά, ποτέ όσο παίζει η ΑΕΚ)
     * fullscreen — πρώτα καμπάνια χορηγού, αλλιώς AdMob.
     */
    fun onArticleClosed() {
        val cfg = Api.configNow()?.ads ?: return
        articles++
        val sponsor = cfg.pick("interstitial")
        if (adFree() || (!cfg.inter.on && sponsor == null)) return
        if (articles % cfg.inter.every.coerceAtLeast(2) != 0) return
        if (System.currentTimeMillis() - lastFull < cfg.inter.gapMin.coerceAtLeast(1) * 60_000L) return
        if (cfg.inter.liveOff && aekLive) return
        if (sponsor != null) { lastFull = System.currentTimeMillis(); overlay.value = sponsor to false; return }
        if (!cfg.enabled) return
        val a = act.get() ?: return
        val ad = inter ?: run { preload(); return }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { inter = null; preload() }
            override fun onAdFailedToShowFullScreenContent(e: com.google.android.gms.ads.AdError) { inter = null; preload() }
        }
        lastFull = System.currentTimeMillis()
        ad.show(a)
    }

    /* ───── Splash χορηγού (μετά το logo) ───── */
    fun maybeSplash(ctx: Context) {
        val cfg = Api.configNow()?.ads ?: return
        if (!cfg.splash) return
        val c = cfg.pick("splash") ?: return
        val p = prefs(ctx); val last = p.getLong("splash_at", 0L)
        if (System.currentTimeMillis() - last < 4 * 3_600_000L) return // το πολύ μία φορά ανά 4 ώρες
        p.edit().putLong("splash_at", System.currentTimeMillis()).apply()
        overlay.value = c to true
    }

    /* ───── Rewarded: «χωρίς διαφημίσεις για Χ ώρες» ───── */
    fun rewardReady() = reward != null
    fun showReward(activity: Activity, hours: Int, done: (Boolean) -> Unit) {
        val ad = reward ?: run { preload(); done(false); return }
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { reward = null; preload(); done(earned) }
        }
        ad.show(activity) { _ ->
            earned = true
            val until = System.currentTimeMillis() + hours.coerceAtLeast(1) * 3_600_000L
            prefs(activity).edit().putLong("free_until", until).apply()
            adFreeUntil.value = until
        }
    }
}

/* ═════════════ Θέσεις ═════════════ */

private val ad9 = 9

@Composable
private fun AdLabel(text: String = "ΔΙΑΦΗΜΙΣΗ") =
    Text(text, style = body(ad9, FontWeight.ExtraBold, C.Muted).copy(letterSpacing = 1.sp), modifier = Modifier.padding(bottom = 4.dp))

/** Banner: πρώτα καμπάνια χορηγού για τη θέση, αλλιώς AdMob. Αν δεν φορτώσει τίποτα, δεν πιάνει χώρο. */
@Composable
fun BannerAd(cfg: AdsConfig, medium: Boolean = false, modifier: Modifier = Modifier, place: String = "home") {
    val free by Ads.adFreeUntil.collectAsState()
    if (System.currentTimeMillis() < free) return
    val sponsor = remember(cfg, place) { cfg.pick("banner", place) }
    if (sponsor != null) { SponsorBanner(sponsor, modifier); return }
    val ready by Ads.ready.collectAsState()
    if (!cfg.enabled || !ready) return
    var failed by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    if (failed) return
    val unit = Ads.unit(cfg)
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (loaded) AdLabel()
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

/** Διαφήμιση μέσα στη ροή ειδήσεων: κάρτα χορηγού → native AdMob → banner. */
@Composable
fun FeedAd(cfg: AdsConfig, modifier: Modifier = Modifier) {
    val free by Ads.adFreeUntil.collectAsState()
    if (System.currentTimeMillis() < free) return
    val sponsor = remember(cfg) { cfg.pick("native") }
    when {
        sponsor != null -> SponsorNative(sponsor, modifier)
        cfg.enabled && cfg.native.on -> AdmobNative(cfg.native.unit.ifBlank { Ads.TEST_NATIVE }, modifier)
        cfg.enabled -> BannerAd(cfg, modifier = modifier, place = "home")
    }
}

@Composable
private fun Impression(c: Campaign) { LaunchedEffect(c.id) { Api.track(c.id, "i") } }

@Composable
fun SponsorBanner(c: Campaign, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Impression(c)
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AdLabel("ΧΟΡΗΓΟΥΜΕΝΟ")
        AsyncImage(c.image, c.name, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Panel).clickable { Ads.click(ctx, c) }, contentScale = ContentScale.FillWidth)
    }
}

/** Κάρτα χορηγού που μοιάζει με άρθρο. */
@Composable
fun SponsorNative(c: Campaign, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Impression(c)
    Column(
        modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(C.Panel).border(1.dp, C.Line, RoundedCornerShape(16.dp)).clickable { Ads.click(ctx, c) },
    ) {
        AsyncImage(c.image, null, Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentScale = ContentScale.Crop)
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ΧΟΡΗΓΟΥΜΕΝΟ · " + c.name.uppercase(), style = body(10, FontWeight.ExtraBold, C.Gold).copy(letterSpacing = 0.8.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (c.title.isNotBlank()) Text(c.title, style = body(17, FontWeight.ExtraBold).copy(lineHeight = 22.sp))
            if (c.link.isNotBlank()) Box(Modifier.clip(RoundedCornerShape(10.dp)).background(C.Gold).padding(horizontal = 14.dp, vertical = 9.dp)) {
                Text(c.cta.ifBlank { "Δες περισσότερα" }, style = body(13, FontWeight.ExtraBold, C.Ink))
            }
        }
    }
}

/** «Παρουσιάζεται από»: λογότυπο χορηγού (Match Center / βαθμολογίες / live). Δεν το κλείνει το rewarded. */
@Composable
fun PresentedBy(place: String, modifier: Modifier = Modifier) {
    val cfg = Api.configNow()?.ads ?: return
    val c = remember(cfg, place) { cfg.pick("presented", place) } ?: return
    val ctx = LocalContext.current
    Impression(c)
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Panel).clickable(enabled = c.link.isNotBlank()) { Ads.click(ctx, c) }.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Text("ΠΑΡΟΥΣΙΑΖΕΤΑΙ ΑΠΟ", style = body(10, FontWeight.ExtraBold, C.Muted).copy(letterSpacing = 1.sp))
        AsyncImage(c.image, c.name, Modifier.padding(start = 12.dp).height(28.dp), contentScale = ContentScale.Fit)
    }
}

/** Native AdMob, στημένο με τα χρώματα του app. */
@Composable
fun AdmobNative(unit: String, modifier: Modifier = Modifier) {
    val ready by Ads.ready.collectAsState()
    if (!ready) return
    val ctx = LocalContext.current
    var ad by remember { mutableStateOf<NativeAd?>(null) }
    DisposableEffect(unit) {
        val loader = AdLoader.Builder(ctx, unit).forNativeAd { ad = it }.withAdListener(object : AdListener() {}).build()
        loader.loadAd(AdRequest.Builder().build())
        onDispose { ad?.destroy() }
    }
    val n = ad ?: return
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        AdLabel()
        AndroidView(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Panel),
            factory = { c ->
                val dp = { v: Int -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), c.resources.displayMetrics).toInt() }
                NativeAdView(c).apply {
                    val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
                    val media = MediaView(c).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(190)) }
                    val head = TextView(c).apply { setTextColor(AColor.WHITE); textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setPadding(dp(14), dp(12), dp(14), dp(2)) }
                    val bodyV = TextView(c).apply { setTextColor(AColor.parseColor("#B8B6AE")); textSize = 13f; maxLines = 2; setPadding(dp(14), dp(2), dp(14), dp(8)) }
                    val cta = Button(c).apply { setBackgroundColor(AColor.parseColor("#F9CF1C")); setTextColor(AColor.BLACK); isAllCaps = false; typeface = Typeface.DEFAULT_BOLD
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(14), 0, dp(14), dp(14)); gravity = Gravity.START } }
                    col.addView(media); col.addView(head); col.addView(bodyV); col.addView(cta); addView(col)
                    mediaView = media; headlineView = head; bodyView = bodyV; callToActionView = cta
                    tag = arrayOf(head, bodyV, cta)
                }
            },
            update = { v ->
                @Suppress("UNCHECKED_CAST") val t = v.tag as Array<android.view.View>
                (t[0] as TextView).text = n.headline
                (t[1] as TextView).text = n.body ?: ""
                (t[2] as Button).text = n.callToAction ?: "Περισσότερα"
                v.setNativeAd(n)
            },
        )
    }
}

/** Χορηγός σε πλήρη οθόνη: splash (3″, «Παράλειψη») ή fullscreen ανάμεσα σε άρθρα (Χ μετά από 3″). */
@Composable
fun SponsorOverlay() {
    val o by Ads.overlay.collectAsState()
    val ctx = LocalContext.current
    AnimatedVisibility(o != null, enter = fadeIn(), exit = fadeOut()) {
        val (c, splash) = o ?: return@AnimatedVisibility
        var left by remember(c.id) { mutableIntStateOf(3) }
        LaunchedEffect(c.id) {
            Api.track(c.id, "i")
            while (left > 0) { delay(1000); left-- }
            if (splash) Ads.overlay.value = null
        }
        Box(Modifier.fillMaxSize().background(Color.Black).clickable { Ads.click(ctx, c) }) {
            AsyncImage(c.image, c.name, Modifier.fillMaxSize(), contentScale = if (splash) ContentScale.Crop else ContentScale.Fit)
            Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (splash) "ΧΟΡΗΓΟΣ" else "ΧΟΡΗΓΟΥΜΕΝΟ", Modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 4.dp), style = body(10, FontWeight.ExtraBold, Color.White).copy(letterSpacing = 1.sp))
                if (splash || left == 0) Box(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.6f)).clickable { Ads.overlay.value = null }.padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (splash) Text("Παράλειψη" + if (left > 0) " · $left" else "", style = body(13, FontWeight.ExtraBold, Color.White))
                    else Icon(Icons.Filled.Close, "Κλείσιμο", tint = Color.White, modifier = Modifier.size(20.dp))
                } else Box(Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                    Text("$left", style = disp(16), color = Color.White)
                }
            }
        }
    }
}

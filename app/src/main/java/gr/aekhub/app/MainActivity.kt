package gr.aekhub.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.os.Bundle
import coil.Coil
import coil.ImageLoader
import coil.decode.SvgDecoder
import okhttp3.OkHttpClient
import gr.aekhub.app.ui.screens.CategoryScreen
import gr.aekhub.app.ui.screens.EventScreen
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import gr.aekhub.app.ads.Ads
import gr.aekhub.app.push.Push
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import gr.aekhub.app.data.Api
import gr.aekhub.app.data.Prefs
import gr.aekhub.app.ui.screens.ArticleScreen
import gr.aekhub.app.ui.screens.FeedScreen
import gr.aekhub.app.ui.screens.HomeScreen
import gr.aekhub.app.ui.screens.MatchScreen
import gr.aekhub.app.ui.screens.MatchesScreen
import gr.aekhub.app.ui.screens.MeScreen
import gr.aekhub.app.ui.screens.SearchScreen
import gr.aekhub.app.ui.screens.StandingsScreen
import gr.aekhub.app.ui.theme.AekTheme
import gr.aekhub.app.ui.theme.C
import gr.aekhub.app.ui.theme.body

val LocalPrefs = staticCompositionLocalOf<Prefs> { error("Prefs") }

/** Άνοιγμα από ειδοποίηση: άρθρο ή αγώνας. */
data class Deep(val post: Long = 0, val match: Long = 0, val at: Long = System.nanoTime())

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_POST = "aek_post"
        const val EXTRA_MATCH = "aek_match"
    }

    private val deep = MutableStateFlow<Deep?>(null)

    private fun readIntent(i: Intent?) {
        val p = i?.getLongExtra(EXTRA_POST, 0L) ?: 0L
        val m = i?.getLongExtra(EXTRA_MATCH, 0L) ?: 0L
        if (p > 0 || m > 0) deep.value = Deep(p, m)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val ink = C.Ink.toArgb()
        enableEdgeToEdge(SystemBarStyle.dark(ink), SystemBarStyle.dark(ink))
        Api.init(applicationContext)
        // Εικόνες: και SVG λογότυπα (πολλά σήματα ομάδων είναι .svg), και παλιά links σε http
        Coil.setImageLoader(ImageLoader.Builder(applicationContext)
            .components { add(SvgDecoder.Factory()) }
            .crossfade(true).build())
        Push.channels(this)
        Ads.start(this)
        val prefs = Prefs(applicationContext)
        if (savedInstanceState == null) readIntent(intent)
        // Οι επιλογές ειδοποιήσεων → θέματα στο Firebase, σε κάθε αλλαγή
        lifecycleScope.launch { prefs.settings.collect { Push.sync(applicationContext, it) } }
        setContent {
            AekTheme {
                CompositionLocalProvider(LocalPrefs provides prefs) {
                    Box(Modifier.fillMaxSize()) {
                        AppRoot(deep)
                        // Animation ανοίγματος με το logo (μία φορά ανά άνοιγμα, όχι σε περιστροφή οθόνης)
                        var intro by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(savedInstanceState == null) }
                        if (intro) gr.aekhub.app.ui.components.IntroSplash { intro = false; Ads.maybeSplash(this@MainActivity) }
                        // Χορηγός σε πλήρη οθόνη (splash / fullscreen) πάνω από όλα
                        gr.aekhub.app.ads.SponsorOverlay()
                    }
                }
            }
        }
    }

    // Επιστροφή στο app: όλες οι ανοιχτές οθόνες φέρνουν αμέσως τα τρέχοντα (χωρίς refresh με το χέρι)
    private var resumedOnce = false
    override fun onResume() {
        super.onResume()
        Ads.attach(this)
        if (resumedOnce) gr.aekhub.app.live.Live.ping() else resumedOnce = true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val TABS = listOf(
    Tab("home", "Ειδήσεις", Icons.AutoMirrored.Outlined.Article),
    Tab("matches", "Αγώνες", Icons.Outlined.SportsSoccer),
    Tab("standings", "Βαθμολογίες", Icons.Outlined.Leaderboard),
    Tab("me", "Εγώ", Icons.Outlined.Person),
)

@Composable
private fun AppRoot(deep: MutableStateFlow<Deep?>) {
    val nav = rememberNavController()
    val link by deep.collectAsState()
    LaunchedEffect(link) {
        val d = link ?: return@LaunchedEffect
        if (d.post > 0) nav.navigate("post/${d.post}") else if (d.match > 0) nav.navigate("match/${d.match}")
        deep.value = null
    }
    // Android 13+: μία φορά ζητάμε άδεια για ειδοποιήσεις
    val ctx = LocalContext.current
    val askPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val sp = ctx.getSharedPreferences("push", android.content.Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 && !Push.allowed(ctx) && !sp.getBoolean("asked", false)) {
            sp.edit().putBoolean("asked", true).apply()
            askPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = TABS.any { it.route == route }

    fun goTab(r: String) = nav.navigate(r) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true; restoreState = true
    }
    val openPost = { id: Long -> nav.navigate("post/$id") }
    val openMatch = { id: Long -> nav.navigate("match/$id") }

    Scaffold(
        containerColor = C.Ink,
        bottomBar = {
            if (showBar) Column {
                HorizontalDivider(thickness = 1.dp, color = C.Line)
                NavigationBar(containerColor = C.Ink2, tonalElevation = 0.dp) {
                    TABS.forEach { t ->
                        val on = route == t.route
                        NavigationBarItem(
                            selected = on, onClick = { goTab(t.route) },
                            icon = { Icon(t.icon, null, Modifier.size(24.dp)) },
                            label = { Text(t.label, style = body(11, if (on) FontWeight.ExtraBold else FontWeight.Bold)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = C.Gold, selectedTextColor = C.Gold, indicatorColor = Color.Transparent,
                                unselectedIconColor = C.Muted, unselectedTextColor = C.Muted,
                            ),
                        )
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().background(C.Ink).padding(pad)) {
            NavHost(nav, startDestination = "home") {
                composable("home") {
                    HomeScreen(openPost, openMatch, { nav.navigate("search") }, { goTab("me") }, { nav.navigate("feed") }, { goTab("matches") },
                        { slug, name -> nav.navigate("cat/" + Uri.encode(slug) + "?name=" + Uri.encode(name)) }, { id -> nav.navigate("event/$id") })
                }
                composable("cat/{slug}?name={name}", arguments = listOf(navArgument("slug") { type = NavType.StringType }, navArgument("name") { type = NavType.StringType; defaultValue = "" })) {
                    CategoryScreen(it.arguments?.getString("slug") ?: "", it.arguments?.getString("name") ?: "", { nav.popBackStack() }, openPost)
                }
                composable("event/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    EventScreen(it.arguments?.getLong("id") ?: 0L) { nav.popBackStack() }
                }
                composable("feed") { FeedScreen({ nav.popBackStack() }, openPost) }
                composable("matches") { MatchesScreen(openMatch) }
                composable("standings") { StandingsScreen() }
                composable("me") { MeScreen(openPost) }
                composable("search") { SearchScreen({ nav.popBackStack() }, openPost) }
                composable("post/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    ArticleScreen(it.arguments?.getLong("id") ?: 0L, { nav.popBackStack() }, openPost)
                }
                composable("match/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    MatchScreen(it.arguments?.getLong("id") ?: 0L) { nav.popBackStack() }
                }
            }
            // Γκολ / έναρξη / τελικό της ΑΕΚ: banner πάνω-πάνω σε κάθε οθόνη
            gr.aekhub.app.ui.components.LiveScoreBanner(openMatch)
        }
    }
}

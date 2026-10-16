package gr.aekhub.app

import android.os.Bundle
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val ink = C.Ink.toArgb()
        enableEdgeToEdge(SystemBarStyle.dark(ink), SystemBarStyle.dark(ink))
        Api.init(applicationContext)
        val prefs = Prefs(applicationContext)
        setContent {
            AekTheme {
                CompositionLocalProvider(LocalPrefs provides prefs) { AppRoot() }
            }
        }
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
private fun AppRoot() {
    val nav = rememberNavController()
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
                composable("home") { HomeScreen(openPost, openMatch, { nav.navigate("search") }, { goTab("me") }) }
                composable("matches") { MatchesScreen(openMatch) }
                composable("standings") { StandingsScreen() }
                composable("me") { MeScreen(openPost) }
                composable("search") { SearchScreen({ nav.popBackStack() }, openPost) }
                composable("post/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    ArticleScreen(it.arguments?.getLong("id") ?: 0L) { nav.popBackStack() }
                }
                composable("match/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    MatchScreen(it.arguments?.getLong("id") ?: 0L) { nav.popBackStack() }
                }
            }
        }
    }
}

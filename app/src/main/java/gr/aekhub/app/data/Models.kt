package gr.aekhub.app.data

import kotlinx.serialization.Serializable

@Serializable
data class Category(val slug: String, val name: String, val icon: String = "eagle")

@Serializable
data class SportItem(val key: String, val name: String)

@Serializable
data class AdsConfig(
    val enabled: Boolean = true,
    val banner: String = "",
    val everyN: Int = 8,
    val article: Boolean = true,
    val matches: Boolean = false,
)

@Serializable
data class AppConfig(
    val site: String = "",
    val categories: List<Category> = emptyList(),
    val sports: List<SportItem> = emptyList(),
    val ads: AdsConfig = AdsConfig(),
    val socials: List<Social> = emptyList(),
)

@Serializable
data class Social(val key: String, val label: String = "", val url: String, val handle: String = "")

@Serializable
data class PostCard(
    val id: Long,
    val title: String,
    val excerpt: String = "",
    val date: String = "",
    val image: String = "",
    val category: String = "",
    val link: String = "",
)

@Serializable
data class Feed(
    val page: Int = 1,
    val pages: Int = 1,
    val featured: PostCard? = null,
    val items: List<PostCard> = emptyList(),
)

@Serializable
data class Post(
    val id: Long,
    val title: String,
    val excerpt: String = "",
    val date: String = "",
    val image: String = "",
    val imageCaption: String = "",
    val category: String = "",
    val link: String = "",
    val author: String = "",
    val authorAvatar: String = "",
    val minutes: Int = 1,
    val html: String = "",
) {
    fun card() = PostCard(id, title, excerpt, date, image, category, link)
}

@Serializable
data class Clock(val b: Double, val s: Double, val l: Double = 45.0)

@Serializable
data class MatchCard(
    val id: Long,
    val sport: String = "football",
    val sportLabel: String = "",
    val status: String = "upcoming",
    val statusLabel: String = "",
    val phase: String = "",
    val live: Boolean = false,
    val minute: String = "",
    val clock: Clock? = null,
    val date: String = "",
    val time: String = "",
    val home: String = "",
    val away: String = "",
    val homeLogo: String = "",
    val awayLogo: String = "",
    val homeColor: String = "",
    val awayColor: String = "",
    val homeNum: String = "",
    val awayNum: String = "",
    val scoreHome: String = "",
    val scoreAway: String = "",
    val penHome: String = "",
    val penAway: String = "",
    val competition: String = "",
    val matchday: String = "",
    val venue: String = "",
    val broadcast: String = "",
    val aek: String = "",
    val link: String = "",
) {
    val finished get() = status == "finished"
    val upcoming get() = status == "upcoming"
    val postponed get() = status == "postponed"
    /** null = ισοπαλία ή δεν ξέρουμε, true = νίκη ΑΕΚ, false = ήττα */
    val aekWon: Boolean?
        get() {
            val h = scoreHome.toIntOrNull() ?: return null
            val a = scoreAway.toIntOrNull() ?: return null
            if (aek.isEmpty()) return null
            var mine = if (aek == "home") h else a
            var their = if (aek == "home") a else h
            if (mine == their) {
                val ph = penHome.toIntOrNull(); val pa = penAway.toIntOrNull()
                if (ph != null && pa != null) { mine = if (aek == "home") ph else pa; their = if (aek == "home") pa else ph }
            }
            return if (mine == their) null else mine > their
        }
}

@Serializable
data class MatchList(
    val today: String = "",
    val from: String = "",
    val to: String = "",
    val items: List<MatchCard> = emptyList(),
)

@Serializable
data class MatchEvent(
    val minute: String = "",
    val type: String = "",
    val label: String = "",
    val team: String = "",
    val text: String = "",
    val score: String = "",
)

@Serializable
data class StatRow(val label: String, val type: String = "", val home: String = "", val away: String = "")

@Serializable
data class PeriodScore(val label: String, val home: String = "", val away: String = "")

@Serializable
data class Player(val number: String = "", val name: String = "", val role: String = "", val captain: Boolean = false)

@Serializable
data class MatchDetail(
    val id: Long,
    val sport: String = "football",
    val sportLabel: String = "",
    val status: String = "upcoming",
    val statusLabel: String = "",
    val phase: String = "",
    val live: Boolean = false,
    val minute: String = "",
    val clock: Clock? = null,
    val date: String = "",
    val time: String = "",
    val home: String = "",
    val away: String = "",
    val homeLogo: String = "",
    val awayLogo: String = "",
    val homeColor: String = "",
    val awayColor: String = "",
    val homeNum: String = "",
    val awayNum: String = "",
    val scoreHome: String = "",
    val scoreAway: String = "",
    val penHome: String = "",
    val penAway: String = "",
    val competition: String = "",
    val matchday: String = "",
    val venue: String = "",
    val broadcast: String = "",
    val aek: String = "",
    val link: String = "",
    val events: List<MatchEvent> = emptyList(),
    val stats: List<StatRow> = emptyList(),
    val periods: List<PeriodScore> = emptyList(),
    val lineupHome: List<Player> = emptyList(),
    val lineupAway: List<Player> = emptyList(),
    val subsHome: List<Player> = emptyList(),
    val subsAway: List<Player> = emptyList(),
    val coachHome: String = "",
    val coachAway: String = "",
    val referee: String = "",
    val stamp: Long = 0,
    val bg: String = "",
    val timeline: Boolean = true,
    val liveScore: Boolean = true,
    val scorersHome: List<String> = emptyList(),
    val scorersAway: List<String> = emptyList(),
    val stream: StreamInfo? = null,
    val highlights: List<Highlight> = emptyList(),
    val pitch: Pitch? = null,
)

@Serializable
data class StreamInfo(val url: String, val embed: String = "", val thumb: String = "", val label: String = "", val kicker: String = "", val state: String = "pre")

@Serializable
data class Highlight(val title: String = "", val url: String, val embed: String = "", val thumb: String = "")

@Serializable
data class PitchPlayer(
    val number: String = "", val name: String = "", val x: Float = 50f, val y: Float = 50f,
    val goals: Int = 0, val yellow: Boolean = false, val red: Boolean = false, val out: String = "", val `in`: String = "",
)

@Serializable
data class Pitch(
    val home: List<PitchPlayer> = emptyList(),
    val away: List<PitchPlayer> = emptyList(),
    val formationHome: String = "",
    val formationAway: String = "",
    val bg: String = "",
)

@Serializable
data class Section(val slug: String, val name: String, val icon: String = "eagle", val items: List<PostCard> = emptyList())

@Serializable
data class HomeData(
    val liveHero: LiveHero? = null,
    val socials: List<Social> = emptyList(),
    val featured: PostCard? = null,
    val top: List<PostCard> = emptyList(),
    val matches: List<MatchCard> = emptyList(),
    val sections: List<Section> = emptyList(),
)

@Serializable
data class StandingRow(
    val pos: Int = 0,
    val name: String = "",
    val logo: String = "",
    val played: String = "",
    val won: String = "",
    val drawn: String = "",
    val lost: String = "",
    val gf: String = "",
    val ga: String = "",
    val points: String = "",
    val aek: Boolean = false,
)

@Serializable
data class Standing(
    val id: Long,
    val title: String,
    val sport: String = "",
    val league: String = "",
    val style: String = "wdl",
    val leagueLogo: String = "",
    val zones: List<Zone> = emptyList(),
    val rows: List<StandingRow> = emptyList(),
)

@Serializable
data class Zone(val from: Int, val to: Int, val label: String = "", val color: String = "#F9CF1C")

@Serializable
data class StandingsList(val items: List<Standing> = emptyList())

@Serializable
data class LiveHero(val type: String, val id: Long, val title: String = "", val image: String = "", val match: MatchCard? = null)

@Serializable
data class EventUpdate(
    val id: Long, val time: String = "", val minute: String = "", val type: String = "info", val typeLabel: String = "",
    val title: String = "", val text: String = "", val highlight: Boolean = false,
    val video: String = "", val embed: String = "", val thumb: String = "", val link: String = "",
)

@Serializable
data class EventDetail(val id: Long, val title: String = "", val image: String = "", val live: Boolean = false, val statusLabel: String = "", val updates: List<EventUpdate> = emptyList())

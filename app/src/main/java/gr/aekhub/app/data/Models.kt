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
    val inter: InterCfg = InterCfg(),
    val native: NativeCfg = NativeCfg(),
    val reward: RewardCfg = RewardCfg(),
    val splash: Boolean = true,
    val campaigns: List<Campaign> = emptyList(),
) {
    /** Μια καμπάνια χορηγού για τη θέση (με βάρος), αν υπάρχει. */
    fun pick(type: String, place: String = "all"): Campaign? {
        val l = campaigns.filter { it.type == type && (it.place == "all" || it.place == place || place == "all") }
        if (l.isEmpty()) return null
        var r = kotlin.random.Random.nextInt(l.sumOf { it.weight.coerceAtLeast(1) })
        for (c in l) { r -= c.weight.coerceAtLeast(1); if (r < 0) return c }
        return l.first()
    }
}

@Serializable data class InterCfg(val on: Boolean = false, val unit: String = "", val every: Int = 4, val gapMin: Int = 3, val liveOff: Boolean = true)
@Serializable data class NativeCfg(val on: Boolean = false, val unit: String = "")
@Serializable data class RewardCfg(val on: Boolean = false, val unit: String = "", val hours: Int = 24)
@Serializable data class Campaign(
    val id: String, val type: String, val place: String = "all", val name: String = "",
    val image: String = "", val link: String = "", val title: String = "", val cta: String = "", val weight: Int = 1,
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
    val video: Boolean = false,
)

/** Το βίντεο ενός video post (YouTube / Vimeo / Facebook / embed παρόχου). */
@Serializable
data class VideoInfo(val url: String = "", val embed: String = "", val html: String = "", val thumb: String = "")

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
    val video: Boolean = false,
    val videoInfo: VideoInfo? = null,
) {
    fun card() = PostCard(id, title, excerpt, date, image, category, link, video)
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
    /** Ώρα σέντρας ISO (μόνο στο «next» της αρχικής). */
    val kickoff: String = "",
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
data class EventGroup(val label: String = "", val score: String = "", val open: Boolean = false, val events: List<MatchEvent> = emptyList())

@Serializable
data class Player(val number: String = "", val name: String = "", val role: String = "", val captain: Boolean = false, val rating: Float = 0f)

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
    /** Πορεία ανά περίοδο/σετ/ημίχρονο (άδειο = ένα σύνολο, όπως στο ποδόσφαιρο) */
    val groups: List<EventGroup> = emptyList(),
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
    /** 🤝 «Powered by» από το Match Center → Χορηγός (ίδια ρύθμιση με το site) */
    val sponsor: Sponsor? = null,
    val pitch: Pitch? = null,
    /** Ανάλυση: διαβάζεται χωριστά (`ext`), ώστε ένα απρόσμενο πεδίο να μη χαλάει όλη την οθόνη του αγώνα */
    val extra: kotlinx.serialization.json.JsonElement? = null,
) {
    val ext: MatchExtra? by lazy { extra?.let { runCatching { Api.json.decodeFromJsonElement(MatchExtra.serializer(), it) }.getOrNull() } }
}

/* ─────────── Ανάλυση αγώνα (Match Center 1.97: FotMob / Flashscore / Sofascore / αναγνώστες) ─────────── */

@Serializable
data class MatchExtra(
    val resultOnly: Boolean = false,
    val htScore: String = "",
    val colorHome: String = "",
    val colorAway: String = "",
    val potm: Potm? = null,
    val ratings: Ratings? = null,
    val momentum: Momentum? = null,
    val shots: List<Shot>? = null,
    val box: BoxScore? = null,
)

@Serializable data class Potm(val name: String = "", val number: String = "", val team: String = "", val side: String = "", val rating: String = "")
@Serializable data class Ratings(val home: List<RatingRow> = emptyList(), val away: List<RatingRow> = emptyList())
@Serializable data class RatingRow(val number: String = "", val name: String = "", val rating: Float = 0f)

@Serializable data class Momentum(val periodLength: Int = 45, val periods: Int = 2, val points: List<MomentumPoint> = emptyList())
@Serializable data class MomentumPoint(val minute: Float = 0f, val value: Int = 0)
/** x/y 0–100, ο γηπεδούχος επιτίθεται δεξιά · result: goal/saved/miss/blocked/post */
@Serializable
data class Shot(val side: String = "home", val x: Float = 50f, val y: Float = 50f, val result: String = "miss", val minute: String = "", val player: String = "", val xg: Float = 0f, val penalty: Boolean = false)

@Serializable data class BoxScore(val src: String = "", val home: List<BoxRow> = emptyList(), val away: List<BoxRow> = emptyList())
@Serializable data class BoxRow(val number: String = "", val name: String = "", val min: Int = 0, val pts: Int = 0, val reb: Int = 0, val ast: Int = 0, val starter: Boolean = false)

@Serializable
data class StreamInfo(val url: String, val embed: String = "", val thumb: String = "", val label: String = "", val kicker: String = "", val state: String = "pre")

/** Χορηγός αγώνα: h/w σε dp (0 = αυτόματο πλάτος), click/view = διευθύνσεις μέτρησης του site */
@Serializable
data class Sponsor(val label: String = "Powered by", val name: String = "", val logo: String = "", val note: String = "", val h: Int = 42, val w: Int = 0, val click: String = "", val view: String = "")

@Serializable
data class Highlight(val title: String = "", val url: String, val embed: String = "", val thumb: String = "")

@Serializable
data class PitchPlayer(
    val number: String = "", val name: String = "", val x: Float = 50f, val y: Float = 50f,
    val goals: Int = 0, val yellow: Boolean = false, val red: Boolean = false, val out: String = "", val `in`: String = "",
    val rating: Float = 0f,
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

/** 🚨 Έκτακτο: κόκκινη λωρίδα πάνω στην αρχική (από το Κέντρο Ελέγχου του site). */
@Serializable
data class Breaking(val text: String = "", val url: String = "", val label: String = "ΕΚΤΑΚΤΟ", val id: String = "")

@Serializable
data class HomeData(
    /** ⏱ Ο αγώνας της αντίστροφης μέτρησης (ίδιος με τη μπάρα «Επόμενος αγώνας» της αρχικής του site). */
    val next: MatchCard? = null,
    val liveHero: LiveHero? = null,
    val breaking: Breaking? = null,
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

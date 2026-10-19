package com.rpm.player.core.radio

import java.text.Normalizer

/**
 * The twenty kinds of station the tuner sorts by. Same table as `RPM.Core/Radio/RadioGenres.cs` on the
 * desktop. Each genre has the name shown, the term the directory is asked with, and the tags that mean
 * it. A station's tag names a genre when it is one of those tags whole (accents, case and punctuation
 * folded away), or ends in one with "music", "radio", "fm", "hits" or "station" after it. A tag of
 * several words names every genre its words spell, longest run first.
 *
 * The names are shown as they are, as the desktop shows them: they come from this table, not from the
 * translations.
 */
object RadioGenres {
    /** Every genre, in the order the menus list them. */
    val all: List<String> get() = TABLE.map { it.genre }

    /** Not music: left out of the tuner's Music menu. Same set as `NotMusic` on the desktop. */
    val notMusic = setOf("Talk", "News", "Sports")

    /** The directory's search term for [genre], or null for a name not in the table. */
    fun term(genre: String?): String? = TABLE.firstOrNull { it.genre == genre }?.term

    /** Whether any of [tags] names [genre]. */
    fun isGenre(tags: List<String>, genre: String): Boolean = tags.any { tag -> genre in names(tag) }

    /** The genres one tag names. */
    fun names(tag: String?): List<String> {
        if (tag.isNullOrBlank()) return emptyList()
        val folded = fold(tag)
        if (folded.isEmpty()) return emptyList()
        return whole(folded)?.let { listOf(it) } ?: inWords(tag)
    }

    private fun inWords(tag: String): List<String> {
        val words = words(tag)
        val named = ArrayList<String>()
        if (words.size < 2) return named
        var at = 0
        while (at < words.size) {
            val run = StringBuilder()
            var hit: String? = null
            var end = at + 1
            for (upto in at until words.size) {
                run.append(words[upto])
                whole(run.toString())?.let {
                    hit = it
                    end = upto + 1
                }
            }
            hit?.let { if (it !in named) named += it }
            at = if (hit == null) at + 1 else end
        }
        return named
    }

    private fun whole(folded: String): String? {
        BY_TAG[folded]?.let { return it }
        for (suffix in SUFFIXES) {
            if (folded.length > suffix.length && folded.endsWith(suffix)) {
                BY_TAG[folded.dropLast(suffix.length)]?.let { return it }
            }
        }
        return null
    }

    private fun decomposed(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)

    private fun isMark(c: Char): Boolean = Character.getType(c) == Character.NON_SPACING_MARK.toInt()

    private fun fold(tag: String): String = buildString {
        for (c in decomposed(tag)) if (!isMark(c) && c.isLetterOrDigit()) append(c.lowercaseChar())
    }

    private fun words(tag: String): List<String> {
        val words = ArrayList<String>()
        val word = StringBuilder()
        for (c in decomposed(tag)) {
            if (isMark(c)) continue
            if (c.isLetterOrDigit()) {
                word.append(c.lowercaseChar())
            } else if (word.isNotEmpty()) {
                words += word.toString()
                word.clear()
            }
        }
        if (word.isNotEmpty()) words += word.toString()
        return words
    }

    private val SUFFIXES = listOf("music", "radio", "fm", "hits", "station")

    private class Row(val genre: String, val term: String, val tags: List<String>)

    private val TABLE = listOf(
        Row("Pop", "pop", listOf("pop", "popular", "poprock", "europop", "kpop", "jpop", "cpop", "mandopop",
            "cantopop", "synthpop", "teenpop", "electropop", "indiepop", "dreampop", "britpop",
            "popmusik", "musicapop", "popmusica", "juvenil", "chanson", "chansonsfrancaises",
            "varietefrancaise", "schlager", "volksmusik", "italianpop", "greekpop", "поп")),
        Row("Top 40", "top 40", listOf("top40", "top40s", "chr", "hits", "hitradio", "tophits", "charthits", "charts",
            "chart", "contemporaryhitradio", "contemporaryhits", "contemporaryhitsradio",
            "currenthits", "hitmusic", "toppop", "top10", "top20", "top50", "top100",
            "topcharts", "singlecharts", "hitparade", "hitlist", "todayshits", "newhits",
            "musicaactual", "exitos")),
        Row("Adult Contemporary", "adult contemporary", listOf("adultcontemporary", "ac", "hotac", "softac", "adultcontempory",
            "hotadultcontemporary", "softadultcontemporary", "easylistening",
            "easy", "softrock", "soft", "adulthits", "adultstandards",
            "middleoftheroad", "mor", "lovesongs", "ballads", "crooners",
            "beautifulmusic", "lightorchestral", "strings", "instrumental",
            "newage", "relax", "relaxing", "relaxation", "calm", "mellow",
            "unwind", "smooth")),
        Row("Oldies", "oldies", listOf("oldies", "goldenoldies", "classichits", "gold", "goldies", "nostalgia",
            "nostalgie", "nostalgic", "retro", "vintage", "flashback", "decades", "ретро",
            "oldskool", "oldschool", "classics", "clasicos", "musicadelrecuerdo",
            "recuerdo", "greatesthits", "yesterday", "yesteryear", "50s", "60s", "70s",
            "80s", "90s", "00s", "1930", "1940", "1950", "1960", "1970", "1980", "1990",
            "1950s", "1960s", "1970s", "1980s", "1990s", "2000s", "50er", "60er", "70er",
            "80er", "90er", "2000er", "anni60", "anni70", "anni80", "anni90", "annees60",
            "annees70", "annees80", "annees90", "fifties", "sixties", "seventies",
            "eighties", "nineties")),
        Row("Rock", "rock", listOf("rock", "altrock", "alternative", "alternativerock", "adultalternative",
            "adultalbumalternative", "indie", "indierock", "grunge", "punk", "punkrock",
            "postpunk", "hardrock", "postrock", "garagerock", "rockpop", "progressiverock",
            "artrock", "psychedelic", "psychedelicrock", "newwave", "рок", "gothic",
            "gothrock", "emo", "modernrock", "southernrock", "folkrock", "rockalternativo",
            "rockmusik")),
        Row("Classic Rock", "classic rock", listOf("classicrock", "albumrock", "aor", "classicrockhits", "oldrock",
            "rockclassics", "60srock", "70srock", "80srock", "rockandroll",
            "rocknroll")),
        Row("Metal", "metal", listOf("metal", "heavymetal", "deathmetal", "blackmetal", "thrash", "thrashmetal",
            "metalcore", "deathcore", "grindcore", "powermetal", "numetal", "gothicmetal",
            "symphonicmetal", "folkmetal", "speedmetal", "progressivemetal", "doom",
            "hardcore", "металл")),
        Row("Dance", "dance", listOf("dance", "edm", "club", "clubmusic", "clubbing", "clubdance", "dancefloor",
            "party", "partymix", "houseparty", "disco", "italodisco", "nudisco", "discofox",
            "dancehits", "eurodance", "danceclassics", "dancepop", "popdance", "rave",
            "mashup", "remix", "remixes", "remixed", "djremix", "djmix", "djmixes", "djsets",
            "djset", "deejay", "megamix", "hardstyle", "beachclub", "ibiza", "танцевальная")),
        Row("Electronic", "electronic", listOf("electronic", "electronica", "electro", "electronicdancemusic", "house",
            "deephouse", "techhouse", "electrohouse", "progressivehouse",
            "soulfulhouse", "tribalhouse", "futurehouse", "techno", "hypertechno",
            "hardtechno", "dubtechno", "trance", "psytrance", "progressivepsytrance",
            "goa", "ambient", "chillout", "chill", "lounge", "downtempo", "dubstep",
            "drumandbass", "drumbass", "dnb", "dandb", "jungle", "neurofunk",
            "techstep", "breakbeat", "ukgarage", "ukfunky", "minimal", "idm", "triphop",
            "synthwave", "retrowave", "vaporwave", "moombahton", "industrial",
            "электронная", "чилаут")),
        Row("Hip-Hop", "hop", listOf("hiphop", "rap", "urban", "trap", "hiphoprap", "raphiphop", "oldschoolhiphop",
            "gangsta", "grime", "drill", "boombap", "rapfrancais", "deutschrap", "рэп",
            "хип-хоп")),
        Row("R&B / Soul", "soul", listOf("rb", "rnb", "randb", "rhythmandblues", "soul", "funk", "funky", "funksoul",
            "motown", "neosoul", "rbsoul", "rnbsoul", "soulfunk", "groove",
            "quietstorm", "urbancontemporary", "blackmusic", "gospel")),
        Row("Jazz", "jazz", listOf("jazz", "smoothjazz", "softjazz", "contemporaryjazz", "classicjazz", "latinjazz",
            "bebop", "swing", "bigband", "dixieland", "ragtime", "jazzfunk", "acidjazz",
            "fusion", "vocaljazz", "nujazz", "pianojazz", "джаз")),
        Row("Blues", "blues", listOf("blues", "deltablues", "chicagoblues", "electricblues", "jumpblues", "soulblues",
            "bluesrock", "rhythmblues")),
        Row("Country", "country", listOf("country", "americana", "bluegrass", "countryhits", "classiccountry",
            "hotcountry", "todayscountry", "traditionalcountry", "countryclassics",
            "countrygold", "honkytonk", "folk", "singersongwriter", "roots", "newcountry",
            "фолк")),
        Row("Classical", "classical", listOf("classical", "klassik", "klassisch", "klassiek", "klassieke", "clasica",
            "classique", "musicaclasica", "opera", "operetta", "baroque", "symphonic",
            "symphony", "orchestral", "choral", "chamber", "earlymusic", "romantic",
            "piano", "классическая")),
        Row("Latin", "latin", listOf("latin", "latino", "salsa", "reggaeton", "regueton", "bachata", "merengue",
            "cumbia", "tango", "latinpop", "musicalatina", "musicalatinoamericana",
            "ranchera", "rancheras", "banda", "bandanortena", "nortena", "nortenas",
            "norteno", "sierreno", "corrido", "corridos", "grupera", "grupero", "vallenato",
            "tropical", "musicatropical", "tropicalmusic", "balada", "baladas", "baladapop",
            "baladaromantica", "romantica", "romanticas", "musicaromantica", "urbano",
            "musicaurbana", "mariachi", "bossanova", "samba", "sertanejo", "mpb", "forro",
            "pagode", "musicamexicana", "mexicanmusic", "mexicana", "musicaregional",
            "regionalmexicana", "regionalmexican", "musicaregionalmexicana",
            "musicapopularmexicana", "musicatradicionalmexicana", "traditionalmexicanmusic",
            "musicaenespanol", "popenespanol", "baladasenespanol", "clasicosenespanol",
            "rockenespanol")),
        Row("Reggae", "reggae", listOf("reggae", "dancehall", "ska", "dub", "rootsreggae", "raggae", "ragga",
            "raggamuffin", "loversrock")),
        Row("Talk", "talk", listOf("talk", "talkshow", "spokenword", "spoken", "chat", "discussion", "debate",
            "politics", "politicaltalk", "conservativetalk", "podcast", "conversation",
            "interview", "entrevistas", "comedy", "gesprek", "hablada", "radiohablada",
            "drama", "radiodrama", "audiodrama", "oldtimeradio", "otr", "storytelling",
            "audiobooks", "hoerspiel", "horspiel", "culture", "cultural", "cultura", "kultur",
            "education", "educational", "educativa", "разговорное", "разговорный", "юмор")),
        Row("News", "news", listOf("news", "newstalk", "information", "currentaffairs", "headlines", "allnews",
            "actualites", "actualite", "nachrichten", "noticias", "noticiero", "noticieros",
            "informativa", "informativo", "informacion", "actualidad", "nieuws", "nyheter",
            "notizie", "journal", "info", "weather", "wetter", "traffic", "verkehr", "npr",
            "новости", "информационное", "информационный", "新闻")),
        Row("Sports", "sport", listOf("sport", "sports", "sportstalk", "sportnews", "football", "soccer", "futbol",
            "basketball", "baseball", "hockey", "rugby", "cricket", "motorsport",
            "formula1", "tennis", "golf", "esports", "deporte", "deportes", "deportivo",
            "deportiva", "voetbal")),
    )

    private val BY_TAG: Map<String, String> = LinkedHashMap<String, String>().also { byTag ->
        for (row in TABLE) {
            byTag.putIfAbsent(fold(row.genre), row.genre)
            for (tag in row.tags) byTag.putIfAbsent(fold(tag), row.genre)
        }
    }
}

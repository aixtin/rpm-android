package com.rpm.player.core.papers

import com.rpm.player.core.cloud.ApiException
import com.rpm.player.core.net.AppKey
import com.rpm.player.core.net.Hosts
import com.rpm.player.core.net.WireJson
import com.rpm.player.core.net.await
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** What `metadata/papers` answers, cut down to the act and the record: their names, the genres the catalogues
 * file the record under, and the outside links, which is what an article is found with. The phone's
 * `CatalogPapers`; the whole contract is `CatalogPapers` in RPM.Fingerprint/Catalog/CatalogContracts.cs.
 * Every field is optional: the server omits what it does not know. */
@Serializable
data class CatalogPapers(val artist: CatalogArtist? = null, val album: CatalogAlbum? = null)

/** The act as the catalogues have it: its MusicBrainz id, its name, its Wikidata entity, and its outside links. */
@Serializable
data class CatalogArtist(val id: String? = null, val name: String? = null, val wikidataId: String? = null, val links: List<CatalogLink>? = null)

/** The record as the catalogues have it. [artistId] is the MusicBrainz id of the act the record itself names, which
 * the server matched through the record rather than by a name ([PapersClient.artist] reads it). */
@Serializable
data class CatalogAlbum(
    val title: String? = null,
    val genres: List<String>? = null,
    val links: List<CatalogLink>? = null,
    val artistId: String? = null,
)

/** One outside page. Which site it is on is decided by its host, as the desktop decides. */
@Serializable
data class CatalogLink(val url: String, val relation: String? = null)

/** What is said about one record or one act: the Wikipedia article, and for a record the genres the catalogues
 * file it under, as they spell them (the screen maps them onto the app's own). The desktop's `TrackDossier`, cut
 * down to what the record and artist pages show. */
@Serializable
data class Dossier(val article: EncyclopediaArticle?, val genres: List<String>) {
    /** Nothing to show: no article and no genres. Kept on the short clock (see [Dossiers]). */
    val isEmpty: Boolean get() = article == null && genres.isEmpty()

    companion object {
        val NOTHING = Dossier(article = null, genres = emptyList())
    }
}

/** A dossier, and whether the papers it was built from were only part of an answer. A partial answer is
 * shown and kept nowhere: the API finishes it behind the reader, and the next read is served the rest. */
data class DossierAnswer(val dossier: Dossier, val partial: Boolean)

/**
 * The Wikipedia text and the catalogue genres for a record, found the way the desktop finds them
 * (`TrackInfoService`, `EncyclopediaLookup`) and the phone does (`PapersClient`).
 *
 * 1. **The catalogue half goes to the RPM API**, `metadata/papers`, with the query the desktop sends. The
 *    server identifies the record, keeps the answer for everybody, and hands back its links.
 * 2. **The encyclopedia half is asked from the TV**, because which Wikipedia to read depends on the
 *    reader's language, and an answer the server caches for everybody could be in only one. Wikidata
 *    first, where the catalogue points; a direct Wikipedia link only where there is no Wikidata link.
 *    Nothing searches Wikipedia by name: a paragraph about the wrong band is worse than none.
 *
 * **A throw is not "nothing".** [Dossier.NOTHING] means the question was answered: the record is in
 * no catalogue, or has no article. A throw means somebody did not answer (no network, a refusal, a server
 * error), and [Dossiers] keeps the first and never the second.
 */
class PapersClient(private val http: OkHttpClient) {

    /** What is said about one record. [artist] and [album] as the files spell them: the server reduces the
     * title to the release group's, so an edition suffix does not lose the record. [tracks] tells an album
     * from the single of the same name. [languages] are the Wikipedias to read, best first. */
    suspend fun album(artist: String, album: String, tracks: Int, languages: List<String>): DossierAnswer {
        if (artist.isBlank() || album.isBlank()) return DossierAnswer(Dossier.NOTHING, partial = false)

        val (papers, partial) = papers(artist, album, tracks)

        // The desktop drops a record with no title before it looks for its article
        // (`EncyclopediaLookup.Profile`), and its genres go with it.
        val record = papers.album?.takeIf { !it.title.isNullOrBlank() }
            ?: return DossierAnswer(Dossier.NOTHING, partial)

        val article = article(entity = null, record.links.orEmpty(), languages)
        return DossierAnswer(Dossier(article, record.genres.orEmpty().filter { it.isNotBlank() }), partial)
    }

    /**
     * What is said about the act [name]: their article, from the act one of [records] names, their own records as the
     * files spell them, asked in turn until one names an act, at most [RECORDS_TRIED]. The same question the album page
     * asks, so its answer is usually kept on the server already.
     *
     * Never from the name alone. The server finds the act half of its answer by the name, and a name picks one of every
     * act called that: a page for Michael Jackson could show a British singer of the same name, and one for
     * Nirvana could show an older British band, even when asked with Thriller or Nevermind. The record
     * half names its own act by id (`album.artistId`, Wikidata's performer checked against the credit), and that act wins
     * ([recordsAct]), as the desktop's `PapersAgree` prefers it. An act with no record a catalogue knows has no article
     * here, which is better than a paragraph about somebody else.
     *
     * The entity is read off the act whether or not it has a name, and the links only where it has one, as the desktop's
     * `EncyclopediaLookup.ArticlesForAsync` does. Partial when any answer asked was.
     */
    suspend fun artist(name: String, records: List<AlbumSubject>, languages: List<String>): DossierAnswer {
        if (name.isBlank()) return DossierAnswer(Dossier.NOTHING, partial = false)
        var partial = false
        for (record in records.filter { !it.blank }.take(RECORDS_TRIED)) {
            val (papers, part) = papers(record.artist, record.title, record.tracks)
            partial = partial || part
            val act = recordsAct(papers) ?: continue
            val links = if (act.name.isNullOrBlank()) emptyList() else act.links.orEmpty()
            return DossierAnswer(Dossier(article(act.wikidataId, links, languages), genres = emptyList()), partial)
        }
        return DossierAnswer(Dossier.NOTHING, partial)
    }

    /** The act [papers]' record names, by the id it carries: the act half of the answer where that is the same act,
     * else the act asked for by the id (`metadata/artist/{mbid}`). Null where the record names no act or the catalogue
     * does not know the id. */
    private suspend fun recordsAct(papers: CatalogPapers): CatalogArtist? {
        val id = papers.album?.artistId?.takeIf { it.isNotBlank() } ?: return null
        val act = papers.artist
        if (act != null && act.id.equals(id, ignoreCase = true)) return act
        return actById(id)
    }

    /** `metadata/artist/{mbid}`: the act by its MusicBrainz id. Null for an id the catalogue does not know (404). */
    private suspend fun actById(mbid: String): CatalogArtist? {
        val url = (Hosts.API + "metadata/artist").toHttpUrl().newBuilder().addPathSegment(mbid).build()
        val request = Request.Builder().url(url).header(AppKey.HEADER, AppKey.VALUE).build()
        return http.newCall(request).await().use { response ->
            if (response.code == 404) return@use null
            if (response.code !in 200..299) throw ApiException.Status(response.code)
            WireJson.decodeFromString(CatalogArtist.serializer(), response.body.string())
        }
    }

    /** What is said about a genre: the opening of the English Wikipedia article [GenreArticles] names for it, as
     * the desktop's genre page reads it (`EncyclopediaLookup.GenreArticleAsync`, English only). Nothing for a
     * genre the table does not name. */
    suspend fun genre(genre: String): DossierAnswer {
        val title = GenreArticles.title(genre) ?: return DossierAnswer(Dossier.NOTHING, partial = false)
        return DossierAnswer(Dossier(extract(WikiPage("en", title)), genres = emptyList()), partial = false)
    }

    /** The catalogue half: `metadata/papers` for an act, or for one of their records. */
    private suspend fun papers(artist: String, album: String?, tracks: Int): Pair<CatalogPapers, Boolean> {
        val url = (Hosts.API + "metadata/papers").toHttpUrl().newBuilder()
            .addQueryParameter("artist", artist)
            .apply { if (!album.isNullOrBlank()) addQueryParameter("album", album) }
            .apply { if (tracks > 0) addQueryParameter("tracks", tracks.toString()) }
            .build()
        val request = Request.Builder().url(url).header(AppKey.HEADER, AppKey.VALUE).build()
        return http.newCall(request).await().use { response ->
            if (response.code !in 200..299) throw ApiException.Status(response.code)
            WireJson.decodeFromString(CatalogPapers.serializer(), response.body.string()) to (response.header(PARTIAL) != null)
        }
    }

    /** [entity] where the catalogue named one, else the first Wikidata link, is looked up once. Where it has no
     * page in any of [languages], or there is neither, the first Wikipedia link is read instead. */
    private suspend fun article(entity: String?, links: List<CatalogLink>, languages: List<String>): EncyclopediaArticle? {
        val named = entity?.takeIf { it.isNotBlank() } ?: Encyclopedia.firstLink(links, "wikidata.org")?.let(Encyclopedia::wikidataEntity)
        if (named != null) {
            val url = WIKIDATA.toHttpUrl().newBuilder()
                .addQueryParameter("action", "wbgetentities")
                .addQueryParameter("format", "json")
                .addQueryParameter("props", "sitelinks")
                .addQueryParameter("ids", named)
                .build()
            Encyclopedia.sitelink(ask(url.toString()), languages)?.let { return extract(it) }
        }
        val link = Encyclopedia.firstLink(links, "wikipedia.org")?.let(Encyclopedia::wikipediaArticle) ?: return null
        return extract(link)
    }

    /** One article's lead section. `formatversion=1` keys the pages by id, the shape [Encyclopedia.extract]
     * reads; `redirects=1` so a title Wikidata still spells the old way lands on the renamed article. */
    private suspend fun extract(page: WikiPage): EncyclopediaArticle? {
        val url = "https://${page.language}.wikipedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "1")
            .addQueryParameter("prop", "extracts|info|pageprops")
            .addQueryParameter("inprop", "url")
            .addQueryParameter("ppprop", "disambiguation")
            .addQueryParameter("exintro", "1")
            .addQueryParameter("explaintext", "1")
            .addQueryParameter("redirects", "1")
            .addQueryParameter("titles", page.title)
            .build()
        return Encyclopedia.extract(ask(url.toString()), page.language)
    }

    private suspend fun ask(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        http.newCall(request).await().use { response ->
            if (response.code !in 200..299) throw ApiException.Status(response.code)
            return response.body.string()
        }
    }

    companion object {
        /** The header the RPM API sets on an answer that is only part of one. */
        const val PARTIAL = "X-RPM-Partial"

        /** How many of an act's records are asked about before the act is taken to be in no catalogue. A cold question
         * can take the server several seconds, and they are asked one after another. */
        const val RECORDS_TRIED = 3
        private const val WIKIDATA = "https://www.wikidata.org/w/api.php"
        /** Wikipedia asks for one that names the app and a way to reach it. The desktop's and the phone's. */
        private const val USER_AGENT = "rpm/1.0 ( https://rpmrpmrpm.com )"
    }
}

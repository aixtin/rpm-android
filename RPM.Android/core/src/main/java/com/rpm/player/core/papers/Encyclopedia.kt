package com.rpm.player.core.papers

import com.rpm.player.core.net.escapeSegment
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI

/** A Wikipedia lead section in plain text, with the page it was read from. It matches the desktop's and
 * the iOS app's `EncyclopediaArticle`. */
@Serializable
data class EncyclopediaArticle(
    /** The wiki it came from: "en", "nl". */
    val language: String,
    val title: String,
    /** Plain text, paragraphs separated by a blank line. */
    val extract: String,
    /** The article on Wikipedia. */
    val url: String,
) {
    /** The first paragraph, which the record page shows (the desktop's `EncyclopediaArticle.Lead`). */
    val lead: String get() = extract.substringBefore("\n\n")
}

/** A Wikipedia article by its wiki and its title. */
data class WikiPage(val language: String, val title: String)

/**
 * Reading Wikidata and Wikipedia: from an entity id or a link to a paragraph of text. It is a port of
 * the iOS app's `Encyclopedia`, which ports `WikipediaJson` and the link readers in `ReferenceLinks`
 * (RPM.Core/Metadata/Providers). The rules are the desktop's. Keep them the same, so every app shows
 * the same article for the same record.
 */
object Encyclopedia {
    private val json = Json { ignoreUnknownKeys = true }

    /** Which Wikipedias to read, best first: the reader's own language, then English. English is
     * where the article usually is, so it is a fallback and not a preference. [preferred] is the
     * device's language tag. The desktop likewise uses only its UI culture. */
    fun languages(preferred: String?): List<String> {
        val own = preferred?.let(::baseLanguage).orEmpty()
        return if (own.isEmpty() || own == "en") listOf("en") else listOf(own, "en")
    }

    /** "zh-Hans" to "zh", "nl_NL" to "nl". Lower-cased, because Wikidata's site keys are. */
    fun baseLanguage(language: String): String {
        val code = language.trim()
        if (code.isEmpty()) return ""
        return (code.split('-', '_').firstOrNull { it.isNotEmpty() } ?: code).lowercase()
    }

    // ── links ──

    /** The Wikidata entity a link points at ("Q170572"), or null for any other link. */
    fun wikidataEntity(url: String): String? {
        val parsed = parse(url) ?: return null
        val host = parsed.host?.lowercase() ?: return null
        if (!host.endsWith("wikidata.org")) return null
        val last = parsed.path.orEmpty().split('/').lastOrNull { it.isNotEmpty() }.orEmpty()
        if (last.length <= 1 || (last[0] != 'Q' && last[0] != 'q')) return null
        val digits = last.substring(1)
        if (!digits.all { it in '0'..'9' }) return null
        return "Q$digits"
    }

    /** The wiki and title of a Wikipedia article link, or null for any other link. */
    fun wikipediaArticle(url: String): WikiPage? {
        val parsed = parse(url) ?: return null
        val host = parsed.host?.lowercase() ?: return null
        if (!host.endsWith("wikipedia.org")) return null
        val parts = host.split('.')
        val language = if (parts.size >= 3) parts[0] else "en"
        // `path` is already percent-decoded.
        val path = parsed.path.orEmpty()
        if (!path.startsWith(WIKI) || path.length <= WIKI.length) return null
        val title = path.substring(WIKI.length).replace('_', ' ')
        return if (title.isBlank()) null else WikiPage(language, title)
    }

    /** The first link on a host that ends in [suffix]. This is what the desktop's `ReferenceLinks.Merge`
     * keeps: one link per site, the first one listed. */
    fun firstLink(links: List<CatalogLink>, suffix: String): String? =
        links.firstOrNull { parse(it.url)?.host?.lowercase()?.endsWith(suffix) == true }?.url

    // ── Wikidata ──

    /**
     * Which Wikipedia has an article for the entity, in the order of [languages], and what it is called
     * there. Wikidata keys its sitelinks "enwiki", "nlwiki": the language code with "wiki" on the end.
     * Sister projects ("enwikiquote", "commonswiki") are in the same object and are not Wikipedia, so
     * only an exact "{language}wiki" counts.
     */
    fun sitelink(body: String, languages: List<String>): WikiPage? {
        val entities = (read(body) as? JsonObject)?.obj("entities") ?: return null
        for (entity in entities.values) {
            val sitelinks = (entity as? JsonObject)?.obj("sitelinks") ?: continue
            for (language in languages) {
                val code = baseLanguage(language)
                if (code.isEmpty()) continue
                val title = sitelinks.obj(code + "wiki")?.text("title")
                if (!title.isNullOrEmpty()) return WikiPage(code, title)
            }
        }
        return null
    }

    // ── Wikipedia ──

    /**
     * The lead section of an article, as the MediaWiki action API returns it with
     * `prop=extracts&exintro&explaintext` and `formatversion=1`.
     *
     * Null for a page that does not exist (the API marks it "missing" rather than failing) and for a
     * disambiguation page, which is a list of other articles and reads as nonsense under an album's name.
     */
    fun extract(body: String, language: String): EncyclopediaArticle? {
        val pages = (read(body) as? JsonObject)?.obj("query")?.obj("pages") ?: return null
        for (element in pages.values) {
            val page = element as? JsonObject ?: continue
            if ("missing" in page) continue
            val text = page.text("extract")
            if (text.isNullOrBlank()) continue
            if (page.obj("pageprops")?.containsKey("disambiguation") == true) continue
            val title = page.text("title").orEmpty()
            val url = page.text("fullurl")?.takeIf { it.isNotEmpty() }
                ?: ("https://$language.wikipedia.org/wiki/" + escapeSegment(title.replace(' ', '_')))
            return EncyclopediaArticle(language, title, tidy(text), url)
        }
        return null
    }

    /** The API separates paragraphs with one newline and leaves blank lines where its section headings
     * were. Paragraphs are joined with a blank line and the stray whitespace goes. */
    fun tidy(extract: String): String =
        extract.replace("\r\n", "\n")
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")

    private const val WIKI = "/wiki/"

    private fun parse(url: String): URI? = try {
        URI(url.trim())
    } catch (_: Exception) {
        null
    }

    private fun read(body: String): JsonElement? = try {
        json.parseToJsonElement(body)
    } catch (_: Exception) {
        null
    }

    private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

    private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
}

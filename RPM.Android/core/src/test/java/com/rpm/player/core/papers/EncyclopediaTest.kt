package com.rpm.player.core.papers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EncyclopediaTest {

    @Test fun theReadersLanguageComesFirstAndEnglishAfterIt() {
        assertEquals(listOf("nl", "en"), Encyclopedia.languages("nl-NL"))
        assertEquals(listOf("zh", "en"), Encyclopedia.languages("zh-Hans-CN"))
        assertEquals(listOf("en"), Encyclopedia.languages("en-US"))
        assertEquals(listOf("en"), Encyclopedia.languages(""))
        assertEquals(listOf("en"), Encyclopedia.languages(null))
    }

    @Test fun aWikidataLinkNamesItsEntity() {
        assertEquals("Q170572", Encyclopedia.wikidataEntity("https://www.wikidata.org/wiki/Q170572"))
        assertEquals("Q42", Encyclopedia.wikidataEntity("https://wikidata.org/wiki/q42"))
        assertNull(Encyclopedia.wikidataEntity("https://www.wikidata.org/wiki/Property:P31"))
        assertNull(Encyclopedia.wikidataEntity("https://en.wikipedia.org/wiki/Q42"))
        assertNull(Encyclopedia.wikidataEntity("not a link"))
    }

    @Test fun aWikipediaLinkNamesItsWikiAndTitle() {
        assertEquals(WikiPage("en", "Rumours (album)"), Encyclopedia.wikipediaArticle("https://en.wikipedia.org/wiki/Rumours_(album)"))
        assertEquals(WikiPage("de", "Björk"), Encyclopedia.wikipediaArticle("https://de.wikipedia.org/wiki/Bj%C3%B6rk"))
        assertEquals(WikiPage("en", "Abbey Road"), Encyclopedia.wikipediaArticle("https://wikipedia.org/wiki/Abbey_Road"))
        assertNull(Encyclopedia.wikipediaArticle("https://en.wikipedia.org/wiki/"))
        assertNull(Encyclopedia.wikipediaArticle("https://en.wikipedia.org/w/index.php?title=X"))
        assertNull(Encyclopedia.wikipediaArticle("https://www.discogs.com/master/123"))
    }

    @Test fun theFirstLinkOnASiteIsTheOneUsed() {
        val links = listOf(
            CatalogLink("https://www.discogs.com/master/1"),
            CatalogLink("https://www.wikidata.org/wiki/Q1"),
            CatalogLink("https://www.wikidata.org/wiki/Q2"),
        )
        assertEquals("https://www.wikidata.org/wiki/Q1", Encyclopedia.firstLink(links, "wikidata.org"))
        assertNull(Encyclopedia.firstLink(links, "wikipedia.org"))
    }

    @Test fun aSitelinkIsTakenInTheReadersOrderAndOnlyFromWikipedia() {
        val body = """{"entities":{"Q1":{"sitelinks":{"enwikiquote":{"title":"Quotes"},"enwiki":{"title":"Rumours (album)"},"nlwiki":{"title":"Rumours"}}}}}"""
        assertEquals(WikiPage("nl", "Rumours"), Encyclopedia.sitelink(body, listOf("nl", "en")))
        assertEquals(WikiPage("en", "Rumours (album)"), Encyclopedia.sitelink(body, listOf("fr", "en")))
        assertNull(Encyclopedia.sitelink("""{"entities":{"Q1":{"sitelinks":{"enwikiquote":{"title":"Quotes"}}}}}""", listOf("en")))
        assertNull(Encyclopedia.sitelink("not json", listOf("en")))
    }

    @Test fun anExtractIsReadTidied() {
        val body = """{"query":{"pages":{"123":{"title":"Rumours (album)","fullurl":"https://en.wikipedia.org/wiki/Rumours_(album)","extract":"First paragraph.\n\n\n  Second one.  \n"}}}}"""
        val article = Encyclopedia.extract(body, "en")!!
        assertEquals("First paragraph.\n\nSecond one.", article.extract)
        assertEquals("First paragraph.", article.lead)
        assertEquals("https://en.wikipedia.org/wiki/Rumours_(album)", article.url)
    }

    @Test fun aMissingPageOrADisambiguationIsNoArticle() {
        assertNull(Encyclopedia.extract("""{"query":{"pages":{"-1":{"title":"X","missing":""}}}}""", "en"))
        assertNull(Encyclopedia.extract("""{"query":{"pages":{"5":{"title":"X","extract":"X may refer to:","pageprops":{"disambiguation":""}}}}}""", "en"))
        assertNull(Encyclopedia.extract("""{"query":{"pages":{"5":{"title":"X","extract":"   "}}}}""", "en"))
    }

    @Test fun aPageWithNoAddressIsGivenOne() {
        val article = Encyclopedia.extract("""{"query":{"pages":{"5":{"title":"Abbey Road","extract":"Text."}}}}""", "en")!!
        assertEquals("https://en.wikipedia.org/wiki/Abbey_Road", article.url)
    }
}

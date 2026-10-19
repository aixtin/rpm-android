package com.rpm.player.core.radio

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

/** Which country the tuner asks for when it first opens: the listener's own (as on the TV), or every
 * country (as on the iPhone). Each app states this when it makes its services. */
enum class TunerOpensOn { ListenersCountry, EveryCountry }

/** How the tuner asks, filters and sorts (the desktop's RadioModel, as the tuner page uses it). */
data class TunerSettings(
    /** What is typed in the tuner's search field. */
    val text: String,
    /** The country asked for, or null for every country. */
    val country: String?,
    val sieve: StationSieve,
    val order: StationOrder,
    /** The saved stations alone, instead of the directory. */
    val favoritesOnly: Boolean,
)

/** What the directory answered for the tuner's last question. */
sealed interface TunerAnswer {
    data object Asking : TunerAnswer
    data class Listed(val stations: List<RadioStation>) : TunerAnswer
    /** Every mirror failed; saved stations still play. */
    data object DirectoryDown : TunerAnswer
}

/** The tuner's list as it is drawn, and why it may be empty. */
data class TunerList(val stations: List<RadioStation>, val empty: Empty?, val asking: Boolean, val directoryDown: Boolean) {
    enum class Empty { NoResults, NoFavorites, NoFilterMatch }
}

/**
 * The tuner (PegasusChrome.RadioTuner.cs with the desktop's RadioModel): what it asks Radio Browser,
 * what it answered, and the list drawn from that. Kept for the life of the app, so the tuner opens as
 * it was left. The directory is asked by name, genre and country, at most 120 stations. The format and
 * advertising filters and every order are applied on the TV.
 *
 * One question at a time: a new question cancels the one before it, so an older answer can never
 * replace a newer one. Typing waits 450 ms after the last key. Answers are kept for 30 minutes, keyed
 * by the question.
 */
class Tuner(
    private val browser: RadioBrowser,
    private val scope: CoroutineScope,
    listenerCountry: String?,
    /** The rpm API's rules. A genre question carries the stations the admin filed under that genre, so
     * a rules answer that changes them asks again ([Question.retagged]). */
    private val rules: StateFlow<StationRules>,
) {
    private val _settings = MutableStateFlow(
        TunerSettings(text = "", country = listenerCountry, sieve = StationSieve.OFF, order = StationOrder.Listed, favoritesOnly = false),
    )
    val settings: StateFlow<TunerSettings> = _settings.asStateFlow()

    private val _answer = MutableStateFlow<TunerAnswer>(TunerAnswer.Asking)
    val answer: StateFlow<TunerAnswer> = _answer.asStateFlow()

    private val _countries = MutableStateFlow<List<RadioCountry>>(emptyList())
    /** The countries the country menu offers: the 80 with most stations, by name. */
    val countries: StateFlow<List<RadioCountry>> = _countries.asStateFlow()

    /** The wheel's entry last picked; the wheel opens on it. Read and written on the main thread. */
    var wheelPicked = 0

    /** The station last tuned from the tuner. Its play key tunes it again, and its player shows it while
     * nothing plays. Read and written on the main thread. */
    var lastTuned: RadioStation? = null

    private var asking: Job? = null
    private val kept = HashMap<Question, Pair<Long, List<RadioStation>>>()

    fun update(change: (TunerSettings) -> TunerSettings) = _settings.update(change)

    /** Starts asking the directory, once; later calls do nothing. */
    fun ensureLoaded() {
        if (asking != null) return
        asking = scope.launch {
            var last: Question? = null
            combine(_settings, rules) { s, r ->
                Question(s.text.trim(), RadioGenres.term(s.sieve.genre), s.country, s.sieve.genre?.let(r::retaggedInto).orEmpty())
            }
                .distinctUntilChanged()
                .collectLatest { question ->
                    val typed = last != null && last?.copy(text = question.text) == question && last?.text != question.text
                    last = question
                    if (typed) delay(TYPING_PAUSE_MS)
                    _answer.value = ask(question)
                }
        }
        scope.launch {
            if (_countries.value.isNotEmpty()) return@launch
            try {
                _countries.value = forPicker(withContext(Dispatchers.IO) { browser.countries() })
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The country menu then offers all countries and the listener's own.
            }
        }
    }

    private suspend fun ask(question: Question): TunerAnswer {
        val now = System.currentTimeMillis()
        kept[question]?.let { (at, stations) -> if (now - at < KEEP_MS) return TunerAnswer.Listed(stations) }
        _answer.value = TunerAnswer.Asking
        return try {
            val found = withContext(Dispatchers.IO) {
                val query = RadioQuery(name = question.text.ifEmpty { null }, tag = question.tag, countryCode = question.country, limit = LIMIT)
                var listed = browser.search(query)
                if (question.retagged.isNotEmpty()) {
                    // Carrying their edits, so a searched text can match the admin's title.
                    val retagged = retaggedOrNone(question.retagged).map(rules.value::edited)
                    listed = RadioResults.withRetagged(listed, retagged, query)
                }
                RadioResults.onePerStation(listed)
            }
            kept[question] = now to found
            TunerAnswer.Listed(found)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            TunerAnswer.DirectoryDown
        }
    }

    /** The retagged stations from the directory, or none where it did not answer. The genre's own answer
     * is still worth listing without them. */
    private suspend fun retaggedOrNone(uuids: List<String>): List<RadioStation> = try {
        browser.byUuid(uuids)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    /** One question to the directory. [retagged] is part of it, so the answers kept for 30 minutes are
     * kept per set of stations the admin filed under the genre, and a change to that set asks again. */
    private data class Question(val text: String, val tag: String?, val country: String?, val retagged: List<String>)

    companion object {
        private const val TYPING_PAUSE_MS = 450L
        private const val KEEP_MS = 30 * 60_000L
        private const val LIMIT = 120
        private const val OFFERED = 80

        /**
         * The list drawn from an answer (RadioModel.Stations): the directory's stations, or the saved
         * ones, less those the rpm API hides, then through the filters, in the chosen order, with full
         * names where two would read the same.
         */
        fun listOf(settings: TunerSettings, answer: TunerAnswer, favorites: List<RadioStation>, rules: StationRules): TunerList {
            val listed = when {
                settings.favoritesOnly -> favorites
                answer is TunerAnswer.Listed -> answer.stations
                else -> emptyList()
            }
            // Hidden ones are removed first, then each station gets its edit, so the genre filter reads the
            // admin's tags and the order and the names read the admin's title.
            val shown = listed.filterNot(rules::hides).map(rules::edited)
            val kept = settings.sieve.through(shown)
            val stations = RadioNames.distinguish(settings.order.sort(kept))
            val empty = when {
                stations.isNotEmpty() -> null
                settings.favoritesOnly && favorites.isEmpty() -> TunerList.Empty.NoFavorites
                shown.isNotEmpty() -> TunerList.Empty.NoFilterMatch
                answer is TunerAnswer.Listed || settings.favoritesOnly -> TunerList.Empty.NoResults
                else -> null
            }
            return TunerList(
                stations = stations,
                empty = empty,
                asking = !settings.favoritesOnly && answer is TunerAnswer.Asking,
                directoryDown = answer is TunerAnswer.DirectoryDown,
            )
        }

        /** The 80 countries with most stations, one row per code, by their name in the TV's language. */
        fun forPicker(countries: List<RadioCountry>): List<RadioCountry> {
            val locale = Locale.getDefault()
            val names = Collator.getInstance(locale)
            return countries.groupBy { it.code }
                .map { (code, same) -> RadioCountry(countryName(code, locale) ?: same.maxBy { it.stationCount }.name, code, same.sumOf { it.stationCount }) }
                .sortedWith(compareByDescending<RadioCountry> { it.stationCount }.thenBy { it.code })
                .take(OFFERED)
                .sortedWith(Comparator<RadioCountry> { a, b -> names.compare(a.name, b.name) }.thenBy { it.code })
        }
    }
}

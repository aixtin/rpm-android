package com.rpm.player.core.radio

import java.net.URI

/**
 * One row per station. Radio Browser lists many stations several times (one entry per stream, per
 * bitrate, per person who added it). Same rules as `RadioResults.OnePerStation` in RPM.Core: entries are
 * joined when they share a folded name in the same country, or the same stream address in the same
 * country. The group keeps its first position, the reachable entry with the highest bitrate supplies
 * the stream, and the other entries' uuids go into [RadioStation.alsoFiledAs] (the hidden rule needs them).
 *
 * The desktop also ranks the group's logos by `RadioLogo.Rank`. Here the kept entry's logo comes
 * first and the others follow in the order found. The rpm API's own logo is asked for first anyway
 * ([StationLogo]), so this order only matters for a station the API has no logo for.
 */
object RadioResults {

    /**
     * A genre search's answer with the stations the admin filed under that genre added
     * (`RadioResults.WithRetagged` in RPM.Core). [retagged] were fetched by uuid. Each is added only where
     * the rest of [query] allows it: the same country, and a name holding the searched text (the
     * directory's name, or the admin's title where [RadioStation.edit] has one). A station already in the
     * answer is not added twice. The result is merged by play count, most first, as the directory
     * answers, so the fold that follows keeps its usual positions.
     */
    fun withRetagged(answer: List<RadioStation>, retagged: List<RadioStation>, query: RadioQuery): List<RadioStation> {
        val present = answer.mapTo(HashSet()) { it.uuid.lowercase() }
        val text = query.name?.trim()?.takeIf { it.isNotEmpty() }
        val country = query.countryCode?.trim()?.takeIf { it.isNotEmpty() }
        val added = retagged
            .filter { present.add(it.uuid.lowercase()) }
            .filter { country == null || it.countryCode.equals(country, ignoreCase = true) }
            .filter { text == null || it.name.contains(text, ignoreCase = true) || it.edit?.name?.contains(text, ignoreCase = true) == true }
        if (added.isEmpty()) return answer

        val queue = ArrayDeque(added.sortedByDescending { it.clickCount })
        val merged = ArrayList<RadioStation>(answer.size + added.size)
        for (row in answer) {
            while (queue.isNotEmpty() && queue.first().clickCount > row.clickCount) merged.add(queue.removeFirst())
            merged.add(row)
        }
        merged.addAll(queue)
        return merged
    }

    fun onePerStation(stations: List<RadioStation>): List<RadioStation> {
        if (stations.size < 2) return stations
        val parent = IntArray(stations.size) { it }

        fun find(row: Int): Int {
            var r = row
            while (parent[r] != r) {
                parent[r] = parent[parent[r]]
                r = parent[r]
            }
            return r
        }

        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra == rb) return
            if (ra < rb) parent[rb] = ra else parent[ra] = rb
        }

        val byName = HashMap<String, Int>()
        val byStream = HashMap<String, Int>()
        fun join(index: HashMap<String, Int>, key: String, row: Int) {
            if (key.isEmpty()) return
            val first = index[key]
            if (first != null) union(first, row) else index[key] = row
        }
        for (i in stations.indices) {
            join(byName, nameKeyOf(stations[i]), i)
            join(byStream, streamKeyOf(stations[i]), i)
        }

        val at = HashMap<Int, Int>()
        val kept = ArrayList<RadioStation>(stations.size)
        for (i in stations.indices) {
            val group = find(i)
            val seen = at[group]
            if (seen != null) {
                kept[seen] = better(kept[seen], stations[i])
                continue
            }
            at[group] = kept.size
            kept.add(stations[i])
        }
        return kept
    }

    private fun better(kept: RadioStation, other: RadioStation): RadioStation {
        val winner = if (rank(other) > rank(kept)) other else kept
        val filed = ArrayList<String>(4)
        for (source in listOf(kept, other)) {
            for (id in source.alsoFiledAs + source.uuid) {
                if (!id.equals(winner.uuid, ignoreCase = true) && filed.none { it.equals(id, ignoreCase = true) }) filed.add(id)
            }
        }
        val logos = ArrayList<String>(4)
        for (source in listOf(winner, kept, other)) {
            for (url in listOfNotNull(source.favicon) + source.otherLogos) {
                if (drawable(url) && logos.none { it.equals(url, ignoreCase = true) }) logos.add(url)
            }
        }
        return winner.copy(
            favicon = logos.firstOrNull() ?: winner.favicon,
            otherLogos = logos.drop(1),
            alsoFiledAs = filed,
        )
    }

    private fun drawable(url: String?): Boolean =
        url != null && (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true))

    private fun rank(station: RadioStation): Long =
        (if (station.reachable) 1_000_000L else 0L) + (if (station.bitrateKbps > 0) 1_000L + station.bitrateKbps else 0L)

    private fun nameKeyOf(station: RadioStation): String {
        val name = RadioNames.fold(station.name, station.countryCode)
        return if (name.isEmpty()) "" else "$name|${country(station)}"
    }

    private fun streamKeyOf(station: RadioStation): String {
        val url = try {
            URI(station.streamUrl.trim())
        } catch (_: Exception) {
            return ""
        }
        var host = url.host?.lowercase() ?: return ""
        if (host.isEmpty()) return ""
        if (host.startsWith("www.")) host = host.substring(4)
        val default = when (url.scheme?.lowercase()) {
            "http" -> 80
            "https" -> 443
            else -> -1
        }
        val port = if (url.port == -1 || url.port == default) "" else ":${url.port}"
        val query = url.rawQuery?.let { "?$it" }.orEmpty()
        return "$host$port${url.rawPath.orEmpty().trimEnd('/')}$query|${country(station)}"
    }

    private fun country(station: RadioStation): String = station.countryCode?.trim()?.lowercase().orEmpty()
}

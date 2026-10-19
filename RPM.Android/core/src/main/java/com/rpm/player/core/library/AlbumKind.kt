package com.rpm.player.core.library

/**
 * What a record's own name says it is: live, a single, a soundtrack or a compilation. Each is a shelf on
 * an artist's page. The patterns must match the iOS app (`AlbumKind.swift`) and the desktop
 * (`RPM.Core/Library/AlbumKind.cs`). Asked of the name as written, never of a tidied one.
 */
object AlbumKind {
    fun isLive(title: String?): Boolean = matches(title, LIVE)
    fun isSingle(title: String?): Boolean = matches(title, SINGLE)
    fun isCompilation(title: String?): Boolean = matches(title, COMPILATION)
    fun isSoundtrack(title: String?): Boolean = matches(title, SOUNDTRACK)

    private fun matches(title: String?, pattern: Regex): Boolean = !title.isNullOrBlank() && pattern.containsMatchIn(title)

    private val LIVE = Regex("""\b(?:live|sessions|unplugged|storytellers)\b""", RegexOption.IGNORE_CASE)
    private val SINGLE = Regex("""[-\u2013\u2014]\s*singles?\b""", RegexOption.IGNORE_CASE)
    private val SOUNDTRACK = Regex(
        """\b(?:soundtracks?|(?:original|film|motion\s+picture)\s+scores?|motion\s+picture|music\s+from|cast\s+recording|ost)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val COMPILATION = Regex(
        """\b(?:greatest\s+hits|best\s+of|essentials?|collections?|anthology|anthologies|compilations?|retrospective|definitive|hits)\b""",
        RegexOption.IGNORE_CASE,
    )
}

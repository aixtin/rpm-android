package com.rpm.player.core.library

/**
 * Whether two names are the same name, allowing for different ways of writing it. The rule matches the
 * iOS app (`NameMatch.swift`) and the desktop (`RPM.Fingerprint/Catalog/NameMatch.cs`): case, spacing,
 * punctuation and the word "and" are removed before two names are compared, so "Earth Wind and Fire" is
 * "Earth, Wind & Fire".
 */
object NameMatch {
    /** Whether these are the same name. False when either is missing, and when both reduce to nothing. */
    fun same(one: String?, other: String?): Boolean {
        if (one.isNullOrEmpty() || other.isNullOrEmpty()) return false
        val squashed = squash(one)
        return squashed.isNotEmpty() && squashed == squash(other)
    }

    /** A name with case, spacing, punctuation and the word "and" taken out. Letters and digits are read by
     * code point, so a letter outside the basic plane counts as one letter. */
    fun squash(name: String?): String {
        if (name.isNullOrEmpty()) return ""
        val squashed = StringBuilder()
        val word = StringBuilder()
        fun close() {
            if (word.isNotEmpty() && word.toString() != "and") squashed.append(word)
            word.setLength(0)
        }
        var at = 0
        while (at < name.length) {
            val point = name.codePointAt(at)
            if (Character.isLetterOrDigit(point)) word.append(String(Character.toChars(point)).lowercase()) else close()
            at += Character.charCount(point)
        }
        close()
        return squashed.toString()
    }
}

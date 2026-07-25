package de.danoeh.antennapod.model.feed

import java.io.Serializable
import java.util.Locale
import java.util.regex.Pattern

class FeedFilter(
    private val includeFilter: String?,
    private val excludeFilter: String?,
    private val minimalDuration: Int
) : Serializable {

    constructor() : this("", "", -1)

    constructor(includeFilter: String?, excludeFilter: String?) : this(includeFilter, excludeFilter, -1)

    /**
     * Parses the text in to a list of single words or quoted strings.
     * Example: "One "Two Three"" returns ["One", "Two Three"]
     * @param filter string to parse in to terms
     * @return list of terms
     */
    private fun parseTerms(filter: String): List<String> {
        // from http://stackoverflow.com/questions/7804335/split-string-on-spaces-in-java-except-if-between-quotes-i-e-treat-hello-wor
        val list = ArrayList<String>()
        val m = Pattern.compile("([^\"]\\S*|\".+?\")\\s*").matcher(filter)
        while (m.find()) {
            list.add(m.group(1).replace("\"", ""))
        }
        return list
    }

    /**
     * @param item
     * @return true if the item should be downloaded
     */
    fun shouldAutoDownload(item: FeedItem): Boolean {
        val includeTerms = parseTerms(includeFilter!!)
        val excludeTerms = parseTerms(excludeFilter!!)

        if (includeTerms.isEmpty() && excludeTerms.isEmpty() && minimalDuration <= -1) {
            // nothing has been specified, so include everything
            return true
        }

        // Check if the episode is long enough if minimal duration filter is on
        if (hasMinimalDurationFilter() && item.media != null) {
            val durationInMs = item.media!!.duration
            // Minimal Duration is stored in seconds
            if (durationInMs > 0 && durationInMs / 1000 < minimalDuration) {
                return false
            }
        }

        // check using lowercase so the users don't have to worry about case.
        val title = item.title?.lowercase(Locale.getDefault()) ?: ""

        // if it's explicitly excluded, it shouldn't be autodownloaded
        // even if it has include terms
        for (term in excludeTerms) {
            if (title.contains(term.trim().lowercase(Locale.getDefault()))) {
                return false
            }
        }

        for (term in includeTerms) {
            if (title.contains(term.trim().lowercase(Locale.getDefault()))) {
                return true
            }
        }

        // now's the tricky bit
        // if they haven't set an include filter, but they have set an exclude filter
        // default to including, but if they've set both, then exclude
        if (!hasIncludeFilter() && hasExcludeFilter()) {
            return true
        }

        // if they only set minimal duration filter and arrived here, autodownload
        // should happen
        if (hasMinimalDurationFilter()) {
            return true
        }

        return false
    }

    fun getIncludeFilterRaw(): String? {
        return includeFilter
    }

    fun getExcludeFilterRaw(): String? {
        return excludeFilter
    }

    fun getIncludeFilter(): List<String> {
        return if (includeFilter == null) ArrayList() else parseTerms(includeFilter)
    }

    fun getExcludeFilter(): List<String> {
        return if (excludeFilter == null) ArrayList() else parseTerms(excludeFilter)
    }

    fun getMinimalDurationFilter(): Int {
        return minimalDuration
    }

    /**
     * @return true if only include is set
     */
    fun includeOnly(): Boolean {
        return hasIncludeFilter() && !hasExcludeFilter()
    }

    /**
     * @return true if only exclude is set
     */
    fun excludeOnly(): Boolean {
        return hasExcludeFilter() && !hasIncludeFilter()
    }

    fun hasIncludeFilter(): Boolean {
        return includeFilter!!.length > 0
    }

    fun hasExcludeFilter(): Boolean {
        return excludeFilter!!.length > 0
    }

    fun hasMinimalDurationFilter(): Boolean {
        return minimalDuration > -1
    }
}

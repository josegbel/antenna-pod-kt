package de.danoeh.antennapod.model.feed

import java.io.Serializable
import java.util.ArrayList
import java.util.regex.Pattern
import org.apache.commons.lang3.StringUtils

class FeedFunding(url: String?, content: String?) : Serializable {

    @JvmField var url: String? = url

    @JvmField var content: String? = content

    fun setUrl(url: String?) {
        this.url = url
    }

    fun setContent(content: String?) {
        this.content = content
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other.javaClass != this.javaClass) {
            return false
        }

        val funding = other as FeedFunding
        if (url == null && funding.url == null && content == null && funding.content == null) {
            return true
        }
        if (url != null && url == funding.url && content != null && content == funding.content) {
            return true
        }
        return false
    }

    override fun hashCode(): Int {
        return (url + FUNDING_TITLE_SEPARATOR + content).hashCode()
    }

    companion object {
        private const val serialVersionUID = 1L

        const val FUNDING_ENTRIES_SEPARATOR = "\u001e"
        const val FUNDING_TITLE_SEPARATOR = "\u001f"

        @JvmStatic
        fun extractPaymentLinks(payLinks: String?): ArrayList<FeedFunding>? {
            if (StringUtils.isBlank(payLinks)) {
                return null
            }
            // old format before we started with PodcastIndex funding tag
            val funding = ArrayList<FeedFunding>()
            if (!payLinks!!.contains(FUNDING_ENTRIES_SEPARATOR) && !payLinks.contains(FUNDING_TITLE_SEPARATOR)) {
                funding.add(FeedFunding(payLinks, ""))
                return funding
            }
            // Do not simplify to Kotlin's `.split(Regex)` / `CharSequence.split(vararg String)` — they do NOT
            // strip trailing empty tokens the way Java's `String.split(String)` does, which would silently
            // reintroduce the bug flagged by legacy-android-red-team as a loop-1 CRITICAL finding (plan review,
            // tasks/antennapod-model-kotlin-milestone-4.md). `Pattern.compile(regex).split(input)` (implicit
            // limit 0) is byte-for-byte identical to Java's `String.split(String)` by JDK contract, and no
            // existing test can catch a silent revert here for these whitespace separators (loop-2 MAJOR finding)
            // — this comment is the regression guard.
            val list = Pattern.compile(FUNDING_ENTRIES_SEPARATOR).split(payLinks)
            if (list.isEmpty()) {
                return null
            }

            for (str in list) {
                // Do not simplify to Kotlin's `.split(Regex)` / `CharSequence.split(vararg String)` — see the
                // "do not simplify" note above (loop-1 CRITICAL / loop-2 MAJOR findings, same regression risk).
                val linkContent = Pattern.compile(FUNDING_TITLE_SEPARATOR).split(str)
                if (StringUtils.isBlank(linkContent[0])) {
                    continue
                }
                val url = linkContent[0]
                var title = ""
                if (linkContent.size > 1 && !StringUtils.isBlank(linkContent[1])) {
                    title = linkContent[1]
                }
                funding.add(FeedFunding(url, title))
            }
            return funding
        }

        @JvmStatic
        fun getPaymentLinksAsString(fundingList: ArrayList<FeedFunding>?): String? {
            val result = StringBuilder()
            if (fundingList == null) {
                return null
            }
            for (fund in fundingList) {
                result.append(fund.url).append(FUNDING_TITLE_SEPARATOR).append(fund.content)
                result.append(FUNDING_ENTRIES_SEPARATOR)
            }
            return StringUtils.removeEnd(result.toString(), FUNDING_ENTRIES_SEPARATOR)
        }
    }
}

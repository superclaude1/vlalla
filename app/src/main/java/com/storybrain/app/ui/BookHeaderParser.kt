package com.storybrain.app.ui

data class BookHeader(val title: String, val author: String?)

object BookHeaderParser {
    private val authorPattern = Regex("""^(.*?)\s+作者\s*[：:]\s*(\S.*?)\s*$""")
    private val wrappedTitle = Regex("""^[《〈](.+)[》〉]$""")
    private val authorPrefix = Regex("""^[\[【](?:中|日|美|英|法|德|俄|意|韩|朝|加|澳|印|西|葡|奥|瑞|荷|比|波|捷|匈|希|土|巴|阿|埃|南非|新西兰)[\]】]\s*""")

    fun parse(rawTitle: String): BookHeader {
        val clean = rawTitle.trim()
        val match = authorPattern.matchEntire(clean)
        if (match == null) return BookHeader(clean, null)

        val explicitTitle = match.groupValues[1].trim()
        val explicitAuthor = match.groupValues[2].trim()
        if (explicitTitle.isBlank() || explicitAuthor.isBlank()) return BookHeader(clean, null)

        return BookHeader(
            title = wrappedTitle.matchEntire(explicitTitle)?.groupValues?.get(1)?.trim() ?: explicitTitle,
            author = explicitAuthor.replace(authorPrefix, "").trim().ifBlank { null }
        )
    }
}

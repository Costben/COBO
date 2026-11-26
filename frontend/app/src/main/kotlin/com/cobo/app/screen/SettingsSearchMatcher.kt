package com.cobo.app.screen

import android.icu.text.Transliterator
import android.os.Build
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.min

/**
 * 设置页本地多模态搜索引擎（零额外依赖）：
 * 支持原字子串、简繁归一、汉语全拼、拼音首字母、音节混拼（如 `shensm` / `cwzd` / `fudl`）、
 * 标题跳字子序列（如 `深模` / `清缓`）、轻度拼写纠错（Damerau-Levenshtein ≤ 1）与相关度打分。
 *
 * Android 10+（API 29+）优先走系统内置 ICU `Transliterator`（`Traditional-Simplified` / `Han-Latin`），
 * 并在低版本或本地 JVM 单测环境自动回退到内置常用设置字表与多音字覆盖表。
 */
internal object SettingsSearchMatcher {

    private val indexCache = ConcurrentHashMap<String, TextSearchIndex>()
    private val normalizedQueryCache = ConcurrentHashMap<String, String>()

    /**
     * 计算某条设置对多词查询的综合相关度分值；任一词未命中时返回 0。
     */
    fun scoreEntry(
        entry: SettingsEntry,
        groupTitle: String,
        terms: List<String>,
    ): Int {
        if (terms.isEmpty()) return 1
        val titleIdx = indexOf(entry.title)
        val summaryIdx = indexOf(entry.summary)
        val keywordIndices = keywordIndicesOf(entry.keywords)
        val groupIdx = indexOf(groupTitle)

        var total = 0
        for (rawTerm in terms) {
            val term = normalizeQuery(rawTerm)
            if (term.isEmpty()) continue
            val keywordBest = keywordIndices.maxOfOrNull { scoreField(term, it, FieldWeight.KEYWORD) } ?: 0
            val best = maxOf(
                scoreField(term, titleIdx, FieldWeight.TITLE),
                keywordBest,
                scoreField(term, summaryIdx, FieldWeight.SUMMARY),
                scoreField(term, groupIdx, FieldWeight.GROUP),
            )
            if (best <= 0) return 0
            total += best
        }
        return total
    }

    /**
     * 计算多词查询在原始文本 [text] 中命中的字符区间（闭区间，已排序并合并相邻/重叠区间），
     * 用于按 ColorOS 系统设置风格对命中的原文、拼音、首字母或跳字片段做强调色高亮。
     */
    fun findHighlightRanges(
        text: String,
        terms: List<String>,
    ): List<IntRange> {
        if (text.isEmpty() || terms.isEmpty()) return emptyList()
        val index = indexOf(text)
        if (index.rawNorm.isEmpty()) return emptyList()

        val rawRanges = ArrayList<IntRange>()
        for (rawTerm in terms) {
            val term = normalizeQuery(rawTerm)
            if (term.isEmpty()) continue
            collectHighlightRanges(term, index, text.length, rawRanges)
        }
        return mergeHighlightRanges(rawRanges, text.length)
    }

    private fun collectHighlightRanges(
        term: String,
        index: TextSearchIndex,
        rawLength: Int,
        out: MutableList<IntRange>,
    ) {
        // 1. 标准化原文直接命中（涵盖汉字子串、大小写忽略、简繁同义词归一）
        var searchFrom = 0
        var foundDirect = false
        while (searchFrom <= index.rawNorm.length - term.length) {
            val pos = index.rawNorm.indexOf(term, searchFrom)
            if (pos < 0) break
            foundDirect = true
            val start = index.rawNormMap[pos]
            val end = index.rawNormMap[pos + term.length - 1]
            if (start in 0 until rawLength && end in start until rawLength) {
                out.add(start..end)
            }
            searchFrom = pos + term.length
        }
        if (foundDirect) return

        // 2. 去标点/去空格原文子串命中（例如「我页」命中「「我」页图标样式」）
        val compactPos = index.compact.indexOf(term)
        if (compactPos >= 0) {
            for (offset in 0 until term.length) {
                val rawIdx = index.compactMap[compactPos + offset]
                if (rawIdx in 0 until rawLength) out.add(rawIdx..rawIdx)
            }
            return
        }

        val isAscii = term.all { it in 'a'..'z' || it in '0'..'9' }
        val isAlphaOnly = isAscii && term.all { it in 'a'..'z' }

        // 3. 拼音/英文音节对齐前缀命中（全拼、首字母、混拼，如 ssms / shensm / xsvip）
        if (isAscii && index.syllables.isNotEmpty()) {
            val syllableMatch = findSyllablePrefixMatch(term, index.syllables)
            if (syllableMatch != null) {
                for ((span, consumed) in syllableMatch) {
                    val start = span.rawStart
                    val end = if (span.isAscii) {
                        min(span.rawEnd, span.rawStart + consumed - 1)
                    } else {
                        span.rawEnd
                    }
                    if (start in 0 until rawLength && end in start until rawLength) {
                        out.add(start..end)
                    }
                }
                return
            }
            // 4. 拼音首字母跳字子序列（如 ycfl 命中 滑动自动隐藏浮动栏）
            if (isAlphaOnly && term.length >= 3) {
                val matchedSpans = findInitialsSubsequenceMatch(term, index.syllables)
                if (matchedSpans != null) {
                    for (span in matchedSpans) {
                        val end = if (span.isAscii) span.rawStart else span.rawEnd
                        if (span.rawStart in 0 until rawLength && end in span.rawStart until rawLength) {
                            out.add(span.rawStart..end)
                        }
                    }
                    return
                }
            }
        }

        // 5. 汉字跳字子序列（如「深模」命中「深色模式」）
        if (!isAscii && term.length >= 2) {
            val subseqIndices = findSubsequenceIndices(term, index.compact)
            if (subseqIndices != null) {
                for (ci in subseqIndices) {
                    val rawIdx = index.compactMap[ci]
                    if (rawIdx in 0 until rawLength) out.add(rawIdx..rawIdx)
                }
                return
            }
        }

        // 6. 轻度拼写容错（Damerau-Levenshtein ≤ 1）
        if (isAscii && term.length >= 4) {
            val matchedTokenSpan = index.syllables.firstOrNull { span ->
                span.isAscii &&
                    span.variants.any { it.length >= 4 && damerauLevenshteinAtMostOne(term, it) }
            }
            if (matchedTokenSpan != null) {
                out.add(matchedTokenSpan.rawStart..matchedTokenSpan.rawEnd)
                return
            }
            val pinyinWin = findSlidingTypoWindow(term, index.joinedPinyin)
            if (pinyinWin != null) {
                for (span in index.syllables) {
                    if (span.joinedEnd >= pinyinWin.first && span.joinedStart <= pinyinWin.last) {
                        out.add(span.rawStart..span.rawEnd)
                    }
                }
            }
        } else if (!isAscii && term.length >= 3 && index.compact.length >= 3) {
            val win = findSlidingTypoWindow(term, index.compact)
            if (win != null) {
                for (ci in win) {
                    val rawIdx = index.compactMap[ci]
                    if (rawIdx in 0 until rawLength) out.add(rawIdx..rawIdx)
                }
            }
        }
    }

    private fun mergeHighlightRanges(
        ranges: List<IntRange>,
        rawLength: Int,
    ): List<IntRange> {
        if (ranges.isEmpty() || rawLength <= 0) return emptyList()
        val sorted = ranges
            .mapNotNull { r ->
                val s = r.first.coerceIn(0, rawLength - 1)
                val e = r.last.coerceIn(0, rawLength - 1)
                if (s <= e) s..e else null
            }
            .sortedWith(compareBy<IntRange> { it.first }.thenBy { it.last })
        if (sorted.isEmpty()) return emptyList()
        val merged = ArrayList<IntRange>(sorted.size)
        var current = sorted[0]
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (next.first <= current.last + 1) {
                current = current.first..maxOf(current.last, next.last)
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        return merged
    }

    private enum class FieldWeight(
        val exact: Int,
        val substring: Int,
        val syllable: Int,
        val initialsSubseq: Int,
        val cjkSubseq: Int,
        val typo: Int,
        val allowShortPinyin: Boolean,
        val allowCjkSubseq: Boolean,
    ) {
        TITLE(120, 100, 86, 68, 62, 54, allowShortPinyin = true, allowCjkSubseq = true),
        KEYWORD(84, 72, 62, 46, 0, 44, allowShortPinyin = false, allowCjkSubseq = false),
        SUMMARY(64, 54, 48, 0, 0, 34, allowShortPinyin = false, allowCjkSubseq = false),
        GROUP(52, 44, 38, 30, 28, 24, allowShortPinyin = true, allowCjkSubseq = true),
    }

    private fun scoreField(
        term: String,
        index: TextSearchIndex,
        weight: FieldWeight,
    ): Int {
        if (index.rawNorm.isEmpty()) return 0

        // 1. 标准化原文 / 去标点原文精确或子串命中（同时覆盖简繁归一与中英混输）
        if (index.compact == term || index.rawNorm == term) return weight.exact
        if (index.rawNorm.contains(term) || index.compact.contains(term)) {
            return if (index.compact.startsWith(term)) weight.substring + 6 else weight.substring
        }

        val isAscii = term.all { it in 'a'..'z' || it in '0'..'9' }
        val isAlphaOnly = isAscii && term.all { it in 'a'..'z' }

        // 2. 英文/拼音音节前缀匹配：同时覆盖全拼（shense）、首字母（ssms）、混拼（shensm / cwzd）
        if (isAscii && index.syllables.isNotEmpty() && (term.length >= 2 || weight.allowShortPinyin)) {
            if (findSyllablePrefixMatch(term, index.syllables) != null) {
                return weight.syllable
            }
            // 3. 拼音首字母跳字子序列（如 ycfl 命中 滑动自动隐藏浮动栏），至少 3 字母以避免 2 字母跨词误召回
            if (isAlphaOnly && term.length >= 3 && weight.initialsSubseq > 0) {
                if (findInitialsSubsequenceMatch(term, index.syllables) != null) {
                    return weight.initialsSubseq
                }
            }
        }

        // 4. 汉字跳字子序列（如「深模」命中「深色模式」、「清缓」命中「清理缓存」）
        if (!isAscii && weight.allowCjkSubseq && term.length >= 2 && findSubsequenceIndices(term, index.compact) != null) {
            return weight.cjkSubseq
        }

        // 5. 轻度拼写容错（Damerau-Levenshtein 距离 ≤ 1：支持错字、漏字、多字、相邻字母打反）
        if (weight.typo > 0) {
            if (isAscii && term.length >= 4) {
                if (index.asciiTokens.any { token ->
                        token.length >= 4 && damerauLevenshteinAtMostOne(term, token)
                    }
                ) {
                    return weight.typo
                }
                if (index.joinedPinyin.length >= 4 && matchesPinyinWithTypo(term, index.joinedPinyin)) {
                    return weight.typo - 4
                }
            } else if (!isAscii && term.length >= 3 && index.compact.length >= 3) {
                if (findSlidingTypoWindow(term, index.compact) != null) {
                    return weight.typo
                }
            }
        }

        return 0
    }

    private val keywordIndicesCache = ConcurrentHashMap<String, List<TextSearchIndex>>()

    /** 将空格分隔的同义关键词拆分为独立词条分别建索引，避免跨同义词拼接造成误匹配（如「主题色 配色」误中 `sp`）。 */
    private fun keywordIndicesOf(keywords: String): List<TextSearchIndex> {
        if (keywords.isBlank()) return emptyList()
        return keywordIndicesCache.getOrPut(keywords) {
            keywords.trim()
                .split(WHITESPACE_REGEX)
                .filter { it.isNotEmpty() }
                .map { indexOf(it) }
        }
    }

    private val WHITESPACE_REGEX = Regex("\\s+")

    private fun indexOf(text: String): TextSearchIndex {
        if (text.isBlank()) return TextSearchIndex.EMPTY
        return indexCache.getOrPut(text) { buildIndex(text) }
    }

    private fun normalizeQuery(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        return normalizedQueryCache.getOrPut(trimmed) {
            val simplified = toSimplified(trimmed)
            normalizeBasic(simplified)
        }
    }

    private fun buildIndex(raw: String): TextSearchIndex {
        val simplified = toSimplified(raw)
        val rawNormSb = StringBuilder(simplified.length)
        val rawNormMapList = ArrayList<Int>(simplified.length)
        val compactSb = StringBuilder(simplified.length)
        val compactMapList = ArrayList<Int>(simplified.length)

        // 逐字符做 NFKC + 小写映射，保留标准化索引与原始字符索引的精确对应，便于结果高亮定位。
        val sameLength = simplified.length == raw.length
        for (i in simplified.indices) {
            val rawIdx = if (sameLength) i else i.coerceIn(0, (raw.length - 1).coerceAtLeast(0))
            val normPiece = normalizeBasic(simplified[i].toString())
            for (ch in normPiece) {
                rawNormSb.append(ch)
                rawNormMapList.add(rawIdx)
                if (ch.isLetterOrDigit() || isCjkHan(ch) || ch == '@' || ch == '+') {
                    compactSb.append(ch)
                    compactMapList.add(rawIdx)
                }
            }
        }

        val rawNorm = rawNormSb.toString()
        val compact = compactSb.toString()
        val syllables = extractSyllables(simplified, raw.length)
        val asciiTokens = LinkedHashSet<String>()
        ASCII_TOKEN_REGEX.findAll(rawNorm).forEach { asciiTokens.add(it.value) }
        val joinedPinyin = syllables.joinToString("") { it.primary }
        // 同步把每个连续中文词段对应的全拼也收入 token，便于对关键词里的长词做拼写纠错
        HAN_SEGMENT_REGEX.findAll(simplified).forEach { match ->
            val segSyllables = extractSyllables(match.value, match.value.length)
            val full = segSyllables.joinToString("") { it.primary }
            if (full.length >= 4) asciiTokens.add(full)
        }
        return TextSearchIndex(
            rawNorm = rawNorm,
            rawNormMap = rawNormMapList.toIntArray(),
            compact = compact,
            compactMap = compactMapList.toIntArray(),
            syllables = syllables,
            joinedPinyin = joinedPinyin,
            asciiTokens = asciiTokens.toList(),
        )
    }

    /**
     * 将文本拆分为「音节候选列表」序列：
     * - 每个汉字映射为 1~N 个无声调拼音候选（自动修正多音字，并将 `ü` 同时展开为 `v` 和 `u`）；
     * - 连续英文/数字段按单词拆成一个音节单元，便于中英混排（如 `vip`、`v标`、`pip`）无缝匹配。
     */
    private fun extractSyllables(text: String, rawLength: Int): List<SyllableSpan> {
        val result = ArrayList<SyllableSpan>(text.length)
        val sameLength = text.length == rawLength
        val maxRaw = (rawLength - 1).coerceAtLeast(0)
        var joinedCursor = 0
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                isCjkHan(ch) -> {
                    val candidates = hanToPinyinVariants(ch)
                    if (candidates.isNotEmpty()) {
                        val primary = candidates.first()
                        val rawPos = if (sameLength) i else i.coerceIn(0, maxRaw)
                        result.add(
                            SyllableSpan(
                                variants = candidates,
                                primary = primary,
                                rawStart = rawPos,
                                rawEnd = rawPos,
                                isAscii = false,
                                joinedStart = joinedCursor,
                                joinedEnd = joinedCursor + primary.length - 1,
                            ),
                        )
                        joinedCursor += primary.length
                    }
                    i++
                }
                ch.isLetterOrDigit() && ch.code < 128 -> {
                    val start = i
                    while (i < text.length && text[i].isLetterOrDigit() && text[i].code < 128) i++
                    val token = text.substring(start, i).lowercase(Locale.ROOT)
                    val rawStart = if (sameLength) start else start.coerceIn(0, maxRaw)
                    val rawEnd = if (sameLength) i - 1 else (i - 1).coerceIn(rawStart, maxRaw)
                    result.add(
                        SyllableSpan(
                            variants = listOf(token),
                            primary = token,
                            rawStart = rawStart,
                            rawEnd = rawEnd,
                            isAscii = true,
                            joinedStart = joinedCursor,
                            joinedEnd = joinedCursor + token.length - 1,
                        ),
                    )
                    joinedCursor += token.length
                }
                else -> i++
            }
        }
        return result
    }

    /**
     * 音节对齐前缀匹配：判断 [query] 是否可以从任意起始音节开始，连续按音节前缀匹配完成，
     * 并返回命中的 `(SyllableSpan, consumedChars)` 序列以便精准高亮对应汉字或英文前缀。
     */
    private fun findSyllablePrefixMatch(
        query: String,
        syllables: List<SyllableSpan>,
    ): List<Pair<SyllableSpan, Int>>? {
        val qLen = query.length
        val sLen = syllables.size
        for (start in 0 until sLen) {
            if (syllables[start].variants.none { it.isNotEmpty() && it[0] == query[0] }) continue
            val visited = BooleanArray((sLen + 1) * (qLen + 1))
            val path = ArrayList<Pair<SyllableSpan, Int>>()
            fun dfs(si: Int, qi: Int): Boolean {
                if (qi == qLen) return true
                if (si >= sLen) return false
                val state = si * (qLen + 1) + qi
                if (visited[state]) return false
                visited[state] = true
                val span = syllables[si]
                for (variant in span.variants) {
                    if (variant.isEmpty() || variant[0] != query[qi]) continue
                    var maxMatch = 0
                    val limit = min(variant.length, qLen - qi)
                    while (maxMatch < limit && variant[maxMatch] == query[qi + maxMatch]) {
                        maxMatch++
                    }
                    // 贪心优先尝试更长前缀，失败再回退到较短前缀（含仅取首字母）
                    for (take in maxMatch downTo 1) {
                        path.add(span to take)
                        if (dfs(si + 1, qi + take)) return true
                        path.removeAt(path.lastIndex)
                    }
                }
                return false
            }
            if (dfs(start, 0)) return path
        }
        return null
    }

    /** 首字母跳字子序列匹配：判断 [query] 是否按顺序命中各音节候选的首字母并返回命中的音节列表。 */
    private fun findInitialsSubsequenceMatch(
        query: String,
        syllables: List<SyllableSpan>,
    ): List<SyllableSpan>? {
        val matched = ArrayList<SyllableSpan>(query.length)
        var qi = 0
        for (span in syllables) {
            val target = query[qi]
            if (span.variants.any { it.isNotEmpty() && it[0] == target }) {
                matched.add(span)
                qi++
                if (qi == query.length) return matched
            }
        }
        return null
    }

    private fun findSubsequenceIndices(query: String, target: String): List<Int>? {
        if (query.length > target.length) return null
        val matched = ArrayList<Int>(query.length)
        var qi = 0
        for (i in target.indices) {
            if (target[i] == query[qi]) {
                matched.add(i)
                qi++
                if (qi == query.length) return matched
            }
        }
        return null
    }

    private fun matchesPinyinWithTypo(term: String, joinedPinyin: String): Boolean {
        if (abs(term.length - joinedPinyin.length) <= 1 && damerauLevenshteinAtMostOne(term, joinedPinyin)) {
            return true
        }
        return findSlidingTypoWindow(term, joinedPinyin) != null
    }

    private fun findSlidingTypoWindow(term: String, target: String): IntRange? {
        val tLen = term.length
        for (winLen in (tLen - 1)..(tLen + 1)) {
            if (winLen < 3 || winLen > target.length) continue
            for (start in 0..(target.length - winLen)) {
                if (damerauLevenshteinAtMostOne(term, target.substring(start, start + winLen))) {
                    return start until (start + winLen)
                }
            }
        }
        return null
    }

    /** O(N) 判断两串的 Damerau-Levenshtein 距离是否 ≤ 1（单次替换、插入、删除或相邻交换）。 */
    private fun damerauLevenshteinAtMostOne(a: String, b: String): Boolean {
        val lenA = a.length
        val lenB = b.length
        if (abs(lenA - lenB) > 1) return false
        if (a == b) return true
        var i = 0
        val minLen = min(lenA, lenB)
        while (i < minLen && a[i] == b[i]) i++
        if (lenA == lenB) {
            if (a.regionMatches(i + 1, b, i + 1, lenA - i - 1)) return true
            return i + 1 < lenA &&
                a[i] == b[i + 1] &&
                a[i + 1] == b[i] &&
                a.regionMatches(i + 2, b, i + 2, lenA - i - 2)
        }
        return if (lenA > lenB) {
            a.regionMatches(i + 1, b, i, lenB - i)
        } else {
            a.regionMatches(i, b, i + 1, lenA - i)
        }
    }

    private fun normalizeBasic(input: String): String {
        val nfkc = Normalizer.normalize(input, Normalizer.Form.NFKC)
        return nfkc.lowercase(Locale.ROOT)
    }

    private fun isCjkHan(ch: Char): Boolean = ch in '\u4E00'..'\u9FFF'

    private fun toSimplified(text: String): String {
        val icuResult = IcuBridge.toSimplified(text)
        return applyTraditionalFallbacks(icuResult)
    }

    private fun applyTraditionalFallbacks(text: String): String {
        var out = text
        for ((trad, simp) in TRAD_PHRASE_FALLBACKS) {
            if (out.contains(trad)) out = out.replace(trad, simp)
        }
        return out
    }

    private fun hanToPinyinVariants(ch: Char): List<String> {
        val variants = LinkedHashSet<String>()
        POLYPHONE_OVERRIDES[ch]?.forEach { variants.add(it) }
        val resolved = IcuBridge.hanToPinyin(ch).ifEmpty { FALLBACK_HAN_PINYIN[ch].orEmpty() }
        if (resolved.isNotEmpty()) {
            // 保留 ü -> v / u 双候选，便于键盘习惯输入 lv / lu 都能命中
            if (resolved.contains('ü')) {
                variants.add(resolved.replace('ü', 'v'))
                variants.add(resolved.replace('ü', 'u'))
            } else {
                variants.add(resolved)
            }
        }
        return variants.toList()
    }

    private object IcuBridge {
        private val t2s: Any? by lazy {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching { Transliterator.getInstance("Traditional-Simplified") }.getOrNull()
            } else {
                null
            }
        }

        private val hanLatin: Any? by lazy {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching { Transliterator.getInstance("Han-Latin") }.getOrNull()
            } else {
                null
            }
        }

        private val charCache = ConcurrentHashMap<Char, String>()

        fun toSimplified(text: String): String {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return text
            val tr = t2s as? Transliterator ?: return text
            return runCatching { tr.transliterate(text) }.getOrDefault(text)
        }

        fun hanToPinyin(ch: Char): String {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ""
            return charCache.getOrPut(ch) {
                val tr = hanLatin as? Transliterator ?: return@getOrPut ""
                val raw = runCatching { tr.transliterate(ch.toString()) }.getOrDefault("")
                stripToneMarksKeepUmlaut(raw)
            }
        }

        /** 去掉四声调号但保留 `ü`，让上层同时生成 `v` 与 `u` 两套拼音。 */
        private fun stripToneMarksKeepUmlaut(raw: String): String {
            val sb = StringBuilder(raw.length)
            for (c in raw.lowercase(Locale.ROOT)) {
                when (c) {
                    'ǖ', 'ǘ', 'ǚ', 'ǜ', 'ü' -> sb.append('ü')
                    else -> {
                        val decomposed = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
                        for (dc in decomposed) {
                            if (dc in 'a'..'z') sb.append(dc)
                        }
                    }
                }
            }
            return sb.toString()
        }
    }

    private val ASCII_TOKEN_REGEX = Regex("[a-z0-9]+")
    private val HAN_SEGMENT_REGEX = Regex("[\\u4e00-\\u9fff]+")

    /** 设置页高频多音字修正：同时收录主读音与常见次读音，避免 ICU 单读音漏匹配。 */
    private val POLYPHONE_OVERRIDES: Map<Char, List<String>> = mapOf(
        '长' to listOf("chang", "zhang"),
        '弹' to listOf("tan", "dan"),
        '调' to listOf("tiao", "diao"),
        '重' to listOf("chong", "zhong"),
        '数' to listOf("shu"),
        '率' to listOf("lv", "lu", "shuai"),
        '绿' to listOf("lv", "lu"),
        '略' to listOf("lve", "lue"),
        '还' to listOf("hai", "huan"),
        '行' to listOf("xing", "hang"),
        '藏' to listOf("cang", "zang"),
        '倒' to listOf("dao"),
        '降' to listOf("jiang", "xiang"),
        '卡' to listOf("ka", "qia"),
        '角' to listOf("jiao", "jue"),
        '系' to listOf("xi", "ji"),
        '省' to listOf("sheng", "xing"),
        '屏' to listOf("ping"),
        '背' to listOf("bei"),
        '传' to listOf("chuan", "zhuan"),
        '差' to listOf("cha", "chai"),
        '应' to listOf("ying"),
        '量' to listOf("liang"),
        '度' to listOf("du"),
        '分' to listOf("fen"),
        '模' to listOf("mo", "mu"),
        '间' to listOf("jian"),
        '发' to listOf("fa"),
        '只' to listOf("zhi"),
        '片' to listOf("pian"),
        '更' to listOf("geng"),
        '乐' to listOf("le", "yue"),
        '地' to listOf("di", "de"),
        '切' to listOf("qie"),
        '折' to listOf("zhe"),
        '中' to listOf("zhong"),
        '圈' to listOf("quan"),
        '单' to listOf("dan"),
        '强' to listOf("qiang"),
        '转' to listOf("zhuan"),
        '划' to listOf("hua"),
        '看' to listOf("kan"),
        '和' to listOf("he"),
        '为' to listOf("wei"),
        '会' to listOf("hui"),
        '没' to listOf("mei"),
        '了' to listOf("le"),
        '着' to listOf("zhe"),
    )

    /** 跨地区常用术语归一（如港台「快取」→「缓存」、「畫質」→「画质」、「帳號」→「账号」）。 */
    private val TRAD_PHRASE_FALLBACKS: List<Pair<String, String>> = listOf(
        "快取" to "缓存",
        "預設" to "默认",
        "预设" to "默认",
        "設定" to "设置",
        "设定" to "设置",
        "帳號" to "账号",
        "帳戶" to "账号",
        "顯示" to "显示",
        "主題" to "主题",
        "圖標" to "图标",
        "畫質" to "画质",
        "離線" to "离线",
        "緩存" to "缓存",
        "網路" to "网络",
        "連結" to "链接",
        "超連結" to "超链接",
        "鎖定" to "锁定",
        "橫屏" to "横屏",
        "豎屏" to "竖屏",
        "過濾" to "过滤",
        "關閉" to "关闭",
        "開啟" to "开启",
        "隱藏" to "隐藏",
        "展開" to "展开",
        "摺疊" to "折叠",
        "預覽" to "预览",
        "實況" to "实况",
        "點讚" to "点赞",
        "瀏覽" to "浏览",
        "歷史" to "历史",
        "動畫" to "动画",
        "手勢" to "手势",
        "評論" to "评论",
        "認證" to "认证",
        "頭像" to "头像",
        "熱搜" to "热搜",
        "視頻" to "视频",
        "懸浮" to "悬浮",
        "畫中畫" to "画中画",
    )

    /**
     * API 26–28 及本地 JVM 单元测试兜底拼音表（覆盖设置页及同义词全部常用汉字）。
     * Android 10+ 真机优先使用系统 ICU `Han-Latin`，此表仅在 ICU 不可用时启用。
     */
    private val FALLBACK_HAN_PINYIN: Map<Char, String> by lazy {
        val raw = "一:yi,三:san,上:shang,下:xia,不:bu,与:yu,东:dong,丝:si,个:ge,中:zhong,为:wei,主:zhu," +
            "义:yi,之:zhi,习:xi,书:shu,二:er,于:yu,互:hu,交:jiao,人:ren,仅:jin,介:jie,从:cong," +
            "他:ta,份:fen,会:hui,位:wei,住:zhu,体:ti,作:zuo,使:shi,侧:ce,保:bao,信:xin,倒:dao," +
            "做:zuo,像:xiang,允:yun,入:ru,全:quan,共:gong,关:guan,其:qi,具:ju,内:nei,再:zai,况:kuang," +
            "出:chu,击:ji,分:fen,切:qie,列:lie,到:dao,制:zhi,刷:shua,前:qian,动:dong,势:shi,勋:xun," +
            "区:qu,升:sheng,单:dan,博:bo,卡:ka,历:li,原:yuan,去:qu,及:ji,双:shuang,发:fa,取:qu," +
            "变:bian,叠:die,口:kou,只:zhi,可:ke,台:tai,史:shi,右:you,号:hao,同:tong,名:ming,后:hou," +
            "向:xiang,启:qi,呼:hu,和:he,器:qi,回:hui,固:gu,图:tu,圆:yuan,在:zai,地:di,坏:huai," +
            "块:kuai,城:cheng,基:ji,声:sheng,复:fu,外:wai,夜:ye,失:shi,头:tou,套:tao,字:zi,存:cun," +
            "学:xue,完:wan,定:ding,实:shi,客:ke,容:rong,对:dui,导:dao,将:jiang,小:xiao,就:jiu,局:ju," +
            "屏:ping,展:zhan,工:gong,左:zuo,已:yi,布:bu,带:dai,幕:mu,平:ping,并:bing,广:guang,序:xu," +
            "库:ku,应:ying,底:di,度:du,开:kai,式:shi,张:zhang,弹:tan,强:qiang,当:dang,录:lu,彩:cai," +
            "往:wang,律:lü,微:wei,心:xin,志:zhi,快:kuai,态:tai,性:xing,恢:hui,息:xi,悬:xuan,情:qing," +
            "慢:man,我:wo,或:huo,户:hu,手:shou,才:cai,打:da,把:ba,折:zhe,抬:tai,拉:la,持:chi," +
            "按:an,挡:dang,挤:ji,换:huan,据:ju,捷:jie,掉:diao,接:jie,搜:sou,播:bo,操:cao,支:zhi," +
            "收:shou,改:gai,放:fang,效:xiao,数:shu,整:zheng,文:wen,新:xin,方:fang,旁:pang,旋:xuan,日:ri," +
            "时:shi,是:shi,昵:ni,显:xian,暗:an,更:geng,最:zui,有:you,末:mo,本:ben,束:shu,条:tiao," +
            "板:ban,果:guo,查:cha,标:biao,栏:lan,样:yang,框:kuang,检:jian,楼:lou,榜:bang,模:mo,横:heng," +
            "次:ci,正:zheng,步:bu,段:duan,毛:mao,求:qiu,沉:chen,没:mei,注:zhu,流:liu,浅:qian,测:ce," +
            "浏:liu,浮:fu,浸:jin,深:shen,清:qing,渐:jian,游:you,源:yuan,滑:hua,滚:gun,点:dian,热:re," +
            "片:pian,版:ban,状:zhuang,独:du,玻:bo,理:li,璃:li,用:yong,电:dian,画:hua,留:liu,登:deng," +
            "的:de,目:mu,直:zhi,省:sheng,看:kan,真:zhen,研:yan,破:po,确:que,示:shi,称:cheng,稿:gao," +
            "究:jiu,穿:chuan,窗:chuang,竖:shu,端:duan,第:di,等:deng,签:qian,简:jian,管:guan,箱:xiang,粉:fen," +
            "糊:hu,系:xi,索:suo,红:hong,级:ji,纯:chun,纳:na,组:zu,结:jie,统:tong,缓:huan,缩:suo," +
            "网:wang,置:zhi,自:zi,至:zhi,致:zhi,航:hang,色:se,节:jie,范:fan,草:cao,菜:cai,蓝:lan," +
            "藏:cang,补:bu,表:biao,被:bei,西:xi,要:yao,规:gui,视:shi,览:lan,角:jiao,认:ren,记:ji," +
            "许:xu,论:lun,设:she,访:fang,证:zheng,评:ping,识:shi,话:hua,详:xiang,误:wu,调:tiao,谢:xie," +
            "败:bai,账:zhang,贴:tie,赞:zan,起:qi,超:chao,跟:gen,跳:tiao,身:shen,转:zhuan,较:jiao,过:guo," +
            "近:jin,返:fan,进:jin,退:tui,选:xuan,透:tou,速:su,部:bu,配:pei,采:cai,释:shi,里:li," +
            "重:chong,金:jin,钮:niu,钻:zuan,铁:tie,铺:pu,链:lian,销:xiao,长:chang,门:men,闭:bi,间:jian," +
            "附:fu,除:chu,随:sui,隐:yin,静:jing,非:fei,靠:kao,面:mian,音:yin,页:ye,顶:ding,项:xiang," +
            "预:yu,频:pin,题:ti,首:shou,高:gao,黑:hei,默:mo,齐:qi,圈:quan,娱:yu,悄:qiao,特:te," +
            "别:bie,喜:xi,欢:huan,磁:ci,盘:pan,空:kong,协:xie,议:yi,扁:bian,触:chu,边:bian,质:zhi," +
            "斯:si,明:ming,挚:zhi,爱:ai,黄:huang,多:duo,添:tian,加:jia,云:yun,场:chang,试:shi,安:an," +
            "装:zhuang,未:wei,截:jie,断:duan,简:jian,介:jie,缩:suo,略:lüe,滤:lü,绿:lü"
        val map = HashMap<Char, String>(440)
        for (pair in raw.split(',')) {
            val sep = pair.indexOf(':')
            if (sep == 1 && pair.length > 2) {
                map[pair[0]] = pair.substring(2)
            }
        }
        map
    }
}

private data class SyllableSpan(
    val variants: List<String>,
    val primary: String,
    val rawStart: Int,
    val rawEnd: Int,
    val isAscii: Boolean,
    val joinedStart: Int,
    val joinedEnd: Int,
)

private class TextSearchIndex(
    val rawNorm: String,
    val rawNormMap: IntArray,
    val compact: String,
    val compactMap: IntArray,
    val syllables: List<SyllableSpan>,
    val joinedPinyin: String,
    val asciiTokens: List<String>,
) {
    companion object {
        val EMPTY = TextSearchIndex("", IntArray(0), "", IntArray(0), emptyList(), "", emptyList())
    }
}

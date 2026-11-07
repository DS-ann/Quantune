package com.dsann.music.data.lyrics

import android.util.Log
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * High-precision XML parser for TTML (Timed Text Markup Language) lyrics.
 * Features:
 * - Relative vs. Absolute word timestamp normalization
 * - Span-level background vocal detection (`role="x-bg"` / `ttm:role="x-bg"`)
 * - Time-bounded background vocal line merging
 */
object TtmlParser {

    private const val TAG = "TtmlParser"

    @JvmStatic
    fun parse(ttmlContent: String?): List<LyricLine> {
        if (ttmlContent.isNullOrBlank()) return emptyList()

        val sanitized = sanitizeXml(ttmlContent)

        return try {
            val dbf = DocumentBuilderFactory.newInstance()
            dbf.isNamespaceAware = true
            // Disable DTD entity expansion for security and stability
            try { dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) } catch (_: Exception) {}
            try { dbf.setFeature("http://xml.org/sax/features/external-general-entities", false) } catch (_: Exception) {}
            try { dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false) } catch (_: Exception) {}
            try { dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) } catch (_: Exception) {}
            try { dbf.isXIncludeAware = false } catch (_: Exception) {}
            dbf.isExpandEntityReferences = false
            val db = dbf.newDocumentBuilder()
            val doc = db.parse(ByteArrayInputStream(sanitized.toByteArray(Charsets.UTF_8)))
            doc.documentElement.normalize()

            val namespacedParagraphs = doc.getElementsByTagNameNS("*", "p")
            val pNodes = if (namespacedParagraphs.length > 0) namespacedParagraphs else doc.getElementsByTagName("p")
            val lines = mutableListOf<LyricLine>()

            for (i in 0 until pNodes.length) {
                val pElement = pNodes.item(i) as? Element ?: continue
                val beginAttr = getAttributeAny(pElement, "begin", "ttm:begin") ?: "00:00.00"
                val endAttr = getAttributeAny(pElement, "end", "ttm:end")
                val durAttr = getAttributeAny(pElement, "dur", "ttm:dur")

                val lineStartMs = parseRawTime(beginAttr)
                val lineEndMs = when {
                    endAttr != null -> parseRawTime(endAttr)
                    durAttr != null -> lineStartMs + parseRawTime(durAttr)
                    else -> lineStartMs + 5000L
                }

                val pendingWords = mutableListOf<PendingTimedWord>()
                val lineSb = StringBuilder()

                val isLineBg = getAttributeAny(pElement, "role", "ttm:role")?.lowercase()?.let {
                    it == "x-bg" || it == "background"
                } ?: false

                val agentAttr = getAttributeAny(pElement, "agent", "ttm:agent")?.trim()

                val childNodes = pElement.childNodes
                val hasSpanChildren = pElement.getElementsByTagNameNS("*", "span").length > 0 ||
                    pElement.getElementsByTagName("span").length > 0
                var hasPendingSpace = false

                if (hasSpanChildren && childNodes.length > 0) {
                    fun processSpanElement(
                        span: Element,
                        parentIsBg: Boolean,
                        inheritedTiming: TtmlSpanTiming
                    ) {
                        val roleAttr = getAttributeAny(span, "role", "ttm:role")?.lowercase()
                        val isSpanBg = parentIsBg || roleAttr == "x-bg" || roleAttr == "background"

                        val rawWordText = span.textContent ?: ""
                        val cleanWordText = rawWordText.trim()
                        if (cleanWordText.isEmpty()) return

                        val wordBegin = getAttributeAny(span, "begin", "ttm:begin") ?: inheritedTiming.begin
                        val wordEnd = getAttributeAny(span, "end", "ttm:end") ?: inheritedTiming.end
                        val wordDur = getAttributeAny(span, "dur", "ttm:dur") ?: inheritedTiming.duration
                        val hasOwnOrInheritedTiming =
                            wordBegin != null || wordEnd != null || wordDur != null

                        val startsWithSpace = rawWordText.startsWith(" ") || rawWordText.startsWith("\n")
                        val endsWithSpace = rawWordText.endsWith(" ") || rawWordText.endsWith("\n")

                        if (lineSb.isNotEmpty() && !lineSb.endsWith(" ")) {
                            if (hasPendingSpace || startsWithSpace) lineSb.append(" ")
                        }

                        val startChar = lineSb.length
                        lineSb.append(cleanWordText)
                        val endChar = (lineSb.length - 1).coerceAtLeast(startChar)

                        // Untimed spans contribute text but are not falsely advertised as
                        // karaoke words. For a start-only span, its end is resolved from the
                        // next timed span or the paragraph end below.
                        if (hasOwnOrInheritedTiming) {
                            pendingWords += PendingTimedWord(
                                text = cleanWordText,
                                rawStartMs = wordBegin?.let(::parseRawTime) ?: lineStartMs,
                                rawEndMs = when {
                                    wordEnd != null -> parseRawTime(wordEnd)
                                    wordDur != null -> {
                                        val rawStart = wordBegin?.let(::parseRawTime) ?: lineStartMs
                                        rawStart + parseRawTime(wordDur)
                                    }
                                    else -> null
                                },
                                charRange = startChar..endChar,
                                isBackground = isSpanBg
                            )
                        }
                        hasPendingSpace = endsWithSpace
                    }

                    fun traverseNode(
                        node: Node,
                        isBg: Boolean,
                        inheritedTiming: TtmlSpanTiming
                    ) {
                        if (node.nodeType == Node.TEXT_NODE) {
                            val valText = node.nodeValue ?: ""
                            if (valText.contains(" ") || valText.contains("\n") || valText.contains("\t")) {
                                hasPendingSpace = true
                            }
                        } else if (node.nodeType == Node.ELEMENT_NODE) {
                            val elem = node as Element
                            val localName = elem.localName ?: elem.tagName
                            if (localName.endsWith("span", ignoreCase = true)) {
                                val roleAttr = getAttributeAny(elem, "role", "ttm:role")?.lowercase()
                                val isSpanBg = isBg || roleAttr == "x-bg" || roleAttr == "background"
                                val effectiveTiming = TtmlSpanTiming(
                                    begin = getAttributeAny(elem, "begin", "ttm:begin") ?: inheritedTiming.begin,
                                    end = getAttributeAny(elem, "end", "ttm:end") ?: inheritedTiming.end,
                                    duration = getAttributeAny(elem, "dur", "ttm:dur") ?: inheritedTiming.duration
                                )
                                val descendants = elem.getElementsByTagNameNS("*", "span").length +
                                    elem.getElementsByTagName("span").length
                                if (descendants > 0) {
                                    val children = elem.childNodes
                                    for (c in 0 until children.length) {
                                        traverseNode(children.item(c), isSpanBg, effectiveTiming)
                                    }
                                } else {
                                    processSpanElement(elem, isSpanBg, inheritedTiming)
                                }
                            } else {
                                val children = elem.childNodes
                                for (c in 0 until children.length) {
                                    traverseNode(children.item(c), isBg, inheritedTiming)
                                }
                            }
                        }
                    }

                    for (j in 0 until childNodes.length) {
                        traverseNode(childNodes.item(j), isLineBg, TtmlSpanTiming())
                    }
                } else {
                    lineSb.append(pElement.textContent ?: "")
                }

                val rawContent = lineSb.toString()
                val contentText = rawContent.trim()
                val trimStart = rawContent.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
                val words = buildTimedWords(
                    pending = pendingWords,
                    lineStartMs = lineStartMs,
                    lineEndMs = lineEndMs,
                    trimStart = trimStart,
                    visibleLength = contentText.length
                )
                val hasCompleteWordTiming = words.isNotEmpty() &&
                    coversVisibleCharacters(contentText, words)
                val displayWords = if (hasCompleteWordTiming) words else emptyList()

                if (contentText.isNotEmpty()) {
                    val prevLine = lines.lastOrNull()
                    val isRecent = prevLine != null && (lineStartMs - prevLine.startTimeMs) < 4000L

                    val isRightAligned = agentAttr?.lowercase()?.let {
                        it == "v2" || it.contains("v2") || it.contains("singer2") || it.contains("artist2") || it.contains("secondary")
                    } ?: (contentText.startsWith("[Singer 2", ignoreCase = true) ||
                          contentText.startsWith("[Artist 2", ignoreCase = true) ||
                          contentText.startsWith("(Singer 2", ignoreCase = true) ||
                          contentText.startsWith("(Artist 2", ignoreCase = true) ||
                          contentText.startsWith("[Duet 2", ignoreCase = true))

                    if (isLineBg && prevLine != null && isRecent && prevLine.backgroundContent == null) {
                        // Merge background vocals into overlapping preceding line
                        val bgWords = if (displayWords.isNotEmpty()) displayWords else {
                            listOf(LyricWord(text = contentText, absoluteStartTimeMs = lineStartMs, durationMs = 2500L, charRange = 0..contentText.lastIndex.coerceAtLeast(0), isBackground = true))
                        }
                        lines[lines.lastIndex] = prevLine.copy(
                            backgroundContent = contentText,
                            backgroundWords = bgWords
                        )
                    } else {
                        lines.add(
                            LyricLine(
                                startTimeMs = lineStartMs,
                                content = contentText,
                                isInterlude = false,
                                isWordByWord = hasCompleteWordTiming,
                                words = displayWords,
                                isBackground = isLineBg,
                                agent = agentAttr,
                                isRightAligned = isRightAligned
                            )
                        )
                    }
                }
            }

            lines.sorted()
        } catch (e: Exception) {
            Log.w(TAG, "DOM parsing failed (${e.message}), attempting resilient regex fallback parser")
            parseWithRegexFallback(sanitized)
        }
    }

    /**
     * Sanitizes raw XML content before passing to DocumentBuilder.
     * Fixes unescaped ampersands, unclosed <br> tags, and illegal XML control characters.
     */
    private fun sanitizeXml(raw: String): String {
        var s = raw.trim()
        // Strip BOM if present
        if (s.startsWith("\uFEFF")) {
            s = s.substring(1)
        }
        // Fix unescaped ampersands (e.g. "R&B" -> "R&amp;B")
        s = s.replace(Regex("&(?!(amp|lt|gt|quot|apos|#\\d+|#x[0-9a-fA-F]+);)"), "&amp;")
        // Fix self-closing br/hr tags
        s = s.replace(Regex("<br(?=[^>]*)(?<!/)>", RegexOption.IGNORE_CASE), "<br/>")
        s = s.replace(Regex("<hr(?=[^>]*)(?<!/)>", RegexOption.IGNORE_CASE), "<hr/>")
        // Remove illegal XML control characters (ASCII 0-8, 11-12, 14-31)
        s = s.replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")
        return s
    }

    /**
     * Resilient regex-based TTML parser that extracts <p> and <span> tags
     * even when the document has invalid XML syntax or is partially truncated.
     */
    private fun parseWithRegexFallback(xml: String): List<LyricLine> {
        return try {
            val lines = mutableListOf<LyricLine>()
            val pPattern = Regex("""<p\b([^>]*)>(.*?)</p>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            val spanPattern = Regex("""<span\b([^>]*)>(.*?)</span>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            val attrPattern = Regex("""([a-zA-Z0-9_:\-]+)\s*=\s*["']([^"']*)["']""")

            fun parseAttributes(attrStr: String): Map<String, String> {
                val map = mutableMapOf<String, String>()
                attrPattern.findAll(attrStr).forEach { match ->
                    val k = match.groupValues[1].lowercase()
                    val v = match.groupValues[2]
                    map[k] = v
                    val suffix = k.substringAfterLast(":")
                    if (suffix != k) map[suffix] = v
                }
                return map
            }

            pPattern.findAll(xml).forEach { pMatch ->
                val pAttrs = parseAttributes(pMatch.groupValues[1])
                val pInner = pMatch.groupValues[2]

                val beginAttr = pAttrs["begin"] ?: "00:00.00"
                val endAttr = pAttrs["end"]
                val durAttr = pAttrs["dur"]

                val lineStartMs = parseRawTime(beginAttr)
                val lineEndMs = when {
                    endAttr != null -> parseRawTime(endAttr)
                    durAttr != null -> lineStartMs + parseRawTime(durAttr)
                    else -> lineStartMs + 5000L
                }

                val roleAttr = pAttrs["role"]?.lowercase()
                val isLineBg = roleAttr == "x-bg" || roleAttr == "background"
                val agentAttr = pAttrs["agent"]?.trim()

                val pendingWords = mutableListOf<PendingTimedWord>()
                val lineSb = StringBuilder()

                val spanMatches = spanPattern.findAll(pInner).toList()
                if (spanMatches.isNotEmpty()) {
                    for (spanMatch in spanMatches) {
                        val spanAttrs = parseAttributes(spanMatch.groupValues[1])
                        val spanRawText = spanMatch.groupValues[2].replace(Regex("<[^>]+>"), "")
                        val cleanText = spanRawText.trim()
                        if (cleanText.isEmpty()) continue

                        val spanRole = spanAttrs["role"]?.lowercase()
                        val isSpanBg = isLineBg || spanRole == "x-bg" || spanRole == "background"
                        val wordBegin = spanAttrs["begin"]
                        val wordEnd = spanAttrs["end"]
                        val wordDur = spanAttrs["dur"]
                        val hasWordTiming = wordBegin != null || wordEnd != null || wordDur != null

                        if (lineSb.isNotEmpty() && !lineSb.endsWith(" ")) lineSb.append(" ")
                        val startChar = lineSb.length
                        lineSb.append(cleanText)
                        val endChar = (lineSb.length - 1).coerceAtLeast(startChar)

                        if (hasWordTiming) {
                            val rawWordStart = wordBegin?.let(::parseRawTime) ?: lineStartMs
                            pendingWords += PendingTimedWord(
                                text = cleanText,
                                rawStartMs = rawWordStart,
                                rawEndMs = when {
                                    wordEnd != null -> parseRawTime(wordEnd)
                                    wordDur != null -> rawWordStart + parseRawTime(wordDur)
                                    else -> null
                                },
                                charRange = startChar..endChar,
                                isBackground = isSpanBg
                            )
                        }
                    }
                } else {
                    lineSb.append(pInner.replace(Regex("<[^>]+>"), "").trim())
                }

                val rawContent = lineSb.toString()
                val contentText = rawContent.trim()
                val trimStart = rawContent.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
                val words = buildTimedWords(
                    pending = pendingWords,
                    lineStartMs = lineStartMs,
                    lineEndMs = lineEndMs,
                    trimStart = trimStart,
                    visibleLength = contentText.length
                )
                val hasCompleteWordTiming = words.isNotEmpty() &&
                    coversVisibleCharacters(contentText, words)
                val displayWords = if (hasCompleteWordTiming) words else emptyList()

                if (contentText.isNotEmpty()) {
                    val prevLine = lines.lastOrNull()
                    val isRecent = prevLine != null && (lineStartMs - prevLine.startTimeMs) < 4000L

                    val isRightAligned = agentAttr?.lowercase()?.let {
                        it == "v2" || it.contains("v2") || it.contains("singer2") || it.contains("artist2") || it.contains("secondary")
                    } ?: (contentText.startsWith("[Singer 2", ignoreCase = true) ||
                          contentText.startsWith("[Artist 2", ignoreCase = true) ||
                          contentText.startsWith("(Singer 2", ignoreCase = true) ||
                          contentText.startsWith("(Artist 2", ignoreCase = true) ||
                          contentText.startsWith("[Duet 2", ignoreCase = true))

                    if (isLineBg && prevLine != null && isRecent && prevLine.backgroundContent == null) {
                        val bgWords = if (displayWords.isNotEmpty()) displayWords else {
                            listOf(LyricWord(text = contentText, absoluteStartTimeMs = lineStartMs, durationMs = 2500L, isBackground = true))
                        }
                        lines[lines.lastIndex] = prevLine.copy(
                            backgroundContent = contentText,
                            backgroundWords = bgWords
                        )
                    } else {
                        lines.add(
                            LyricLine(
                                startTimeMs = lineStartMs,
                                content = contentText,
                                isInterlude = false,
                                isWordByWord = hasCompleteWordTiming,
                                words = displayWords,
                                isBackground = isLineBg,
                                agent = agentAttr,
                                isRightAligned = isRightAligned
                            )
                        )
                    }
                }
            }

            lines.sorted()
        } catch (e: Exception) {
            Log.e(TAG, "Regex fallback TTML parsing also failed: ${e.message}")
            emptyList()
        }
    }

    private data class TtmlSpanTiming(
        val begin: String? = null,
        val end: String? = null,
        val duration: String? = null
    )

    private data class PendingTimedWord(
        val text: String,
        val rawStartMs: Long,
        val rawEndMs: Long?,
        val charRange: IntRange,
        val isBackground: Boolean
    )

    private fun buildTimedWords(
        pending: List<PendingTimedWord>,
        lineStartMs: Long,
        lineEndMs: Long,
        trimStart: Int,
        visibleLength: Int
    ): List<LyricWord> {
        if (pending.isEmpty() || visibleLength <= 0) return emptyList()
        return pending.mapIndexedNotNull { index, item ->
            val first = (item.charRange.first - trimStart).coerceAtLeast(0)
            val last = (item.charRange.last - trimStart).coerceAtMost(visibleLength - 1)
            if (first > last) return@mapIndexedNotNull null

            val startMs = normalizeChildTime(
                item.rawStartMs, lineStartMs, lineEndMs, lineStartMs
            )
            val inferredRawEnd = item.rawEndMs ?: pending.getOrNull(index + 1)
                ?.rawStartMs ?: lineEndMs
            val endMs = normalizeChildTime(
                inferredRawEnd, lineStartMs, lineEndMs, lineEndMs
            ).coerceAtLeast(startMs)
            LyricWord(
                text = item.text,
                absoluteStartTimeMs = startMs,
                durationMs = (endMs - startMs).coerceAtLeast(50L),
                charRange = first..last,
                isBackground = item.isBackground
            )
        }
    }

    private fun coversVisibleCharacters(text: String, words: List<LyricWord>): Boolean {
        if (text.isBlank()) return false
        val covered = BooleanArray(text.length)
        words.forEach { word ->
            for (index in word.charRange.first..word.charRange.last) {
                if (index in covered.indices) covered[index] = true
            }
        }
        return text.indices.all { index -> text[index].isWhitespace() || covered[index] }
    }

    private fun getAttributeAny(element: Element, vararg names: String): String? {
        for (name in names) {
            if (element.hasAttribute(name)) return element.getAttribute(name)
        }
        val attrs = element.attributes ?: return null
        for (i in 0 until attrs.length) {
            val node = attrs.item(i) ?: continue
            val nodeName = node.nodeName
            for (name in names) {
                if (nodeName.endsWith(name, ignoreCase = true)) return node.nodeValue
            }
        }
        return null
    }

    /**
     * Normalizes child word timestamps.
     * Handles Apple Music relative offset spans vs absolute timestamps.
     */
    private fun normalizeChildTime(
        rawMs: Long,
        lineStartMs: Long,
        lineEndMs: Long,
        fallbackMs: Long
    ): Long {
        if (rawMs < 0) return fallbackMs
        val lineDurationMs = (lineEndMs - lineStartMs).coerceAtLeast(0L)
        val isProbablyRelative = rawMs < (lineStartMs - 100L) && rawMs <= (lineDurationMs + 1000L)
        val adjustedMs = if (isProbablyRelative) lineStartMs + rawMs else rawMs
        return adjustedMs.coerceIn(lineStartMs.coerceAtLeast(0L), lineEndMs.coerceAtLeast(lineStartMs))
    }

    private fun parseRawTime(timeStr: String): Long {
        val trimmed = timeStr.trim()
        if (trimmed.endsWith("ms")) {
            return trimmed.dropLast(2).toLongOrNull() ?: 0L
        }
        if (trimmed.endsWith("s")) {
            return (trimmed.dropLast(1).toFloatOrNull()?.times(1000))?.toLong() ?: 0L
        }
        if (trimmed.endsWith("t")) {
            val ticks = trimmed.dropLast(1).toDoubleOrNull() ?: 0.0
            return (ticks / 10.0).toLong()
        }

        val parts = trimmed.split(":")
        return try {
            when (parts.size) {
                3 -> {
                    val h = parts[0].toLong()
                    val m = parts[1].toLong()
                    val s = parts[2].toDouble()
                    ((h * 3600 + m * 60 + s) * 1000).toLong()
                }
                2 -> {
                    val m = parts[0].toLong()
                    val s = parts[1].toDouble()
                    ((m * 60 + s) * 1000).toLong()
                }
                1 -> {
                    (parts[0].toDouble() * 1000).toLong()
                }
                else -> 0L
            }
        } catch (e: Exception) {
            0L
        }
    }
}

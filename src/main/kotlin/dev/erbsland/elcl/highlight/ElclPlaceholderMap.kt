package dev.erbsland.elcl.highlight

import com.intellij.psi.tree.IElementType
import dev.erbsland.elcl.lexer.ElclLexerAdapter
import dev.erbsland.elcl.psi.ElclTypes
import dev.erbsland.elcl.syntax.decodeElclText

/** Source ranges for complete Core placeholders; malformed input never produces diagnostics. */
internal class ElclPlaceholderMap {
    data class Part(val start: Int, val end: Int, val type: IElementType)
    private val parts = mutableListOf<Part>()
    fun containing(offset: Int): Part? = parts.binarySearch { part ->
        when { offset < part.start -> 1; offset >= part.end -> -1; else -> 0 }
    }.takeIf { it >= 0 }?.let(parts::get)
    fun nextStart(offset: Int, end: Int): Int {
        val index = parts.binarySearch { it.start.compareTo(offset) }
        return parts.getOrNull(if (index >= 0) index else -index - 1)?.start?.coerceAtMost(end) ?: end
    }

    fun prepare(buffer: CharSequence, enabled: Boolean) {
        parts.clear()
        if (!enabled) return
        val lexer = ElclLexerAdapter()
        lexer.start(buffer)
        var meta = false
        var spanStart = -1
        var spanEnd = -1
        fun flush() {
            if (spanStart >= 0) scan(buffer, spanStart, spanEnd)
            spanStart = -1
        }
        while (lexer.tokenType != null) {
            val type = lexer.tokenType
            if (type == ElclTypes.META_NAME) meta = true
            if (type == ElclTypes.NAME || type == ElclTypes.TEXT_NAME ||
                type == ElclTypes.SECTION_MAP_OPEN || type == ElclTypes.SECTION_LIST_OPEN) meta = false
            if (!meta && type == ElclTypes.MULTILINE_TEXT_CONTENT) {
                if (spanStart < 0) spanStart = lexer.tokenStart
                spanEnd = lexer.tokenEnd
            } else {
                flush()
                if (!meta && type == ElclTypes.TEXT) scan(buffer, lexer.tokenStart + 1, lexer.tokenEnd - 1)
            }
            lexer.advance()
        }
        flush()
    }

    private fun scan(buffer: CharSequence, start: Int, end: Int) {
        var i = start
        while (i < end) {
            if (buffer[i] == '\\') {
                i += ElclTextEscapeLexer.escapeLengthAt(buffer, i, end) ?: 2
            } else if (i + 1 < end && buffer[i] == '$' && buffer[i + 1] == '{') {
                val parsed = parse(buffer, i, end)
                if (parsed != null) { parts += parsed; i = parsed.last().end }
                else {
                    // Do not highlight a nested expression inside a malformed outer one.
                    while (i < end && buffer[i] != '}') i++
                    i++
                }
            } else i++
        }
    }

    private fun parse(buffer: CharSequence, start: Int, end: Int): List<Part>? {
        val result = mutableListOf(Part(start, start + 2, ElclHighlightTokenTypes.PLACEHOLDER_DELIMITER))
        var i = start + 2
        var filters = 0
        while (i < end) {
            val nameStart = i
            while (i < end && buffer[i] !in charArrayOf(':', '|', '}')) {
                i = advanceContent(buffer, i, end) ?: return null
            }
            val name = runCatching { decodeElclText(buffer.subSequence(nameStart, i).toString()) }.getOrNull() ?: return null
            if (name.length > 100 || !name.matches(Regex("[A-Za-z][A-Za-z0-9]*([ _][A-Za-z0-9]+)*"))) return null
            result += Part(nameStart, i, ElclHighlightTokenTypes.PLACEHOLDER_TYPE)
            if (i < end && buffer[i] == ':') {
                result += Part(i, i + 1, ElclHighlightTokenTypes.PLACEHOLDER_DELIMITER)
                val parameterStart = ++i
                while (i < end && buffer[i] != '|' && buffer[i] != '}') i = advanceContent(buffer, i, end) ?: return null
                if (i > parameterStart) result += Part(parameterStart, i, ElclHighlightTokenTypes.PLACEHOLDER_PARAMETER)
            }
            if (i >= end) return null
            result += Part(i, i + 1, ElclHighlightTokenTypes.PLACEHOLDER_DELIMITER)
            if (buffer[i++] == '}') return result
            if (++filters > 16) return null
        }
        return null
    }

    private fun advanceContent(buffer: CharSequence, offset: Int, end: Int): Int? {
        if (buffer[offset] == '\r' || buffer[offset] == '\n') return null
        if (buffer[offset] == '$' && offset + 1 < end && buffer[offset + 1] == '{') return null
        if (buffer[offset] == '\\') {
            val length = ElclTextEscapeLexer.escapeLengthAt(buffer, offset, end) ?: return null
            if (runCatching { decodeElclText(buffer.subSequence(offset, offset + length).toString()) }.isFailure) return null
            return offset + length
        }
        return offset + 1
    }
}

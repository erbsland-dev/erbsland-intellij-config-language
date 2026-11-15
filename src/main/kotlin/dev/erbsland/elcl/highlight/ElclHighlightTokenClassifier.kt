package dev.erbsland.elcl.highlight

import com.intellij.lexer.Lexer
import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType
import dev.erbsland.elcl.lexer.ElclLexerAdapter
import dev.erbsland.elcl.psi.ElclTypes
import dev.erbsland.elcl.validation.isReservedVrName
import dev.erbsland.elcl.validation.normalize
import dev.erbsland.elcl.validation.vrEscapePrefixLength

/** Adds context-sensitive token categories without changing parser tokens. */
internal class ElclHighlightTokenClassifier(private val validationRules: Boolean) : LexerBase() {
    private val delegate: Lexer = ElclLexerAdapter()
    private var escapeEnd: Int? = null
    private var escapedSuffix = false

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        escapedSuffix = initialState and ESCAPED_SUFFIX_STATE != 0
        // Re-lex the complete name when restarting at its suffix, which may begin with a digit.
        val prefixLength = if (initialState and LITERAL_SUFFIX_STATE != 0) 11 else 6
        delegate.start(buffer, if (escapedSuffix) startOffset - prefixLength else startOffset,
            endOffset, initialState and SUFFIX_STATE_MASK.inv())
        splitEscapedName()
    }

    override fun getState(): Int = delegate.state or if (escapedSuffix) {
        if (escapeEnd == delegate.tokenStart + 11) ESCAPED_SUFFIX_STATE or LITERAL_SUFFIX_STATE else ESCAPED_SUFFIX_STATE
    } else 0
    override fun getTokenStart(): Int = if (escapedSuffix) escapeEnd ?: delegate.tokenStart else delegate.tokenStart
    override fun getTokenEnd(): Int = if (escapedSuffix) delegate.tokenEnd else escapeEnd ?: delegate.tokenEnd
    override fun advance() {
        if (escapeEnd != null && !escapedSuffix) escapedSuffix = true
        else {
            delegate.advance()
            escapedSuffix = false
            splitEscapedName()
        }
    }
    override fun getBufferSequence(): CharSequence = delegate.bufferSequence
    override fun getBufferEnd(): Int = delegate.bufferEnd

    override fun getTokenType(): IElementType? {
        val type = delegate.tokenType ?: return null
        val sectionHeader = lineText().matches(SECTION_HEADER_LINE)
        if (escapeEnd != null) {
            return if (!escapedSuffix) ElclHighlightTokenTypes.VR_RESERVED
            else if (sectionHeader) ElclHighlightTokenTypes.SECTION_NAME else ElclTypes.NAME
        }
        val text = delegate.bufferSequence.subSequence(delegate.tokenStart, delegate.tokenEnd).toString()
        if (validationRules && type == ElclTypes.NAME && isReservedVrName(text)) {
            return ElclHighlightTokenTypes.VR_RESERVED
        }
        if (sectionHeader) {
            if (lineText().matches(SECTION_LIST_LINE) && type in SECTION_DELIMITER_TOKENS) {
                return ElclHighlightTokenTypes.SECTION_LIST_DELIMITER
            }
            if (type == ElclTypes.TEXT_NAME) return ElclHighlightTokenTypes.SECTION_TEXT_NAME
            if (type == ElclTypes.NAME) return ElclHighlightTokenTypes.SECTION_NAME
        }
        if (!validationRules) return type
        val normalized = normalize(text)
        return when {
            type == ElclTypes.NAME && isReservedVrName(normalized) -> ElclHighlightTokenTypes.VR_RESERVED
            type == ElclTypes.NAME && normalized in VR_FIELDS -> ElclHighlightTokenTypes.VR_FIELD
            type == ElclTypes.TEXT && normalized in VR_TYPES -> ElclHighlightTokenTypes.VR_TYPE
            else -> type
        }
    }

    private fun splitEscapedName() {
        val text = delegate.bufferSequence.subSequence(delegate.tokenStart, delegate.tokenEnd)
        val length = if (validationRules && delegate.tokenType == ElclTypes.NAME) vrEscapePrefixLength(text) else 0
        escapeEnd = if (length > 0) delegate.tokenStart + length else null
    }

    private fun lineText(): String {
        val buffer = delegate.bufferSequence
        var lineStart = delegate.tokenStart
        while (lineStart > 0 && buffer[lineStart - 1] != '\n' && buffer[lineStart - 1] != '\r') lineStart--
        var lineEnd = delegate.tokenEnd
        while (lineEnd < buffer.length && buffer[lineEnd] != '\n' && buffer[lineEnd] != '\r') lineEnd++
        return buffer.subSequence(lineStart, lineEnd).toString()
    }

    companion object {
        private const val ESCAPED_SUFFIX_STATE = 0x10000
        private const val LITERAL_SUFFIX_STATE = 0x20000
        private const val SUFFIX_STATE_MASK = ESCAPED_SUFFIX_STATE or LITERAL_SUFFIX_STATE
        private val SECTION_HEADER_LINE = Regex("-*(?:\\*)?\\[.*")
        private val SECTION_LIST_LINE = Regex("-*\\*\\[.*")
        private val SECTION_DELIMITER_TOKENS = setOf(
            ElclTypes.SECTION_DECORATION,
            ElclTypes.SECTION_LIST_OPEN,
            ElclTypes.SECTION_LIST_CLOSE,
            ElclTypes.SECTION_MAP_CLOSE,
        )

        val VR_TYPES = setOf(
            "integer", "boolean", "float", "text", "date", "time", "datetime", "date_time",
            "bytes", "timedelta", "time_delta", "regex", "value", "valuelist", "value_list",
            "valuematrix", "value_matrix", "section", "sectionlist", "section_list",
            "sectionwithtexts", "section_with_texts", "notvalidated", "not_validated",
        )

        val VR_FIELDS = setOf(
            "type", "use_template", "default", "is_optional", "title", "description", "error",
            "case_sensitive", "is_secret", "minimum", "maximum", "equals", "in", "multiple",
            "matches", "starts", "ends", "contains", "chars", "key", "version", "minimum_version",
            "maximum_version", "name", "path", "mode", "source", "target",
        )
    }
}

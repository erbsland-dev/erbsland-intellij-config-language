package dev.erbsland.elcl.highlight

import com.intellij.lexer.Lexer
import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType
import dev.erbsland.elcl.lexer.ElclLexerAdapter
import dev.erbsland.elcl.psi.ElclTypes

/** Adds context-sensitive token categories without changing parser tokens. */
internal class ElclHighlightTokenClassifier(private val validationRules: Boolean) : LexerBase() {
    private val delegate: Lexer = ElclLexerAdapter()

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) =
        delegate.start(buffer, startOffset, endOffset, initialState)

    override fun getState(): Int = delegate.state
    override fun getTokenStart(): Int = delegate.tokenStart
    override fun getTokenEnd(): Int = delegate.tokenEnd
    override fun advance() = delegate.advance()
    override fun getBufferSequence(): CharSequence = delegate.bufferSequence
    override fun getBufferEnd(): Int = delegate.bufferEnd

    override fun getTokenType(): IElementType? {
        val type = delegate.tokenType ?: return null
        val sectionHeader = lineText().matches(SECTION_HEADER_LINE)
        if (sectionHeader) {
            if (lineText().matches(SECTION_LIST_LINE) && type in SECTION_DELIMITER_TOKENS) {
                return ElclHighlightTokenTypes.SECTION_LIST_DELIMITER
            }
            if (type == ElclTypes.TEXT_NAME) return ElclHighlightTokenTypes.SECTION_TEXT_NAME
            if (type == ElclTypes.NAME) return ElclHighlightTokenTypes.SECTION_NAME
        }
        if (!validationRules) return type
        val text = delegate.bufferSequence.subSequence(delegate.tokenStart, delegate.tokenEnd).toString()
        val normalized = text.trim('"').lowercase().replace(' ', '_')
        return when {
            type == ElclTypes.NAME && normalized.startsWith("vr_") -> ElclHighlightTokenTypes.VR_RESERVED
            type == ElclTypes.NAME && normalized in VR_FIELDS -> ElclHighlightTokenTypes.VR_FIELD
            type == ElclTypes.TEXT && normalized in VR_TYPES -> ElclHighlightTokenTypes.VR_TYPE
            else -> type
        }
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

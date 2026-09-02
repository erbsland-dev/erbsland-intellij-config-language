package dev.erbsland.elcl.highlight

import com.intellij.lexer.Lexer
import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType
import dev.erbsland.elcl.ElclTokenType
import dev.erbsland.elcl.lexer.ElclLexerAdapter
import dev.erbsland.elcl.psi.ElclTypes

class ElclHighlightingLexer(private val validationRules: Boolean) : LexerBase() {
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
        if (!validationRules) return type
        val text = delegate.bufferSequence.subSequence(delegate.tokenStart, delegate.tokenEnd).toString()
        val normalized = text.trim('"').lowercase().replace(' ', '_')
        return when {
            type == ElclTypes.NAME && normalized.startsWith("vr_") -> VR_RESERVED
            type == ElclTypes.NAME && normalized in VR_FIELDS -> VR_FIELD
            type == ElclTypes.TEXT && normalized in VR_TYPES -> VR_TYPE
            else -> type
        }
    }

    companion object {
        val VR_RESERVED = ElclTokenType("VR_RESERVED")
        val VR_FIELD = ElclTokenType("VR_FIELD")
        val VR_TYPE = ElclTokenType("VR_TYPE")

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

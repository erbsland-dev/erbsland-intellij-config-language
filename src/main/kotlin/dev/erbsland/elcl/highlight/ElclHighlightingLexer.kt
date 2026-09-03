package dev.erbsland.elcl.highlight

import com.intellij.lexer.LayeredLexer
import com.intellij.psi.TokenType
import dev.erbsland.elcl.psi.ElclTypes

/** Lexer used by the editor to add contextual and escape-sequence colors. */
class ElclHighlightingLexer(validationRules: Boolean) :
    LayeredLexer(ElclHighlightTokenClassifier(validationRules)) {
    private val whitespaceMap = ElclMultilineWhitespaceMap()

    init {
        registerLayer(ElclTextEscapeLexer(), ElclTypes.TEXT)
        registerLayer(
            ElclMultilineFragmentLexer(whitespaceMap, ElclTypes.MULTILINE_TEXT_CONTENT, highlightEscapes = true),
            ElclTypes.MULTILINE_TEXT_CONTENT,
        )
        listOf(
            ElclTypes.MULTILINE_CODE_CONTENT,
            ElclTypes.MULTILINE_REGEX_CONTENT,
            ElclTypes.MULTILINE_BYTES_CONTENT,
            ElclTypes.MULTILINE_TEXT_CLOSE,
            ElclTypes.MULTILINE_CODE_CLOSE,
            ElclTypes.MULTILINE_REGEX_CLOSE,
            ElclTypes.MULTILINE_BYTES_CLOSE,
        ).forEach { type -> registerLayer(ElclMultilineFragmentLexer(whitespaceMap, type), type) }
        registerLayer(ElclMultilineFragmentLexer(whitespaceMap, TokenType.WHITE_SPACE), TokenType.WHITE_SPACE)
    }

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        whitespaceMap.prepare(buffer)
        super.start(buffer, startOffset, endOffset, initialState)
    }

    companion object {
        val SECTION_NAME = ElclHighlightTokenTypes.SECTION_NAME
        val SECTION_TEXT_NAME = ElclHighlightTokenTypes.SECTION_TEXT_NAME
        val SECTION_LIST_DELIMITER = ElclHighlightTokenTypes.SECTION_LIST_DELIMITER
        val TEXT_ESCAPE = ElclHighlightTokenTypes.TEXT_ESCAPE
        val MULTILINE_IGNORED_WHITESPACE = ElclHighlightTokenTypes.MULTILINE_IGNORED_WHITESPACE
        val VR_RESERVED = ElclHighlightTokenTypes.VR_RESERVED
        val VR_FIELD = ElclHighlightTokenTypes.VR_FIELD
        val VR_TYPE = ElclHighlightTokenTypes.VR_TYPE

        val VR_TYPES = ElclHighlightTokenClassifier.VR_TYPES
        val VR_FIELDS = ElclHighlightTokenClassifier.VR_FIELDS
    }
}

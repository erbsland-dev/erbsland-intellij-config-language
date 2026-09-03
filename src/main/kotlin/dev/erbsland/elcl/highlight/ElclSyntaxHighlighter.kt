package dev.erbsland.elcl.highlight

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import dev.erbsland.elcl.psi.ElclTypes

/** Maps contextual ELCL lexer tokens to configurable editor attributes. */
class ElclSyntaxHighlighter(private val validationRules: Boolean = false) : SyntaxHighlighterBase() {
    override fun getHighlightingLexer() = ElclHighlightingLexer(validationRules)

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> = pack(
        when (tokenType) {
            ElclTypes.COMMENT -> ElclTextAttributes.COMMENT
            ElclTypes.NAME -> ElclTextAttributes.NAME
            ElclTypes.TEXT_NAME -> ElclTextAttributes.TEXT_NAME
            ElclHighlightingLexer.SECTION_NAME -> ElclTextAttributes.SECTION_NAME
            ElclHighlightingLexer.SECTION_TEXT_NAME -> ElclTextAttributes.SECTION_TEXT_NAME
            ElclTypes.META_NAME -> ElclTextAttributes.META_NAME
            ElclTypes.SECTION_MAP_OPEN, ElclTypes.SECTION_MAP_CLOSE,
            ElclTypes.SECTION_DECORATION -> ElclTextAttributes.STRUCTURE
            ElclHighlightingLexer.SECTION_LIST_DELIMITER -> ElclTextAttributes.SECTION_LIST_DELIMITER
            ElclTypes.DOT -> ElclTextAttributes.SECTION_NAME_SEPARATOR
            ElclTypes.COMMA, ElclTypes.LIST_MARKER -> ElclTextAttributes.VALUE_LIST_SEPARATOR
            ElclTypes.ASSIGN -> ElclTextAttributes.OPERATOR
            ElclTypes.INTEGER, ElclTypes.FLOAT -> ElclTextAttributes.NUMBER
            ElclTypes.BOOLEAN -> ElclTextAttributes.BOOLEAN
            ElclTypes.TEXT, ElclTypes.MULTILINE_TEXT_CONTENT -> ElclTextAttributes.STRING
            ElclHighlightingLexer.TEXT_ESCAPE -> ElclTextAttributes.ESCAPE
            ElclTypes.CODE, ElclTypes.MULTILINE_CODE_CONTENT -> ElclTextAttributes.CODE
            ElclTypes.REGEX, ElclTypes.MULTILINE_REGEX_CONTENT -> ElclTextAttributes.REGEX
            ElclTypes.BYTES, ElclTypes.MULTILINE_BYTES_CONTENT -> ElclTextAttributes.BYTES
            ElclTypes.DATE, ElclTypes.TIME, ElclTypes.DATE_TIME, ElclTypes.TIME_DELTA -> ElclTextAttributes.DATE_TIME
            ElclTypes.MULTILINE_TEXT_OPEN, ElclTypes.MULTILINE_TEXT_CLOSE,
            ElclTypes.MULTILINE_CODE_OPEN, ElclTypes.MULTILINE_CODE_CLOSE,
            ElclTypes.MULTILINE_REGEX_OPEN, ElclTypes.MULTILINE_REGEX_CLOSE,
            ElclTypes.MULTILINE_BYTES_OPEN, ElclTypes.MULTILINE_BYTES_CLOSE -> ElclTextAttributes.MULTILINE_DELIMITER
            ElclTypes.MULTILINE_CODE_LANGUAGE, ElclTypes.MULTILINE_BYTES_FORMAT -> ElclTextAttributes.MULTILINE_TAG
            ElclHighlightingLexer.MULTILINE_IGNORED_WHITESPACE -> ElclTextAttributes.MULTILINE_IGNORED_WHITESPACE
            ElclHighlightingLexer.VR_RESERVED -> ElclTextAttributes.VR_RESERVED
            ElclHighlightingLexer.VR_FIELD -> ElclTextAttributes.VR_FIELD
            ElclHighlightingLexer.VR_TYPE -> ElclTextAttributes.VR_TYPE
            TokenType.BAD_CHARACTER, ElclTypes.BAD_CHARACTER -> ElclTextAttributes.BAD_CHARACTER
            else -> null
        },
    )
}

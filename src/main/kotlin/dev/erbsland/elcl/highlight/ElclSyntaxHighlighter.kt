package dev.erbsland.elcl.highlight

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import dev.erbsland.elcl.psi.ElclTypes

class ElclSyntaxHighlighter(private val validationRules: Boolean = false) : SyntaxHighlighterBase() {
    override fun getHighlightingLexer() = ElclHighlightingLexer(validationRules)

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> = pack(
        when (tokenType) {
            ElclTypes.COMMENT -> ElclTextAttributes.COMMENT
            ElclTypes.NAME -> ElclTextAttributes.NAME
            ElclTypes.TEXT_NAME -> ElclTextAttributes.TEXT_NAME
            ElclTypes.META_NAME -> ElclTextAttributes.META_NAME
            ElclTypes.SECTION_MAP_OPEN, ElclTypes.SECTION_MAP_CLOSE,
            ElclTypes.SECTION_LIST_OPEN, ElclTypes.SECTION_LIST_CLOSE,
            ElclTypes.SECTION_DECORATION, ElclTypes.DOT, ElclTypes.COMMA,
            ElclTypes.LIST_MARKER -> ElclTextAttributes.STRUCTURE
            ElclTypes.ASSIGN -> ElclTextAttributes.OPERATOR
            ElclTypes.INTEGER, ElclTypes.FLOAT -> ElclTextAttributes.NUMBER
            ElclTypes.BOOLEAN -> ElclTextAttributes.BOOLEAN
            ElclTypes.TEXT, ElclTypes.MULTILINE_TEXT_CONTENT -> ElclTextAttributes.STRING
            ElclTypes.CODE, ElclTypes.MULTILINE_CODE_CONTENT -> ElclTextAttributes.CODE
            ElclTypes.REGEX, ElclTypes.MULTILINE_REGEX_CONTENT -> ElclTextAttributes.REGEX
            ElclTypes.BYTES, ElclTypes.MULTILINE_BYTES_CONTENT -> ElclTextAttributes.BYTES
            ElclTypes.DATE, ElclTypes.TIME, ElclTypes.DATE_TIME, ElclTypes.TIME_DELTA -> ElclTextAttributes.DATE_TIME
            ElclTypes.MULTILINE_TEXT_OPEN, ElclTypes.MULTILINE_TEXT_CLOSE,
            ElclTypes.MULTILINE_CODE_OPEN, ElclTypes.MULTILINE_CODE_CLOSE,
            ElclTypes.MULTILINE_REGEX_OPEN, ElclTypes.MULTILINE_REGEX_CLOSE,
            ElclTypes.MULTILINE_BYTES_OPEN, ElclTypes.MULTILINE_BYTES_CLOSE -> ElclTextAttributes.MULTILINE_DELIMITER
            ElclHighlightingLexer.VR_RESERVED -> ElclTextAttributes.VR_RESERVED
            ElclHighlightingLexer.VR_FIELD -> ElclTextAttributes.VR_FIELD
            ElclHighlightingLexer.VR_TYPE -> ElclTextAttributes.VR_TYPE
            TokenType.BAD_CHARACTER, ElclTypes.BAD_CHARACTER -> ElclTextAttributes.BAD_CHARACTER
            else -> null
        },
    )
}

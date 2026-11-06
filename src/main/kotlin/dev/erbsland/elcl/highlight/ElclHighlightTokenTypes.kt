package dev.erbsland.elcl.highlight

import dev.erbsland.elcl.ElclTokenType

/** Synthetic token types used only by the editor highlighter. */
internal object ElclHighlightTokenTypes {
    val SECTION_NAME = ElclTokenType("SECTION_NAME")
    val SECTION_TEXT_NAME = ElclTokenType("SECTION_TEXT_NAME")
    val SECTION_LIST_DELIMITER = ElclTokenType("SECTION_LIST_DELIMITER")
    val PLACEHOLDER_TYPE = ElclTokenType("PLACEHOLDER_TYPE")
    val PLACEHOLDER_PARAMETER = ElclTokenType("PLACEHOLDER_PARAMETER")
    val PLACEHOLDER_DELIMITER = ElclTokenType("PLACEHOLDER_DELIMITER")
    val TEXT_ESCAPE = ElclTokenType("TEXT_ESCAPE")
    val MULTILINE_IGNORED_WHITESPACE = ElclTokenType("MULTILINE_IGNORED_WHITESPACE")
    val VR_RESERVED = ElclTokenType("VR_RESERVED")
    val VR_FIELD = ElclTokenType("VR_FIELD")
    val VR_TYPE = ElclTokenType("VR_TYPE")
}

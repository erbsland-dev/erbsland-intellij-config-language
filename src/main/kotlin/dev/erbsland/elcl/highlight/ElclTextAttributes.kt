package dev.erbsland.elcl.highlight

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey

/** Stable color-scheme keys for every ELCL syntax category. */
object ElclTextAttributes {
    val COMMENT = key("ELCL_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
    val NAME = key("ELCL_NAME", DefaultLanguageHighlighterColors.INSTANCE_FIELD)
    val TEXT_NAME = key("ELCL_TEXT_NAME", DefaultLanguageHighlighterColors.LABEL)
    val SECTION_NAME = key("ELCL_SECTION_NAME", DefaultLanguageHighlighterColors.CLASS_NAME)
    val SECTION_TEXT_NAME = key("ELCL_SECTION_TEXT_NAME", DefaultLanguageHighlighterColors.LABEL)
    val META_NAME = key("ELCL_META_NAME", DefaultLanguageHighlighterColors.METADATA)
    /** Retains the original key name so existing user color schemes keep working. */
    val STRUCTURE = key("ELCL_STRUCTURE", DefaultLanguageHighlighterColors.BRACKETS)
    val SECTION_LIST_DELIMITER = key("ELCL_SECTION_LIST_DELIMITER", DefaultLanguageHighlighterColors.MARKUP_TAG)
    val SECTION_NAME_SEPARATOR = key("ELCL_SECTION_NAME_SEPARATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
    val VALUE_LIST_SEPARATOR = key("ELCL_VALUE_LIST_SEPARATOR", DefaultLanguageHighlighterColors.COMMA)
    val OPERATOR = key("ELCL_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
    val NUMBER = key("ELCL_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
    val BOOLEAN = key("ELCL_BOOLEAN", DefaultLanguageHighlighterColors.KEYWORD)
    val STRING = key("ELCL_STRING", DefaultLanguageHighlighterColors.STRING)
    val ESCAPE = key("ELCL_ESCAPE", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE)
    val CODE = key("ELCL_CODE", DefaultLanguageHighlighterColors.TEMPLATE_LANGUAGE_COLOR)
    val REGEX = key("ELCL_REGEX", DefaultLanguageHighlighterColors.STRING)
    val BYTES = key("ELCL_BYTES", DefaultLanguageHighlighterColors.CONSTANT)
    val DATE_TIME = key("ELCL_DATE_TIME", DefaultLanguageHighlighterColors.CONSTANT)
    val MULTILINE_DELIMITER = key("ELCL_MULTILINE_DELIMITER", DefaultLanguageHighlighterColors.MARKUP_TAG)
    val MULTILINE_TAG = key("ELCL_MULTILINE_TAG", DefaultLanguageHighlighterColors.METADATA)
    val MULTILINE_IGNORED_WHITESPACE = key(
        "ELCL_MULTILINE_IGNORED_WHITESPACE",
        DefaultLanguageHighlighterColors.LINE_COMMENT,
    )
    val VR_RESERVED = key("ELCL_VR_RESERVED", DefaultLanguageHighlighterColors.KEYWORD)
    val VR_FIELD = key("ELCL_VR_FIELD", DefaultLanguageHighlighterColors.INSTANCE_METHOD)
    val VR_TYPE = key("ELCL_VR_TYPE", DefaultLanguageHighlighterColors.CLASS_REFERENCE)
    val BAD_CHARACTER = key("ELCL_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)

    private fun key(name: String, fallback: TextAttributesKey): TextAttributesKey =
        TextAttributesKey.createTextAttributesKey(name, fallback)
}

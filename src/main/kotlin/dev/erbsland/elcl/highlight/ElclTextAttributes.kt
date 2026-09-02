package dev.erbsland.elcl.highlight

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey

object ElclTextAttributes {
    val COMMENT = key("ELCL_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
    val NAME = key("ELCL_NAME", DefaultLanguageHighlighterColors.INSTANCE_FIELD)
    val TEXT_NAME = key("ELCL_TEXT_NAME", DefaultLanguageHighlighterColors.LABEL)
    val META_NAME = key("ELCL_META_NAME", DefaultLanguageHighlighterColors.METADATA)
    val STRUCTURE = key("ELCL_STRUCTURE", DefaultLanguageHighlighterColors.BRACKETS)
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
    val VR_RESERVED = key("ELCL_VR_RESERVED", DefaultLanguageHighlighterColors.KEYWORD)
    val VR_FIELD = key("ELCL_VR_FIELD", DefaultLanguageHighlighterColors.INSTANCE_METHOD)
    val VR_TYPE = key("ELCL_VR_TYPE", DefaultLanguageHighlighterColors.CLASS_REFERENCE)
    val BAD_CHARACTER = key("ELCL_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)

    private fun key(name: String, fallback: TextAttributesKey): TextAttributesKey =
        TextAttributesKey.createTextAttributesKey(name, fallback)
}

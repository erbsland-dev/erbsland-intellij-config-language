package dev.erbsland.elcl.highlight

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import dev.erbsland.elcl.ElclFileType
import javax.swing.Icon

class ElclColorSettingsPage : ColorSettingsPage {
    override fun getIcon(): Icon = ElclFileType.icon
    override fun getHighlighter(): SyntaxHighlighter = ElclSyntaxHighlighter(true)
    override fun getDemoText(): String = DEMO
    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null
    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
    override fun getDisplayName(): String = "Erbsland Configuration Language"

    companion object {
        private val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Comment", ElclTextAttributes.COMMENT),
            AttributesDescriptor("Name", ElclTextAttributes.NAME),
            AttributesDescriptor("Text name", ElclTextAttributes.TEXT_NAME),
            AttributesDescriptor("Meta name", ElclTextAttributes.META_NAME),
            AttributesDescriptor("Structure and punctuation", ElclTextAttributes.STRUCTURE),
            AttributesDescriptor("Assignment operator", ElclTextAttributes.OPERATOR),
            AttributesDescriptor("Number", ElclTextAttributes.NUMBER),
            AttributesDescriptor("Boolean", ElclTextAttributes.BOOLEAN),
            AttributesDescriptor("Text", ElclTextAttributes.STRING),
            AttributesDescriptor("Escape sequence", ElclTextAttributes.ESCAPE),
            AttributesDescriptor("Code", ElclTextAttributes.CODE),
            AttributesDescriptor("Regular expression", ElclTextAttributes.REGEX),
            AttributesDescriptor("Bytes", ElclTextAttributes.BYTES),
            AttributesDescriptor("Date and time", ElclTextAttributes.DATE_TIME),
            AttributesDescriptor("Multiline delimiter", ElclTextAttributes.MULTILINE_DELIMITER),
            AttributesDescriptor("Validation rules//Reserved name", ElclTextAttributes.VR_RESERVED),
            AttributesDescriptor("Validation rules//Rule field", ElclTextAttributes.VR_FIELD),
            AttributesDescriptor("Validation rules//Type identifier", ElclTextAttributes.VR_TYPE),
            AttributesDescriptor("Invalid syntax", ElclTextAttributes.BAD_CHARACTER),
        )

        private val DEMO = """
            # ELCL syntax highlighting preview
            @version: "1.0"
            @features: "all"

            --------[ Server . Main ]--------------------------------
            Name: "Example"
            Enabled: yes
            Port: 8080
            Timeout: 30s
            Started: 2026-09-02T10:15:00Z
            Pattern: /[a-z]+/
            Data: <01 ab cd ef>

            Message: <triple>
                A multiline value.
                <triple>

            [server.port]
            type: "integer"
            minimum: 1
            maximum: 65535
        """.trimIndent().replace("<triple>", "\"\"\"")
    }
}

package dev.erbsland.elcl.highlight

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import dev.erbsland.elcl.ElclFileType
import javax.swing.Icon

/** Color-scheme page with logically grouped ELCL attributes and a complete preview. */
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
            AttributesDescriptor("General//Comment", ElclTextAttributes.COMMENT),
            AttributesDescriptor("General//Meta name", ElclTextAttributes.META_NAME),
            AttributesDescriptor("General//Invalid syntax", ElclTextAttributes.BAD_CHARACTER),
            AttributesDescriptor("Sections//Delimiter", ElclTextAttributes.STRUCTURE),
            AttributesDescriptor("Sections//Section-list delimiter", ElclTextAttributes.SECTION_LIST_DELIMITER),
            AttributesDescriptor("Sections//Name", ElclTextAttributes.SECTION_NAME),
            AttributesDescriptor("Sections//Text name", ElclTextAttributes.SECTION_TEXT_NAME),
            AttributesDescriptor("Sections//Name separator", ElclTextAttributes.SECTION_NAME_SEPARATOR),
            AttributesDescriptor("Values//Name", ElclTextAttributes.NAME),
            AttributesDescriptor("Values//Text name", ElclTextAttributes.TEXT_NAME),
            AttributesDescriptor("Values//Assignment operator", ElclTextAttributes.OPERATOR),
            AttributesDescriptor("Values//Value-list separator", ElclTextAttributes.VALUE_LIST_SEPARATOR),
            AttributesDescriptor("Values//Number", ElclTextAttributes.NUMBER),
            AttributesDescriptor("Values//Boolean", ElclTextAttributes.BOOLEAN),
            AttributesDescriptor("Values//Text", ElclTextAttributes.STRING),
            AttributesDescriptor("Values//Escape sequence", ElclTextAttributes.ESCAPE),
            AttributesDescriptor("Values//Code", ElclTextAttributes.CODE),
            AttributesDescriptor("Values//Regular expression", ElclTextAttributes.REGEX),
            AttributesDescriptor("Values//Bytes", ElclTextAttributes.BYTES),
            AttributesDescriptor("Values//Date and time", ElclTextAttributes.DATE_TIME),
            AttributesDescriptor("Values//Multiline delimiter", ElclTextAttributes.MULTILINE_DELIMITER),
            AttributesDescriptor("Values//Language or format identifier", ElclTextAttributes.MULTILINE_TAG),
            AttributesDescriptor("Values//Ignored multiline whitespace", ElclTextAttributes.MULTILINE_IGNORED_WHITESPACE),
            AttributesDescriptor("Validation rules//Reserved name", ElclTextAttributes.VR_RESERVED),
            AttributesDescriptor("Validation rules//Rule field", ElclTextAttributes.VR_FIELD),
            AttributesDescriptor("Validation rules//Type identifier", ElclTextAttributes.VR_TYPE),
        )

        private val DEMO = """
            # ELCL syntax highlighting preview
            @version: "1.0"
            @features: "all"

            --------[ Server . "Main instance" ]--------------------
            "Display name": "Example\NServer \U{1f642}"
            Enabled: yes
            Port: 8080
            Ports: 8080, 8081
            Timeout: 30s
            Started: 2026-09-02T10:15:00Z
            Pattern: /[a-z]+/
            Data: <hex: 01 ab cd ef>
            Inline Code: `server.start()`

            Startup Code: ```kotlin # Injected when Kotlin is available
                fun start() = println("ready")
                ``` # End of code

            --------*[ Server . Peer ]*------------------------------
            Name: "Backup"

            Message: <triple>
                A multiline value.
                <triple>

            Broken Value: ???

            # ELCL-VR syntax highlighting preview
            [Server . Interface]
            Type: "Section List"

            [Server . Interface . VR Entry . Name]
            Type: "Text"
            Minimum: 1
            Maximum: 32

            *[Server . VR Key]*
            Name: "Interface Name"
            Key: "Interface.VR Entry.Name"
        """.trimIndent().replace("<triple>", "\"\"\"")
    }
}

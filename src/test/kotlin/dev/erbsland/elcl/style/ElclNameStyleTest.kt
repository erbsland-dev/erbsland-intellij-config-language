package dev.erbsland.elcl.style

import com.intellij.application.options.CodeStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class ElclNameStyleTest {
    @Test
    fun `renders all six supported name forms`() {
        assertEquals(
            listOf("foo_bar", "Foo_Bar", "FOO_BAR", "foo bar", "Foo Bar", "FOO BAR"),
            ElclNameStyle.entries.map { formatElclName("foo_bar", it) },
        )
        assertEquals("VR Entry", formatElclName("vr_entry", ElclNameStyle.TITLE_SPACE))
    }

    @Test
    fun `detects section value and semantic identifier styles independently`() {
        val settings = settings()
        val text = """
            [foo_bar]
            first_value: "Text"
            type: "first_identifier"
            [bar_baz]
            second_value: 2
            mode: "second_identifier"
            [baz_qux]
            third_value: 3
            key: "third_identifier"
        """.trimIndent()

        val styles = ElclNameStyleResolver.forText(text, settings)
        assertEquals(ElclNameStyle.LOWER_UNDERSCORE, styles.section)
        assertEquals(ElclNameStyle.LOWER_UNDERSCORE, styles.value)
        assertEquals(ElclNameStyle.LOWER_UNDERSCORE, styles.identifier)
    }

    @Test
    fun `requires a strict greater than two thirds majority per style dimension`() {
        val settings = settings()
        val text = """
            [foo_bar]
            [bar_baz]
            [BAZ_QUX]
        """.trimIndent()

        val styles = ElclNameStyleResolver.forText(text, settings)
        assertEquals(ElclNameStyle.TITLE_UNDERSCORE, styles.section)
    }

    @Test
    fun `prose strings and text names do not influence detection`() {
        val settings = settings()
        val text = """
            [First Section]
            "literal_key": "FOO_BAR"
            Title: "LOUD_IDENTIFIER"
            Description: "ANOTHER_IDENTIFIER"
            Error: "THIRD_IDENTIFIER"
        """.trimIndent()

        val styles = ElclNameStyleResolver.forText(text, settings)
        assertEquals(ElclNameStyle.TITLE_SPACE, styles.identifier)
    }

    @Test
    fun `automatic detection can be disabled independently`() {
        val settings = settings().apply {
            SECTION_NAME_STYLE = ElclNameStyle.UPPER_UNDERSCORE.name
            DETECT_SECTION_NAME_STYLE = false
            VALUE_NAME_STYLE = ElclNameStyle.LOWER_SPACE.name
            DETECT_VALUE_NAME_STYLE = false
            IDENTIFIER_STYLE = ElclNameStyle.TITLE_UNDERSCORE.name
            DETECT_IDENTIFIER_STYLE = false
        }

        val styles = ElclNameStyleResolver.forText("[foo bar]\nfoo_bar: \"foo bar\"", settings)
        assertEquals(ElclNameStyle.UPPER_UNDERSCORE, styles.section)
        assertEquals(ElclNameStyle.LOWER_SPACE, styles.value)
        assertEquals(ElclNameStyle.TITLE_UNDERSCORE, styles.identifier)
    }

    private fun settings(): ElclCodeStyleSettings = ElclCodeStyleSettings(CodeStyle.createTestSettings())
}

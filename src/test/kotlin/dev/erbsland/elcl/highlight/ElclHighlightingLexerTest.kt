package dev.erbsland.elcl.highlight

import com.intellij.psi.tree.IElementType
import dev.erbsland.elcl.psi.ElclTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElclHighlightingLexerTest {
    @Test
    fun `section and value names use distinct token types`() {
        val tokens = lex("[Server Main]\nValue Name: 42\n")

        assertEquals(ElclHighlightingLexer.SECTION_NAME, tokens.first { it.text == "Server Main" }.type)
        assertEquals(ElclTypes.NAME, tokens.first { it.text == "Value Name" }.type)
    }

    @Test
    fun `the complete section-list delimiter uses its distinct token type`() {
        val tokens = lex("---*[Server.Main]*---\n*[Compact]\n")

        assertEquals(
            listOf("---", "*[", "]*", "---", "*[", "]"),
            tokens.filter { it.type == ElclHighlightingLexer.SECTION_LIST_DELIMITER }.map { it.text },
        )
        assertEquals(
            listOf("Server", "Main", "Compact"),
            tokens.filter { it.type == ElclHighlightingLexer.SECTION_NAME }.map { it.text },
        )
    }

    @Test
    fun `regular section delimiters keep the standard structure token types`() {
        val tokens = lex("---[Server]---\n")

        assertEquals(emptyList<String>(), tokens.filter { it.type == ElclHighlightingLexer.SECTION_LIST_DELIMITER }.map { it.text })
        assertEquals(ElclTypes.SECTION_MAP_OPEN, tokens.first { it.text == "[" }.type)
        assertEquals(ElclTypes.SECTION_MAP_CLOSE, tokens.first { it.text == "]" }.type)
    }

    @Test
    fun `section text names and text escapes have distinct token types`() {
        val tokens = lex("[Server.\"Primary user\"]\nMessage: \"Line one\\NLine two \\U{20} \\T\"\n")

        assertEquals(
            ElclHighlightingLexer.SECTION_TEXT_NAME,
            tokens.first { it.text == "\"Primary user\"" }.type,
        )
        assertEquals(
            listOf("\\N", "\\U{20}", "\\T"),
            tokens.filter { it.type == ElclHighlightingLexer.TEXT_ESCAPE }.map { it.text },
        )
    }

    @Test
    fun `discarded multiline indentation and trailing whitespace are distinct`() {
        val text = """
            [Main]
            Text1: <triple>
                Hello1<trailing>
                Hello2
                <triple>
            Text2:
              <triple>
                Hello1
                  Hello2
              <triple>
        """.trimIndent()
            .replace("<triple>", "\"\"\"")
            .replace("<trailing>", "  ")
        val tokens = lex(text)
        val ignored = tokens.filter { it.type == ElclHighlightingLexer.MULTILINE_IGNORED_WHITESPACE }

        val text1Hello = text.indexOf("    Hello1")
        assertTrue("ignored=$ignored; tokens=$tokens", ignored.any { it.start == text1Hello && it.text == "    " })
        assertTrue(ignored.any { it.start == text.indexOf("  \n", text1Hello) && it.text == "  " })

        val text2 = text.indexOf("Text2:")
        val text2Hello = text.indexOf("    Hello1", text2)
        assertTrue(ignored.any { it.start == text2Hello && it.text == "  " })
        assertEquals("  Hello1", tokens.first { it.start == text2Hello + 2 }.text)
        val text2SecondLine = text.indexOf("      Hello2", text2)
        assertTrue(ignored.any { it.start == text2SecondLine && it.text == "  " })
        assertEquals("    Hello2", tokens.first { it.start == text2SecondLine + 2 }.text)
    }

    @Test
    fun `color settings are grouped and preview every requested construct`() {
        val page = ElclColorSettingsPage()
        val names = page.attributeDescriptors.map { it.displayName }
        assertTrue(names.contains("Sections//Text name"))
        assertTrue(names.contains("Sections//Name separator"))
        assertTrue(names.contains("Values//Value-list separator"))
        assertTrue(names.contains("Values//Language or format identifier"))
        assertTrue(page.demoText.contains("\"Display name\""))
        assertTrue(page.demoText.contains("```kotlin"))
        assertTrue(page.demoText.contains("Inline Code"))
        assertTrue(page.demoText.contains("???"))
        assertTrue(page.demoText.contains("VR Entry"))
        assertTrue(page.demoText.contains("VR Key"))
    }

    @Test
    fun `validation rules highlight escape prefixes and keep suffixes ordinary`() {
        val tokens = lex("[App.VR Entry]\n*[App.VR Key]*\n[App.VR Headset]\n[App.VR VR Headset]\nVR VR Value: 1\n", true)
        val reserved = tokens.filter { it.type == ElclHighlightingLexer.VR_RESERVED }.map { it.text }

        assertEquals(listOf("VR Entry", "VR Key", "VR Headset", "VR VR ", "VR VR "), reserved)
        assertEquals(ElclHighlightingLexer.SECTION_NAME, tokens.last { it.text == "Headset" }.type)
        assertEquals(ElclTypes.NAME, tokens.first { it.text == "Value" }.type)
    }

    @Test
    fun `escaped names handle all spellings and restart at every fragment`() {
        val text = "[vr_vr_3d.vR_Vr_type.VR Literal Matches]\nvr vr matches: 1\nvr_vr_vr_name: 2\nvr_literal_type: 3\n[vr_literal_vr_name.vr_literal_vr_literal_type]\n"
        val tokens = lex(text, true)
        assertEquals(listOf("vr_vr_", "vR_Vr_", "VR Literal ", "vr vr ", "vr_vr_", "vr_literal_", "vr_literal_", "vr_literal_"),
            tokens.filter { it.type == ElclHighlightingLexer.VR_RESERVED }.map { it.text })
        assertEquals(ElclTypes.NAME, tokens.first { it.text == "matches" }.type)
        assertEquals(ElclTypes.NAME, tokens.first { it.text == "vr_name" }.type)
        assertEquals(ElclHighlightingLexer.SECTION_NAME, tokens.first { it.text == "3d" }.type)
        assertEquals(ElclHighlightingLexer.SECTION_NAME, tokens.first { it.text == "Matches" }.type)
        assertEquals(ElclHighlightingLexer.SECTION_NAME, tokens.last { it.text == "vr_name" }.type)
        assertEquals(ElclHighlightingLexer.SECTION_NAME, tokens.first { it.text == "vr_literal_type" }.type)
        val lexer = ElclHighlightTokenClassifier(true)
        lexer.start(text)
        tokens.forEach { token ->
            val restarted = ElclHighlightTokenClassifier(true)
            restarted.start(text, lexer.tokenStart, text.length, lexer.state)
            assertEquals(token.type, restarted.tokenType)
            assertEquals(token.start, restarted.tokenStart)
            assertEquals(token.text, text.substring(restarted.tokenStart, restarted.tokenEnd))
            restarted.advance()
            lexer.advance()
            assertEquals(lexer.tokenType, restarted.tokenType)
            assertEquals(lexer.tokenStart, restarted.tokenStart)
            assertEquals(lexer.tokenEnd, restarted.tokenEnd)
        }
        val ordinary = lex(text)
        assertTrue(ordinary.none { it.type == ElclHighlightingLexer.VR_RESERVED })
        assertTrue(ordinary.any { it.text == "vr_vr_3d" })
    }

    private fun lex(text: String, validationRules: Boolean = false): List<Token> {
        val lexer = ElclHighlightingLexer(validationRules)
        lexer.start(text)
        return buildList {
            while (lexer.tokenType != null) {
                add(Token(lexer.tokenType!!, text.substring(lexer.tokenStart, lexer.tokenEnd), lexer.tokenStart))
                lexer.advance()
            }
        }
    }

    private data class Token(val type: IElementType, val text: String, val start: Int)
}

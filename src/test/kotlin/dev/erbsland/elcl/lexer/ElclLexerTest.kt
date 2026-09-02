package dev.erbsland.elcl.lexer

import com.intellij.psi.TokenType
import dev.erbsland.elcl.psi.ElclTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElclLexerTest {
    @Test
    fun `lexes representative ELCL token families`() {
        val text = """
            # comment
            *[Servers]*
            enabled: yes
            count: 0x2a
            size: 2 MiB
            ratio: 1.25e2
            born: 2026-09-02
            at: 12:34:56Z
            wait: 5 milliseconds
            text: "hello"
            code: `hello()`
            regex: /[a-z]+/
            bytes: <de ad be ef>
        """.trimIndent()
        val types = lex(text)
        assertTrue(types.containsAll(listOf(
            ElclTypes.COMMENT, ElclTypes.SECTION_LIST_OPEN, ElclTypes.SECTION_LIST_CLOSE,
            ElclTypes.BOOLEAN, ElclTypes.INTEGER, ElclTypes.FLOAT, ElclTypes.DATE, ElclTypes.TIME,
            ElclTypes.TIME_DELTA, ElclTypes.TEXT, ElclTypes.CODE, ElclTypes.REGEX, ElclTypes.BYTES,
        )))
        assertTrue("valid fixture must not emit a bad character", TokenType.BAD_CHARACTER !in types)
    }

    @Test
    fun `restart state continues multiline text`() {
        val text = "[main]\nvalue: \"\"\"\nbody\n\"\"\"\n"
        val lexer = ElclLexerAdapter()
        lexer.start(text)
        while (lexer.tokenType != ElclTypes.MULTILINE_TEXT_CONTENT) lexer.advance()
        val offset = lexer.tokenStart
        val state = lexer.state
        val restarted = ElclLexerAdapter()
        restarted.start(text, offset, text.length, state)
        assertEquals(ElclTypes.MULTILINE_TEXT_CONTENT, restarted.tokenType)
    }

    private fun lex(text: String): List<Any> {
        val lexer = ElclLexerAdapter()
        lexer.start(text)
        val result = mutableListOf<Any>()
        while (lexer.tokenType != null) {
            result += lexer.tokenType!!
            lexer.advance()
        }
        return result
    }
}

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

    @Test
    fun `multiline headers and comments are separate from content`() {
        val text = """
            [main]
            code: ```kotlin # opener
                println("# remains code")
                ``` # closer
            bytes: <<<hex # opener
                01 02 # bytes comment
                >>> # closer
            regex: /// # opener
                a\#b # regex comment
                /// # closer
        """.trimIndent()

        val tokens = lexTokens(text)
        assertTrue(tokens.any { it.type == ElclTypes.MULTILINE_CODE_LANGUAGE && it.text == "kotlin" })
        assertTrue(tokens.toString(), tokens.any { it.type == ElclTypes.MULTILINE_BYTES_FORMAT && it.text == "hex" })
        assertTrue(tokens.any { it.type == ElclTypes.MULTILINE_CODE_CONTENT && "# remains code" in it.text })
        assertTrue(tokens.any { it.type == ElclTypes.MULTILINE_REGEX_CONTENT && "\\#" in it.text })
        assertTrue(tokens.none { it.type == ElclTypes.MULTILINE_BYTES_CONTENT && "comment" in it.text })
        assertTrue(tokens.none { it.type == ElclTypes.MULTILINE_REGEX_CONTENT && "comment" in it.text })
        assertEquals(8, tokens.count { it.type == ElclTypes.COMMENT })
    }

    @Test
    fun `restart state continues every multiline header and body`() {
        listOf(
            Triple("value: \"\"\" # text\n  body\n  \"\"\"\n", ElclTypes.COMMENT, ElclTypes.MULTILINE_TEXT_CONTENT),
            Triple("value: ```kotlin # code\n  body\n  ```\n", ElclTypes.MULTILINE_CODE_LANGUAGE, ElclTypes.MULTILINE_CODE_CONTENT),
            Triple("value: /// # regex\n  body\n  ///\n", ElclTypes.COMMENT, ElclTypes.MULTILINE_REGEX_CONTENT),
            Triple("value: <<<hex # bytes\n  01\n  >>>\n", ElclTypes.MULTILINE_BYTES_FORMAT, ElclTypes.MULTILINE_BYTES_CONTENT),
        ).forEach { (text, headerType, bodyType) ->
            assertRestartedToken(text, headerType)
            assertRestartedToken(text, bodyType)
        }
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

    private fun lexTokens(text: String): List<Token> {
        val lexer = ElclLexerAdapter()
        lexer.start(text)
        return buildList {
            while (lexer.tokenType != null) {
                add(Token(lexer.tokenType!!, text.substring(lexer.tokenStart, lexer.tokenEnd)))
                lexer.advance()
            }
        }
    }

    private fun assertRestartedToken(text: String, expectedType: Any) {
        val lexer = ElclLexerAdapter()
        lexer.start(text)
        while (lexer.tokenType != null && lexer.tokenType != expectedType) lexer.advance()
        assertEquals(expectedType, lexer.tokenType)
        val restarted = ElclLexerAdapter()
        restarted.start(text, lexer.tokenStart, text.length, lexer.state)
        assertEquals(expectedType, restarted.tokenType)
    }

    private data class Token(val type: Any, val text: String)
}

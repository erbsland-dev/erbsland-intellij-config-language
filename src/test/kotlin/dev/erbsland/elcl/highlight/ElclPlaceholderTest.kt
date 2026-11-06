package dev.erbsland.elcl.highlight

import org.junit.Assert.*
import org.junit.Test

class ElclPlaceholderTest {
    private fun tokens(text: String, enabled: Boolean = true): List<Pair<String, String>> {
        val lexer = ElclHighlightingLexer(false) { enabled }
        lexer.start(text)
        val result = mutableListOf<Pair<String, String>>()
        while (lexer.tokenType != null) {
            if (lexer.tokenType in setOf(ElclHighlightTokenTypes.PLACEHOLDER_TYPE, ElclHighlightTokenTypes.PLACEHOLDER_PARAMETER,
                    ElclHighlightTokenTypes.PLACEHOLDER_DELIMITER, ElclHighlightTokenTypes.TEXT_ESCAPE)) {
                result += lexer.tokenType.toString() to text.substring(lexer.tokenStart, lexer.tokenEnd)
            }
            lexer.advance()
        }
        return result
    }
    private fun expression(body: String) = "$" + "{$body}"
    @Test fun `types parameters punctuation and filters are distinct`() {
        val result = tokens("[a]\nx: \"${expression("var:User|trim|fallback:Guest") }\"\n")
        assertEquals(listOf("var", "trim", "fallback"), result.filter { it.first.endsWith("PLACEHOLDER_TYPE") }.map { it.second })
        assertEquals(listOf("User", "Guest"), result.filter { it.first.endsWith("PLACEHOLDER_PARAMETER") }.map { it.second })
    }
    @Test fun `ordinary continued list and multiline texts are supported`() {
        val value = expression("custom source:|custom_filter")
        val text = "[a]\nx:\n    \"$value\"\ny:\n    * \"$value\"\n    * \"$value\"\nz: \"\"\"\n    before $value after\n    \"\"\"\n"
        assertEquals(8, tokens(text).count { it.first.endsWith("PLACEHOLDER_TYPE") })
        assertTrue(tokens(text, false).isEmpty())
    }
    @Test fun `meta names code and regex are excluded`() {
        val value = expression("var:x")
        val text = "@include:\n    \"$value\"\n[a.\"$value\"]\n\"$value\": `$value`\nregex: /$value/\ncode: ```\n    $value\n    ```\nregex2: ///\n    $value\n    ///\n"
        assertTrue(tokens(text).isEmpty())
    }
    @Test fun `escapes retain their own colors and do not become placeholder syntax`() {
        val text = "[a]\nx: \"\\${expression("var:no")} ${expression("var:a\\u007cb\\u007dc|trim")}\"\n"
        val result = tokens(text)
        assertEquals(listOf("var", "trim"), result.filter { it.first.endsWith("PLACEHOLDER_TYPE") }.map { it.second })
        assertEquals(listOf("\\$", "\\u007c", "\\u007d"), result.filter { it.first.endsWith("TEXT_ESCAPE") }.map { it.second })
    }
    @Test fun `malformed nested cross line and too many filters stay ordinary text`() {
        val malformed = listOf("${'$'}{var:x", expression("var:${expression("env:x")}"), expression("bad__name:x"), expression("var:x" + "|trim".repeat(17)), "${'$'}{var:x\n    }")
        for (value in malformed) assertTrue(value, tokens("[a]\nx: \"\"\"\n    $value\n    \"\"\"\n").none { it.first.contains("PLACEHOLDER") })
        assertEquals(17, tokens("[a]\nx: \"${expression("var:x" + "|trim".repeat(16))}\"\n").count { it.first.endsWith("PLACEHOLDER_TYPE") })
    }
}

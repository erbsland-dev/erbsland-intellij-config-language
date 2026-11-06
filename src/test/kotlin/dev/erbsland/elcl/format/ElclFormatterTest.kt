package dev.erbsland.elcl.format

import com.intellij.openapi.util.TextRange
import org.junit.Assert.*
import org.junit.Test

/** Golden cases ported from Core's SyntaxFormatterTest and SyntaxFormatTest. */
class ElclFormatterTest {
    private fun simple(vararg properties: Pair<FormatProperty, String>) = ElclFormat(ElclFormatStyle(mapOf(
        FormatProperty.SECTION_INDENT to "0", FormatProperty.SECTION_SUFFIX to "0",
        FormatProperty.SEPARATOR_MODE to "spaces", FormatProperty.SEPARATOR_POSITION to "0",
    ) + properties), emptyList())
    private fun format(text: String, options: ElclFormat = simple()): String = ElclFormatter().format(text, options).text

    @Test fun `names spacing and literal preservation`() {
        val input = "[my_section]\r\nmy_value=0x0a # comment\r\ntext:\"untouched_text\""
        val expected = "[ My Section ]\r\nMy Value: 0x0a # comment\r\nText: \"untouched_text\""
        assertEquals(expected, format(input))
        assertEquals(expected, format(expected))
    }
    @Test fun `default decorations relative sections and metadata match Core`() {
        val output = format("@VERSION = \"1.0\"\n[a]\nx:1\n[.b]\ny:2\n*[c]\nz:3\n", ElclFormat())
        assertTrue(output.startsWith("@version: \"1.0\"\n----[ A ]"))
        assertTrue(output.contains("\nX                            : 1\n"))
        assertTrue(output.contains("\n------[ . B ]----\n"))
        assertEquals(118, output.lineSequence().first { it.startsWith("----[ A") }.length)
        assertTrue(output.contains("\n---*[ C ]*"))
        assertEquals(output, format(output, ElclFormat()))
    }
    @Test fun `all value placement modes and comments`() {
        val input = "[a]\nlong_name: \"1234567890\" # note\n"
        val base = simple(FormatProperty.WIDTH to "24")
        val wrapped = format(input, base)
        assertEquals("[ A ]\nLong Name:\n    \"1234567890\" # note\n", wrapped)
        assertEquals(wrapped, format(wrapped, base))
        assertEquals("[ A ]\nLong Name: \"1234567890\" # note\n", format(wrapped, simple(FormatProperty.WRAPPING to "same_line")))
        assertEquals("[ A ]\nX:\n    1", format("[a]\nx:1", simple(FormatProperty.WRAPPING to "next_line")))
        assertEquals("[ A ]\nX:\n    1\n", format("[a]\nx:\n  1\n", simple(FormatProperty.WRAPPING to "keep")))
        val long = "[a]\nx: \"a value longer than the line\"\n"
        assertTrue(format(long, simple(FormatProperty.WIDTH to "10")).contains("X: \"a value"))
        assertTrue(format(long, simple(FormatProperty.WIDTH to "10", FormatProperty.WRAPPING to "when_too_long")).contains("X:\n    \"a value"))
        val attached = "[a]\nx: # attached\n    12\n"
        assertTrue(format(attached).contains("X: # attached\n    12"))
    }
    @Test fun `Unicode widths and newline conventions`() {
        val source = "[a]\r\nlong_name: \"界界\""
        assertEquals("[ A ]\r\nLong Name: \"界界\"", format(source, simple(FormatProperty.WIDTH to "15")))
        assertEquals("[ A ]\r\nLong Name:\r\n    \"界界\"", format(source, simple(FormatProperty.WIDTH to "14")))
        assertEquals("[ A ]\nLong Name: \"😀😀\"", format("[a]\nlong_name: \"😀😀\"", simple(FormatProperty.WIDTH to "15")))
        val mixed = "[a]\r\nx:1\n\r\ny:2"
        assertEquals("[ A ]\r\nX: 1\n\r\nY: 2", format(mixed))
    }
    @Test fun `section alignment infinity star and spacing options`() {
        assertEquals("-----*[ List ]*-----\n", format("*[list]\n", simple(FormatProperty.WIDTH to "20", FormatProperty.SECTION_INDENT to "infinite", FormatProperty.SECTION_SUFFIX to "infinite")))
        assertEquals("--------------[ A ]-\n", format("[a]\n", simple(FormatProperty.WIDTH to "20", FormatProperty.SECTION_INDENT to "infinite", FormatProperty.SECTION_SUFFIX to "1")))
        assertEquals("*[a.b]\n", format("*[a.b]*\n", simple(FormatProperty.SECTION_STAR to "false", FormatProperty.SPACE_AFTER_OPEN to "false", FormatProperty.SPACE_BEFORE_CLOSE to "false", FormatProperty.SPACE_AROUND_DOT to "false", FormatProperty.SECTION_CASE to "lower")))
        val comment = format("[a] # note\n", simple(FormatProperty.WIDTH to "20", FormatProperty.SECTION_SUFFIX to "infinite"))
        assertEquals(20, comment.trimEnd().length)
        assertTrue(comment.endsWith(" # note\n"))
    }
    @Test fun `vector reflow matrix preservation alignment and width fallback`() {
        assertEquals("[ A ]\nX:\n    * 1\n    * 2\n    * 3\n", format("[a]\nx:1,2,3\n", simple(FormatProperty.VECTOR to "multiline")))
        assertEquals("[ A ]\nX: 1, 2\n", format("[a]\nx:\n    * 1\n    * 2\n", simple(FormatProperty.VECTOR to "compact")))
        assertEquals("[ A ]\nLong Name:\n    1, 2, 3\n", format("[a]\nlong_name:1,2,3\n", simple(FormatProperty.VECTOR to "compact", FormatProperty.WIDTH to "16")))
        val matrix = "[a]\nx:\n    * 1,  22\n    * 333,4\n"
        assertEquals("[ A ]\nX:\n    * 1,  22\n    * 333,4\n", format(matrix, simple(FormatProperty.VECTOR to "compact")))
        assertEquals("[ A ]\nX:\n    * 1,   22\n    * 333, 4\n", format(matrix, simple(FormatProperty.MATRIX to "align")))
        assertEquals("[ A ]\nX:\n    * 1, 22\n    * 333, 4\n", format(matrix, simple(FormatProperty.MATRIX to "align", FormatProperty.WIDTH to "10")))
        assertTrue(format("[a]\nmixed_name:\n  * 1\n", simple(FormatProperty.VECTOR to "compact")).contains("\n    * 1\n"))
    }
    @Test fun `multiline content preserves additional indentation and delimiters`() {
        for ((open, close) in listOf("\"\"\"" to "\"\"\"", "```kotlin" to "```", "///" to "///", "<<<hex" to ">>>")) {
            val body = if (open == "<<<hex") "01 ff" else "content"
            val source = "[a]\nx: $open # open\n  $body  \n    $body\n  $close # close\n"
            val output = format(source)
            assertTrue(output, output.contains("    $body  \n      $body\n    $close # close"))
            assertEquals(output, format(output))
        }
        assertEquals("[ A ]\nX:\n    \"\"\"\n      content\n    \"\"\"\n", format("[a]\nx:\n  \"\"\"\n    content\n  \"\"\"\n"))
    }
    @Test fun `text names and text identifiers change only by explicit opt in`() {
        val source = "[a]\n\"my_name\": \"some_value\"\n\"file.txt\": \"not-an-identifier\"\n"
        val output = format(source, simple(FormatProperty.TEXT_NAME_CASE to "upper", FormatProperty.TEXT_IDENTIFIER_CASE to "title"))
        assertTrue(output.contains("\"MY NAME\": \"Some Value\""))
        assertTrue(output.contains("\"file.txt\": \"not-an-identifier\""))
        assertTrue(format(source).contains("\"my_name\": \"some_value\""))
        assertEquals("[ A ]\nMixed_Name: 1\n", format("[a]\nMixed Name:1\n", simple(FormatProperty.VALUE_CASE to "keep", FormatProperty.VALUE_SEPARATOR to "underscore")))
    }
    @Test fun `assignment positioning indentation and bullet spacing`() {
        assertEquals("[ A ]\nX  =    1\n", format("[a]\nx:1\n", simple(FormatProperty.SEPARATOR to "=", FormatProperty.SEPARATOR_POSITION to "2", FormatProperty.VALUE_MODE to "column", FormatProperty.VALUE_POSITION to "8")))
        assertEquals("[ A ]\nLong Name :1\n", format("[a]\nlong_name:1\n", simple(FormatProperty.SEPARATOR_MODE to "column", FormatProperty.SEPARATOR_POSITION to "1", FormatProperty.VALUE_POSITION to "0")))
        assertEquals("[ A ]\nX:\n\t\t*   1\n\t\t*   2\n", format("[a]\nx:1,2\n", simple(FormatProperty.VECTOR to "multiline", FormatProperty.INDENT_STYLE to "tabs", FormatProperty.INDENT_COUNT to "2", FormatProperty.BULLET_SPACES to "3")))
    }
    @Test fun `malformed statements preserved with surrounding recovery`() {
        val source = "[a]\nbroken: ???\nx:1\ntext:\"\"\"\n    content\n[healthy]\ny:2\n"
        val output = format(source)
        assertEquals("[ A ]\nbroken: ???\nX: 1\ntext:\"\"\"\n    content\n[ Healthy ]\nY: 2\n", output)
        assertEquals("[ A ]\nx:\n\n    1\nY: 2\n", format("[a]\nx:\n\n    1\ny:2\n"))
    }
    @Test fun `selection edits leave unrelated statements untouched and signed changes warn`() {
        val source = "@signature: \"sample\"\n[a]\nx:1\ny:2\n"
        val start = source.indexOf("x:")
        val result = ElclFormatter().format(source, simple(), listOf(TextRange(start, start + 1)))
        assertEquals(source.replace("x:1", "X: 1"), result.text)
        assertTrue(result.signedSourceChanged)
        val whitespace = ElclFormatter().format("[a]\nfoo_bar=1\n", simple(), whitespaceOnly = true).text
        assertEquals("[a]\nfoo_bar= 1\n", whitespace)
    }
    @Test fun `per property specificity later ties and original path matching`() {
        val snapshot = ElclSyntaxSnapshot("[server]\nport:3\n[server.deep]\nport:4\n")
        val shallow = ElclFormatSelector(paths = listOf(ElclPathPattern("SERVER.*")))
        val recursive = ElclFormatSelector(paths = listOf(ElclPathPattern("server.**.port")))
        assertNotNull(shallow.match(snapshot.statements[1])); assertNull(shallow.match(snapshot.statements[3]))
        assertNotNull(recursive.match(snapshot.statements[1])); assertNotNull(recursive.match(snapshot.statements[3]))
        val exact = ElclFormatRule(ElclFormatSelector(paths = listOf(ElclPathPattern("server.port"))), mapOf(FormatProperty.VALUE_CASE to "upper"))
        val glob = ElclFormatRule(shallow, mapOf(FormatProperty.VALUE_CASE to "lower", FormatProperty.SEPARATOR to "="))
        val style = ElclFormat(rules = listOf(exact, glob)).resolve(snapshot.statements[1])
        assertEquals("upper", style[FormatProperty.VALUE_CASE]); assertEquals("=", style[FormatProperty.SEPARATOR])
        assertEquals("title", ElclFormat(rules = listOf(exact, glob, exact.copy(patch = mapOf(FormatProperty.VALUE_CASE to "title")))).resolve(snapshot.statements[1])[FormatProperty.VALUE_CASE])
        assertTrue(ElclPathPattern("server.**").matches(listOf("server")))
        assertTrue(ElclPathPattern("s?rver.[pq]ort").matches(listOf("server", "port")))
        assertTrue(ElclPathPattern("section.\"a.b*\".x").matches(listOf("section", "\"a.b*\"", "x")))
        assertTrue(ElclPathPattern("section.*.x").matches(listOf("section", "\"a.b*\"", "x")))
        assertFalse(ElclPathPattern("section.a.x").matches(listOf("section", "\"a\"", "x")))
        for (bad in listOf("a.***", "a.b**", "a..b", "a.[xy", "a.\"open")) assertThrows(IllegalArgumentException::class.java) { ElclPathPattern(bad) }
    }
    @Test fun `all selector conditions and unresolved contexts`() {
        val s = ElclSyntaxSnapshot("@version:\"1.0\"\n[a]\nx:1\n*[.b]\ny:2\n[bad\nz:3\n").statements
        assertNotNull(ElclFormatSelector(metaValue = true, valueTypes = listOf("text")).match(s[0]))
        assertNull(ElclFormatSelector(metaValue = false).match(s[1]))
        assertNotNull(ElclFormatSelector(kind = "section", relativeSection = true, listSection = true, sectionNamesMoreThan = 0).match(s[3]))
        assertNull(ElclFormatSelector(sectionNamesMoreThan = 1).match(s[3]))
        assertNull(ElclFormatSelector(paths = listOf(ElclPathPattern("**"))).match(s.last()))
    }
    @Test fun `BOM and invalid literal statements are preserved`() {
        assertEquals("\uFEFF[ A ]\nX: 1\n", format("\uFEFF[a]\nx:1\n"))
        for (literal in listOf("<01 zz>", "\"bad\\q\"", "<01 f>")) {
            val input = "[a]\nbad:$literal\ngood:1\n"
            assertEquals("[ A ]\nbad:$literal\nGood: 1\n", format(input))
        }
    }
    @Test fun `every style property validates and serializes`() {
        for (property in FormatProperty.entries) {
            assertEquals(property.default, property.validate(property.default))
            for (choice in property.choices) assertEquals(choice, property.validate(choice))
            assertThrows(IllegalArgumentException::class.java) { property.validate("not_supported") }
        }
        val style = ElclFormatStyle.deserialize(ElclFormatStyle().serialize())
        for (property in FormatProperty.entries) assertEquals(property.default, style[property])
    }
}

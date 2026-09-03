package dev.erbsland.elcl.validation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ElclTextAnalyzerTest {
    @Test
    fun `accepts a representative ELCL document`() {
        val source = """
            @version: "1.0"
            @features: "advanced", "include"

            [server]
            port: 8080
            names:
                * "one"
                * "two"
            banner:
                ${"\"\"\""}
                hello
                ${"\"\"\""}

            [.tls]
            enabled: yes
        """.trimIndent()
        val diagnostics = ElclTextAnalyzer().analyze(source, false)
        assertTrue(diagnostics.joinToString("\n") { it.message }, diagnostics.none { it.severity == ElclDiagnosticSeverity.ERROR })
    }

    @Test
    fun `finds normalized conflicts and broken relative context`() {
        val source = """
            [.orphan]
            value: 1
            [Main Section]
            User Name: "a"
            user_name: "b"
        """.trimIndent()
        val messages = ElclTextAnalyzer().analyze(source, false).map { it.message }
        assertTrue(messages.any { "relative section" in it.lowercase() })
        assertTrue(messages.any { "duplicate" in it.lowercase() || "conflict" in it.lowercase() })
    }

    @Test
    fun `section-list entries have independent value scopes`() {
        val source = """
            ------------*[ Reference Groups ]
            Page: "core/application.rst"
            Title: "Application"

            ------------*[ Reference Groups ]
            Page: "math/integer_math.rst"
            Title: "Integer Math"
        """.trimIndent()

        val diagnostics = ElclTextAnalyzer().analyze(source, false)

        assertFalse(diagnostics.joinToString("\n") { it.message }, diagnostics.any { "duplicate" in it.message.lowercase() || "conflict" in it.message.lowercase() })
    }

    @Test
    fun `section-list paths continue through their most recent entries`() {
        val source = """
            *[server]
            name: "host01"
            [server.filter]
            reject: "udp"

            *[server]
            name: "host02"
            [server.filter]
            reject: "tcp"
        """.trimIndent()

        val diagnostics = ElclTextAnalyzer().analyze(source, false)

        assertFalse(diagnostics.joinToString("\n") { it.message }, diagnostics.any { "duplicate" in it.message.lowercase() || "conflict" in it.message.lowercase() })
    }

    @Test
    fun `duplicate values in one section-list entry are still rejected`() {
        val source = """
            *[server]
            port: 8080
            Port: 9000
        """.trimIndent()

        val diagnostics = ElclTextAnalyzer().analyze(source, false)

        assertTrue(diagnostics.any { "duplicate" in it.message.lowercase() || "conflict" in it.message.lowercase() })
    }

    @Test
    fun `inline multiline opener takes indentation from first content line`() {
        val source = """
            [client.interface]
            type: "SectionList"
            description: ${"\"\"\""}
                A list of client interfaces to open.
                Multiple interfaces are required for IPv4 and IPv6 support.
                ${"\"\"\""}
            minimum: 1
        """.trimIndent()

        val diagnostics = ElclTextAnalyzer().analyze(source, false)

        assertFalse(diagnostics.joinToString("\n") { it.message }, diagnostics.any { "multiline" in it.message.lowercase() })
    }

    @Test
    fun `continued multiline opener keeps assignment indentation as the pattern`() {
        val source = """
            [main]
            text:
                ${"\"\"\""}
                    Four leading spaces remain part of this text.
                ${"\"\"\""}
        """.trimIndent()

        val diagnostics = ElclTextAnalyzer().analyze(source, false)

        assertFalse(diagnostics.joinToString("\n") { it.message }, diagnostics.any { "multiline" in it.message.lowercase() })
    }

    @Test
    fun `all multiline families follow inline indentation and accept opener details`() {
        val source = """
            [values]
            text: ${"\"\"\""} # explanation
                text
                ${"\"\"\""} # done
            code: ```kotlin # language
                println("hello")
                ``` # done
            regex: ///
              [a-z]+
              ///
            bytes: <<<hex # format
                01 02 03
                >>> # done
        """.trimIndent()

        val diagnostics = ElclTextAnalyzer().analyze(source, false)

        assertFalse(diagnostics.joinToString("\n") { it.message }, diagnostics.any { "multiline" in it.message.lowercase() })
    }

    @Test
    fun `code language identifiers follow the ELCL length and character rules`() {
        val valid = ElclTextAnalyzer().analyze("[main]\ncode: ```cpp-20\n  body\n  ```\n", false)
        val invalid = ElclTextAnalyzer().analyze("[main]\ncode: ```9invalid\n  body\n  ```\n", false)

        assertFalse(valid.joinToString("\n") { it.message }, valid.any { "language identifier" in it.message.lowercase() })
        assertTrue(invalid.any { "language identifier" in it.message.lowercase() })
    }

    @Test
    fun `accepts supported byte syntax and reports exact single-line byte errors`() {
        val valid = "[main]\nData: <HEX: 01ff A0 7b>\n"
        assertFalse(ElclTextAnalyzer().analyze(valid, false).any { it.severity == ElclDiagnosticSeverity.ERROR })

        val source = "[main]\nSplit: <010 203>\nCharacter: <01x2>\nFormat: <dec: 0102>\n"
        val diagnostics = ElclTextAnalyzer().analyze(source, false)
        assertTrue(diagnostics.any { "splits a hexadecimal byte" in it.message && source.substring(it.range.startOffset, it.range.endOffset).isBlank() })
        assertTrue(diagnostics.any { "Illegal character 'x'" in it.message && source.substring(it.range.startOffset, it.range.endOffset) == "x" })
        assertTrue(diagnostics.any { "Unsupported byte-data format" in it.message && source.substring(it.range.startOffset, it.range.endOffset) == "dec" })
    }

    @Test
    fun `reports multiline byte format splits and characters at their source ranges`() {
        val source = "[main]\nData: <<<dec\n    010 203\n    01xz\n    >>>\n"
        val diagnostics = ElclTextAnalyzer().analyze(source, false)

        assertTrue(diagnostics.any { "Unsupported byte-data format" in it.message && source.substring(it.range.startOffset, it.range.endOffset) == "dec" })
        assertTrue(diagnostics.any { "splits a hexadecimal byte" in it.message && source.substring(it.range.startOffset, it.range.endOffset).isBlank() })
        assertTrue(diagnostics.any { "Illegal character 'x'" in it.message && source.substring(it.range.startOffset, it.range.endOffset) == "x" })
        assertTrue(diagnostics.any { "Illegal character 'z'" in it.message && source.substring(it.range.startOffset, it.range.endOffset) == "z" })
    }

    @Test
    fun `text escapes are case insensitive and Unicode scalar values are validated`() {
        val valid = "[main]\nText: \"\\N \\R \\T \\U0041 \\u{1f642}\"\n"
        assertFalse(ElclTextAnalyzer().analyze(valid, false).any { it.severity == ElclDiagnosticSeverity.ERROR })

        val source = "[main]\nUnknown: \"\\q\"\nNull: \"\\u0000\"\nSurrogate: \"\\U{D800}\"\n" +
            "TooHigh: \"\\u{110000}\"\nMultiline: \"\"\"\n    \\Q\n    \"\"\"\n"
        val diagnostics = ElclTextAnalyzer().analyze(source, false)
        assertTrue(diagnostics.any { "Unknown text escape" in it.message && source.substring(it.range.startOffset, it.range.endOffset) == "\\q" })
        assertTrue(diagnostics.any { "null code point" in it.message && source.substring(it.range.startOffset, it.range.endOffset) == "\\u0000" })
        assertTrue(diagnostics.any { "valid Unicode scalar" in it.message && source.substring(it.range.startOffset, it.range.endOffset) == "\\U{D800}" })
        assertTrue(diagnostics.any { "valid Unicode scalar" in it.message && source.substring(it.range.startOffset, it.range.endOffset) == "\\u{110000}" })
        assertTrue(diagnostics.any { "Unknown text escape" in it.message && source.substring(it.range.startOffset, it.range.endOffset) == "\\Q" })
    }

    @Test
    fun `reports multiline content and closing indentation mismatches`() {
        val source = """
            [main]
            text: ${"\"\"\""}
                first
              ${"\"\"\""}
        """.trimIndent()

        val messages = ElclTextAnalyzer().analyze(source, false).map { it.message }

        assertTrue(messages.any { "indentation pattern" in it })
    }

    @Test
    fun `resolves sorted local includes and rejects project escapes`() {
        val root = Files.createTempDirectory("elcl-includes")
        val parts = Files.createDirectories(root.resolve("parts"))
        Files.writeString(parts.resolve("b.elcl"), "[b]\nvalue: 2\n")
        Files.writeString(parts.resolve("a.elcl"), "[a]\nvalue: 1\n")
        val main = root.resolve("main.elcl")
        Files.writeString(main, "@include: \"parts/*.elcl\"\n")
        val valid = ElclTextAnalyzer(root).analyze(Files.readString(main), false, main)
        assertFalse(valid.any { it.message.contains("does not match") })

        val escaped = ElclTextAnalyzer(root).analyze("@include: \"../outside.elcl\"\n", false, main)
        assertTrue(escaped.any { it.severity == ElclDiagnosticSeverity.WARNING && it.message.contains("outside") })
    }

    @Test
    fun `include resets the relative-section context`() {
        val root = Files.createTempDirectory("elcl-include-context")
        val included = root.resolve("included.elcl")
        Files.writeString(included, "[included]\nvalue: 1\n")
        val main = root.resolve("main.elcl")
        val source = """
            [main]
            value: 1
            @include: "included.elcl"
            [.child]
            value: 2
        """.trimIndent()
        Files.writeString(main, source)

        val diagnostics = ElclTextAnalyzer(root).analyze(source, false, main)

        assertTrue(diagnostics.any { "relative section" in it.message.lowercase() })
    }

    @Test
    fun `included section-list entries merge as independent scopes`() {
        val root = Files.createTempDirectory("elcl-include-list")
        val included = root.resolve("included.elcl")
        Files.writeString(included, "*[server]\nname: \"included\"\n")
        val main = root.resolve("main.elcl")
        val source = """
            *[server]
            name: "main"
            @include: "included.elcl"
            *[server]
            name: "after"
        """.trimIndent()
        Files.writeString(main, source)

        val diagnostics = ElclTextAnalyzer(root).analyze(source, false, main)

        assertFalse(diagnostics.joinToString("\n") { it.message }, diagnostics.any { "duplicate" in it.message.lowercase() || "conflict" in it.message.lowercase() })
    }
}

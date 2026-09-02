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
}

package dev.erbsland.elcl.validation

import org.junit.Assert.assertTrue
import org.junit.Test

class ElclVrAnalyzerTest {
    @Test
    fun `accepts templates entries indexes and dependencies`() {
        val source = """
            [vr_template.port]
            type: "Integer"
            minimum: 1
            maximum: 65535

            [server]
            type: "Section"

            [server.port]
            use_template: "port"
            is_optional: yes

            [server.legacy_port]
            type: "Integer"
            is_optional: yes

            [filter]
            type: "SectionList"

            [filter.vr_entry]
            type: "Section"

            [filter.vr_entry.id]
            type: "Text"

            *[vr_key]*
            name: "filter_id"
            key: "filter.vr_entry.id"

            [server.filter]
            type: "Text"
            key: "filter_id"

            *[server.vr_dependency]*
            mode: "if_not"
            source: "port"
            target: "legacy_port"
        """.trimIndent()
        val diagnostics = ElclTextAnalyzer().analyze(source, true)
        assertTrue(diagnostics.joinToString("\n") { it.message }, diagnostics.none { it.severity == ElclDiagnosticSeverity.ERROR })
    }

    @Test
    fun `reports rule errors at their fields`() {
        val source = """
            [server.port]
            type: "Integer"
            use_template: "missing"
            minimum: 9000
            maximum: 1000
            matches: /x/
            matches_error: "bad"
            is_optional: yes
            default: 8080

            [server.vr_dependency]
            mode: "nand"
        """.trimIndent()
        val messages = ElclTextAnalyzer().analyze(source, true).map { it.message }
        assertTrue(messages.any { "both type and use_template" in it })
        assertTrue(messages.any { "maximum" in it })
        assertTrue(messages.any { "only applicable" in it })
        assertTrue(messages.any { "already makes" in it })
        assertTrue(messages.any { "section list" in it })
        assertTrue(messages.any { "Unknown dependency mode" in it })
    }

    @Test
    fun `validates alternatives and list entry rules`() {
        val source = """
            *[app.mode]*
            type: "Integer"
            default: 1

            *[app.mode]*
            type: "Text"
            default: "automatic"
            is_optional: yes

            [app.tags]
            type: "ValueList"

            [app.tags.vr_entry]
            type: "Text"
            default: "tag"
        """.trimIndent()
        val messages = ElclTextAnalyzer().analyze(source, true).map { it.message }
        assertTrue(messages.any { "Only one alternative may define a default" in it })
        assertTrue(messages.any { "is_optional must be defined on the first alternative" in it })
        assertTrue(messages.any { "vr_entry must not define a default" in it })
    }
}

package dev.erbsland.elcl.validation

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
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

    @Test
    fun `resolves index key paths relative to their vr_key scope`() {
        val source = """
            [Client]
            Type: "Section"

            [Client.Interface]
            Type: "SectionList"

            [Client.Interface.VR Entry.Name]
            Type: "Text"

            *[Client.VR Key]*
            Name: "interface_name"
            Key: "interface.vr_entry.name"
        """.trimIndent()

        val diagnostics = ElclTextAnalyzer().analyze(source, true)
        assertFalse(diagnostics.joinToString("\n") { it.message }, diagnostics.any { "Index key path" in it.message || "Indexed values" in it.message })
    }

    @Test
    fun `accepts index compatibility paths without explicit vr_entry`() {
        val source = """
            [Filter]
            Type: "SectionList"
            [Filter.VR Entry.Id]
            Type: "Integer"
            *[VR Key]*
            Name: "filter_id"
            Key: "filter.id"
        """.trimIndent()

        val diagnostics = ElclTextAnalyzer().analyze(source, true)
        assertFalse(diagnostics.joinToString("\n") { it.message }, diagnostics.any { "Index key path" in it.message || "Indexed values" in it.message })
    }

    @Test
    fun `index names are scoped and nearest visible definition wins`() {
        val source = """
            [Global Items]
            Type: "SectionList"
            [Global Items.VR Entry.Id]
            Type: "Text"
            *[VR Key]*
            Name: "id"
            Key: "global_items.vr_entry.id"

            [Client]
            Type: "Section"
            [Client.Local Items]
            Type: "SectionList"
            [Client.Local Items.VR Entry.Id]
            Type: "Text"
            *[Client.VR Key]*
            Name: "id"
            Key: "local_items.vr_entry.id"
            [Client.Selection]
            Type: "Text"
            Key: "id"
        """.trimIndent()

        val diagnostics = ElclTextAnalyzer().analyze(source, true)
        assertFalse(diagnostics.joinToString("\n") { it.message }, diagnostics.any { "Duplicate normalized index" in it.message || "Unknown vr_key index" in it.message })
    }

    @Test
    fun `escaped vr names are ordinary while unknown reserved names are rejected`() {
        val escaped = ElclTextAnalyzer().analyze("[Settings.VR VR Headset]\nType: \"Text\"", true)
        val reserved = ElclTextAnalyzer().analyze("[Settings.VR Headset]\nType: \"Text\"", true)

        assertFalse(escaped.joinToString("\n") { it.message }, escaped.any { "Unknown reserved" in it.message })
        assertTrue(reserved.any { "Unknown reserved" in it.message })
    }

    @Test
    fun `rejects invisible indexes and duplicate names in the same scope`() {
        val source = """
            [Client]
            Type: "Section"
            [Client.Items]
            Type: "SectionList"
            [Client.Items.VR Entry.Id]
            Type: "Text"
            *[Client.VR Key]*
            Name: "local"
            Key: "items.vr_entry.id"
            *[Client.VR Key]*
            Name: "LOCAL"
            Key: "items.vr_entry.id"
            [Outside]
            Type: "Text"
            Key: "local"
        """.trimIndent()

        val messages = ElclTextAnalyzer().analyze(source, true).map { it.message }
        assertTrue(messages.any { "Duplicate normalized index" in it })
        assertTrue(messages.any { "Unknown vr_key index" in it })
    }

    @Test
    fun `rejects nested section lists mixed composite roots and invalid target types`() {
        val source = """
            [First]
            Type: "SectionList"
            [First.VR Entry.Id]
            Type: "Text"
            [First.VR Entry.Children]
            Type: "SectionList"
            [First.VR Entry.Children.VR Entry.Id]
            Type: "Text"
            [Second]
            Type: "SectionList"
            [Second.VR Entry.Created]
            Type: "DateTime"
            *[VR Key]*
            Key: "first.vr_entry.children.vr_entry.id"
            *[VR Key]*
            Key: "first.vr_entry.id", "second.vr_entry.created"
        """.trimIndent()

        val messages = ElclTextAnalyzer().analyze(source, true).map { it.message }
        assertTrue(messages.any { "exactly one SectionList vr_entry" in it })
        assertTrue(messages.any { "same SectionList" in it })
        assertTrue(messages.any { "Integer or Text" in it })
    }
}

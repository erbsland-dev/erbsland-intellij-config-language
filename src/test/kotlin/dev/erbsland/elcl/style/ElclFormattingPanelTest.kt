package dev.erbsland.elcl.style

import com.intellij.openapi.options.ConfigurationException
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.EditorTextField
import com.intellij.util.xmlb.XmlSerializer
import dev.erbsland.elcl.ElclFileType
import dev.erbsland.elcl.format.ElclSyntaxSnapshot
import dev.erbsland.elcl.format.FormatProperty
import dev.erbsland.elcl.settings.ElclSettings
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JTabbedPane
import javax.swing.JTextField

class ElclFormattingPanelTest : BasePlatformTestCase() {
    private lateinit var saved: ElclSettings.State
    override fun setUp() {
        super.setUp()
        saved = ElclSettings.getInstance().state.copy()
        ElclSettings.getInstance().loadState(ElclSettings.State())
    }
    override fun tearDown() {
        try { ElclSettings.getInstance().loadState(saved) } finally { super.tearDown() }
    }

    fun testAspectTabsExposeAllPropertiesAndUpdateMatchingHighlightedExamples() {
        val settings = CodeStyleSettingsManager.getInstance(project).createSettings()
        val configurable = ElclCodeStyleSettingsProvider().createConfigurable(settings, CodeStyleSettingsManager.getInstance(project).createSettings())
        try {
            val component = configurable.createComponent()!!
            val children = descendants(component)
            val tabs = children.filterIsInstance<JTabbedPane>().single()
            assertEquals(listOf("Layout", "Names", "Sections", "Lists", "Completion"), (0 until tabs.tabCount).map(tabs::getTitleAt))
            FormatProperty.entries.forEach { property ->
                assertEquals(property.path, 1, children.count { it.name == property.path })
            }
            val previews = children.filterIsInstance<EditorTextField>()
            assertEquals(5, previews.size)
            previews.forEach {
                assertSame(ElclFileType, it.fileType)
                assertTrue(it.isViewer)
                val editor = it.getEditor(true)!!
                val iterator = editor.highlighter.createIterator(0)
                val tokens = mutableSetOf<com.intellij.psi.tree.IElementType>()
                while (!iterator.atEnd()) { tokens += iterator.tokenType; iterator.advance() }
                assertTrue(tokens.toString(), tokens.contains(dev.erbsland.elcl.psi.ElclTypes.TEXT))
                assertFalse(it.text, ElclSyntaxSnapshot(it.text).statements.any { statement -> statement.damaged })
                myFixture.configureByText(ElclFileType, it.text)
                myFixture.checkHighlighting()
            }
            fun preview(tab: String) = previews.single { it.name == "elcl.preview.$tab" }.text
            fun choose(property: FormatProperty, label: String) {
                val combo = children.single { it.name == property.path } as JComboBox<*>
                combo.selectedIndex = (0 until combo.itemCount).first { combo.getItemAt(it).toString() == label }
            }
            val namesBefore = preview("Names")
            choose(FormatProperty.VALUE_CASE, "UPPER CASE")
            assertFalse(namesBefore == preview("Names"))
            assertTrue(preview("Names").contains("DISPLAY NAME"))
            choose(FormatProperty.SEPARATOR, "Equals (=)")
            assertTrue(preview("Layout").contains("= 8080"))
            choose(FormatProperty.WRAPPING, "Always move values to the next line")
            assertTrue(preview("Layout").contains("\n    8080"))
            choose(FormatProperty.VECTOR, "One item per line")
            assertTrue(preview("Lists").contains("* 8080\n"))
            choose(FormatProperty.MATRIX, "Align columns")
            assertTrue(preview("Lists").contains("1,    22,  333"))
            (children.single { it.name == FormatProperty.SECTION_STAR.path } as JCheckBox).doClick()
            assertFalse(preview("Sections").contains("]*"))
            assertTrue(configurable.isModified)
            configurable.apply()
            assertFalse(configurable.isModified)
            configurable.reset()
            assertTrue(preview("Names").contains("DISPLAY NAME"))
            // Invalid partial input leaves the last valid preview visible and prevents applying.
            val before = preview("Layout")
            (children.single { it.name == FormatProperty.WIDTH.path } as JTextField).text = ""
            assertEquals(before, preview("Layout"))
            assertTrue(configurable.isModified)
            assertThrows(ConfigurationException::class.java) { configurable.apply() }
            configurable.reset()
            assertFalse(configurable.isModified)
        } finally { configurable.disposeUIResources() }
    }

    fun testFileAccessConsentIsMachineLocalPersistentAndOnlyChangesOnApply() {
        val settings = CodeStyleSettingsManager.getInstance(project).createSettings()
        val configurable = ElclCodeStyleSettingsProvider().createConfigurable(settings, CodeStyleSettingsManager.getInstance(project).createSettings())
        try {
            val component = configurable.createComponent()!!
            val checkbox = descendants(component).filterIsInstance<JCheckBox>().single { it.text.startsWith("Load style from") }
            assertFalse(checkbox.isSelected)
            checkbox.doClick()
            assertFalse(ElclSettings.getInstance().state.loadFormattingConfiguration)
            assertTrue(configurable.isModified)
            configurable.reset()
            assertFalse(checkbox.isSelected)
            checkbox.doClick()
            configurable.apply()
            val state = ElclSettings.getInstance().state
            assertTrue(state.loadFormattingConfiguration)
            val restored = XmlSerializer.deserialize(XmlSerializer.serialize(state), ElclSettings.State::class.java)
            assertTrue(restored.loadFormattingConfiguration)
            assertEquals("ERBSLAND_CORE", restored.placeholders)
            // Switching/importing a code-style scheme does not grant or revoke access.
            configurable.reset(CodeStyleSettingsManager.getInstance(project).createSettings())
            assertTrue(checkbox.isSelected)
            ElclSettings.getInstance().loadState(ElclSettings.State())
            configurable.reset(CodeStyleSettingsManager.getInstance(project).createSettings())
            assertFalse(checkbox.isSelected)
            // Existing 0.6.0 state has no consent field and must migrate to disabled.
            val old = org.jdom.Element("state").addContent(org.jdom.Element("option").setAttribute("name", "placeholders").setAttribute("value", "NONE"))
            assertFalse(XmlSerializer.deserialize(old, ElclSettings.State::class.java).loadFormattingConfiguration)
        } finally { configurable.disposeUIResources() }
    }

    private fun descendants(component: java.awt.Component): List<java.awt.Component> = listOf(component) +
        (component as? java.awt.Container)?.components.orEmpty().flatMap(::descendants)
}

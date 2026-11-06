package dev.erbsland.elcl.format

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.erbsland.elcl.highlight.ElclHighlightTokenTypes
import dev.erbsland.elcl.settings.ElclSettings
import dev.erbsland.elcl.settings.ElclSettingsConfigurable
import dev.erbsland.elcl.settings.refreshElclEditors
import dev.erbsland.elcl.style.ElclCodeStyleSettings
import dev.erbsland.elcl.style.ElclCodeStyleSettingsProvider
import org.jdom.Element
import com.intellij.ui.EditorTextField
import javax.swing.JComboBox

class ElclFormattingServiceTest : BasePlatformTestCase() {
    private lateinit var savedPreferences: ElclSettings.State
    override fun setUp() {
        super.setUp()
        savedPreferences = ElclSettings.getInstance().state.copy()
        ElclSettings.getInstance().loadState(ElclSettings.State())
    }
    override fun tearDown() {
        try { ElclSettings.getInstance().loadState(savedPreferences) } finally { super.tearDown() }
    }
    override fun createTempDirTestFixture() = com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl()
    private fun reformat() {
        WriteCommandAction.runWriteCommandAction(project) { CodeStyleManager.getInstance(project).reformat(myFixture.file) }
    }
    fun testReformatCodeUsesRegisteredServiceAndUndoRedo() {
        myFixture.configureByText("example.elcl", "[my_section]\nmy_name=1\n")
        val original = myFixture.editor.document.text
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_REFORMAT)
        val formatted = myFixture.editor.document.text
        assertTrue(formatted, formatted.contains("My Name"))
        assertTrue(formatted.startsWith("----[ My Section ]"))
        myFixture.performEditorAction(IdeActions.ACTION_UNDO)
        assertEquals(original, myFixture.editor.document.text)
        myFixture.performEditorAction(IdeActions.ACTION_REDO)
        assertEquals(formatted, myFixture.editor.document.text)
    }
    fun testSelectionFormatsOnlyIntersectingStatements() {
        myFixture.configureByText("selection.elcl", "[a]\nx:1\ny:2\n")
        WriteCommandAction.runWriteCommandAction(project) { CodeStyleManager.getInstance(project).reformatText(myFixture.file, 4, 7) }
        assertEquals("[a]\nX                            : 1\ny:2\n", myFixture.editor.document.text)
    }
    fun testUnsavedConfigAndIncludedChangesTakePrecedenceOverIdeStyle() {
        ElclSettings.getInstance().state.loadFormattingConfiguration = true
        val config = myFixture.addFileToProject("cfg/elcl_format.elcl", "@include:\"names.elcl\"\n[Elcl Format.Base]\nMaximum Line Length:80\n")
        val names = myFixture.addFileToProject("cfg/names.elcl", "[Elcl Format.Base.Names]\nValue Case:\"upper\"\n")
        val input = myFixture.addFileToProject("cfg/input.elcl", "[a]\nmy_name:1\n")
        myFixture.configureFromExistingVirtualFile(input.virtualFile)
        reformat()
        assertTrue(myFixture.editor.document.text.contains("MY NAME"))
        val includedDocument = PsiDocumentManager.getInstance(project).getDocument(names)!!
        WriteCommandAction.runWriteCommandAction(project) { includedDocument.setText("[Elcl Format.Base.Names]\nValue Case:\"lower\"\n") }
        assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(includedDocument))
        reformat()
        assertTrue(myFixture.editor.document.text.contains("my name"))
        val configDocument = PsiDocumentManager.getInstance(project).getDocument(config)!!
        WriteCommandAction.runWriteCommandAction(project) { configDocument.setText("[Elcl Format.Base.Names]\nValue Case:\"keep\"\nValue Separator:\"underscore\"\n") }
        reformat()
        assertTrue(myFixture.editor.document.text.contains("my_name"))
    }
    fun testConfigurationAccessIsDisabledByDefaultIncludingParentFilesAndIncludes() {
        assertFalse(ElclSettings.getInstance().state.loadFormattingConfiguration)
        val config = myFixture.addFileToProject("cfg/elcl_format.elcl", "[Elcl Format.Base.Names]\nValue Case:\"upper\"\n")
        val input = myFixture.addFileToProject("cfg/nested/input.elcl", "[a]\nmy_name:1\n")
        myFixture.configureFromExistingVirtualFile(input.virtualFile)
        reformat()
        assertTrue(myFixture.editor.document.text.contains("My Name"))
        ElclSettings.getInstance().state.loadFormattingConfiguration = true
        reformat()
        assertTrue(myFixture.editor.document.text.contains("MY NAME"))
        // Disabling takes effect immediately, including for a previously loaded configuration.
        ElclSettings.getInstance().state.loadFormattingConfiguration = false
        WriteCommandAction.runWriteCommandAction(project) {
            PsiDocumentManager.getInstance(project).getDocument(config)!!.setText("@include:\"missing-file.elcl\"\ninvalid config\n")
        }
        reformat()
        assertTrue(myFixture.editor.document.text.contains("My Name"))
    }
    fun testCodeStylePersistencePreviewAndDefaultDetection() {
        val settings = com.intellij.psi.codeStyle.CodeStyleSettingsManager.getInstance(project).createSettings()
        val custom = settings.getCustomSettings(ElclCodeStyleSettings::class.java)
        custom.FORMAT_STYLE = ElclFormatStyle(mapOf(FormatProperty.WIDTH to "90", FormatProperty.VALUE_CASE to "upper")).serialize()
        custom.FORMAT_DETECT_SECTION_NAMES = true
        val element = Element("settings")
        custom.writeExternal(element, ElclCodeStyleSettings(settings))
        val copy = ElclCodeStyleSettings(settings)
        copy.readExternal(element)
        assertEquals(custom.FORMAT_STYLE, copy.FORMAT_STYLE)
        assertTrue(copy.FORMAT_DETECT_SECTION_NAMES)
        val configurable = ElclCodeStyleSettingsProvider().createConfigurable(settings, com.intellij.psi.codeStyle.CodeStyleSettingsManager.getInstance(project).createSettings())
        val component = configurable.createComponent()
        configurable.reset()
        assertFalse(configurable.isModified)
        val preview = descendants(component!!).filterIsInstance<EditorTextField>().single { it.name == "elcl.preview.Names" }
        assertTrue(preview.text.contains("DISPLAY NAME"))
        configurable.apply()
        configurable.disposeUIResources()
        val source = "[first_section]\nx_value:1\n[second_section]\ny_value:2\n[third_section]\nz_value:3\n"
        val default = ElclCodeStyleSettings(settings)
        assertEquals("title", default.formatFor(source).base[FormatProperty.VALUE_CASE])
        default.FORMAT_DETECT_VALUE_NAMES = true
        assertEquals("lower", default.formatFor(source).base[FormatProperty.VALUE_CASE])
        assertEquals("underscore", default.formatFor(source).base[FormatProperty.VALUE_SEPARATOR])
    }
    fun testPlaceholderPreferencePersistsAndRefreshesOpenEditor() {
        val service = ElclSettings.getInstance()
        val saved = service.state.copy()
        val settings = ElclSettingsConfigurable()
        try {
            service.loadState(ElclSettings.State())
            myFixture.configureByText("placeholders.elcl", "[a]\nx:\"${'$'}{var:hello}\"\n")
            refreshElclEditors()
            fun types(): List<com.intellij.psi.tree.IElementType> {
                val highlighter = (myFixture.editor as EditorEx).highlighter
                val iterator = highlighter.createIterator(0)
                val result = mutableListOf<com.intellij.psi.tree.IElementType>()
                while (!iterator.atEnd()) { result += iterator.tokenType; iterator.advance() }
                return result
            }
            assertTrue(types().contains(ElclHighlightTokenTypes.PLACEHOLDER_TYPE))
            val component = settings.createComponent()
            val choices = descendants(component).filterIsInstance<JComboBox<*>>().single()
            choices.selectedIndex = 0
            settings.apply()
            assertEquals("NONE", service.state.placeholders)
            assertFalse(types().contains(ElclHighlightTokenTypes.PLACEHOLDER_TYPE))
            choices.selectedIndex = 1
            settings.apply()
            assertTrue(types().contains(ElclHighlightTokenTypes.PLACEHOLDER_TYPE))
        } finally { service.loadState(saved); refreshElclEditors(); settings.disposeUIResources() }
    }
    private fun descendants(component: java.awt.Component): List<java.awt.Component> = listOf(component) +
        (component as? java.awt.Container)?.components.orEmpty().flatMap(::descendants)
}

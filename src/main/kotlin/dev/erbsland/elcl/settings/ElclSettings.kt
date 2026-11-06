package dev.erbsland.elcl.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.options.Configurable
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import java.awt.FlowLayout

/** IDE-wide preferences that do not affect the meaning of parsed documents. */
@Service(Service.Level.APP)
@State(name = "ElclSettings", storages = [Storage("elcl.xml")])
class ElclSettings : PersistentStateComponent<ElclSettings.State> {
    data class State(
        var placeholders: String = "ERBSLAND_CORE",
        // Machine-local consent: a project or imported code-style scheme must not enable filesystem discovery.
        var loadFormattingConfiguration: Boolean = false,
    )
    private var stored = State()
    override fun getState(): State = stored
    override fun loadState(state: State) { stored = state }

    companion object {
        fun getInstance(): ElclSettings = ApplicationManager.getApplication().getService(ElclSettings::class.java)
        fun placeholdersEnabled(): Boolean = ApplicationManager.getApplication()?.getService(ElclSettings::class.java)
            ?.state?.placeholders != "NONE"
    }
}

class ElclSettingsConfigurable : Configurable {
    private var choice: JComboBox<String>? = null
    override fun getDisplayName(): String = "Erbsland Configuration Language"
    override fun createComponent(): JComponent = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
        add(JLabel("Placeholders:"))
        add(JComboBox(arrayOf("None", "Erbsland Core")).also { choice = it })
        reset()
    }
    override fun isModified(): Boolean = choice?.selectedIndex != index()
    override fun reset() { choice?.selectedIndex = index() }
    override fun apply() {
        ElclSettings.getInstance().state.placeholders = if (choice?.selectedIndex == 0) "NONE" else "ERBSLAND_CORE"
        refreshElclEditors()
    }
    override fun disposeUIResources() { choice = null }
    private fun index() = if (ElclSettings.placeholdersEnabled()) 1 else 0
}

/** Recreate editor lexers so an applied preference is visible without reopening files. */
internal fun refreshElclEditors() {
    for (editor in EditorFactory.getInstance().allEditors) {
        val ex = editor as? EditorEx ?: continue
        val file = ex.virtualFile ?: continue
        if (file.extension?.equals("elcl", true) != true) continue
        ex.highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(ex.project, file)
    }
}

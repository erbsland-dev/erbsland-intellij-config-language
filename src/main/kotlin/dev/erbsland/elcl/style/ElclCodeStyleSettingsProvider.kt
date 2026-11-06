package dev.erbsland.elcl.style

import com.intellij.psi.codeStyle.CodeStyleConfigurable
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider
import com.intellij.psi.codeStyle.CustomCodeStyleSettings
import dev.erbsland.elcl.ElclLanguage
import javax.swing.JComponent

/** Registers ELCL formatting and completion preferences in the active code-style scheme. */
class ElclCodeStyleSettingsProvider : CodeStyleSettingsProvider() {
    override fun createCustomSettings(settings: CodeStyleSettings): CustomCodeStyleSettings = ElclCodeStyleSettings(settings)
    override fun getConfigurableDisplayName(): String = "Erbsland Configuration Language"
    override fun getLanguage() = ElclLanguage.INSTANCE

    override fun createConfigurable(settings: CodeStyleSettings, modelSettings: CodeStyleSettings): CodeStyleConfigurable =
        ElclCodeStyleConfigurable(settings)
}

private class ElclCodeStyleConfigurable(private val settings: CodeStyleSettings) : CodeStyleConfigurable {
    private var panel: ElclFormattingPanel? = null

    override fun getDisplayName(): String = "Erbsland Configuration Language"
    override fun createComponent(): JComponent = panel ?: ElclFormattingPanel().also {
        panel = it
        reset()
    }
    override fun isModified(): Boolean = panel?.isModified(settings.getCustomSettings(ElclCodeStyleSettings::class.java)) ?: false
    override fun apply() = apply(settings)
    override fun apply(target: CodeStyleSettings) { panel?.apply(target.getCustomSettings(ElclCodeStyleSettings::class.java)) }
    override fun reset() = reset(settings)
    override fun reset(target: CodeStyleSettings) { panel?.reset(target.getCustomSettings(ElclCodeStyleSettings::class.java)) }
    override fun disposeUIResources() { panel?.dispose(); panel = null }
}

package dev.erbsland.elcl.style

import com.intellij.openapi.options.ConfigurationException
import com.intellij.psi.codeStyle.CodeStyleConfigurable
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider
import com.intellij.psi.codeStyle.CustomCodeStyleSettings
import dev.erbsland.elcl.ElclLanguage
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/** Registers ELCL naming preferences in the active IDE/project code-style scheme. */
class ElclCodeStyleSettingsProvider : CodeStyleSettingsProvider() {
    override fun createCustomSettings(settings: CodeStyleSettings): CustomCodeStyleSettings = ElclCodeStyleSettings(settings)
    override fun getConfigurableDisplayName(): String = "Erbsland Configuration Language"
    override fun getLanguage() = ElclLanguage.INSTANCE

    override fun createConfigurable(settings: CodeStyleSettings, modelSettings: CodeStyleSettings): CodeStyleConfigurable =
        ElclCodeStyleConfigurable(settings)
}

private class ElclCodeStyleConfigurable(private val settings: CodeStyleSettings) : CodeStyleConfigurable {
    private data class Controls(val style: JComboBox<ElclNameStyle>, val detect: JCheckBox)

    private val panel = JPanel(GridBagLayout())
    private val section = addProfile(0, "Section names")
    private val value = addProfile(1, "Value names")
    private val identifier = addProfile(2, "Identifiers in text values")

    init {
        panel.add(
            JPanel(),
            GridBagConstraints().apply {
                gridx = 0
                gridy = 3
                gridwidth = 3
                weightx = 1.0
                weighty = 1.0
                fill = GridBagConstraints.BOTH
            },
        )
        reset()
    }

    override fun getDisplayName(): String = "Erbsland Configuration Language"
    override fun createComponent(): JComponent = panel

    override fun isModified(): Boolean = isModified(settings)

    private fun isModified(target: CodeStyleSettings): Boolean {
        val stored = target.getCustomSettings(ElclCodeStyleSettings::class.java)
        return selected(section) != stored.SECTION_NAME_STYLE || section.detect.isSelected != stored.DETECT_SECTION_NAME_STYLE ||
            selected(value) != stored.VALUE_NAME_STYLE || value.detect.isSelected != stored.DETECT_VALUE_NAME_STYLE ||
            selected(identifier) != stored.IDENTIFIER_STYLE || identifier.detect.isSelected != stored.DETECT_IDENTIFIER_STYLE
    }

    @Throws(ConfigurationException::class)
    override fun apply() = apply(settings)

    override fun apply(target: CodeStyleSettings) {
        val stored = target.getCustomSettings(ElclCodeStyleSettings::class.java)
        stored.SECTION_NAME_STYLE = selected(section)
        stored.DETECT_SECTION_NAME_STYLE = section.detect.isSelected
        stored.VALUE_NAME_STYLE = selected(value)
        stored.DETECT_VALUE_NAME_STYLE = value.detect.isSelected
        stored.IDENTIFIER_STYLE = selected(identifier)
        stored.DETECT_IDENTIFIER_STYLE = identifier.detect.isSelected
    }

    override fun reset() = reset(settings)

    override fun reset(target: CodeStyleSettings) {
        val stored = target.getCustomSettings(ElclCodeStyleSettings::class.java)
        reset(section, stored.SECTION_NAME_STYLE, stored.DETECT_SECTION_NAME_STYLE)
        reset(value, stored.VALUE_NAME_STYLE, stored.DETECT_VALUE_NAME_STYLE)
        reset(identifier, stored.IDENTIFIER_STYLE, stored.DETECT_IDENTIFIER_STYLE)
    }

    private fun addProfile(row: Int, label: String): Controls {
        val style = JComboBox(ElclNameStyle.entries.toTypedArray())
        val detect = JCheckBox("Detect automatically")
        panel.add(JLabel("$label:"), constraints(0, row))
        panel.add(style, constraints(1, row).apply { fill = GridBagConstraints.HORIZONTAL; weightx = 1.0 })
        panel.add(detect, constraints(2, row))
        return Controls(style, detect)
    }

    private fun constraints(column: Int, row: Int) = GridBagConstraints().apply {
        gridx = column
        gridy = row
        anchor = GridBagConstraints.WEST
        insets = Insets(4, 4, 4, 8)
    }

    private fun selected(controls: Controls): String = (controls.style.selectedItem as ElclNameStyle).name

    private fun reset(controls: Controls, style: String, detect: Boolean) {
        controls.style.selectedItem = ElclNameStyle.fromStored(style)
        controls.detect.isSelected = detect
    }
}

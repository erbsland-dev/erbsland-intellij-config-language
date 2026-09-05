package dev.erbsland.elcl.style

import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CustomCodeStyleSettings

/** Persisted, formatter-ready ELCL naming preferences. */
class ElclCodeStyleSettings(container: CodeStyleSettings) : CustomCodeStyleSettings("ElclCodeStyleSettings", container) {
    @JvmField var SECTION_NAME_STYLE: String = ElclNameStyle.TITLE_SPACE.name
    @JvmField var DETECT_SECTION_NAME_STYLE: Boolean = true
    @JvmField var VALUE_NAME_STYLE: String = ElclNameStyle.TITLE_SPACE.name
    @JvmField var DETECT_VALUE_NAME_STYLE: Boolean = true
    @JvmField var IDENTIFIER_STYLE: String = ElclNameStyle.TITLE_SPACE.name
    @JvmField var DETECT_IDENTIFIER_STYLE: Boolean = true

    internal fun sectionProfile() = ElclNameStyleProfile(ElclNameStyle.fromStored(SECTION_NAME_STYLE), DETECT_SECTION_NAME_STYLE)
    internal fun valueProfile() = ElclNameStyleProfile(ElclNameStyle.fromStored(VALUE_NAME_STYLE), DETECT_VALUE_NAME_STYLE)
    internal fun identifierProfile() = ElclNameStyleProfile(ElclNameStyle.fromStored(IDENTIFIER_STYLE), DETECT_IDENTIFIER_STYLE)
}

internal data class ElclNameStyleProfile(val style: ElclNameStyle, val detectAutomatically: Boolean)

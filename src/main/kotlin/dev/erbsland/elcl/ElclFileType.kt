package dev.erbsland.elcl

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object ElclFileType : LanguageFileType(ElclLanguage.INSTANCE) {
    override fun getName(): String = "ELCL"
    override fun getDescription(): String = "Erbsland Configuration Language file"
    override fun getDefaultExtension(): String = "elcl"
    override fun getIcon(): Icon = IconLoader.getIcon("/icons/elcl.svg", ElclFileType::class.java)
}

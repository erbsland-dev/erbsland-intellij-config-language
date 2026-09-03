package dev.erbsland.elcl

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.psi.FileViewProvider

/** PSI file root for ordinary and validation-rules ELCL documents. */
class ElclFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, ElclLanguage.INSTANCE) {
    override fun getFileType() = ElclFileType
    override fun toString(): String = "ELCL File"

    val isValidationRules: Boolean
        get() = name.lowercase().endsWith(".vr.elcl")
}

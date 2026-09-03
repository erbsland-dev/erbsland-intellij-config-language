package dev.erbsland.elcl.injection

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.LanguageFileType

/** Resolves an ELCL code tag against languages installed in the current IDE. */
internal object ElclCodeLanguageResolver {
    /**
     * Finds a language by case-insensitive ID or display name, then treats the
     * tag as a file extension. Unknown tags intentionally return `null`.
     */
    fun resolve(tag: String): Language? {
        val registered = Language.getRegisteredLanguages()
        registered.firstOrNull { it.id.equals(tag, ignoreCase = true) }?.let { return it }
        registered.firstOrNull { it.displayName.equals(tag, ignoreCase = true) }?.let { return it }
        return (FileTypeManager.getInstance().getFileTypeByExtension(tag.lowercase()) as? LanguageFileType)?.language
    }
}

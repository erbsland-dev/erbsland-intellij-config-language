package dev.erbsland.elcl.highlight

import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/** Selects ordinary or validation-rules highlighting from the file name. */
class ElclSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter =
        ElclSyntaxHighlighter(virtualFile?.name?.lowercase()?.endsWith(".vr.elcl") == true)
}

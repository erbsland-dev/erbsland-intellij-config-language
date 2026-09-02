package dev.erbsland.elcl.editor

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import dev.erbsland.elcl.ElclFile
import dev.erbsland.elcl.psi.ElclMultilineBytes
import dev.erbsland.elcl.psi.ElclMultilineCode
import dev.erbsland.elcl.psi.ElclMultilineRegex
import dev.erbsland.elcl.psi.ElclMultilineText

class ElclFoldingBuilder : FoldingBuilderEx() {
    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val descriptors = mutableListOf<FoldingDescriptor>()
        listOf(
            ElclMultilineText::class.java,
            ElclMultilineCode::class.java,
            ElclMultilineRegex::class.java,
            ElclMultilineBytes::class.java,
        ).forEach { kind ->
            PsiTreeUtil.findChildrenOfType(root, kind).forEach { value ->
                if (value.textRange.length > 6) descriptors += FoldingDescriptor(value.node, value.textRange)
            }
        }
        if (root is ElclFile) addSectionFolds(root, document, descriptors)
        return descriptors.toTypedArray()
    }

    private fun addSectionFolds(file: ElclFile, document: Document, result: MutableList<FoldingDescriptor>) {
        val starts = Regex("(?m)^[ \\t]*(?:-*[ \\t]*)?(?:\\*\\[|\\[)")
            .findAll(file.text).map { it.range.first }.toList()
        starts.forEachIndexed { index, start ->
            val firstLineEnd = document.getLineEndOffset(document.getLineNumber(start))
            val end = if (index + 1 < starts.size) starts[index + 1] else file.textLength
            if (end > firstLineEnd + 1) {
                result += FoldingDescriptor(file.node, TextRange(firstLineEnd, end))
            }
        }
    }

    override fun getPlaceholderText(node: ASTNode): String = " … "
    override fun isCollapsedByDefault(node: ASTNode): Boolean = false
}

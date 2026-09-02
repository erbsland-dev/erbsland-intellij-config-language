package dev.erbsland.elcl.editor

import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.lang.PsiStructureViewFactory
import dev.erbsland.elcl.psi.ElclAssignment
import dev.erbsland.elcl.psi.ElclSection
import javax.swing.Icon

class ElclStructureViewFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder =
        object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: com.intellij.openapi.editor.Editor?): StructureViewModel =
                StructureViewModelBase(psiFile, editor, ElclStructureElement(psiFile))
        }
}

private class ElclStructureElement(private val element: PsiElement) : StructureViewTreeElement {
    override fun getValue(): Any = element

    override fun getChildren(): Array<TreeElement> =
        PsiTreeUtil.getChildrenOfAnyType(element, ElclSection::class.java, ElclAssignment::class.java)
            .map(::ElclStructureElement).toTypedArray()

    private fun presentableText(): String = when (val value = element) {
        is PsiFile -> value.name
        is ElclSection -> value.namePath.text
        is ElclAssignment -> value.text.substringBefore(':').trim()
        else -> value.text.lineSequence().firstOrNull()?.trim().orEmpty()
    }

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String = this@ElclStructureElement.presentableText()
        override fun getLocationString(): String? = null
        override fun getIcon(unused: Boolean): Icon? = element.getIcon(0)
    }

    override fun navigate(requestFocus: Boolean) = (element as? Navigatable)?.navigate(requestFocus) ?: Unit
    override fun canNavigate(): Boolean = (element as? Navigatable)?.canNavigate() == true
    override fun canNavigateToSource(): Boolean = (element as? Navigatable)?.canNavigateToSource() == true
}

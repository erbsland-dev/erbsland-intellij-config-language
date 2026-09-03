package dev.erbsland.elcl.editor

import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import dev.erbsland.elcl.psi.ElclAssignment
import dev.erbsland.elcl.psi.ElclSection

/**
 * Structure model that keeps ELCL declarations in source order.
 *
 * ELCL sections establish context for following assignments rather than owning
 * them syntactically, so the displayed tree is a semantic projection of PSI.
 */
internal class ElclStructureViewModel(file: PsiFile, editor: Editor?) :
    StructureViewModelBase(file, editor, ElclStructureElement.root(file)),
    StructureViewModel.ElementInfoProvider {

    override fun getSorters(): Array<Sorter> = emptyArray()

    override fun getSuitableClasses(): Array<Class<*>> =
        arrayOf(ElclSection::class.java, ElclAssignment::class.java)

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean = false

    override fun isAlwaysLeaf(element: StructureViewTreeElement): Boolean =
        element.value is ElclAssignment
}

package dev.erbsland.elcl.editor

import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.psi.PsiFile
import com.intellij.lang.PsiStructureViewFactory

/** Creates the semantic Structure view used for ELCL files. */
class ElclStructureViewFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder =
        object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: com.intellij.openapi.editor.Editor?): StructureViewModel =
                ElclStructureViewModel(psiFile, editor)
        }
}

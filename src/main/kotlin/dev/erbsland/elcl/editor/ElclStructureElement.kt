package dev.erbsland.elcl.editor

import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.TokenSet
import dev.erbsland.elcl.psi.ElclAssignment
import dev.erbsland.elcl.psi.ElclMetaAssignment
import dev.erbsland.elcl.psi.ElclSection
import dev.erbsland.elcl.psi.ElclTypes
import javax.swing.Icon

/** One navigable entry in the ELCL Structure view. */
internal class ElclStructureElement(
    private val element: PsiElement,
    private val label: String,
    private val icon: Icon?,
    private val childElements: () -> List<ElclStructureElement>,
) : StructureViewTreeElement {

    override fun getValue(): Any = element
    override fun getChildren(): Array<TreeElement> = childElements().toTypedArray()

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String = label
        override fun getLocationString(): String? = null
        override fun getIcon(unused: Boolean): Icon? = icon
    }

    override fun navigate(requestFocus: Boolean) = (element as? Navigatable)?.navigate(requestFocus) ?: Unit
    override fun canNavigate(): Boolean = (element as? Navigatable)?.canNavigate() == true
    override fun canNavigateToSource(): Boolean = (element as? Navigatable)?.canNavigateToSource() == true

    companion object {
        private val NAME_TOKENS = TokenSet.create(ElclTypes.NAME, ElclTypes.TEXT_NAME)

        /** Builds the root entry and its semantic section/value hierarchy. */
        fun root(file: PsiFile): ElclStructureElement =
            ElclStructureElement(file, file.name, file.getIcon(0)) { buildFileChildren(file) }

        private fun buildFileChildren(file: PsiFile): List<ElclStructureElement> {
            val roots = mutableListOf<MutableStructureNode>()
            val sectionListIndexes = mutableMapOf<String, Int>()
            var currentSection: MutableStructureNode? = null
            var lastAbsolutePath: List<String>? = null

            file.children.sortedBy(PsiElement::getTextOffset).forEach { child ->
                when (child) {
                    is ElclSection -> {
                        val rawPath = child.namePath.text.trim()
                        val pathElements = child.namePath.node.getChildren(NAME_TOKENS).map { it.text }
                        val relative = rawPath.startsWith('.')
                        val displayPath = when {
                            relative && lastAbsolutePath != null -> lastAbsolutePath.orEmpty() + pathElements
                            relative -> null
                            else -> pathElements.also { lastAbsolutePath = it }
                        }
                        val baseLabel = displayPath?.joinToString(" . ") ?: rawPath
                        val isList = child.node.findChildByType(ElclTypes.SECTION_LIST_OPEN) != null
                        val label = if (isList) {
                            val index = sectionListIndexes.getOrDefault(baseLabel, 0)
                            sectionListIndexes[baseLabel] = index + 1
                            "$baseLabel [$index]"
                        } else {
                            baseLabel
                        }
                        currentSection = MutableStructureNode(
                            child,
                            label,
                            if (isList) {
                                AllIcons.Nodes.DataTables
                            } else {
                                AllIcons.Nodes.Folder
                            },
                        ).also(roots::add)
                    }

                    is ElclAssignment -> {
                        val name = child.node.findChildByType(NAME_TOKENS)?.text
                            ?: child.text.substringBefore(':').substringBefore('=').trim()
                        val valueNode = MutableStructureNode(child, name, AllIcons.Nodes.Property)
                        currentSection?.children?.add(valueNode) ?: roots.add(valueNode)
                    }

                    is ElclMetaAssignment -> if (child.metaName().equals("@include", ignoreCase = true)) {
                        currentSection = null
                        lastAbsolutePath = null
                    }
                }
            }

            return roots.map(MutableStructureNode::freeze)
        }

        private fun ElclMetaAssignment.metaName(): String? =
            node.findChildByType(ElclTypes.META_NAME)?.text
    }
}

/** Mutable only while a single Structure view snapshot is assembled. */
private class MutableStructureNode(
    private val element: PsiElement,
    private val label: String,
    private val icon: Icon,
    val children: MutableList<MutableStructureNode> = mutableListOf(),
) {
    fun freeze(): ElclStructureElement =
        ElclStructureElement(element, label, icon) { children.map(MutableStructureNode::freeze) }
}

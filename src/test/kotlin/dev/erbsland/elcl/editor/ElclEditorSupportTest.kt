package dev.erbsland.elcl.editor

import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.erbsland.elcl.psi.ElclTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElclEditorSupportTest : BasePlatformTestCase() {
    @Test
    fun `commenter uses ELCL line comments`() {
        assertEquals("#", ElclCommenter().lineCommentPrefix)
    }

    @Test
    fun `brace matcher covers sections and all multiline families`() {
        val pairs = ElclBraceMatcher().pairs
        assertTrue(pairs.any { it.leftBraceType == ElclTypes.SECTION_MAP_OPEN && it.rightBraceType == ElclTypes.SECTION_MAP_CLOSE })
        assertTrue(pairs.any { it.leftBraceType == ElclTypes.SECTION_LIST_OPEN && it.rightBraceType == ElclTypes.SECTION_LIST_CLOSE })
        assertTrue(pairs.any { it.leftBraceType == ElclTypes.MULTILINE_TEXT_OPEN })
        assertTrue(pairs.any { it.leftBraceType == ElclTypes.MULTILINE_CODE_OPEN })
        assertTrue(pairs.any { it.leftBraceType == ElclTypes.MULTILINE_REGEX_OPEN })
        assertTrue(pairs.any { it.leftBraceType == ElclTypes.MULTILINE_BYTES_OPEN })
    }

    fun testStructureViewGroupsValuesInSourceOrder() {
        val file = myFixture.configureByText(
            "structure.elcl",
            """
                [Server]
                First: 1
                Second = 2
                [.TLS]
                "Certificate name": "main"
                *[Workers]*
                Name: "one"
                *[Workers]*
                Name: "two"
                @include: "other.elcl"
                [.Unresolved]
                Value: 3
            """.trimIndent(),
        )
        val model = ElclStructureViewModel(file, null)
        val sections = model.root.children.map { it as StructureViewTreeElement }

        assertEquals(listOf("Server", "Server . TLS", "Workers [0]", "Workers [1]", ".Unresolved"), sections.map(::label))
        assertEquals(listOf("First", "Second"), sections[0].children.map { label(it as StructureViewTreeElement) })
        assertEquals(listOf("\"Certificate name\""), sections[1].children.map { label(it as StructureViewTreeElement) })
        assertEquals(AllIcons.Nodes.Folder, sections[0].presentation.getIcon(false))
        assertEquals(AllIcons.Nodes.DataTables, sections[2].presentation.getIcon(false))
        assertEquals(AllIcons.Nodes.Property, (sections[0].children[0] as StructureViewTreeElement).presentation.getIcon(false))
        assertTrue(model.sorters.isEmpty())
        assertTrue(model.isAlwaysLeaf(sections[0].children[0] as StructureViewTreeElement))
    }

    fun testStructureViewRebuildsChildrenAfterDocumentChanges() {
        val file = myFixture.configureByText("live.elcl", "[Main]\nFirst: 1\n")
        val model = ElclStructureViewModel(file, myFixture.editor)
        assertEquals(listOf("First"), model.root.children[0].children.map { label(it as StructureViewTreeElement) })

        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.insertString(myFixture.editor.document.textLength, "Second: 2\n")
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        assertEquals(
            listOf("First", "Second"),
            model.root.children[0].children.map { label(it as StructureViewTreeElement) },
        )
    }

    private fun label(element: StructureViewTreeElement): String = element.presentation.presentableText.orEmpty()
}

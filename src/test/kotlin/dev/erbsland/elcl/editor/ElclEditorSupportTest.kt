package dev.erbsland.elcl.editor

import dev.erbsland.elcl.psi.ElclTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElclEditorSupportTest {
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
}

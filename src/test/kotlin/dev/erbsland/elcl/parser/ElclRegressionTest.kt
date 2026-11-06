package dev.erbsland.elcl.parser

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.erbsland.elcl.lexer.ElclLexerAdapter
import dev.erbsland.elcl.psi.ElclAssignment
import dev.erbsland.elcl.psi.ElclTypes
import dev.erbsland.elcl.validation.ElclTextAnalyzer

class ElclRegressionTest : BasePlatformTestCase() {
    fun testReportedDocumentsHaveNoParserOrEditorErrors() {
        for (name in listOf("files", "doc-process", "vectors")) {
            val text = javaClass.getResource("/regressions/$name.elcl")!!.readText()
            val file = myFixture.configureByText("$name.elcl", text)
            assertEmpty(PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java))
            assertEmpty(ElclTextAnalyzer().analyze(text, false))
            assertEmpty(myFixture.doHighlighting().filter { it.severity == HighlightSeverity.ERROR })
        }
    }

    fun testEveryScalarContinuesOnNextLineAndBelongsToAssignment() {
        val values = listOf("\"text\"", "/[a-z]+/", "`code`", "123", "1.25", "yes", "<01 ff>", "2026-11-06", "12:34:56", "2026-11-06T12:34:56", "30s", "1, 2, 3")
        for (newline in listOf("\n", "\r\n")) for (value in values) {
            val source = "[main]${newline}value: # comment${newline}    $value${newline}next: 42$newline"
            val file = myFixture.configureByText("values.elcl", source)
            assertEmpty(PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java))
            assertEmpty(ElclTextAnalyzer().analyze(source, false))
            val assignments = PsiTreeUtil.collectElementsOfType(file, ElclAssignment::class.java).toList()
            assertEquals(2, assignments.size)
            assertTrue(assignments.first().text.contains(value))
        }
    }

    fun testBulletedRowsBelongToTheirAssignment() {
        val file = myFixture.configureByText("rows.elcl", "[a]\nx:\n    * 1, 2\n    * 3, 4\ny: 5\n")
        val assignments = PsiTreeUtil.collectElementsOfType(file, ElclAssignment::class.java).toList()
        assertEquals(2, assignments.size)
        assertTrue(assignments.first().text.contains("* 3, 4"))
    }

    fun testMissingValuesRecoverWithoutConsumingNextAssignment() {
        for (gap in listOf("", "\n", "# comment\n")) {
            val source = "[a]\nx:\n${gap}y: 42\n[good]\nz: 1\n"
            val file = myFixture.configureByText("broken.elcl", source)
            assertEquals(3, PsiTreeUtil.collectElementsOfType(file, ElclAssignment::class.java).size)
            assertTrue(ElclTextAnalyzer().analyze(source, false).isNotEmpty())
        }
        assertTrue(ElclTextAnalyzer().analyze("[a]\nx:\n\"unindented\"\n", false).isNotEmpty())
    }

    fun testContinuationLexerRestartsAtEveryToken() {
        val text = "[a]\nx:\r\n    \"text\", 42\r\ny:\n    true\nnext: 3\n"
        val lexer = ElclLexerAdapter()
        lexer.start(text)
        data class Token(val start: Int, val end: Int, val state: Int, val type: com.intellij.psi.tree.IElementType)
        val tokens = mutableListOf<Token>()
        while (lexer.tokenType != null) { tokens += Token(lexer.tokenStart, lexer.tokenEnd, lexer.state, lexer.tokenType!!); lexer.advance() }
        assertEquals(ElclTypes.TEXT, tokens.first { text.substring(it.start, it.end) == "\"text\"" }.type)
        for (token in tokens) {
            val restarted = ElclLexerAdapter()
            restarted.start(text, token.start, text.length, token.state)
            assertEquals(token.type, restarted.tokenType)
            assertEquals(token.end, restarted.tokenEnd)
        }
    }

    fun testQuotedNamesPreserveIdentityAndValidateStructure() {
        assertEmpty(ElclTextAnalyzer().analyze("[files]\n\"File.txt\": 1\n\"file.txt\": 2\n\"has : and = and \\\" quotes\": 3\n", false))
        assertTrue(ElclTextAnalyzer().analyze("[files]\n\"file.txt\": 1\n\"file\\u002etxt\": 2\n", false).any { "Duplicate" in it.message })
        for (source in listOf("[a]\nregular: 1\n\"text\": 2\n", "*[a.\"text\"]\nx: 1\n", "[a.\"text\".child]\nx: 1\n", "[a]\n\"\": 1\n")) {
            assertTrue(source, ElclTextAnalyzer().analyze(source, false).isNotEmpty())
        }
        assertEmpty(ElclTextAnalyzer().analyze("[a.\"file.txt\"]\nx: 1\n[a.\"FILE.txt\"]\nx: 2\n", false))
    }
}

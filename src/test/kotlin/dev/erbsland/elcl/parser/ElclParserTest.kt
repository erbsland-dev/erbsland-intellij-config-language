package dev.erbsland.elcl.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.erbsland.elcl.ElclFile
import dev.erbsland.elcl.psi.ElclAssignment
import dev.erbsland.elcl.psi.ElclSection

class ElclParserTest : BasePlatformTestCase() {
    fun testSectionsListsMetaCommandsAndRecovery() {
        val file = myFixture.configureByText(
            "syntax.elcl",
            """
                @version: "1.0"
                @features: "advanced"

                --[ Server . Main ]--
                port: 8080, 8081
                matrix:
                    * 1, 2
                    * 3, 4

                [.tls]
                enabled: yes

                *[workers]*
                name: "first"
            """.trimIndent(),
        )
        assertInstanceOf(file, ElclFile::class.java)
        val errors = PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java)
        assertTrue(
            errors.joinToString { "${it.textOffset}:${it.errorDescription}:${it.text}" },
            errors.isEmpty(),
        )
        assertEquals(3, PsiTreeUtil.collectElementsOfType(file, ElclSection::class.java).size)
        assertEquals(4, PsiTreeUtil.collectElementsOfType(file, ElclAssignment::class.java).size)
    }

    fun testMalformedValueRecoversAtNextSection() {
        val file = myFixture.configureByText(
            "recovery.elcl",
            "[broken]\nvalue: ???\n[healthy]\nvalue: 42\n",
        )
        val sections = PsiTreeUtil.collectElementsOfType(file, ElclSection::class.java)
        assertEquals(2, sections.size)
        assertEquals("healthy", sections.last().namePath.text)
        assertTrue("malformed input must remain recoverable and visible", file.text.contains("???"))
    }

    fun testMultilineHeadersAndBodyCommentsParseWithoutErrors() {
        val file = myFixture.configureByText(
            "multiline.elcl",
            """
                [main]
                text: <triple> # opening comment
                    # text content
                    <triple> # closing comment
                code: ```java # language and comment
                    // code content
                    ``` # closing comment
                regex: /// # opening comment
                    [a-z]+ # ELCL comment
                    /// # closing comment
                bytes: <<<hex # format and comment
                    de ad be ef # ELCL comment
                    >>> # closing comment
            """.trimIndent().replace("<triple>", "\"\"\""),
        )
        assertEmpty(PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java))
    }

    fun testHexPrefixedSingleLineBytesAndBracedUnicodeEscapesParse() {
        val file = myFixture.configureByText(
            "literals.elcl",
            "[main]\nData: <hex: 01 ff A0>\nText: \"\\u{1f642} \\N \\U0041\"\n",
        )

        assertEmpty(PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java))
    }
}

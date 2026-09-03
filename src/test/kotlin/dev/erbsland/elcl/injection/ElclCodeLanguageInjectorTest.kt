package dev.erbsland.elcl.injection

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.erbsland.elcl.psi.ElclMultilineCode

class ElclCodeLanguageInjectorTest : BasePlatformTestCase() {
    fun testTaggedCodeIsDecodedAndInjected() {
        val file = myFixture.configureByText(
            "injection.elcl",
            """
                [main]
                Code: ```java # opener
                    class Demo {<trailing>
                        void run() {}
                    }
                    ``` # closer
            """.trimIndent().replace("<trailing>", "  "),
        )
        val host = PsiTreeUtil.findChildOfType(file, ElclMultilineCode::class.java)!!
        val escaper = host.createLiteralTextEscaper()
        val decoded = StringBuilder()
        assertTrue(escaper.decode(escaper.relevantTextRange, decoded))
        assertEquals("class Demo {\n    void run() {}\n}\n", decoded.toString())
        assertEquals(host.text.indexOf("void run"), escaper.getOffsetInHost(decoded.indexOf("void run"), escaper.relevantTextRange))

        val hostOffset = file.text.indexOf("class Demo")
        val injected = InjectedLanguageManager.getInstance(project).findInjectedElementAt(file, hostOffset)
        assertNotNull(injected)
        assertEquals("JAVA", injected!!.language.id.uppercase())
    }

    fun testUnavailableLanguageTagDoesNotInject() {
        val file = myFixture.configureByText(
            "unknown.elcl",
            "[main]\nCode: ```not-a-real-lang\n    content\n    ```\n",
        )
        val hostOffset = file.text.indexOf("content")
        assertNull(InjectedLanguageManager.getInstance(project).findInjectedElementAt(file, hostOffset))
    }

    fun testDecodedEditRetainsTheElclFrameAndStructuralIndentation() {
        val file = myFixture.configureByText(
            "edit.elcl",
            "[main]\nCode: ```java # opener\n    class Before {}\n    ``` # closer\n",
        )
        val host = PsiTreeUtil.findChildOfType(file, ElclMultilineCode::class.java)!!
        lateinit var updated: PsiLanguageInjectionHost

        WriteCommandAction.runWriteCommandAction(project) {
            updated = host.updateText("class After {}\n")
        }

        assertEquals("```java # opener\n    class After {}\n    ``` ", updated.text)
        assertTrue(file.text.endsWith(" # closer\n"))
    }
}

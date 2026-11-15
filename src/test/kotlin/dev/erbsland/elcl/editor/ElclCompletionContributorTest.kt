package dev.erbsland.elcl.editor

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertTrue

class ElclCompletionContributorTest : BasePlatformTestCase() {
    fun testFieldCompletionUsesPreferredPresentationForEitherTypedCase() {
        configure("[Server]\nT<caret>")
        assertContains(complete(), "Type")

        configure("[Server]\nt<caret>")
        assertContains(complete(), "Type")
    }

    fun testTypeCompletionWorksInsideAnUnfinishedTextValue() {
        configure("[Server]\nType: \"S<caret>")
        val items = myFixture.completeBasic()
        assertContains(items, "Section List")
        choose(items, "Section List")
        myFixture.checkResult("[Server]\nType: \"Section List\"")
    }

    fun testIndexKeyCompletionOffersScopedCanonicalEntryPaths() {
        configure(
            """
                [Client]
                Type: "Section"
                [Client.Interface]
                Type: "Section List"
                [Client.Interface.VR Entry.Name]
                Type: "Text"
                *[Client.VR Key]*
                Name: "Interface Name"
                Key: "i<caret>
            """.trimIndent(),
        )

        assertSuggestedOrInserted("Interface.VR Entry.Name")
    }

    fun testNodeKeyCompletionOffersVisibleNamedIndexes() {
        configure(
            """
                [Items]
                Type: "Section List"
                [Items.VR Entry.Id]
                Type: "Text"
                *[VR Key]*
                Name: "Item Id"
                Key: "Items.VR Entry.Id"
                [Selection]
                Type: "Text"
                Key: "i<caret>
            """.trimIndent(),
        )

        assertSuggestedOrInserted("Item Id")
    }

    fun testBooleanCompletionInsertsAnUnquotedLiteral() {
        configure("[Server]\nType: \"Section\"\nIs Optional: Y<caret>")
        complete()
        myFixture.checkResult("[Server]\nType: \"Section\"\nIs Optional: Yes")
    }

    fun testNameRulesOfferTemplatesAndOnlyTextTypes() {
        configure("[Users.VR Any.VR Name]\n<caret>")
        val fields = complete()
        assertContains(fields, "Type")
        assertContains(fields, "Use Template")
        assertContains(fields, "Not Starts")

        configure("[Users.VR Any.VR Name]\nType: \"Text\"\n<caret>")
        assertTrue(complete().none { it.lookupString == "Use Template" })
        configure("[Users.VR Any.VR Name]\nUse Template: \"Name\"\n<caret>")
        assertTrue(complete().none { it.lookupString == "Type" })

        configure("[Users.VR Any.VR Name]\nType: \"<caret>")
        val types = complete()
        assertTrue(types.none { it.lookupString == "Integer" || it.lookupString == "Section" })
        assertTrue(types.any { it.lookupString == "Text" } || myFixture.editor.document.text.endsWith("Type: \"Text\""))

        configure("[VR Template.Name]\nType: \"Text\"\n[Users.VR Any.VR Name]\nUse Template: \"N<caret>")
        assertSuggestedOrInserted("Name")
    }

    fun testReferencesOfferActualEscapedNames() {
        configure("""
            [App.VR Literal Type]
            Type: "Text"
            Is Optional: Yes
            [App.VR VR Type]
            Type: "Text"
            Is Optional: Yes
            [App.VR Literal VR Entry]
            Type: "Text"
            Is Optional: Yes
            *[App.VR Dependency]*
            Source: "<caret>
        """.trimIndent())
        val dependencies = complete()
        assertContains(dependencies, "Type")
        assertContains(dependencies, "VR Type")
        assertContains(dependencies, "VR Entry")
        assertTrue(dependencies.none { "Literal" in it.lookupString || "VR VR" in it.lookupString })

        configure("""
            [VR Literal VR Entry]
            Type: "Section List"
            [VR Literal VR Entry.VR Entry.VR Literal Type]
            Type: "Text"
            [VR Literal VR Entry.VR Entry.VR VR Entry]
            Type: "Integer"
            *[VR Key]*
            Key: "<caret>
        """.trimIndent())
        val keys = complete()
        assertContains(keys, "VR Entry.VR Entry.Type")
        assertContains(keys, "VR Entry.VR Entry.VR Entry")
    }

    fun testCompletionDoesNotRunForOrdinaryElclFiles() {
        myFixture.configureByText("ordinary.elcl", "[Server]\nT<caret>")
        assertTrue(myFixture.completeBasic().orEmpty().none { it.lookupString == "Type" })
    }

    private fun configure(text: String) {
        myFixture.configureByText("schema.vr.elcl", text)
    }

    private fun complete(): Array<out LookupElement> = myFixture.completeBasic().orEmpty()

    private fun assertContains(items: Array<out LookupElement>, value: String) {
        assertTrue("Expected '$value' in ${items.map { it.lookupString }}", items.any { it.lookupString == value })
    }

    private fun assertSuggestedOrInserted(value: String) {
        val items = complete()
        assertTrue(
            "Expected '$value' in ${items.map { it.lookupString }} or the completed document",
            items.any { it.lookupString == value } || myFixture.editor.document.text.contains("\"$value\""),
        )
    }

    private fun choose(items: Array<out LookupElement>, value: String) {
        myFixture.lookup.setCurrentItem(items.first { it.lookupString == value })
        myFixture.type('\n')
    }
}

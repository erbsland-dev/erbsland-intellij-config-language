package dev.erbsland.elcl.editor

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import dev.erbsland.elcl.ElclFile
import dev.erbsland.elcl.highlight.ElclHighlightingLexer

/** Offers context-sensitive ELCL and ELCL-VR completion variants. */
class ElclCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), object : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                val file = parameters.originalFile as? ElclFile ?: return
                if (!file.isValidationRules) return
                val before = file.text.substring(0, parameters.offset.coerceAtMost(file.textLength))
                val atSection = before.substringAfterLast('\n').contains('[')
                val values = if (atSection) RESERVED_NAMES else FIELDS + TYPES + MODES
                values.forEach { result.addElement(LookupElementBuilder.create(it)) }
            }
        })
    }

    companion object {
        val RESERVED_NAMES = setOf("vr_template", "vr_any", "vr_name", "vr_entry", "vr_key", "vr_dependency")
        val FIELDS = ElclHighlightingLexer.VR_FIELDS + setOf(
            "allowed_chars", "minimum_version", "maximum_version", "index", "keys", "index_name",
        )
        val TYPES = setOf(
            "Integer", "Boolean", "Float", "Text", "Date", "Time", "DateTime", "Bytes", "TimeDelta",
            "RegEx", "Value", "ValueList", "ValueMatrix", "Section", "SectionList", "SectionWithTexts", "NotValidated",
        )
        val MODES = setOf("if", "if_not", "or", "xor", "xnor", "and")
    }
}

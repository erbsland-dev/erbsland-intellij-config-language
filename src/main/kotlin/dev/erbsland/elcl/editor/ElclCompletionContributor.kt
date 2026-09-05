package dev.erbsland.elcl.editor

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import dev.erbsland.elcl.ElclFile
import dev.erbsland.elcl.style.ElclDocumentNameStyles
import dev.erbsland.elcl.style.ElclNameCategory
import dev.erbsland.elcl.style.ElclNameStyleResolver
import dev.erbsland.elcl.validation.AnalysisState
import dev.erbsland.elcl.validation.BOOLEAN_FIELDS
import dev.erbsland.elcl.validation.CANONICAL_TYPES
import dev.erbsland.elcl.validation.CONSTRAINTS
import dev.erbsland.elcl.validation.ElclDocumentAnalyzer
import dev.erbsland.elcl.validation.IN_TYPES
import dev.erbsland.elcl.validation.MODES
import dev.erbsland.elcl.validation.MULTIPLE_TYPES
import dev.erbsland.elcl.validation.RuleSection
import dev.erbsland.elcl.validation.firstValue
import dev.erbsland.elcl.validation.normalize
import dev.erbsland.elcl.validation.semanticVrPath

/** Offers recoverable, context-sensitive ELCL-VR completion variants. */
class ElclCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), object : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                val file = parameters.originalFile as? ElclFile ?: return
                if (!file.isValidationRules) return
                complete(file, parameters.offset, result)
            }
        })
    }

    private fun complete(file: ElclFile, requestedOffset: Int, result: CompletionResultSet) {
        val text = file.text
        val offset = requestedOffset.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', (offset - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val lineBefore = text.substring(lineStart, offset)
        val styles = ElclNameStyleResolver.forFile(file)
        val state = ElclDocumentAnalyzer().buildModel(text)
        val currentSection = state.sections.lastOrNull { it.range.startOffset <= offset }

        if (isSectionHeader(lineBefore)) {
            val prefix = lineBefore.substringAfterLast('[').substringAfterLast('.').trimStart()
            val candidates = reservedCandidates(state, lineBefore).map { styles.format(it, ElclNameCategory.SECTION) }
            addCandidates(result, prefix, candidates, quoted = false)
            return
        }

        val separator = findAssignmentSeparator(lineBefore)
        if (separator < 0) {
            val prefix = lineBefore.trimStart()
            val candidates = fieldCandidates(currentSection).map { styles.format(it, ElclNameCategory.VALUE) }
            addCandidates(result, prefix, candidates, quoted = false)
            return
        }

        val fieldName = normalize(lineBefore.substring(0, separator))
        val valueFragment = lineBefore.substring(separator + 1).substringAfterLast(',').trimStart()
        val quoted = valueFragment.startsWith('"')
        val prefix = valueFragment.removePrefix("\"")
        val candidates = valueCandidates(fieldName, currentSection, state, styles)
        addCandidates(result, prefix, candidates, quoted = fieldName !in BOOLEAN_FIELDS, openingQuotePresent = quoted)
    }

    private fun reservedCandidates(state: AnalysisState, lineBefore: String): List<String> {
        val pathText = lineBefore.substringAfterLast('[').removePrefix(".")
        val parent = pathText.substringBeforeLast('.', "").split('.').map(String::trim).filter(String::isNotEmpty).map(::normalize)
        if (parent.isEmpty()) return listOf("vr_template", "vr_key", "vr_dependency")
        if (parent.lastOrNull() == "vr_any") return listOf("vr_name", "vr_dependency", "vr_key")
        val parentType = state.sections.lastOrNull { semanticVrPath(it.path) == semanticVrPath(parent) }
            ?.fields?.get("type")?.value?.let(::firstValue)?.let(::normalize)
        return buildList {
            if (parentType in LIST_TYPES) add("vr_entry")
            add("vr_any")
            add("vr_dependency")
            add("vr_key")
        }.distinct()
    }

    private fun fieldCandidates(section: RuleSection?): List<String> {
        if (section == null) return emptyList()
        val fields = when (section.path.lastOrNull()) {
            "vr_key" -> INDEX_FIELDS
            "vr_dependency" -> DEPENDENCY_FIELDS
            "vr_name" -> NAME_RULE_FIELDS
            else -> nodeRuleFields(section)
        }
        return fields.filterNot(section.fields::containsKey)
    }

    private fun nodeRuleFields(section: RuleSection): Set<String> {
        val type = section.fields["type"]?.value?.let(::firstValue)?.let(::normalize)
        val result = linkedSetOf<String>()
        result += NODE_FIELDS
        result += VERSION_FIELDS
        if (type == null) result += CONSTRAINTS
        if (type == "text") result += TEXT_CONSTRAINTS
        if (type in IN_TYPES) result += setOf("equals", "in", "not_equals", "not_in")
        if (type in MULTIPLE_TYPES) result += setOf("multiple", "not_multiple")
        if (type in setOf("integer", "text")) result += setOf("key", "not_key")
        if (type !in NO_MINIMUM_TYPES) result += setOf("minimum", "maximum")
        if ("type" in section.fields) result.remove("use_template")
        if ("use_template" in section.fields) result.remove("type")
        if ("default" in section.fields) result.remove("is_optional")
        if ("is_optional" in section.fields) result.remove("default")
        if (section.path.lastOrNull() == "vr_entry") result.removeAll(setOf("default", "is_optional"))
        if (section.path.firstOrNull() == "vr_template") result.remove("use_template")
        return result
    }

    private fun valueCandidates(
        field: String,
        section: RuleSection?,
        state: AnalysisState,
        styles: ElclDocumentNameStyles,
    ): List<String> = when {
        field == "type" -> CANONICAL_TYPES.map { styles.format(it, ElclNameCategory.IDENTIFIER) }
        field == "mode" -> MODES.map { styles.format(it, ElclNameCategory.IDENTIFIER) }
        field == "use_template" -> state.sections
            .filter { it.path.firstOrNull() == "vr_template" && it.path.size == 2 }
            .map { styles.format(it.path[1], ElclNameCategory.IDENTIFIER) }
            .distinct()
        field in BOOLEAN_FIELDS -> listOf("Yes", "No")
        field == "key" && section?.path?.lastOrNull() == "vr_key" -> indexKeyPaths(state, section, styles)
        field == "key" && section != null -> visibleIndexNames(state, section, styles)
        field in setOf("source", "target") && section != null -> dependencyPaths(state, section, styles)
        else -> emptyList()
    }

    private fun indexKeyPaths(state: AnalysisState, index: RuleSection, styles: ElclDocumentNameStyles): List<String> {
        val scope = semanticVrPath(index.path.dropLast(1))
        return state.sections.mapNotNull { target ->
            val targetPath = semanticVrPath(target.path)
            if (targetPath.take(scope.size) != scope) return@mapNotNull null
            val relative = targetPath.drop(scope.size)
            val entries = relative.indices.filter { relative[it] == "vr_entry" }
            if (entries.size != 1 || entries.single() == 0 || entries.single() == relative.lastIndex) return@mapNotNull null
            val listPath = scope + relative.take(entries.single())
            val listType = state.sections.lastOrNull { semanticVrPath(it.path) == listPath }
                ?.fields?.get("type")?.value?.let(::firstValue)?.let(::normalize)
            val targetType = target.fields["type"]?.value?.let(::firstValue)?.let(::normalize)
            if (listType !in SECTION_LIST_TYPES || targetType !in setOf("integer", "text")) return@mapNotNull null
            relative.joinToString(".") { styles.format(it, ElclNameCategory.IDENTIFIER) }
        }.distinct()
    }

    private fun visibleIndexNames(state: AnalysisState, node: RuleSection, styles: ElclDocumentNameStyles): List<String> {
        val nodePath = semanticVrPath(node.path)
        return state.sections.filter { it.path.lastOrNull() == "vr_key" }.mapNotNull { index ->
            val scope = semanticVrPath(index.path.dropLast(1))
            val name = index.fields["name"]?.value?.let(::firstValue) ?: return@mapNotNull null
            if (nodePath.take(scope.size) != scope) return@mapNotNull null
            Triple(normalize(name), scope.size, styles.format(name, ElclNameCategory.IDENTIFIER))
        }.groupBy { it.first }.values.mapNotNull { matches -> matches.maxByOrNull { it.second }?.third }.distinct()
    }

    private fun dependencyPaths(state: AnalysisState, dependency: RuleSection, styles: ElclDocumentNameStyles): List<String> {
        val scope = semanticVrPath(dependency.path.dropLast(1))
        return state.sections.mapNotNull { candidate ->
            val path = semanticVrPath(candidate.path)
            if (path.take(scope.size) != scope || path.size <= scope.size || path.any { it == "vr_entry" }) return@mapNotNull null
            if (path.lastOrNull()?.startsWith("vr_") == true) return@mapNotNull null
            path.drop(scope.size).joinToString(".") { styles.format(it, ElclNameCategory.IDENTIFIER) }
        }.distinct()
    }

    private fun addCandidates(
        result: CompletionResultSet,
        prefix: String,
        candidates: Iterable<String>,
        quoted: Boolean,
        openingQuotePresent: Boolean = false,
    ) {
        val matched = result.withPrefixMatcher(ElclVrPrefixMatcher(prefix))
        candidates.distinct().sorted().forEach { candidate ->
            var builder = LookupElementBuilder.create(candidate).withTypeText("ELCL-VR", true)
            if (quoted) builder = builder.withInsertHandler { context, _ ->
                val document = context.document
                val start = context.startOffset
                var end = context.tailOffset
                if (!openingQuotePresent) {
                    document.insertString(start, "\"")
                    end++
                }
                if (end >= document.textLength || document.charsSequence[end] != '"') document.insertString(end, "\"")
                context.editor.caretModel.moveToOffset(end)
            }
            matched.addElement(builder)
        }
    }

    private fun isSectionHeader(line: String): Boolean {
        val open = line.indexOf('[')
        return open >= 0 && line.indexOf(']', open) < 0
    }

    private fun findAssignmentSeparator(line: String): Int {
        var quoted = false
        line.forEachIndexed { index, char ->
            if (char == '"') quoted = !quoted
            if (!quoted && (char == ':' || char == '=')) return index
        }
        return -1
    }

    private companion object {
        val INDEX_FIELDS = setOf("name", "key", "case_sensitive")
        val DEPENDENCY_FIELDS = setOf("mode", "source", "target", "error")
        val NAME_RULE_FIELDS = setOf("title", "description", "error", "case_sensitive", "minimum", "maximum", "matches", "starts", "ends", "contains", "chars", "allowed_chars")
        val NODE_FIELDS = setOf("type", "use_template", "default", "is_optional", "title", "description", "error", "is_secret", "case_sensitive")
        val VERSION_FIELDS = setOf("version", "minimum_version", "maximum_version", "not_version")
        val NO_MINIMUM_TYPES = setOf("boolean", "not_validated", "notvalidated")
        val TEXT_CONSTRAINTS = setOf("matches", "starts", "ends", "contains", "allowed_chars", "chars")
        val LIST_TYPES = setOf("value_list", "valuelist", "value_matrix", "valuematrix", "section_list", "sectionlist")
        val SECTION_LIST_TYPES = setOf("section_list", "sectionlist")
    }
}

/** Matches ELCL names without case or space/underscore sensitivity. */
private class ElclVrPrefixMatcher(prefix: String) : PrefixMatcher(prefix) {
    override fun prefixMatches(name: String): Boolean = matchForm(name).startsWith(matchForm(prefix))
    override fun cloneWithPrefix(prefix: String): PrefixMatcher = ElclVrPrefixMatcher(prefix)

    private fun matchForm(value: String): String = value.lowercase().replace(' ', '_')
}

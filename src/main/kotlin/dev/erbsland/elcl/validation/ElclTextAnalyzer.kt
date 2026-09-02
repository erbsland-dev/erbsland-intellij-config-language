package dev.erbsland.elcl.validation

import com.intellij.openapi.util.TextRange
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/** Fast, editor-oriented ELCL 1.3.1 validation. It deliberately recovers at every line. */
class ElclTextAnalyzer(
    private val projectRoot: Path? = null,
) {
    fun analyze(text: String, validationRules: Boolean, source: Path? = null): List<ElclDiagnostic> {
        val state = State(text)
        parseDocument(state, source, 0, linkedSetOf(), reportIncludedErrors = false)
        if (validationRules) validateRules(state)
        return state.diagnostics.distinctBy { Triple(it.range, it.message, it.severity) }
    }

    private fun parseDocument(
        state: State,
        source: Path?,
        includeDepth: Int,
        includeStack: LinkedHashSet<Path>,
        reportIncludedErrors: Boolean,
    ) {
        var currentPath: List<String>? = null
        var lastAbsolute: List<String>? = null
        var afterInclude = false
        var seenSection = false
        var multiline: Multiline? = null
        var expectsContinuation = false
        var continuationIndent: String? = null
        val lines = state.text.lineSequence().toList()
        var offset = 0

        lines.forEachIndexed { lineIndex, rawLine ->
            val lineRange = TextRange(offset, offset + rawLine.length)
            val indent = rawLine.takeWhile { it == ' ' || it == '\t' }
            val significant = stripComment(rawLine).trim()
            if (rawLine.toByteArray(Charsets.UTF_8).size > 4000) {
                state.error(lineRange, "Line exceeds the ELCL limit of 4000 UTF-8 bytes")
            }

            if (multiline != null) {
                val active = multiline!!
                val content = rawLine.substring(indent.length)
                val isEmptyLine = content.isEmpty()
                val isClosingLine = isMultilineClosingLine(content, active.close)
                if (!isEmptyLine && active.indent == null) {
                    active.indent = indent
                    if (indent.isEmpty()) {
                        state.error(lineRange, "Continued multiline content must be indented by at least one space or tab")
                    }
                }
                val requiredIndent = active.indent
                if (!isEmptyLine && requiredIndent != null && !indent.startsWith(requiredIndent)) {
                    state.error(lineRange, "Multiline indentation must start with the pattern established by its first continued line")
                }
                if (isClosingLine && requiredIndent != null) {
                    when {
                        indent == requiredIndent -> multiline = null
                        !indent.startsWith(requiredIndent) -> {
                            state.error(lineRange, "Multiline closing delimiter indentation must match the established indentation pattern")
                            multiline = null
                        }
                        // A delimiter with additional indentation is content, not the closing delimiter.
                    }
                }
                offset += rawLine.length + if (lineIndex < lines.lastIndex) 1 else 0
                return@forEachIndexed
            }
            if (significant.isEmpty()) {
                offset += rawLine.length + if (lineIndex < lines.lastIndex) 1 else 0
                return@forEachIndexed
            }

            if (expectsContinuation && significant.startsWith('*')) {
                if (continuationIndent == null) continuationIndent = indent
                else if (continuationIndent != indent) state.error(lineRange, "All rows of a multiline list or matrix must use the same indentation")
                offset += rawLine.length + if (lineIndex < lines.lastIndex) 1 else 0
                return@forEachIndexed
            }
            val continuedOpening = multilineOpening(significant)
            if (expectsContinuation && continuedOpening != null) {
                if (indent.isEmpty()) {
                    state.error(lineRange, "A value placed on the line after its separator must be indented")
                }
                multiline = Multiline(indent, continuedOpening)
                expectsContinuation = false
                continuationIndent = null
                offset += rawLine.length + if (lineIndex < lines.lastIndex) 1 else 0
                return@forEachIndexed
            }
            expectsContinuation = false
            continuationIndent = null

            val section = parseSection(significant, lineRange, state)
            if (section != null) {
                if (rawLine.firstOrNull()?.let { it == ' ' || it == '\t' } == true) state.error(lineRange, "Section headers must start at the beginning of the line")
                seenSection = true
                val rawPath = section.path
                val relative = rawPath.startsWith('.')
                val elements = splitPath(rawPath.removePrefix("."), lineRange, state)
                if (elements.size > 10) state.error(section.range, "Section paths are limited to 10 name elements")
                val normalized = elements.map(::normalize)
                if (relative) {
                    if (lastAbsolute == null || afterInclude) {
                        state.error(section.range, "A relative section requires a preceding absolute section in this document")
                        currentPath = normalized
                    } else currentPath = lastAbsolute!! + normalized
                } else {
                    currentPath = normalized
                    lastAbsolute = normalized
                }
                afterInclude = false
                val key = currentPath!!.joinToString(".")
                val previous = state.paths.putIfAbsent(key, section.range)
                if (previous != null && !section.list) state.error(section.range, "Duplicate or conflicting section path '$key'")
                state.sections += RuleSection(currentPath!!, section.list, section.range, significant)
                offset += rawLine.length + if (lineIndex < lines.lastIndex) 1 else 0
                return@forEachIndexed
            }

            val assignment = parseAssignment(significant, lineRange)
            if (assignment == null) {
                state.error(lineRange, "Expected a section, meta command, or name-value assignment")
            } else if (assignment.name.startsWith('@')) {
                val meta = normalize(assignment.name.removePrefix("@"))
                when (meta) {
                    "version" -> {
                        if (seenSection) state.error(assignment.nameRange, "@version must appear before the first section")
                        if (!state.metaValues.add("version")) state.error(assignment.nameRange, "@version may only be declared once")
                        if (firstValue(assignment.value) != "1.0") state.error(assignment.valueRange, "The supported ELCL document version is \"1.0\"")
                    }
                    "features" -> validateFeatures(state, assignment)
                    "include" -> {
                        afterInclude = true
                        currentPath = null
                        lastAbsolute = null
                        resolveInclude(state, assignment, source, includeDepth, includeStack)
                    }
                    "signature" -> Unit
                    else -> state.error(assignment.nameRange, "Unknown ELCL meta name '@$meta'")
                }
            } else {
                val name = normalize(assignment.name)
                validateName(assignment.name, assignment.nameRange, state)
                if (currentPath == null) state.error(assignment.nameRange, "Values must be placed inside a section")
                val fullPath = (currentPath.orEmpty() + name).joinToString(".")
                if (state.values.putIfAbsent(fullPath, assignment.nameRange) != null) {
                    state.error(assignment.nameRange, "Duplicate or normalized name conflict at '$fullPath'")
                }
                val pathConflict = state.paths.keys.any { it == fullPath || it.startsWith("$fullPath.") || fullPath.startsWith("$it.") && state.values.containsKey(it) }
                if (pathConflict) state.error(assignment.nameRange, "Value path conflicts with an existing value or section")
                validateLiteral(state, assignment.value, assignment.valueRange)
                state.sections.lastOrNull()?.fields?.put(name, RuleField(name, assignment.value, assignment.nameRange, assignment.valueRange))?.let {
                    state.error(assignment.nameRange, "Duplicate field '$name' in this section")
                }
                val opening = multilineOpening(assignment.value)
                // With an inline opener, the first non-empty continued line establishes indentation.
                if (opening != null) multiline = Multiline(null, opening)
                if (assignment.value.isEmpty()) expectsContinuation = true
            }
            offset += rawLine.length + if (lineIndex < lines.lastIndex) 1 else 0
        }
        if (multiline != null) state.error(TextRange(state.text.length.coerceAtLeast(1) - 1, state.text.length), "Unterminated multiline value")
    }

    private fun resolveInclude(
        state: State,
        assignment: Assignment,
        source: Path?,
        depth: Int,
        stack: LinkedHashSet<Path>,
    ) {
        val requested = firstValue(assignment.value)
        if (requested == null) {
            state.error(assignment.valueRange, "@include requires a text source")
            return
        }
        if (requested.substringBefore(':').let { requested.contains(':') && it != "file" }) {
            state.warning(assignment.valueRange, "Only local file: include sources can be analyzed by the IDE")
            return
        }
        if (source == null || projectRoot == null) {
            state.warning(assignment.valueRange, "Include cannot be resolved without a project file location")
            return
        }
        if (depth >= 5) {
            state.error(assignment.valueRange, "Include nesting exceeds the five-document limit")
            return
        }
        val pattern = requested.removePrefix("file:").replace('\\', '/')
        if (pattern.split('/').any { it.contains("**") && it != "**" }) {
            state.error(assignment.valueRange, "'**' must be a standalone include path element")
            return
        }
        if (pattern.split('/').dropLast(1).any { it.contains('*') && it != "**" }) {
            state.error(assignment.valueRange, "'*' is allowed only in the filename; use a standalone '**' for directories")
            return
        }
        val base = source.parent ?: projectRoot
        val root = projectRoot.toAbsolutePath().normalize()
        val staticPrefix = pattern.substringBefore('*').substringBeforeLast('/', "")
        val searchRoot = base.resolve(staticPrefix).normalize().let { if (Files.isDirectory(it)) it else it.parent ?: base }
        if (!searchRoot.toAbsolutePath().normalize().startsWith(root)) {
            state.warning(assignment.valueRange, "Include is outside the project content root and was not analyzed")
            return
        }
        val candidates = try {
            if ('*' !in pattern) listOf(base.resolve(pattern).normalize())
            else Files.walk(searchRoot).use { stream ->
                val matcher = FileSystems.getDefault().getPathMatcher("glob:${base.resolve(pattern).normalize()}")
                stream.filter { matcher.matches(it) }.toList()
            }
        } catch (_: Exception) {
            emptyList()
        }.filter { it.isRegularFile() && it.toAbsolutePath().normalize().startsWith(root) }.sortedBy { it.toString() }
        if (candidates.isEmpty()) {
            state.warning(assignment.valueRange, "Include source does not match an available project file")
            return
        }
        candidates.forEach { candidate ->
            val canonical = candidate.toAbsolutePath().normalize()
            if (!stack.add(canonical)) {
                state.error(assignment.valueRange, "Include cycle detected for '${candidate.fileName}'")
                return@forEach
            }
            try {
                // Included documents are parsed independently; only their value-tree paths are merged.
                val child = State(candidate.readText())
                parseDocument(child, candidate, depth + 1, stack, reportIncludedErrors = false)
                child.paths.forEach { (path, _) ->
                    if (state.paths.putIfAbsent(path, assignment.valueRange) != null) state.error(assignment.valueRange, "Included section conflicts at '$path'")
                }
                child.values.forEach { (path, _) ->
                    if (state.values.putIfAbsent(path, assignment.valueRange) != null) state.error(assignment.valueRange, "Included value conflicts at '$path'")
                }
            } catch (_: Exception) {
                state.warning(assignment.valueRange, "Included file '${candidate.fileName}' could not be analyzed")
            } finally {
                stack.remove(canonical)
            }
        }
    }

    private fun validateRules(state: State) {
        val templates = state.sections.filter { it.path.firstOrNull() == "vr_template" && it.path.size == 2 }
            .associateBy { it.path[1] }
        val namedIndexes = linkedMapOf<String, RuleSection>()
        state.sections.filter { it.path.lastOrNull() == "vr_key" }.forEach { index ->
            index.fields["name"]?.let { field ->
                val name = normalize(firstValue(field.value).orEmpty())
                if (name.isEmpty()) state.error(field.valueRange, "Index name must be a non-empty ELCL name")
                else if (namedIndexes.putIfAbsent(name, index) != null) state.error(field.valueRange, "Duplicate normalized index name '$name'")
            }
        }
        state.sections.forEach { section ->
            val reserved = section.path.filter { it.startsWith("vr_") }
            reserved.filter { it !in RESERVED_NAMES && !it.startsWith("vr_vr_") }.forEach {
                state.error(section.range, "Unknown reserved validation-rules name '$it'; use 'vr_$it' to escape a literal vr_ name")
            }
            if ("vr_template" in section.path && section.path.firstOrNull() != "vr_template") {
                state.error(section.range, "vr_template is only valid at the document root")
            }
            if (section.path.lastOrNull() == "vr_any" && section.path.size == 1) state.error(section.range, "vr_any requires a parent section rule")
            when (section.path.lastOrNull()) {
                "vr_dependency" -> validateDependency(state, section)
                "vr_key" -> validateIndex(state, section)
                else -> validateNodeRule(state, section, templates, namedIndexes.keys)
            }
        }
        validateAlternatives(state)
    }

    private fun validateAlternatives(state: State) {
        state.sections.groupBy { it.path }.filterValues { it.size > 1 }.forEach { (_, alternatives) ->
            if (alternatives.any { !it.list }) {
                alternatives.drop(1).forEach { state.error(it.range, "Multiple definitions for one node must all use section-list alternative syntax") }
                return@forEach
            }
            val defaults = alternatives.mapNotNull { it.fields["default"] }
            defaults.drop(1).forEach { state.error(it.nameRange, "Only one alternative may define a default") }
            val optional = alternatives.mapIndexedNotNull { index, rule -> rule.fields["is_optional"]?.let { index to it } }
            optional.drop(1).forEach { (_, field) -> state.error(field.nameRange, "Only one alternative may define is_optional") }
            optional.firstOrNull()?.takeIf { it.first != 0 }?.let { (_, field) -> state.error(field.nameRange, "is_optional must be defined on the first alternative") }
        }
    }

    private fun validateNodeRule(state: State, section: RuleSection, templates: Map<String, RuleSection>, indexes: Set<String>) {
        val last = section.path.lastOrNull() ?: return
        val isTemplate = section.path.firstOrNull() == "vr_template" && section.path.size == 2
        val isName = last == "vr_name"
        val type = section.fields["type"]
        val useTemplate = section.fields["use_template"]
        if (isTemplate && useTemplate != null) state.error(useTemplate.nameRange, "Templates cannot inherit from another template")
        if (type != null && useTemplate != null) state.error(useTemplate.nameRange, "A node rule cannot define both type and use_template")
        if (!isName && type == null && useTemplate == null) state.error(section.range, "Node rule requires exactly one effective type or use_template")
        if (isName && section.path.dropLast(1).lastOrNull() != "vr_any") state.error(section.range, "vr_name is only valid below a vr_any rule")
        if (type != null) {
            val id = normalize(firstValue(type.value).orEmpty())
            if (id !in TYPES) state.error(type.valueRange, "Unknown ELCL-VR type '${firstValue(type.value).orEmpty()}'")
        }
        if (useTemplate != null) {
            val target = normalize(firstValue(useTemplate.value).orEmpty())
            if (target !in templates) state.error(useTemplate.valueRange, "Unknown validation-rules template '$target'")
        }
        val effectiveType = type?.value?.let(::firstValue)?.let(::normalize)
            ?: useTemplate?.value?.let(::firstValue)?.let(::normalize)?.let { templates[it]?.fields?.get("type")?.value?.let(::firstValue)?.let(::normalize) }
        if (last == "vr_entry") {
            val parentType = state.sections.find { it.path == section.path.dropLast(1) }?.fields?.get("type")?.value?.let(::firstValue)?.let(::normalize)
            if (parentType !in setOf("value_list", "valuelist", "value_matrix", "valuematrix", "section_list", "sectionlist")) state.error(section.range, "vr_entry requires a ValueList, ValueMatrix, or SectionList parent")
            if (section.fields.containsKey("default")) state.error(section.fields.getValue("default").nameRange, "vr_entry must not define a default")
            if (section.fields.containsKey("is_optional")) state.error(section.fields.getValue("is_optional").nameRange, "vr_entry must not be optional")
            if (parentType in setOf("value_list", "valuelist", "value_matrix", "valuematrix") && effectiveType in STRUCTURAL_TYPES + setOf("value_list", "valuelist", "value_matrix", "valuematrix")) {
                state.error(type?.valueRange ?: section.range, "Value-list and matrix entries must use scalar types")
            }
            if (parentType in setOf("section_list", "sectionlist") && effectiveType != null && effectiveType !in setOf("section", "section_with_texts", "sectionwithtexts")) {
                state.error(type?.valueRange ?: section.range, "SectionList entries must use Section or SectionWithTexts")
            }
        }
        if (section.fields.containsKey("default") && section.fields.containsKey("is_optional")) {
            state.error(section.fields.getValue("is_optional").nameRange, "A default already makes the node optional; is_optional must not also be set")
        }
        if (section.fields.containsKey("default") && effectiveType in STRUCTURAL_TYPES) state.error(section.fields.getValue("default").nameRange, "Defaults are only valid for scalar values and value lists")
        if (section.fields.containsKey("is_secret") && effectiveType in STRUCTURAL_TYPES) state.error(section.fields.getValue("is_secret").nameRange, "is_secret is only valid for scalar values")
        section.fields["key"]?.let {
            val indexName = normalize(firstValue(it.value).orEmpty().substringBefore('['))
            if (indexName !in indexes) state.error(it.valueRange, "Unknown vr_key index reference")
        }
        if (effectiveType in setOf("value_list", "valuelist", "value_matrix", "valuematrix", "section_list", "sectionlist")) {
            val hasEntry = state.sections.any { candidate ->
                candidate.path.size > section.path.size && candidate.path.take(section.path.size) == section.path && candidate.path[section.path.size] == "vr_entry"
            }
            if (!hasEntry) state.error(section.range, "List and matrix rules require a vr_entry definition")
        }
        validateFieldsAndConstraints(state, section, effectiveType)
    }

    private fun validateFieldsAndConstraints(state: State, section: RuleSection, effectiveType: String?) {
        section.fields.values.forEach { field ->
            val constraintName = field.name.removeSuffix("_error")
            val base = constraintName.removePrefix("not_")
            if (field.name !in COMMON_FIELDS && base !in CONSTRAINTS) state.error(field.nameRange, "Unknown ELCL-VR field '${field.name}'")
            if (field.name.endsWith("_error") && !section.fields.containsKey(constraintName)) {
                state.error(field.nameRange, "Custom error field requires its exact matching '$constraintName' constraint")
            }
            if (field.name.endsWith("_error") && base in setOf("version", "minimum_version", "maximum_version")) {
                state.error(field.nameRange, "Version constraints do not support custom error messages")
            }
            if (field.name.startsWith("not_") && base !in NEGATABLE) state.error(field.nameRange, "Constraint '$base' has no not_ form")
            if (constraintName.startsWith("not_") && section.fields.containsKey(base)) {
                state.error(field.nameRange, "A constraint cannot combine '$base' with its negated form")
            }
            if (base in setOf("matches", "starts", "ends", "contains", "allowed_chars", "chars") && effectiveType != "text") {
                state.error(field.nameRange, "Constraint '$base' is only applicable to type Text")
            }
            if (base == "multiple" && effectiveType !in MULTIPLE_TYPES) state.error(field.nameRange, "multiple is not applicable to this node type")
            if (base == "in" && effectiveType !in IN_TYPES) state.error(field.nameRange, "in is not applicable to this node type")
            if (base == "key" && effectiveType !in setOf("integer", "text")) state.error(field.nameRange, "key is only applicable to Integer and Text nodes")
        }
        section.fields["in"]?.let { field ->
            val values = valueList(field.value).map { if (effectiveType == "text") it.lowercase() else it }
            if (values.size != values.distinct().size) state.error(field.valueRange, "The in constraint must not contain duplicate values")
        }
        section.fields["version"]?.let { field ->
            val versions = valueList(field.value)
            if (versions.any { it.toLongOrNull() == null }) state.error(field.valueRange, "version accepts only integer values")
            if (versions.size != versions.distinct().size) state.error(field.valueRange, "version must not contain duplicate values")
        }
        listOf("minimum_version", "maximum_version").forEach { name ->
            section.fields[name]?.let { if (valueList(it.value).size != 1 || firstValue(it.value)?.toLongOrNull() == null) state.error(it.valueRange, "$name requires exactly one integer") }
        }
        val minimum = section.fields["minimum"]?.value?.let(::firstNumber)
        val maximum = section.fields["maximum"]?.value?.let(::firstNumber)
        if (minimum != null && maximum != null && minimum > maximum) state.error(section.fields.getValue("maximum").valueRange, "maximum must not be smaller than minimum")
        val minVersion = section.fields["minimum_version"]?.value?.let(::firstValue)
        val maxVersion = section.fields["maximum_version"]?.value?.let(::firstValue)
        if (minVersion != null && maxVersion != null && compareVersions(minVersion, maxVersion) > 0) state.error(section.fields.getValue("maximum_version").valueRange, "maximum_version must not be smaller than minimum_version")
    }

    private fun validateDependency(state: State, section: RuleSection) {
        if (!section.list) state.error(section.range, "vr_dependency must be a section list (*[...]* syntax)")
        listOf("mode", "source", "target").forEach { name -> if (name !in section.fields) state.error(section.range, "vr_dependency requires '$name'") }
        section.fields["mode"]?.let {
            if (normalize(firstValue(it.value).orEmpty()) !in MODES) state.error(it.valueRange, "Unknown dependency mode; expected if, if_not, or, xor, xnor, or and")
        }
        section.fields.keys.filter { it !in setOf("mode", "source", "target", "error") }.forEach { state.error(section.fields.getValue(it).nameRange, "Field '$it' is not valid in vr_dependency") }
        listOf("source", "target").forEach { fieldName ->
            section.fields[fieldName]?.let { field ->
                valueList(field.value).forEach { reference -> validateDependencyReference(state, section, field, reference) }
            }
        }
    }

    private fun validateDependencyReference(state: State, dependency: RuleSection, field: RuleField, reference: String) {
        val relative = reference.split('.').map(::normalize)
        if (relative.any { it == "vr_entry" }) {
            state.error(field.valueRange, "Dependencies cannot reference values inside section-list entries")
            return
        }
        val parent = dependency.path.dropLast(1)
        val path = parent + relative
        val rules = state.sections.filter { it.path == path }
        if (rules.isEmpty()) {
            state.error(field.valueRange, "Dependency reference '$reference' has no node-rule definition in this scope")
            return
        }
        if (rules.size > 1 || rules.any { it.list }) state.error(field.valueRange, "Dependencies cannot reference nodes defined by alternatives")
        val conditionallyPresent = path.indices.any { index ->
            state.sections.filter { it.path == path.take(index + 1) }.any { "is_optional" in it.fields || "default" in it.fields }
        }
        if (!conditionallyPresent) state.error(field.valueRange, "Dependency reference '$reference' is unconditionally required")
    }

    private fun validateIndex(state: State, section: RuleSection) {
        if (!section.list) state.error(section.range, "vr_key must be a section list (*[...]* syntax)")
        if ("key" !in section.fields) state.error(section.range, "vr_key requires 'key'")
        section.fields.keys.filter { it !in setOf("name", "key", "case_sensitive") }.forEach { state.error(section.fields.getValue(it).nameRange, "Field '$it' is not valid in vr_key") }
        section.fields["key"]?.let { field ->
            val listRoots = mutableSetOf<List<String>>()
            valueList(field.value).forEach { reference ->
                val path = reference.split('.').map(::normalize)
                val entryIndexes = path.indices.filter { path[it] == "vr_entry" }
                if (entryIndexes.size != 1) {
                    state.error(field.valueRange, "Index key paths must pass through exactly one SectionList vr_entry")
                    return@forEach
                }
                val listPath = path.take(entryIndexes.single())
                listRoots += listPath
                val listType = state.sections.find { it.path == listPath }?.fields?.get("type")?.value?.let(::firstValue)?.let(::normalize)
                if (listType !in setOf("section_list", "sectionlist")) state.error(field.valueRange, "Index key path must reference a SectionList")
                val targetType = state.sections.find { it.path == path }?.fields?.get("type")?.value?.let(::firstValue)?.let(::normalize)
                if (targetType !in setOf("integer", "text")) state.error(field.valueRange, "Indexed values must use type Integer or Text")
            }
            if (listRoots.size > 1) state.error(field.valueRange, "All values in a composite index must reference the same SectionList")
        }
    }

    private fun validateFeatures(state: State, assignment: Assignment) {
        val values = valueList(assignment.value).map(::normalize)
        values.filter { it !in FEATURES }.forEach { state.error(assignment.valueRange, "Unknown ELCL feature identifier '$it'") }
    }

    private fun validateLiteral(state: State, value: String, range: TextRange) {
        val trimmed = value.trim()
        if (trimmed.startsWith('"') && !trimmed.endsWith('"') && !trimmed.startsWith("\"\"\"")) state.error(range, "Unterminated text literal")
        Regex("\\\\(?:u(?![0-9a-fA-F]{4})|U(?![0-9a-fA-F]{8})|x(?![0-9a-fA-F]{2}))").findAll(trimmed).forEach {
            state.error(TextRange(range.startOffset + it.range.first, (range.startOffset + it.range.last + 1).coerceAtMost(range.endOffset)), "Malformed hexadecimal escape")
        }
        if (trimmed.endsWith('\\') && trimmed.startsWith('"')) state.error(TextRange(range.endOffset - 1, range.endOffset), "Incomplete escape sequence")
    }

    private fun parseSection(line: String, range: TextRange, state: State): ParsedSection? {
        val undecorated = line.trim('-').trim()
        val list = undecorated.startsWith("*[")
        if (!list && !undecorated.startsWith('[')) return null
        val close = if (list && undecorated.endsWith("]*")) "]*" else "]"
        if (!undecorated.endsWith(close)) {
            state.error(range, "Unterminated or mismatched section delimiter")
            return null
        }
        val start = if (list) 2 else 1
        return ParsedSection(undecorated.substring(start, undecorated.length - close.length).trim(), list, range)
    }

    private fun parseAssignment(line: String, range: TextRange): Assignment? {
        var quote = false
        var escape = false
        line.forEachIndexed { index, char ->
            if (escape) escape = false
            else if (char == '\\' && quote) escape = true
            else if (char == '"') quote = !quote
            else if (!quote && (char == ':' || char == '=')) {
                val name = line.substring(0, index).trim()
                if (name.isEmpty()) return null
                val value = line.substring(index + 1).trim()
                val nameStart = range.startOffset + line.indexOf(name)
                val valueStart = if (value.isEmpty()) range.endOffset else range.startOffset + line.indexOf(value, index + 1)
                return Assignment(name, value, TextRange(nameStart, nameStart + name.length), TextRange(valueStart, valueStart + value.length))
            }
        }
        return null
    }

    private fun splitPath(path: String, range: TextRange, state: State): List<String> {
        val result = mutableListOf<String>()
        var quote = false
        var start = 0
        path.forEachIndexed { index, char ->
            if (char == '"') quote = !quote
            if (char == '.' && !quote) {
                result += path.substring(start, index).trim().trim('"')
                start = index + 1
            }
        }
        result += path.substring(start).trim().trim('"')
        if (result.any { it.isEmpty() }) state.error(range, "Name paths cannot contain empty elements")
        result.forEach { validateName(it, range, state) }
        return result
    }

    private fun validateName(name: String, range: TextRange, state: State) {
        val plain = name.trim().trim('"')
        if (plain.length > 100) state.error(range, "Names are limited to 100 characters")
        if (plain.isEmpty() || !plain.first().isLetter() || plain.any { !(it.isLetterOrDigit() || it == ' ' || it == '_') }) {
            state.error(range, "Regular names use letters, digits, spaces, and underscores and must start with a letter")
        }
        if (plain.contains("  ") || plain.contains("__") || plain.contains(" _") || plain.contains("_ ")) state.error(range, "Name separators cannot be repeated or mixed")
    }

    private fun stripComment(line: String): String {
        var quote: Char? = null
        var escape = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            if (escape) escape = false
            else if (char == '\\' && quote != null) escape = true
            else if (quote == null && MULTILINE_OPENERS.any { line.startsWith(it, index) }) {
                index += 3
                continue
            }
            else if (quote == null && char in charArrayOf('"', '`', '/')) quote = char
            else if (quote == char) quote = null
            else if (quote == null && char == '#') return line.substring(0, index)
            index++
        }
        return line
    }

    private fun multilineOpening(value: String): String? = when {
        value.trim().matches(Regex("\"\"\"[ \\t]*(?:#.*)?")) -> "\"\"\""
        value.trim().matches(Regex("```[A-Za-z0-9_+.-]*[ \\t]*(?:#.*)?")) -> "```"
        value.trim().matches(Regex("///[ \\t]*(?:#.*)?")) -> "///"
        value.trim().matches(Regex("<<<(?:hex)?[ \\t]*(?:#.*)?", RegexOption.IGNORE_CASE)) -> ">>>"
        else -> null
    }

    private fun isMultilineClosingLine(content: String, close: String): Boolean {
        if (!content.startsWith(close)) return false
        val remainder = content.substring(close.length)
        return remainder.isBlank() || remainder.trimStart().startsWith('#')
    }

    private fun normalize(value: String): String = value.trim().trim('"').lowercase().replace(Regex("[ _]+"), "_")
    private fun firstValue(value: String): String? = valueList(value).firstOrNull()
    private fun valueList(value: String): List<String> = value.split(',').map { it.trim().trim('"', '`') }.filter { it.isNotEmpty() }
    private fun firstNumber(value: String): Double? = valueList(value).firstOrNull()?.replace("'", "")?.toDoubleOrNull()
    private fun compareVersions(a: String, b: String): Int {
        val left = a.split('.').map { it.toIntOrNull() ?: 0 }
        val right = b.split('.').map { it.toIntOrNull() ?: 0 }
        return (0 until maxOf(left.size, right.size)).firstNotNullOfOrNull { i ->
            (left.getOrElse(i) { 0 } - right.getOrElse(i) { 0 }).takeIf { it != 0 }
        } ?: 0
    }

    private data class ParsedSection(val path: String, val list: Boolean, val range: TextRange)
    private data class Assignment(val name: String, val value: String, val nameRange: TextRange, val valueRange: TextRange)
    private data class Multiline(var indent: String?, val close: String)
    private data class RuleField(val name: String, val value: String, val nameRange: TextRange, val valueRange: TextRange)
    private data class RuleSection(
        val path: List<String>, val list: Boolean, val range: TextRange, val source: String,
        val fields: MutableMap<String, RuleField> = linkedMapOf(),
    )
    private class State(val text: String) {
        val diagnostics = mutableListOf<ElclDiagnostic>()
        val paths = linkedMapOf<String, TextRange>()
        val values = linkedMapOf<String, TextRange>()
        val sections = mutableListOf<RuleSection>()
        val metaValues = mutableSetOf<String>()
        fun error(range: TextRange, message: String) { diagnostics += ElclDiagnostic(range, message) }
        fun warning(range: TextRange, message: String) { diagnostics += ElclDiagnostic(range, message, ElclDiagnosticSeverity.WARNING) }
    }

    companion object {
        private val MULTILINE_OPENERS = listOf("\"\"\"", "```", "///", "<<<")
        val RESERVED_NAMES = setOf("vr_template", "vr_any", "vr_name", "vr_entry", "vr_key", "vr_dependency")
        val TYPES = setOf(
            "integer", "boolean", "float", "text", "date", "time", "date_time", "datetime", "bytes", "time_delta",
            "timedelta", "regex", "reg_ex", "value", "value_list", "valuelist", "value_matrix", "valuematrix",
            "section", "section_list", "sectionlist", "section_with_texts", "sectionwithtexts", "not_validated", "notvalidated",
        )
        val STRUCTURAL_TYPES = setOf("section", "section_list", "sectionlist", "section_with_texts", "sectionwithtexts", "not_validated", "notvalidated")
        val MULTIPLE_TYPES = setOf("integer", "float", "text", "bytes", "value_list", "valuelist", "value_matrix", "valuematrix", "section", "section_list", "sectionlist", "section_with_texts", "sectionwithtexts")
        val IN_TYPES = setOf("integer", "float", "text", "bytes")
        val MODES = setOf("if", "if_not", "or", "xor", "xnor", "and")
        val FEATURES = setOf(
            "core", "minimum", "standard", "advanced", "all", "float", "byte_count", "multi_line", "section_list",
            "value_list", "text_names", "date_time", "code", "byte_data", "include", "regex", "time_delta",
        )
        val COMMON_FIELDS = setOf(
            "type", "use_template", "default", "is_optional", "title", "description", "error", "is_secret", "case_sensitive",
            "name", "key", "keys", "index", "index_name", "mode", "source", "target",
        )
        val CONSTRAINTS = setOf(
            "minimum", "maximum", "equals", "in", "multiple", "matches", "starts", "ends", "contains", "chars", "allowed_chars",
            "key", "version", "minimum_version", "maximum_version",
        )
        val NEGATABLE = setOf("equals", "in", "multiple", "matches", "starts", "ends", "contains", "chars", "allowed_chars", "key", "version")
    }
}

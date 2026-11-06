package dev.erbsland.elcl.validation

import com.intellij.openapi.util.TextRange
import dev.erbsland.elcl.syntax.decodeElclText
import dev.erbsland.elcl.syntax.nameKey
import dev.erbsland.elcl.syntax.splitElclPath
import dev.erbsland.elcl.syntax.elclLines
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/** Parses one ELCL document and its local includes into the shared analysis model. */
internal class ElclDocumentAnalyzer(
    private val projectRoot: Path? = null,
) {
    /** Parses [text], optionally runs ELCL-VR checks, and returns de-duplicated findings. */
    fun analyze(text: String, validationRules: Boolean, source: Path? = null): List<ElclDiagnostic> {
        val state = buildModel(text, source)
        if (validationRules) ElclValidationRulesAnalyzer().validate(state)
        return state.diagnostics.distinctBy { Triple(it.range, it.message, it.severity) }
    }

    /** Builds the recoverable section model used by editor features while a document is incomplete. */
    internal fun buildModel(text: String, source: Path? = null): AnalysisState = AnalysisState(text).also {
        parseDocument(it, source, 0, linkedSetOf())
    }

    private fun parseDocument(
        state: AnalysisState,
        source: Path?,
        includeDepth: Int,
        includeStack: LinkedHashSet<Path>,
    ) {
        var currentPath: List<String>? = null
        var currentScope: NamespaceScope? = null
        var lastAbsolute: List<String>? = null
        var afterInclude = false
        var seenSection = false
        var multiline: Multiline? = null
        var expectsContinuation = false
        var continuationIndent: String? = null
        val lines = elclLines(state.text)
        val valueValidator = ElclValueValidator(state)
        var consumedScalarLine = -1
        var pendingRange: TextRange? = null

        lines.forEachIndexed { lineIndex, sourceLine ->
            val rawLine = sourceLine.text
            val offset = sourceLine.start
            val lineRange = TextRange(offset, offset + rawLine.length)
            val indent = rawLine.takeWhile { it == ' ' || it == '\t' }
            val significant = stripComment(rawLine).trim()
            if (rawLine.toByteArray(Charsets.UTF_8).size + sourceLine.newline.length > 4000) {
                state.error(lineRange, "Line exceeds the ELCL limit of 4000 UTF-8 bytes")
            }

            if (lineIndex == consumedScalarLine) return@forEachIndexed
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
                if (!isClosingLine || (requiredIndent != null && indent.startsWith(requiredIndent) && indent != requiredIndent)) {
                    valueValidator.validateMultilineContent(active, rawLine, lineRange)
                }
                return@forEachIndexed
            }
            if (significant.isEmpty()) {
                if (expectsContinuation && continuationIndent == null) {
                    state.error(pendingRange ?: lineRange, "A value must immediately follow its separator")
                }
                expectsContinuation = false
                continuationIndent = null
                return@forEachIndexed
            }

            if (expectsContinuation && significant.startsWith('*') && !significant.startsWith("*[")) {
                if (indent.isEmpty()) state.error(lineRange, "A value placed on the line after its separator must be indented")
                valueValidator.validate(significant.removePrefix("*").trim(), lineRange)
                if (continuationIndent == null) continuationIndent = indent
                else if (continuationIndent != indent) state.error(lineRange, "All rows of a multiline list or matrix must use the same indentation")
                return@forEachIndexed
            }
            val significantStart = rawLine.indexOf(significant).coerceAtLeast(0)
            val significantRange = TextRange(offset + significantStart, offset + significantStart + significant.length)
            val continuedOpening = valueValidator.validateContinuedOpening(significant, significantRange)
            if (expectsContinuation && continuedOpening != null) {
                if (indent.isEmpty()) {
                    state.error(lineRange, "A value placed on the line after its separator must be indented")
                }
                multiline = Multiline(indent, continuedOpening)
                expectsContinuation = false
                continuationIndent = null
                return@forEachIndexed
            }
            if (expectsContinuation && continuationIndent == null) {
                state.error(pendingRange ?: lineRange, "Expected an indented value immediately after the separator")
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
                val normalized = elements.map { runCatching { nameKey(it) }.getOrDefault(it) }
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
                currentScope = state.namespace.openSection(currentPath!!, section.list)
                if (currentScope == null) state.error(section.range, "Duplicate or conflicting section path '$key'")
                state.sections += RuleSection(currentPath!!, section.list, section.range, significant)
                return@forEachIndexed
            }

            var assignment = parseAssignment(significant, significantRange)
            if (assignment != null && assignment.value.isEmpty()) {
                val next = lines.getOrNull(lineIndex + 1)
                if (next != null && next.indent.isNotEmpty()) {
                    val value = stripComment(next.text).trim()
                    if (value.isNotEmpty() && !value.startsWith('*') && MULTILINE_OPENERS.none { value.startsWith(it) }) {
                        val valueStart = next.start + next.text.indexOf(value)
                        assignment = assignment.copy(value = value, valueRange = TextRange(valueStart, valueStart + value.length))
                        consumedScalarLine = lineIndex + 1
                        valueValidator.validate(value, assignment.valueRange)
                    }
                }
            }
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
                        currentScope = null
                        lastAbsolute = null
                        resolveInclude(state, assignment, source, includeDepth, includeStack)
                    }
                    "signature" -> Unit
                    else -> state.error(assignment.nameRange, "Unknown ELCL meta name '@$meta'")
                }
            } else {
                val name = runCatching { nameKey(assignment.name) }.getOrDefault(assignment.name)
                validateName(assignment.name, assignment.nameRange, state)
                if (currentPath == null) state.error(assignment.nameRange, "Values must be placed inside a section")
                val fullPath = (currentPath.orEmpty() + name).joinToString(".")
                if (currentScope != null && !currentScope!!.defineValue(name)) {
                    state.error(assignment.nameRange, "Duplicate or conflicting name (regular and text names cannot be mixed) at '$fullPath'")
                }
                val opening = valueValidator.validate(assignment.value, assignment.valueRange)
                state.sections.lastOrNull()?.fields?.put(name, RuleField(name, assignment.value, assignment.nameRange, assignment.valueRange))?.let {
                    state.error(assignment.nameRange, "Duplicate field '$name' in this section")
                }
                // With an inline opener, the first non-empty continued line establishes indentation.
                if (opening != null) multiline = Multiline(null, opening)
                if (assignment.value.isEmpty()) {
                    expectsContinuation = true
                    pendingRange = assignment.nameRange
                }
            }
        }
        if (expectsContinuation && continuationIndent == null) state.error(pendingRange ?: TextRange(0, 0), "Missing assignment value")
        if (multiline != null) state.error(TextRange(state.text.length.coerceAtLeast(1) - 1, state.text.length), "Unterminated multiline value")
    }

    private fun resolveInclude(
        state: AnalysisState,
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
                val child = AnalysisState(candidate.readText())
                parseDocument(child, candidate, depth + 1, stack)
                state.namespace.mergeFrom(child.namespace).forEach { path ->
                    state.error(assignment.valueRange, "Included name conflicts at '$path'")
                }
            } catch (_: Exception) {
                state.warning(assignment.valueRange, "Included file '${candidate.fileName}' could not be analyzed")
            } finally {
                stack.remove(canonical)
            }
        }
    }

    private fun validateFeatures(state: AnalysisState, assignment: Assignment) {
        val values = valueList(assignment.value).map(::normalize)
        values.filter { it !in FEATURES }.forEach { state.error(assignment.valueRange, "Unknown ELCL feature identifier '$it'") }
    }

    private fun parseSection(line: String, range: TextRange, state: AnalysisState): ParsedSection? {
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

    private fun splitPath(path: String, range: TextRange, state: AnalysisState): List<String> {
        val result = runCatching { splitElclPath(path) }.getOrElse {
            state.error(range, it.message ?: "Invalid name path")
            return emptyList()
        }
        if (result.any { it.isEmpty() }) state.error(range, "Name paths cannot contain empty elements")
        result.forEach { validateName(it, range, state) }
        return result
    }

    private fun validateName(name: String, range: TextRange, state: AnalysisState) {
        val plain = name.trim()
        if (plain.startsWith('"')) {
            ElclValueValidator(state).validate(plain, range)
            val decoded = runCatching { decodeElclText(plain.removeSurrounding("\"")) }.getOrNull()
            if (decoded.isNullOrEmpty()) state.error(range, "Text names must be nonempty valid text")
            return
        }
        if (plain.codePointCount(0, plain.length) > 100) state.error(range, "Names are limited to 100 characters")
        if (plain.isEmpty() || !plain.first().isLetter() || plain.any { !(it.isLetterOrDigit() || it == ' ' || it == '_') }) {
            state.error(range, "Regular names use letters, digits, spaces, and underscores and must start with a letter")
        }
        if (plain.contains("  ") || plain.contains("__") || plain.contains(" _") || plain.contains("_ ")) state.error(range, "Name separators cannot be repeated or mixed")
        if (plain.endsWith('_')) state.error(range, "Regular names cannot end with an underscore")
    }

    private fun stripComment(line: String): String {
        var quote: Char? = null
        var byteData = false
        var escape = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            if (escape) escape = false
            else if (char == '\\' && quote != null) escape = true
            else if (quote == null && !byteData && char == '<' && !line.startsWith("<<<", index)) byteData = true
            else if (byteData && char == '>') byteData = false
            else if (quote == null && !byteData && MULTILINE_OPENERS.any { line.startsWith(it, index) }) {
                index += 3
                continue
            }
            else if (quote == null && !byteData && char in charArrayOf('"', '`', '/')) quote = char
            else if (quote == char) quote = null
            else if (quote == null && !byteData && char == '#') return line.substring(0, index)
            index++
        }
        return line
    }

    private fun isMultilineClosingLine(content: String, close: String): Boolean {
        if (!content.startsWith(close)) return false
        val remainder = content.substring(close.length)
        return remainder.isBlank() || remainder.trimStart().startsWith('#')
    }

}

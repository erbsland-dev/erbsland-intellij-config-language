package dev.erbsland.elcl.format

import dev.erbsland.elcl.syntax.decodeElclText
import dev.erbsland.elcl.syntax.nameKey

/** Core-compatible conditions and per-property rule specificity. */
internal data class ElclFormatSelector(
    val kind: String? = null,
    val listSection: Boolean? = null,
    val relativeSection: Boolean? = null,
    val sectionNamesMoreThan: Int? = null,
    val metaValue: Boolean? = null,
    val paths: List<ElclPathPattern> = emptyList(),
    val valueTypes: List<String> = emptyList(),
) {
    fun match(statement: ElclStatement): List<Int>? {
        var conditions = 0
        if (kind != null) { if (kind != statement.kind) return null; conditions++ }
        if (listSection != null) { if (statement.kind != "section" || listSection != statement.list) return null; conditions++ }
        if (relativeSection != null) { if (statement.kind != "section" || relativeSection != statement.relative) return null; conditions++ }
        if (sectionNamesMoreThan != null) { if (statement.kind != "section" || statement.names.size <= sectionNamesMoreThan) return null; conditions++ }
        if (metaValue != null) { if (statement.kind != "assignment" || statement.meta != metaValue) return null; conditions++ }
        if (valueTypes.isNotEmpty()) { if (statement.valueType !in valueTypes) return null; conditions++ }
        var specificity = listOf(0, 0, 0, 0, 0)
        if (paths.isNotEmpty()) {
            val path = statement.path ?: return null
            specificity = paths.filter { it.matches(path) }.map { it.specificity }.maxWithOrNull(::compareScore) ?: return null
        }
        return specificity + conditions
    }
}

/** Component globs support *, ?, character classes, and whole-component **. */
internal class ElclPathPattern(pattern: String) {
    private data class Component(val recursive: Boolean, val quoted: Boolean?, val regex: Regex?)
    val specificity: List<Int>
    private val components: List<Component>
    init {
        val score = mutableListOf(2, 0, 0, 0, 0)
        components = splitPattern(pattern).map { raw ->
            require(raw.isNotEmpty()) { "Empty path pattern component" }
            if (raw == "**") {
                score[0] = 1; score[3]--
                Component(true, null, null)
            } else {
                val quoted = raw.startsWith('"')
                if (quoted) {
                    require(raw.endsWith('"') && raw.length >= 2) { "Unterminated quoted path component" }
                    val literal = decodeElclText(raw.substring(1, raw.lastIndex))
                    // Core counts the escaped glob spelling for quoted components.
                    val escaped = literal.replace("\\", "\\\\").replace("*", "\\*").replace("?", "\\?").replace("[", "\\[").replace("]", "\\]")
                    score[1]++
                    escaped.codePoints().forEach { if (it == 42 || it == 63) score[4]-- else score[2]++ }
                    Component(false, true, Regex(Regex.escape(literal)))
                } else {
                    require(!raw.contains("**")) { "Recursive wildcard must occupy an entire component" }
                    val normalized = nameKey(raw)
                    val literal = normalized.none { it == '*' || it == '?' || it == '[' }
                    if (literal) score[1]++ else score[0] = 1
                    normalized.codePoints().forEach { if (it == 42 || it == 63) score[4]-- else score[2]++ }
                    Component(false, if (literal) false else null, globRegex(normalized))
                }
            }
        }
        specificity = score
    }

    fun matches(path: List<String>): Boolean {
        val memo = mutableMapOf<Pair<Int, Int>, Boolean>()
        fun match(i: Int, j: Int): Boolean = memo.getOrPut(i to j) {
            if (i == components.size) j == path.size
            else if (components[i].recursive) match(i + 1, j) || (j < path.size && match(i, j + 1))
            else if (j == path.size) false
            else {
                val quoted = path[j].startsWith('"')
                val text = if (quoted) decodeElclText(path[j].removeSurrounding("\"")) else path[j]
                (components[i].quoted == null || components[i].quoted == quoted) && components[i].regex!!.matches(text) && match(i + 1, j + 1)
            }
        }
        return match(0, 0)
    }
}

private fun splitPattern(pattern: String): List<String> {
    val result = mutableListOf<String>()
    var quote = false
    var escape = false
    var bracket = false
    var start = 0
    pattern.forEachIndexed { i, c ->
        when {
            escape -> escape = false
            c == '\\' -> escape = true
            c == '[' && !quote -> bracket = true
            c == ']' && !quote -> bracket = false
            c == '"' && !bracket -> quote = !quote
            c == '.' && !quote && !bracket -> { result += pattern.substring(start, i).trim(); start = i + 1 }
        }
    }
    require(!quote && !escape && !bracket) { "Unterminated name-path pattern" }
    result += pattern.substring(start).trim()
    return result
}

internal fun globRegex(pattern: String): Regex {
    val regex = StringBuilder()
    var i = 0
    while (i < pattern.length) {
        when (val c = pattern[i++]) {
            '*' -> regex.append(".*")
            '?' -> regex.append('.')
            '\\' -> { require(i < pattern.length) { "Incomplete glob escape" }; regex.append(Regex.escape(pattern[i++].toString())) }
            '[' -> {
                val end = pattern.indexOf(']', i)
                require(end > i) { "Unterminated or empty glob character class" }
                var content = pattern.substring(i, end)
                if (content.startsWith('!')) content = "^" + content.drop(1)
                regex.append('[').append(content.replace("&", "\\&")).append(']')
                i = end + 1
            }
            else -> regex.append(Regex.escape(c.toString()))
        }
    }
    return Regex(regex.toString())
}

package dev.erbsland.elcl.format

import com.intellij.openapi.util.TextRange
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import dev.erbsland.elcl.lexer.ElclLexerAdapter
import dev.erbsland.elcl.lexer._ElclLexer
import dev.erbsland.elcl.psi.ElclTypes
import dev.erbsland.elcl.syntax.*
import dev.erbsland.elcl.validation.AnalysisState
import dev.erbsland.elcl.validation.ElclValueValidator
import dev.erbsland.elcl.validation.Multiline

internal data class ElclLexeme(val type: IElementType, val text: String, val start: Int, val end: Int) {
    val trivia: Boolean get() = type == TokenType.WHITE_SPACE || type == ElclTypes.COMMENT
}
internal data class ElclSyntaxLine(val source: ElclLine, val tokens: List<ElclLexeme>) {
    val significant: List<ElclLexeme> get() = tokens.filterNot { it.trivia }
    val comment: String get() = tokens.firstOrNull { it.type == ElclTypes.COMMENT }?.text.orEmpty()
}
internal data class ElclStatement(
    val kind: String,
    val lines: List<ElclSyntaxLine>,
    val names: List<ElclLexeme>,
    val path: List<String>?,
    val list: Boolean = false,
    val relative: Boolean = false,
    val meta: Boolean = false,
    val valueType: String = "",
    val damaged: Boolean = false,
    val multilineHeader: Int? = null,
    val payloadIndent: String = "",
) {
    val range: TextRange get() = TextRange(lines.first().source.start, lines.last().source.end)
    val values: List<ElclLexeme> get() = lines.flatMap { it.tokens }.filter { it.type in SCALAR_TYPES }
    val finalNewline: String get() = lines.last().source.newline
}

internal val SCALAR_TYPES = setOf(ElclTypes.INTEGER, ElclTypes.FLOAT, ElclTypes.BOOLEAN, ElclTypes.TEXT,
    ElclTypes.CODE, ElclTypes.REGEX, ElclTypes.BYTES, ElclTypes.DATE, ElclTypes.TIME, ElclTypes.DATE_TIME, ElclTypes.TIME_DELTA)
internal val MULTILINE_TYPES = setOf(ElclTypes.MULTILINE_TEXT_OPEN, ElclTypes.MULTILINE_CODE_OPEN,
    ElclTypes.MULTILINE_REGEX_OPEN, ElclTypes.MULTILINE_BYTES_OPEN)

internal fun lexLine(line: ElclLine, value: Boolean = false): ElclSyntaxLine {
    val lexer = ElclLexerAdapter()
    lexer.start(line.text, 0, line.text.length, if (value) _ElclLexer.VALUE else _ElclLexer.YYINITIAL)
    val tokens = mutableListOf<ElclLexeme>()
    while (lexer.tokenType != null) {
        tokens += ElclLexeme(lexer.tokenType!!, line.text.substring(lexer.tokenStart, lexer.tokenEnd),
            line.start + lexer.tokenStart, line.start + lexer.tokenEnd)
        lexer.advance()
    }
    return ElclSyntaxLine(line, tokens)
}

/** Lossless statement boundaries, with independent recovery at unindented declarations. */
internal class ElclSyntaxSnapshot(val text: String) {
    val statements: List<ElclStatement>
    val newline = elclLines(text).firstOrNull { it.newline.isNotEmpty() }?.newline ?: "\n"
    init {
        val raw = elclLines(text).mapIndexed { index, line ->
            if (index == 0 && line.text.startsWith('\uFEFF')) line.copy(start = 1, text = line.text.drop(1)) else line
        }
        val result = mutableListOf<ElclStatement>()
        var absolute: List<String>? = null
        var section: List<String>? = null
        var i = 0
        while (i < raw.size) {
            val first = lexLine(raw[i])
            val sig = first.significant
            if (sig.isEmpty()) { i++; continue }
            val sectionOpen = sig.indexOfFirst { it.type == ElclTypes.SECTION_MAP_OPEN || it.type == ElclTypes.SECTION_LIST_OPEN }
            if (sectionOpen >= 0 || raw[i].text.trimStart('-').startsWith('[')) {
                val names = sig.filter { it.type == ElclTypes.NAME || it.type == ElclTypes.TEXT_NAME }
                val relative = sig.getOrNull(sectionOpen + 1)?.type == ElclTypes.DOT
                val list = sig.getOrNull(sectionOpen)?.type == ElclTypes.SECTION_LIST_OPEN
                val close = sig.indexOfFirst { it.type == ElclTypes.SECTION_MAP_CLOSE || it.type == ElclTypes.SECTION_LIST_CLOSE }
                var damaged = first.source.indent.isNotEmpty() || names.isEmpty() || names.size > 10 || close < sectionOpen ||
                    sig.take(sectionOpen.coerceAtLeast(0)).any { it.type != ElclTypes.SECTION_DECORATION } ||
                    (close >= 0 && sig.drop(close + 1).any { it.type != ElclTypes.SECTION_DECORATION }) ||
                    (!list && sig.any { it.type == ElclTypes.SECTION_LIST_CLOSE })
                if (close > sectionOpen) {
                    val pathTokens = sig.subList(sectionOpen + 1, close).drop(if (relative) 1 else 0)
                    damaged = damaged || pathTokens.isEmpty() || pathTokens.size % 2 == 0 || pathTokens.withIndex().any { (n, t) ->
                        if (n % 2 == 0) t.type !in setOf(ElclTypes.NAME, ElclTypes.TEXT_NAME) else t.type != ElclTypes.DOT
                    }
                }
                val keys = runCatching { names.map { checkedName(it) } }.getOrElse { damaged = true; emptyList() }
                if (relative && absolute == null) damaged = true
                val path = if (damaged) null else if (relative) absolute!! + keys else keys
                section = path
                if (!relative && !damaged) absolute = path
                result += ElclStatement("section", listOf(first), names, path, list, relative, damaged = damaged || invalidLine(first))
                i++
                continue
            }
            val name = sig.first()
            val assignment = sig.getOrNull(1)?.type == ElclTypes.ASSIGN && name.type in setOf(ElclTypes.NAME, ElclTypes.TEXT_NAME, ElclTypes.META_NAME)
            if (!assignment) {
                result += ElclStatement("assignment", listOf(first), emptyList(), null, damaged = true)
                i++
                continue
            }
            val lines = mutableListOf(first)
            val meta = name.type == ElclTypes.META_NAME
            var damaged = first.source.indent.isNotEmpty()
            val key = runCatching { checkedName(name) }.getOrElse { damaged = true; name.text }
            val path = if (meta) listOf(key) else section?.plus(key)
            if (key == "@include") { section = null; absolute = null }
            var valueLine = 0
            var valueTokens = sig.drop(2)
            i++
            if (valueTokens.isEmpty()) {
                if (i < raw.size && raw[i].indent.isNotEmpty() && raw[i].text.isNotBlank() && !raw[i].text.trimStart().startsWith('#')) {
                    lines += lexLine(raw[i++], true)
                    valueLine = 1
                    valueTokens = lines.last().significant
                } else damaged = true
            }
            val open = valueTokens.firstOrNull()?.takeIf { it.type in MULTILINE_TYPES }
            var payloadIndent = ""
            if (open != null) {
                if (valueLine == 1) payloadIndent = lines[1].source.indent
                var established = valueLine == 1
                val closeText = if (open.type == ElclTypes.MULTILINE_BYTES_OPEN) ">>>" else open.text
                var closed = false
                while (i < raw.size) {
                    val line = raw[i]
                    if (line.text.isNotBlank() && line.indent.isEmpty()) { damaged = true; break }
                    if (!established && line.text.isNotBlank()) { payloadIndent = line.indent; established = true }
                    val content = line.text.removePrefix(payloadIndent)
                    val suffix = content.removePrefix(closeText).trimStart()
                    val closing = content.startsWith(closeText) && (suffix.isEmpty() || suffix.startsWith('#'))
                    if (line.text.isNotBlank() && !line.text.startsWith(payloadIndent)) damaged = true
                    lines += ElclSyntaxLine(line, emptyList())
                    i++
                    if (closing) { closed = true; break }
                }
                damaged = damaged || !closed || payloadIndent.isEmpty()
                val allowed = when (open.type) {
                    ElclTypes.MULTILINE_CODE_OPEN -> setOf(open.type, ElclTypes.MULTILINE_CODE_LANGUAGE)
                    ElclTypes.MULTILINE_BYTES_OPEN -> setOf(open.type, ElclTypes.MULTILINE_BYTES_FORMAT)
                    else -> setOf(open.type)
                }
                damaged = damaged || valueTokens.any { it.type !in allowed }
            } else if (valueTokens.firstOrNull()?.type == ElclTypes.LIST_MARKER) {
                if (valueLine == 0) damaged = true
                val indent = lines.last().source.indent
                while (i < raw.size && raw[i].indent.isNotEmpty() && raw[i].text.trimStart().startsWith('*')) {
                    val line = lexLine(raw[i++], true)
                    damaged = damaged || line.source.indent != indent
                    lines += line
                }
                damaged = damaged || lines.drop(valueLine).any { !validScalars(it.significant.drop(1)) }
            } else damaged = damaged || !validScalars(valueTokens)
            damaged = damaged || lines.any(::invalidLine)
            val validation = AnalysisState(text)
            val validator = ElclValueValidator(validation)
            for (token in lines.flatMap { it.tokens }.filter { it.type == ElclTypes.TEXT || it.type == ElclTypes.BYTES }) {
                validator.validate(token.text, TextRange(token.start, token.end))
            }
            if (open != null) {
                val header = lines[valueLine].source
                val headerText = header.text.substring(open.start - header.start)
                val start = validator.validate(headerText, TextRange(open.start, header.start + header.text.length))
                if (start != null) for (line in lines.drop(valueLine + 1).dropLast(1)) {
                    validator.validateMultilineContent(Multiline(payloadIndent, start), line.source.text,
                        TextRange(line.source.start, line.source.start + line.source.text.length))
                }
            }
            damaged = damaged || validation.diagnostics.isNotEmpty()
            if (open?.type == ElclTypes.MULTILINE_TEXT_OPEN) {
                damaged = damaged || lines.drop(valueLine + 1).dropLast(1).any {
                    runCatching { decodeElclText(it.source.text.removePrefix(payloadIndent).trimEnd(' ', '\t')) }.isFailure
                }
            }
            val tokens = lines.flatMap { it.tokens }
            val valueType = if (tokens.any { it.type == ElclTypes.COMMA || it.type == ElclTypes.LIST_MARKER }) "value_list"
                else valueType(open?.type ?: valueTokens.firstOrNull()?.type)
            result += ElclStatement("assignment", lines, listOf(name), path, meta = meta, valueType = valueType,
                damaged = damaged, multilineHeader = if (open != null) valueLine else null, payloadIndent = payloadIndent)
        }
        statements = result
    }
}

private fun checkedName(token: ElclLexeme): String {
    if (token.type == ElclTypes.TEXT_NAME) {
        val decoded = decodeElclText(token.text.removeSurrounding("\""))
        require(decoded.isNotEmpty())
    } else {
        val name = token.text.removePrefix("@")
        require(name.length <= 100 && name.matches(Regex("[\\p{L}][\\p{L}\\p{N}]*([ _][\\p{L}\\p{N}]+)*")))
    }
    return nameKey(token.text)
}
private fun invalidLine(line: ElclSyntaxLine): Boolean = line.source.text.toByteArray(Charsets.UTF_8).size + line.source.newline.length > 4000 ||
    line.tokens.any { it.type == ElclTypes.BAD_CHARACTER || it.type == TokenType.BAD_CHARACTER }
private fun validScalars(tokens: List<ElclLexeme>): Boolean = tokens.isNotEmpty() && tokens.size % 2 == 1 && tokens.withIndex().all { (i, t) ->
    if (i % 2 == 1) t.type == ElclTypes.COMMA else t.type in SCALAR_TYPES &&
        (t.type != ElclTypes.TEXT || runCatching { decodeElclText(t.text.removeSurrounding("\"")) }.isSuccess)
}
private fun valueType(type: IElementType?): String = when (type) {
    ElclTypes.INTEGER -> "integer"
    ElclTypes.FLOAT -> "float"
    ElclTypes.BOOLEAN -> "boolean"
    ElclTypes.TEXT, ElclTypes.CODE, ElclTypes.MULTILINE_TEXT_OPEN, ElclTypes.MULTILINE_CODE_OPEN -> "text"
    ElclTypes.REGEX, ElclTypes.MULTILINE_REGEX_OPEN -> "regex"
    ElclTypes.BYTES, ElclTypes.MULTILINE_BYTES_OPEN -> "bytes"
    ElclTypes.DATE -> "date"
    ElclTypes.TIME -> "time"
    ElclTypes.DATE_TIME -> "date_time"
    ElclTypes.TIME_DELTA -> "time_delta"
    else -> ""
}

package dev.erbsland.elcl.format

import com.intellij.openapi.util.TextRange
import com.intellij.psi.TokenType
import dev.erbsland.elcl.psi.ElclTypes
import dev.erbsland.elcl.syntax.decodeElclText
import dev.erbsland.elcl.syntax.quoteElclText

internal data class ElclFormatEdit(val range: TextRange, val replacement: String)
internal data class ElclFormatResult(val text: String, val edits: List<ElclFormatEdit>, val signedSourceChanged: Boolean)

/** Source-preserving implementation of Core's syntax formatter. No values are evaluated. */
internal class ElclFormatter {
    fun format(text: String, format: ElclFormat = ElclFormat(), ranges: List<TextRange>? = null, whitespaceOnly: Boolean = false): ElclFormatResult {
        format.base.validate()
        format.rules.forEach { rule -> rule.patch.forEach { (key, value) -> key.validate(value) } }
        val source = ElclSyntaxSnapshot(text)
        val edits = source.statements.mapNotNull { statement ->
            if (statement.damaged || ranges != null && ranges.none { it.intersectsStrict(statement.range) }) return@mapNotNull null
            val original = text.substring(statement.range.startOffset, statement.range.endOffset)
            val writer = Writer(format.resolve(statement), source.newline, whitespaceOnly)
            val replacement = writer.render(statement)
            // Whitespace-only IDE requests must never change a token or literal payload.
            if (replacement == original || whitespaceOnly && original.filterNot(Char::isWhitespace) != replacement.filterNot(Char::isWhitespace)) null
            else ElclFormatEdit(statement.range, replacement)
        }
        val output = StringBuilder(text)
        edits.asReversed().forEach { output.replace(it.range.startOffset, it.range.endOffset, it.replacement) }
        return ElclFormatResult(output.toString(), edits,
            edits.isNotEmpty() && source.statements.any { it.meta && it.names.firstOrNull()?.text?.equals("@signature", true) == true })
    }
}

private class Writer(private val style: ElclFormatStyle, private val documentNewline: String, private val whitespaceOnly: Boolean) {
    private val indent get() = (if (style[FormatProperty.INDENT_STYLE] == "tabs") "\t" else " ").repeat(style.number(FormatProperty.INDENT_COUNT))
    private val width get() = style.number(FormatProperty.WIDTH)

    fun render(statement: ElclStatement): String {
        if (statement.kind == "section") return section(statement)
        if (!whitespaceOnly) reflowVector(statement)?.let { return it }
        reflowValue(statement)?.let { return it }
        val isMatrix = statement.lines.any { line -> line.tokens.any { it.type == ElclTypes.LIST_MARKER } && line.tokens.any { it.type == ElclTypes.COMMA } }
        val preserveSpacing = isMatrix && style[FormatProperty.MATRIX] == "keep"
        var columns = emptyList<Int>()
        if (isMatrix && style[FormatProperty.MATRIX] == "align") {
            val rows = statement.lines.filter { line -> line.tokens.any { it.type == ElclTypes.LIST_MARKER } }
                .map { line -> line.tokens.filter { it.type in SCALAR_TYPES }.map { tokenText(it).cpLength() } }
            columns = (0 until (rows.maxOfOrNull { it.size } ?: 0)).map { i -> rows.maxOf { it.getOrElse(i) { 0 } } }
            if (statement.lines.withIndex().any { (i, line) -> renderLine(line.tokens, i == 0, columns).cpLength() > width }) columns = emptyList()
        }
        return statement.lines.mapIndexed { index, line ->
            if (statement.multilineHeader != null && index > statement.multilineHeader) {
                val raw = line.source.text
                (if (raw.startsWith(statement.payloadIndent) && raw.isNotEmpty()) indent + raw.removePrefix(statement.payloadIndent) else raw) + line.source.newline
            } else renderLine(line.tokens, index == 0, columns, preserveSpacing) + line.source.newline
        }.joinToString("")
    }

    private fun section(statement: ElclStatement): String {
        if (whitespaceOnly) return statement.lines.joinToString("") { it.source.text + it.source.newline }
        val header = buildString {
            if (statement.list) append('*')
            append('[')
            if (style.enabled(FormatProperty.SPACE_AFTER_OPEN)) append(' ')
            if (statement.relative) { append('.'); if (style.enabled(FormatProperty.SPACE_AROUND_DOT)) append(' ') }
            append(statement.names.joinToString(if (style.enabled(FormatProperty.SPACE_AROUND_DOT)) " . " else ".") {
                if (it.type == ElclTypes.TEXT_NAME) tokenText(it)
                else formatName(it.text, style[FormatProperty.SECTION_CASE], style[FormatProperty.SECTION_SEPARATOR])
            })
            if (style.enabled(FormatProperty.SPACE_BEFORE_CLOSE)) append(' ')
            append(']')
            if (statement.list && style.enabled(FormatProperty.SECTION_STAR)) append('*')
        }
        val comment = statement.lines.first().comment
        val available = (width - if (comment.isEmpty()) 0 else comment.cpLength() + 1).coerceAtLeast(0)
        val prefix = style.number(FormatProperty.SECTION_INDENT)
        val suffix = style.number(FormatProperty.SECTION_SUFFIX)
        val star = if (statement.list && style.enabled(FormatProperty.SECTION_STAR)) 1 else 0
        val before: Int
        val after: Int
        if (prefix == -1 && suffix == -1) {
            val remaining = (available - header.cpLength()).coerceAtLeast(0)
            before = remaining / 2; after = remaining - before
        } else if (prefix == -1) {
            after = (suffix - star).coerceAtLeast(0)
            before = (available - header.cpLength() - after).coerceAtLeast(0)
        } else {
            before = (prefix - if (statement.list) 1 else 0).coerceAtLeast(0)
            after = if (suffix == -1) (available - before - header.cpLength()).coerceAtLeast(0) else (suffix - star).coerceAtLeast(0)
        }
        return "-".repeat(before) + header + "-".repeat(after) + (if (comment.isNotEmpty()) " $comment" else "") + statement.finalNewline
    }

    private fun tokenText(token: ElclLexeme): String {
        if (whitespaceOnly) return token.text
        return when (token.type) {
            ElclTypes.NAME, ElclTypes.META_NAME -> formatName(token.text, style[FormatProperty.VALUE_CASE], style[FormatProperty.VALUE_SEPARATOR])
            ElclTypes.TEXT, ElclTypes.TEXT_NAME -> {
                val textName = token.type == ElclTypes.TEXT_NAME
                val case = style[if (textName) FormatProperty.TEXT_NAME_CASE else FormatProperty.TEXT_IDENTIFIER_CASE]
                if (case == "keep") token.text
                else {
                    val text = decodeElclText(token.text.removeSurrounding("\""))
                    if (!text.matches(Regex("[A-Za-z][A-Za-z0-9]*([ _][A-Za-z0-9]+)*")) || text.length > 100) token.text
                    else quoteElclText(formatName(text, case, style[if (textName) FormatProperty.TEXT_NAME_SEPARATOR else FormatProperty.TEXT_IDENTIFIER_SEPARATOR]))
                }
            }
            else -> token.text
        }
    }

    private fun renderLine(tokens: List<ElclLexeme>, first: Boolean, columns: List<Int> = emptyList(), preserveSpacing: Boolean = false): String {
        val out = StringBuilder()
        var valueArea = false
        var column = 0
        var previousWidth = 0
        if (!first && tokens.isNotEmpty()) out.append(indent)
        for (token in tokens) {
            when (token.type) {
                TokenType.WHITE_SPACE -> if (preserveSpacing && valueArea) out.append(token.text)
                ElclTypes.ASSIGN -> {
                    pad(out, FormatProperty.SEPARATOR_MODE, FormatProperty.SEPARATOR_POSITION, 1)
                    out.append(if (whitespaceOnly) token.text else style[FormatProperty.SEPARATOR])
                }
                ElclTypes.COMMA -> {
                    out.append(',')
                    if (!preserveSpacing) out.append(" ".repeat(1 + (columns.getOrNull(column)?.minus(previousWidth) ?: 0).coerceAtLeast(0)))
                    column++
                }
                ElclTypes.LIST_MARKER -> out.append('*').append(" ".repeat(style.number(FormatProperty.BULLET_SPACES)))
                ElclTypes.COMMENT -> {
                    if (out.isNotEmpty() && !(preserveSpacing && out.endsWith(" "))) out.append(' ')
                    out.append(token.text)
                }
                else -> {
                    if (first && (out.endsWith(":") || out.endsWith("="))) pad(out, FormatProperty.VALUE_MODE, FormatProperty.VALUE_POSITION)
                    val value = tokenText(token)
                    out.append(value)
                    if (token.type in SCALAR_TYPES) { valueArea = true; previousWidth = value.cpLength() }
                }
            }
        }
        return out.toString()
    }

    private fun pad(out: StringBuilder, mode: FormatProperty, position: FormatProperty, minimum: Int = 0) {
        val count = style.number(position)
        val spaces = if (style[mode] == "spaces") count else (count - out.toString().cpLength()).coerceAtLeast(minimum)
        out.append(" ".repeat(spaces))
    }

    private fun nextLine(sameWidth: Int, nextWidth: Int, wasNext: Boolean): Boolean = when (style[FormatProperty.WRAPPING]) {
        "keep" -> wasNext
        "same_line" -> false
        "next_line" -> true
        "when_too_long" -> sameWidth > width
        else -> sameWidth > width && nextWidth <= width
    }

    private fun placeValue(prefix: String, value: String, wasNext: Boolean, statement: ElclStatement): String {
        val sameLine = StringBuilder(prefix)
        pad(sameLine, FormatProperty.VALUE_MODE, FormatProperty.VALUE_POSITION)
        return if (nextLine(sameLine.toString().cpLength() + value.cpLength(), indent.cpLength() + value.cpLength(), wasNext)) {
            prefix + newline(statement) + indent + value + statement.finalNewline
        } else sameLine.toString() + value + statement.finalNewline
    }

    private fun reflowValue(statement: ElclStatement): String? {
        if (statement.multilineHeader != null || statement.lines.size > 2 || statement.lines.any { line -> line.tokens.any { it.type == ElclTypes.LIST_MARKER } }) return null
        val first = statement.lines.first().tokens
        val separator = first.indexOfFirst { it.type == ElclTypes.ASSIGN }
        if (separator < 0) return null
        val after = first.drop(separator + 1)
        if (after.firstOrNull { it.type != TokenType.WHITE_SPACE }?.type == ElclTypes.COMMENT) return null
        val next = statement.lines.size > 1
        if (next && after.any { !it.trivia }) return null
        val values = if (next) statement.lines.last().tokens else after
        val trimmed = values.dropWhile { it.type == TokenType.WHITE_SPACE }
        if (trimmed.firstOrNull()?.type !in SCALAR_TYPES) return null
        val prefix = renderLine(first.take(separator + 1), true)
        // Values rendered in isolation must not get continuation indentation.
        val value = renderLine(trimmed, true)
        return placeValue(prefix, value, next, statement)
    }

    private fun reflowVector(statement: ElclStatement): String? {
        if (style[FormatProperty.VECTOR] == "keep" || statement.valueType != "value_list" || statement.multilineHeader != null) return null
        if (statement.lines.any { it.comment.isNotEmpty() || it.tokens.any { t -> t.type == ElclTypes.LIST_MARKER } && it.tokens.any { t -> t.type == ElclTypes.COMMA } }) return null
        val values = statement.values.map(::tokenText)
        if (values.isEmpty()) return null
        val tokens = statement.lines.first().tokens
        val separator = tokens.indexOfFirst { it.type == ElclTypes.ASSIGN }
        val prefix = renderLine(tokens.take(separator + 1), true)
        if (style[FormatProperty.VECTOR] == "compact" && values.size >= 2) {
            val value = values.joinToString(", ")
            val same = StringBuilder(prefix)
            pad(same, FormatProperty.VALUE_MODE, FormatProperty.VALUE_POSITION)
            val sameWidth = same.toString().cpLength() + value.cpLength()
            val continuationWidth = indent.cpLength() + value.cpLength()
            if (sameWidth <= width || continuationWidth <= width && nextLine(sameWidth, continuationWidth, statement.lines.size > 1)) {
                return placeValue(prefix, value, statement.lines.size > 1, statement)
            }
        }
        val bullet = newline(statement) + indent + "*" + " ".repeat(style.number(FormatProperty.BULLET_SPACES))
        return prefix + bullet + values.joinToString(bullet) + statement.finalNewline
    }

    private fun newline(statement: ElclStatement): String = statement.lines.firstOrNull { it.source.newline.isNotEmpty() }?.source?.newline ?: documentNewline
}

internal fun String.cpLength(): Int = codePointCount(0, length)
internal fun formatName(text: String, case: String, separator: String): String = buildString {
    var first = true
    text.forEach { c ->
        if (c == ' ' || c == '_') { append(if (separator == "space") ' ' else '_'); first = true }
        else {
            append(when {
                case == "upper" || case == "title" && first -> if (c in 'a'..'z') c.uppercaseChar() else c
                case != "keep" -> if (c in 'A'..'Z') c.lowercaseChar() else c
                else -> c
            })
            if (c != '@') first = false
        }
    }
}

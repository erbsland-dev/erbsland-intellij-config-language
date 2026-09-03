package dev.erbsland.elcl.validation

import com.intellij.openapi.util.TextRange

/** Validates atomic text and byte-data details that the recovery lexer keeps permissive. */
internal class ElclValueValidator(private val state: AnalysisState) {
    /** Validates one assignment value and returns its multiline family when it opens one. */
    fun validate(value: String, range: TextRange): MultilineStart? {
        validateQuotedText(value, range)
        validateSingleLineBytes(value, range)
        return multilineStart(value)?.also { validateMultilineHeader(value, range, it) }
    }

    /** Validates an opener continued onto its own indented line. */
    fun validateContinuedOpening(value: String, range: TextRange): MultilineStart? =
        multilineStart(value)?.also { validateMultilineHeader(value, range, it) }

    /** Validates content after the fixed indentation of an active multiline value. */
    fun validateMultilineContent(active: Multiline, rawLine: String, lineRange: TextRange) {
        val indentation = active.indent ?: return
        if (!rawLine.startsWith(indentation)) return
        val content = rawLine.substring(indentation.length)
        val contentOffset = lineRange.startOffset + indentation.length
        when (active.start.kind) {
            MultilineKind.TEXT -> validateEscapes(content.trimEnd(' ', '\t'), contentOffset, assumeText = true)
            MultilineKind.BYTES -> {
                val bytes = content.substringBefore('#').trimEnd(' ', '\t')
                validateHexBody(bytes, contentOffset)
            }
            MultilineKind.CODE, MultilineKind.REGEX -> Unit
        }
    }

    private fun multilineStart(value: String): MultilineStart? {
        val trimmed = value.trimStart()
        return when {
            trimmed.startsWith("\"\"\"") -> MultilineStart(MultilineKind.TEXT, "\"\"\"")
            trimmed.startsWith("```") -> MultilineStart(MultilineKind.CODE, "```")
            trimmed.startsWith("///") -> MultilineStart(MultilineKind.REGEX, "///")
            trimmed.startsWith("<<<") -> MultilineStart(MultilineKind.BYTES, ">>>")
            else -> null
        }
    }

    private fun validateMultilineHeader(value: String, range: TextRange, start: MultilineStart) {
        val delimiterOffset = value.indexOf(
            when (start.kind) {
                MultilineKind.TEXT -> "\"\"\""
                MultilineKind.CODE -> "```"
                MultilineKind.REGEX -> "///"
                MultilineKind.BYTES -> "<<<"
            },
        )
        val suffixStart = delimiterOffset + 3
        val suffix = value.substring(suffixStart).substringBefore('#')
        when (start.kind) {
            MultilineKind.CODE -> validateIdentifier(
                suffix.trim(),
                range.startOffset + suffixStart + suffix.indexOfFirst { !it.isElclSpacing() }.coerceAtLeast(0),
                "Code language",
                supported = null,
            )
            MultilineKind.BYTES -> {
                val identifier = suffix.trim()
                val identifierStart = range.startOffset + suffixStart + suffix.indexOfFirst { !it.isElclSpacing() }.coerceAtLeast(0)
                if (identifier.isNotEmpty() && suffix.firstOrNull()?.isElclSpacing() == true) {
                    state.error(TextRange(identifierStart, identifierStart + identifier.length), "Byte-data format must immediately follow '<<<' without spacing")
                }
                validateIdentifier(identifier, identifierStart, "Byte-data format", supported = "hex")
            }
            MultilineKind.TEXT, MultilineKind.REGEX -> Unit
        }
    }

    private fun validateIdentifier(identifier: String, offset: Int, label: String, supported: String?) {
        if (identifier.isEmpty()) return
        val range = TextRange(offset, offset + identifier.length)
        if (!identifier.matches(FORMAT_IDENTIFIER)) {
            state.error(range, "$label identifiers start with a letter and contain at most 16 letters, digits, underscores, or hyphens")
        } else if (supported != null && !identifier.equals(supported, ignoreCase = true)) {
            state.error(range, "Unsupported byte-data format '$identifier'; only 'hex' is supported")
        }
    }

    private fun validateSingleLineBytes(value: String, range: TextRange) {
        var quote: Char? = null
        var escaped = false
        var index = 0
        while (index < value.length) {
            val character = value[index]
            when {
                escaped -> escaped = false
                quote != null && character == '\\' -> escaped = true
                quote == null && character in charArrayOf('"', '`', '/') -> quote = character
                quote == character -> quote = null
                quote == null && character == '<' && !value.startsWith("<<<", index) -> {
                    val end = value.indexOf('>', index + 1)
                    if (end < 0) return
                    validateSingleLineBytesAt(value, range, index, end)
                    index = end
                }
            }
            index++
        }
    }

    private fun validateSingleLineBytesAt(value: String, range: TextRange, start: Int, end: Int) {
        var bodyStart = start + 1
        val inside = value.substring(bodyStart, end)
        val colon = inside.indexOf(':')
        if (colon >= 0) {
            val identifier = inside.substring(0, colon)
            validateIdentifier(identifier, range.startOffset + bodyStart, "Byte-data format", supported = "hex")
            if (identifier.any { it.isElclSpacing() }) {
                state.error(
                    TextRange(range.startOffset + bodyStart, range.startOffset + bodyStart + identifier.length),
                    "No spacing is allowed around a single-line byte-data format identifier",
                )
            }
            bodyStart += colon + 1
        }
        validateHexBody(value.substring(bodyStart, end), range.startOffset + bodyStart)
    }

    private fun validateHexBody(body: String, absoluteOffset: Int) {
        val illegalCharacters = body.withIndex().filter { (_, character) ->
            !character.isElclSpacing() && !character.isHexDigit()
        }
        illegalCharacters.forEach { (index, character) ->
                state.error(
                    TextRange(absoluteOffset + index, absoluteOffset + index + 1),
                    "Illegal character '$character' in hexadecimal byte data",
                )
        }
        if (illegalCharacters.isNotEmpty()) return
        val groups = HEX_GROUP.findAll(body).toList()
        var groupIndex = 0
        while (groupIndex < groups.size) {
            val group = groups[groupIndex]
            if (group.value.length % 2 == 0) {
                groupIndex++
                continue
            }
            val next = groups.getOrNull(groupIndex + 1)
            val splitIsSpacing = next != null && body.substring(group.range.last + 1, next.range.first).all { it.isElclSpacing() }
            if (next != null && next.value.length % 2 == 1 && splitIsSpacing) {
                val splitStart = group.range.last + 1
                val splitEnd = next.range.first
                state.error(
                    TextRange(absoluteOffset + splitStart, absoluteOffset + splitEnd),
                    "Spacing splits a hexadecimal byte; bytes must contain exactly two digits",
                )
                groupIndex += 2
            } else {
                state.error(
                    TextRange(absoluteOffset + group.range.first, absoluteOffset + group.range.last + 1),
                    "Hexadecimal byte groups must contain an even number of digits",
                )
                groupIndex++
            }
        }
    }

    private fun validateQuotedText(value: String, range: TextRange) {
        var inText = false
        var index = 0
        while (index < value.length) {
            val character = value[index]
            if (character == '"') {
                inText = !inText
                index++
            } else if (inText && character == '\\') {
                index += validateEscape(value, index, range.startOffset)
            } else {
                index++
            }
        }
        if (inText && !value.trimStart().startsWith("\"\"\"")) {
            state.error(range, "Unterminated text literal")
        }
    }

    private fun validateEscapes(text: String, absoluteOffset: Int, assumeText: Boolean) {
        if (!assumeText) return
        var index = 0
        while (index < text.length) {
            index += if (text[index] == '\\') validateEscape(text, index, absoluteOffset) else 1
        }
    }

    private fun validateEscape(text: String, start: Int, absoluteOffset: Int): Int {
        if (start + 1 >= text.length) {
            state.error(TextRange(absoluteOffset + start, absoluteOffset + start + 1), "Incomplete text escape sequence")
            return 1
        }
        val marker = text[start + 1]
        if (marker in SIMPLE_ESCAPES) return 2
        if (marker != 'u' && marker != 'U') {
            state.error(TextRange(absoluteOffset + start, absoluteOffset + start + 2), "Unknown text escape sequence '\\$marker'")
            return 2
        }
        val unicode = parseUnicodeEscape(text, start)
        val length = unicode.length.coerceAtLeast(2)
        val escapeRange = TextRange(absoluteOffset + start, (absoluteOffset + start + length).coerceAtMost(absoluteOffset + text.length))
        if (unicode.digits == null) {
            state.error(escapeRange, "Malformed Unicode escape; expected four hex digits or 1-8 digits in braces")
        } else {
            val codePoint = unicode.digits.toLong(16)
            when {
                codePoint == 0L -> state.error(escapeRange, "Unicode null code point U+0000 is not allowed in ELCL text")
                codePoint > 0x10ffffL || codePoint in 0xd800L..0xdfffL ->
                    state.error(escapeRange, "Unicode escape does not represent a valid Unicode scalar value")
            }
        }
        return length
    }

    private fun parseUnicodeEscape(text: String, start: Int): UnicodeEscape {
        val payload = start + 2
        if (payload >= text.length) return UnicodeEscape(null, 2)
        if (text[payload] == '{') {
            val close = text.indexOf('}', payload + 1)
            val end = if (close >= 0) close + 1 else text.length
            val digits = text.substring(payload + 1, if (close >= 0) close else text.length)
            return UnicodeEscape(digits.takeIf { it.length in 1..8 && it.all { character -> character.isHexDigit() } }, end - start)
        }
        val end = (payload + 4).coerceAtMost(text.length)
        val digits = text.substring(payload, end)
        return UnicodeEscape(digits.takeIf { it.length == 4 && it.all { character -> character.isHexDigit() } }, end - start)
    }

    private data class UnicodeEscape(val digits: String?, val length: Int)

    private companion object {
        val FORMAT_IDENTIFIER = Regex("[A-Za-z][A-Za-z0-9_-]{0,15}")
        val HEX_GROUP = Regex("[0-9A-Fa-f]+")
        val SIMPLE_ESCAPES = setOf('\\', '"', '$', 'n', 'N', 'r', 'R', 't', 'T')
        fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
        fun Char.isElclSpacing(): Boolean = this == ' ' || this == '\t'
    }
}

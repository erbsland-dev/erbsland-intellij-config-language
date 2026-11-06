package dev.erbsland.elcl.syntax

/** Decodes ELCL text without interpreting placeholders or normalizing its spelling. */
internal fun decodeElclText(text: String): String {
    val result = StringBuilder()
    var i = 0
    while (i < text.length) {
        val ch = text[i++]
        if (ch != '\\') {
            require(ch.code >= 32 || ch == '\t') { "Control character in text" }
            result.append(ch)
            continue
        }
        require(i < text.length) { "Incomplete text escape" }
        when (val escaped = text[i++].lowercaseChar()) {
            '\\', '"', '$' -> result.append(escaped)
            'n' -> result.append('\n')
            'r' -> result.append('\r')
            't' -> result.append('\t')
            'u' -> {
                val braced = i < text.length && text[i] == '{'
                if (braced) i++
                val end = if (braced) text.indexOf('}', i) else i + 4
                require(end in i + 1..text.length && end - i <= 8) { "Malformed Unicode escape" }
                val digits = text.substring(i, end)
                require(digits.all { it in "0123456789abcdefABCDEF" }) { "Malformed Unicode escape" }
                val cp = digits.toLong(16)
                require(cp in 1..0x10ffff && cp !in 0xd800..0xdfff) { "Invalid Unicode code point" }
                result.appendCodePoint(cp.toInt())
                i = end + if (braced) 1 else 0
            }
            else -> error("Unknown text escape: $escaped")
        }
    }
    return result.toString()
}

/** Canonical quoted spelling keeps text names distinct from regular identifiers. */
internal fun quoteElclText(text: String): String = buildString {
    append('"')
    text.forEach {
        append(when (it) {
            '\\' -> "\\\\"
            '"' -> "\\\""
            '\n' -> "\\n"
            '\r' -> "\\r"
            '\t' -> "\\t"
            else -> it.toString()
        })
    }
    append('"')
}

internal fun nameKey(raw: String): String {
    val name = raw.trim()
    return if (name.startsWith('"') && name.endsWith('"') && name.length >= 2) {
        quoteElclText(decodeElclText(name.substring(1, name.lastIndex)))
    } else name.lowercase(java.util.Locale.ROOT).replace(' ', '_')
}

/** Splits paths only at unquoted dots; quoted components retain their delimiters. */
internal fun splitElclPath(path: String): List<String> {
    val parts = mutableListOf<String>()
    var quoted = false
    var escaped = false
    var start = 0
    path.forEachIndexed { index, ch ->
        when {
            escaped -> escaped = false
            ch == '\\' && quoted -> escaped = true
            ch == '"' -> quoted = !quoted
            ch == '.' && !quoted -> { parts += path.substring(start, index).trim(); start = index + 1 }
        }
    }
    require(!quoted) { "Unterminated quoted name" }
    parts += path.substring(start).trim()
    return parts
}

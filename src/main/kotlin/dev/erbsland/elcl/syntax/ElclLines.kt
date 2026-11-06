package dev.erbsland.elcl.syntax

internal data class ElclLine(val start: Int, val text: String, val newline: String) {
    val end: Int get() = start + text.length + newline.length
    val indent: String get() = text.takeWhile { it == ' ' || it == '\t' }
}

/** UTF-16 offsets for IDE ranges, retaining each physical newline exactly. */
internal fun elclLines(text: String): List<ElclLine> {
    val result = mutableListOf<ElclLine>()
    var start = 0
    while (start < text.length) {
        var end = start
        while (end < text.length && text[end] != '\r' && text[end] != '\n') end++
        var next = end
        if (next < text.length) {
            if (text[next++] == '\r' && next < text.length && text[next] == '\n') next++
        }
        result += ElclLine(start, text.substring(start, end), text.substring(end, next))
        start = next
    }
    return result
}

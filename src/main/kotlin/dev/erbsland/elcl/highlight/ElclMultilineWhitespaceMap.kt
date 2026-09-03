package dev.erbsland.elcl.highlight

import com.intellij.openapi.util.TextRange
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import dev.erbsland.elcl.lexer.ElclLexerAdapter
import dev.erbsland.elcl.psi.ElclTypes

/**
 * Locates whitespace discarded by ELCL multiline-value decoding.
 *
 * The map is rebuilt once whenever the highlighting lexer is restarted. It
 * uses parser tokens so delimiter-like text inside a value cannot accidentally
 * start or end a block.
 */
internal class ElclMultilineWhitespaceMap {
    private val ignored = mutableListOf<TextRange>()

    /** Recomputes ignored indentation and trailing-whitespace ranges. */
    fun prepare(buffer: CharSequence) {
        ignored.clear()
        val lexer = ElclLexerAdapter()
        lexer.start(buffer)
        var block: Block? = null
        while (lexer.tokenType != null) {
            val type = lexer.tokenType!!
            val start = lexer.tokenStart
            val end = lexer.tokenEnd
            if (type in OPEN_TYPES) {
                val lineStart = buffer.lineStart(start)
                val prefix = buffer.subSequence(lineStart, start).toString()
                val continuationIndent = prefix.takeIf { it.isNotEmpty() && it.all(::isIndent) }
                continuationIndent?.let { add(lineStart, start) }
                block = Block(type, continuationIndent, header = true)
            } else {
                val active = block
                if (active != null) {
                    when {
                        active.header && type == TokenType.WHITE_SPACE -> add(start, end)
                        type == ElclTypes.LINE_BREAK -> active.header = false
                        type == active.contentType -> markContent(buffer, start, end, active)
                        type == active.closeType -> {
                            markClose(buffer, start, end, active)
                            block = null
                        }
                    }
                }
            }
            lexer.advance()
        }
    }

    /** Returns the ignored range containing [offset], if any. */
    fun containing(offset: Int): TextRange? = ignored.firstOrNull { offset >= it.startOffset && offset < it.endOffset }

    /** Returns the next ignored range start at or after [offset]. */
    fun nextStart(offset: Int, limit: Int): Int =
        ignored.firstOrNull { it.startOffset >= offset && it.startOffset < limit }?.startOffset ?: limit

    private fun markContent(buffer: CharSequence, start: Int, end: Int, block: Block) {
        val text = buffer.subSequence(start, end).toString()
        val leadingLength = text.takeWhile(::isIndent).length
        if (block.indentation == null && text.any { !isIndent(it) }) {
            block.indentation = text.substring(0, leadingLength)
        }
        block.indentation?.takeIf { it.isNotEmpty() && text.startsWith(it) }?.let {
            add(start, start + it.length)
        }
        val trailingStart = text.indexAfterLastNonIndent()
        if (trailingStart < text.length) add(start + trailingStart, end)
    }

    private fun markClose(buffer: CharSequence, start: Int, end: Int, block: Block) {
        val text = buffer.subSequence(start, end).toString()
        val leadingLength = text.takeWhile(::isIndent).length
        if (block.indentation == null) block.indentation = text.substring(0, leadingLength)
        block.indentation?.takeIf { it.isNotEmpty() && text.startsWith(it) }?.let {
            add(start, start + it.length)
        }
        val delimiterEnd = leadingLength + 3
        if (delimiterEnd < text.length) add(start + delimiterEnd, end)
    }

    private fun add(start: Int, end: Int) {
        if (start >= end) return
        val previous = ignored.lastOrNull()
        if (previous != null && previous.endOffset == start) {
            ignored[ignored.lastIndex] = TextRange(previous.startOffset, end)
        } else {
            ignored += TextRange(start, end)
        }
    }

    private class Block(openType: IElementType, var indentation: String?, var header: Boolean) {
        val contentType: IElementType = when (openType) {
            ElclTypes.MULTILINE_TEXT_OPEN -> ElclTypes.MULTILINE_TEXT_CONTENT
            ElclTypes.MULTILINE_CODE_OPEN -> ElclTypes.MULTILINE_CODE_CONTENT
            ElclTypes.MULTILINE_REGEX_OPEN -> ElclTypes.MULTILINE_REGEX_CONTENT
            else -> ElclTypes.MULTILINE_BYTES_CONTENT
        }
        val closeType: IElementType = when (openType) {
            ElclTypes.MULTILINE_TEXT_OPEN -> ElclTypes.MULTILINE_TEXT_CLOSE
            ElclTypes.MULTILINE_CODE_OPEN -> ElclTypes.MULTILINE_CODE_CLOSE
            ElclTypes.MULTILINE_REGEX_OPEN -> ElclTypes.MULTILINE_REGEX_CLOSE
            else -> ElclTypes.MULTILINE_BYTES_CLOSE
        }
    }

    companion object {
        private val OPEN_TYPES = setOf(
            ElclTypes.MULTILINE_TEXT_OPEN,
            ElclTypes.MULTILINE_CODE_OPEN,
            ElclTypes.MULTILINE_REGEX_OPEN,
            ElclTypes.MULTILINE_BYTES_OPEN,
        )

        private fun isIndent(char: Char): Boolean = char == ' ' || char == '\t'
        private fun String.indexAfterLastNonIndent(): Int = indexOfLast { !isIndent(it) } + 1
        private fun CharSequence.lineStart(offset: Int): Int {
            var result = offset
            while (result > 0 && this[result - 1] != '\n' && this[result - 1] != '\r') result--
            return result
        }
    }
}

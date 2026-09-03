package dev.erbsland.elcl.highlight

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

/**
 * Splits one multiline token into content, discarded whitespace, and optional
 * text escape fragments.
 */
internal class ElclMultilineFragmentLexer(
    private val whitespaceMap: ElclMultilineWhitespaceMap,
    private val contentType: IElementType,
    private val highlightEscapes: Boolean = false,
) : LexerBase() {
    private lateinit var buffer: CharSequence
    private var endOffset = 0
    private var tokenStart = 0
    private var tokenEnd = 0
    private var tokenType: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.endOffset = endOffset
        tokenStart = startOffset
        locateToken()
    }

    override fun getState(): Int = 0
    override fun getTokenType(): IElementType? = tokenType
    override fun getTokenStart(): Int = tokenStart
    override fun getTokenEnd(): Int = tokenEnd
    override fun getBufferSequence(): CharSequence = buffer
    override fun getBufferEnd(): Int = endOffset

    override fun advance() {
        tokenStart = tokenEnd
        locateToken()
    }

    private fun locateToken() {
        if (tokenStart >= endOffset) {
            tokenType = null
            tokenEnd = endOffset
            return
        }
        whitespaceMap.containing(tokenStart)?.let { range ->
            tokenType = ElclHighlightTokenTypes.MULTILINE_IGNORED_WHITESPACE
            tokenEnd = minOf(endOffset, range.endOffset)
            return
        }
        val escapeLength = if (highlightEscapes) ElclTextEscapeLexer.escapeLengthAt(buffer, tokenStart, endOffset) else null
        if (escapeLength != null) {
            tokenType = ElclHighlightTokenTypes.TEXT_ESCAPE
            tokenEnd = tokenStart + escapeLength
            return
        }
        tokenType = contentType
        val nextWhitespace = whitespaceMap.nextStart(tokenStart, endOffset)
        val nextEscape = if (highlightEscapes) {
            (tokenStart + 1 until minOf(nextWhitespace, endOffset))
                .firstOrNull { ElclTextEscapeLexer.escapeLengthAt(buffer, it, endOffset) != null }
                ?: endOffset
        } else {
            endOffset
        }
        tokenEnd = minOf(nextWhitespace, nextEscape, endOffset)
    }
}

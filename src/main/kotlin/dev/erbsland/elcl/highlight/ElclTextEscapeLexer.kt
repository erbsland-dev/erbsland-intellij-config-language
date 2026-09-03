package dev.erbsland.elcl.highlight

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType
import dev.erbsland.elcl.psi.ElclTypes

/** Splits valid ELCL text escapes from the surrounding text for coloring. */
internal class ElclTextEscapeLexer : LexerBase() {
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
        val escapeLength = escapeLengthAt(buffer, tokenStart, endOffset)
        if (escapeLength != null) {
            tokenType = ElclHighlightTokenTypes.TEXT_ESCAPE
            tokenEnd = tokenStart + escapeLength
            return
        }
        tokenType = ElclTypes.TEXT
        tokenEnd = (tokenStart + 1 until endOffset)
            .firstOrNull { escapeLengthAt(buffer, it, endOffset) != null }
            ?: endOffset
    }

    companion object {
        /** Returns the length of a valid ELCL escape beginning at [offset]. */
        fun escapeLengthAt(buffer: CharSequence, offset: Int, endOffset: Int): Int? {
            if (buffer[offset] != '\\' || offset + 1 >= endOffset) return null
            return when (buffer[offset + 1]) {
                '\\', '"', '$', 'n', 'N', 'r', 'R', 't', 'T' -> 2
                'u', 'U' -> unicodeEscapeLength(buffer, offset, endOffset)
                else -> null
            }
        }

        private fun unicodeEscapeLength(buffer: CharSequence, offset: Int, endOffset: Int): Int? {
            val payload = offset + 2
            if (payload >= endOffset) return null
            if (buffer[payload] == '{') {
                var cursor = payload + 1
                while (cursor < endOffset && cursor - payload <= 8 && buffer[cursor].isHexDigit()) cursor++
                val digitCount = cursor - payload - 1
                return if (digitCount in 1..8 && cursor < endOffset && buffer[cursor] == '}') cursor - offset + 1 else null
            }
            return if (payload + 4 <= endOffset && (payload until payload + 4).all { buffer[it].isHexDigit() }) 6 else null
        }

        private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
    }
}

package dev.erbsland.elcl.editor

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import dev.erbsland.elcl.psi.ElclTypes

/** Declares the paired delimiters used by section and literal syntax. */
class ElclBraceMatcher : PairedBraceMatcher {
    override fun getPairs(): Array<BracePair> = PAIRS

    override fun isPairedBracesAllowedBeforeType(leftBraceType: IElementType, contextType: IElementType?): Boolean = true

    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int = openingBraceOffset

    companion object {
        private val PAIRS = arrayOf(
            BracePair(ElclTypes.SECTION_MAP_OPEN, ElclTypes.SECTION_MAP_CLOSE, true),
            BracePair(ElclTypes.SECTION_LIST_OPEN, ElclTypes.SECTION_LIST_CLOSE, true),
            BracePair(ElclTypes.MULTILINE_TEXT_OPEN, ElclTypes.MULTILINE_TEXT_CLOSE, true),
            BracePair(ElclTypes.MULTILINE_CODE_OPEN, ElclTypes.MULTILINE_CODE_CLOSE, true),
            BracePair(ElclTypes.MULTILINE_REGEX_OPEN, ElclTypes.MULTILINE_REGEX_CLOSE, true),
            BracePair(ElclTypes.MULTILINE_BYTES_OPEN, ElclTypes.MULTILINE_BYTES_CLOSE, true),
        )
    }
}

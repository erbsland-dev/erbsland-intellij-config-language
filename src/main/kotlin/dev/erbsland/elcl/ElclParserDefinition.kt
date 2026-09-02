package dev.erbsland.elcl

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import dev.erbsland.elcl.lexer.ElclLexerAdapter
import dev.erbsland.elcl.parser.ElclParser
import dev.erbsland.elcl.psi.ElclTypes

class ElclParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer = ElclLexerAdapter()
    override fun createParser(project: Project?): PsiParser = ElclParser()
    override fun getFileNodeType(): IFileElementType = FILE
    override fun getWhitespaceTokens(): TokenSet = TokenSet.create(TokenType.WHITE_SPACE)
    override fun getCommentTokens(): TokenSet = TokenSet.create(ElclTypes.COMMENT)
    override fun getStringLiteralElements(): TokenSet = TokenSet.create(
        ElclTypes.TEXT,
        ElclTypes.TEXT_NAME,
        ElclTypes.CODE,
        ElclTypes.REGEX,
        ElclTypes.BYTES,
        ElclTypes.MULTILINE_TEXT,
        ElclTypes.MULTILINE_CODE,
        ElclTypes.MULTILINE_REGEX,
        ElclTypes.MULTILINE_BYTES,
    )

    override fun createElement(node: ASTNode): PsiElement = ElclTypes.Factory.createElement(node)
    override fun createFile(viewProvider: FileViewProvider): PsiFile = ElclFile(viewProvider)

    companion object {
        val FILE = IFileElementType(ElclLanguage.INSTANCE)
    }
}

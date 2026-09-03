package dev.erbsland.elcl.lexer

import com.intellij.lexer.FlexAdapter

/** IntelliJ adapter for the generated, restartable ELCL JFlex lexer. */
class ElclLexerAdapter : FlexAdapter(_ElclLexer(null))

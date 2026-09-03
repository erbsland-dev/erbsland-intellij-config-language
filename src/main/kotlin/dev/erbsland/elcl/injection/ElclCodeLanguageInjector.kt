package dev.erbsland.elcl.injection

import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.psi.PsiElement
import dev.erbsland.elcl.psi.ElclMultilineCode
import dev.erbsland.elcl.psi.ElclTypes

/** Injects an installed IDE language into a tagged multiline ELCL code value. */
class ElclCodeLanguageInjector : MultiHostInjector {
    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        val host = context as? ElclMultilineCode ?: return
        val tag = host.node.findChildByType(ElclTypes.MULTILINE_CODE_LANGUAGE)?.text ?: return
        val language = ElclCodeLanguageResolver.resolve(tag) ?: return
        val bodyRange = ElclMultilineCodeSupport.layout(host).bodyRange()
        if (bodyRange.isEmpty) return
        registrar.startInjecting(language)
            .addPlace(null, null, host, bodyRange)
            .doneInjecting()
    }

    override fun elementsToInjectIn(): List<Class<out PsiElement>> =
        listOf(ElclMultilineCode::class.java)
}

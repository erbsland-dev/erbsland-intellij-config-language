package dev.erbsland.elcl.injection;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.NotNull;

/**
 * Base class that turns generated multiline-code PSI into an injection host.
 *
 * <p>The generated implementation extends this class through the Grammar-Kit
 * mixin declaration. Content decoding and replacement are delegated to the
 * support class so the PSI contract remains small.</p>
 */
public abstract class ElclMultilineCodeMixin extends ASTWrapperPsiElement implements PsiLanguageInjectionHost {
    protected ElclMultilineCodeMixin(@NotNull ASTNode node) {
        super(node);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isValidHost() {
        return isValid();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull PsiLanguageInjectionHost updateText(@NotNull String text) {
        return ElclMultilineCodeSupport.updateText(this, text);
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull LiteralTextEscaper<? extends PsiLanguageInjectionHost> createLiteralTextEscaper() {
        return new ElclMultilineCodeEscaper(this);
    }
}

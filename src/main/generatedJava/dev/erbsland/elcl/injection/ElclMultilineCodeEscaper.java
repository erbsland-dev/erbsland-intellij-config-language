package dev.erbsland.elcl.injection;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.NotNull;

/** Decodes the logical code body and maps injected offsets back to ELCL text. */
public final class ElclMultilineCodeEscaper extends LiteralTextEscaper<PsiLanguageInjectionHost> {
    public ElclMultilineCodeEscaper(@NotNull PsiLanguageInjectionHost host) {
        super(host);
    }

    /** {@inheritDoc} */
    @Override
    public boolean decode(@NotNull TextRange rangeInsideHost, @NotNull StringBuilder output) {
        ElclMultilineCodeSupport.Layout layout = ElclMultilineCodeSupport.layout(myHost);
        output.append(ElclMultilineCodeSupport.decode(myHost, rangeInsideHost, layout.indentation()).text());
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public int getOffsetInHost(int offsetInDecoded, @NotNull TextRange rangeInsideHost) {
        ElclMultilineCodeSupport.Layout layout = ElclMultilineCodeSupport.layout(myHost);
        return ElclMultilineCodeSupport.decode(myHost, rangeInsideHost, layout.indentation())
            .hostOffset(offsetInDecoded);
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull TextRange getRelevantTextRange() {
        return ElclMultilineCodeSupport.layout(myHost).bodyRange();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isOneLine() {
        return false;
    }
}

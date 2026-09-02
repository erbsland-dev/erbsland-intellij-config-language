package dev.erbsland.elcl;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public final class ElclTokenType extends IElementType {
    public ElclTokenType(@NotNull String debugName) {
        super(debugName, ElclLanguage.INSTANCE);
    }

    @Override
    public String toString() {
        return "ELCL_" + super.toString();
    }
}

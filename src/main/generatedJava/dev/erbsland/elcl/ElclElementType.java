package dev.erbsland.elcl;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public final class ElclElementType extends IElementType {
    public ElclElementType(@NotNull String debugName) {
        super(debugName, ElclLanguage.INSTANCE);
    }
}

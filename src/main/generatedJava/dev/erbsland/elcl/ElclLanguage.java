package dev.erbsland.elcl;

import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

public final class ElclLanguage extends Language {
    public static final ElclLanguage INSTANCE = new ElclLanguage();

    private ElclLanguage() {
        super("ELCL");
    }

    @Override
    public @NotNull String getDisplayName() {
        return "Erbsland Configuration Language";
    }
}

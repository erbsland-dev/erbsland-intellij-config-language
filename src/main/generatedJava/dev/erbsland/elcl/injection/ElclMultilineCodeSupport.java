package dev.erbsland.elcl.injection;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import dev.erbsland.elcl.ElclLanguage;
import dev.erbsland.elcl.psi.ElclMultilineCode;
import dev.erbsland.elcl.psi.ElclTypes;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Shared range, decoding, and update operations for multiline-code hosts. */
public final class ElclMultilineCodeSupport {
    private ElclMultilineCodeSupport() {
    }

    /** Describes the body range and structural indentation within one host. */
    public record Layout(@NotNull TextRange bodyRange, @NotNull String indentation) {
    }

    /** Decoded code plus host offsets for each decoded character. */
    public record Decoded(@NotNull String text, int @NotNull [] hostOffsets, int endOffset) {
        /** Maps a decoded offset back into the host, or returns {@code -1} if invalid. */
        public int hostOffset(int decodedOffset) {
            if (decodedOffset < 0 || decodedOffset > text.length()) return -1;
            return decodedOffset == text.length() ? endOffset : hostOffsets[decodedOffset];
        }
    }

    /** Finds the code body, excluding the opener header and closing delimiter. */
    public static @NotNull Layout layout(@NotNull PsiLanguageInjectionHost host) {
        String text = host.getText();
        ASTNode open = host.getNode().findChildByType(ElclTypes.MULTILINE_CODE_OPEN);
        ASTNode close = host.getNode().findChildByType(ElclTypes.MULTILINE_CODE_CLOSE);
        if (open == null || close == null) return new Layout(TextRange.EMPTY_RANGE, "");

        int hostStart = host.getTextRange().getStartOffset();
        int openEnd = open.getTextRange().getEndOffset() - hostStart;
        int bodyStart = lineEnd(text, openEnd);
        int bodyEnd = Math.max(bodyStart, close.getTextRange().getStartOffset() - hostStart);
        String closeText = close.getText();
        int indentEnd = 0;
        while (indentEnd < closeText.length() && isIndent(closeText.charAt(indentEnd))) indentEnd++;
        return new Layout(new TextRange(bodyStart, bodyEnd), closeText.substring(0, indentEnd));
    }

    /** Decodes a host range by removing ELCL indentation and normalizing line breaks. */
    public static @NotNull Decoded decode(
        @NotNull PsiLanguageInjectionHost host,
        @NotNull TextRange range,
        @NotNull String indentation
    ) {
        String source = host.getText();
        StringBuilder result = new StringBuilder();
        List<Integer> offsets = new ArrayList<>();
        int cursor = range.getStartOffset();
        boolean lineStart = cursor == 0 || isLineBreak(source.charAt(cursor - 1));
        while (cursor < range.getEndOffset()) {
            if (lineStart && !indentation.isEmpty() && source.startsWith(indentation, cursor)) {
                cursor += indentation.length();
            }
            if (cursor >= range.getEndOffset()) break;
            offsets.add(cursor);
            char value = source.charAt(cursor);
            if (value == '\r' && cursor + 1 < range.getEndOffset() && source.charAt(cursor + 1) == '\n') {
                removeTrailingIndent(result, offsets);
                result.append('\n');
                cursor += 2;
                lineStart = true;
            } else {
                if (value == '\n' || value == '\r') removeTrailingIndent(result, offsets);
                result.append(value);
                cursor++;
                lineStart = value == '\n' || value == '\r';
            }
        }
        removeTrailingIndent(result, offsets);
        int[] offsetArray = offsets.stream().mapToInt(Integer::intValue).toArray();
        return new Decoded(result.toString(), offsetArray, range.getEndOffset());
    }

    /** Replaces decoded code while retaining the original ELCL frame and indentation. */
    public static @NotNull PsiLanguageInjectionHost updateText(
        @NotNull PsiLanguageInjectionHost host,
        @NotNull String content
    ) {
        Layout layout = layout(host);
        String hostText = host.getText();
        String lineSeparator = hostText.contains("\r\n") ? "\r\n" : "\n";
        String encoded = encode(content, layout.indentation(), lineSeparator);
        String replacementText = hostText.substring(0, layout.bodyRange().getStartOffset())
            + encoded
            + hostText.substring(layout.bodyRange().getEndOffset());
        String fileText = "[Injection]\nValue: " + replacementText;
        PsiFile file = PsiFileFactory.getInstance(host.getProject())
            .createFileFromText("injection.elcl", ElclLanguage.INSTANCE, fileText);
        ElclMultilineCode replacement = PsiTreeUtil.findChildOfType(file, ElclMultilineCode.class);
        if (replacement == null) return host;
        PsiElement updated = ((PsiElement) host).replace(replacement);
        return (PsiLanguageInjectionHost) updated;
    }

    private static int lineEnd(String text, int offset) {
        int cursor = offset;
        while (cursor < text.length() && !isLineBreak(text.charAt(cursor))) cursor++;
        if (cursor < text.length() && text.charAt(cursor) == '\r') cursor++;
        if (cursor < text.length() && text.charAt(cursor) == '\n') cursor++;
        return cursor;
    }

    private static String encode(String content, String indentation, String lineSeparator) {
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.isEmpty()) return "";
        String[] lines = normalized.split("\n", -1);
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            if (!line.isEmpty()) result.append(indentation).append(line);
            if (index < lines.length - 1) result.append(lineSeparator);
        }
        if (!normalized.endsWith("\n")) result.append(lineSeparator);
        return result.toString();
    }

    private static boolean isIndent(char value) {
        return value == ' ' || value == '\t';
    }

    /** Removes whitespace that ELCL discards at the logical end of a line. */
    private static void removeTrailingIndent(StringBuilder text, List<Integer> offsets) {
        while (!text.isEmpty() && isIndent(text.charAt(text.length() - 1))) {
            text.deleteCharAt(text.length() - 1);
            offsets.remove(offsets.size() - 1);
        }
    }

    private static boolean isLineBreak(char value) {
        return value == '\n' || value == '\r';
    }
}

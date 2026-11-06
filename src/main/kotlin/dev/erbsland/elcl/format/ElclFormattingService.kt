package dev.erbsland.elcl.format

import com.intellij.formatting.FormattingContext
import com.intellij.formatting.service.AbstractDocumentFormattingService
import com.intellij.formatting.service.FormattingNotificationService
import com.intellij.formatting.service.FormattingService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiFile
import dev.erbsland.elcl.ElclFile
import dev.erbsland.elcl.settings.ElclSettings
import dev.erbsland.elcl.style.ElclCodeStyleSettings
import java.nio.file.Files
import java.nio.file.Path

/** Reformat Code integration, including selection formatting and normal IDE undo handling. */
class ElclFormattingService : AbstractDocumentFormattingService() {
    override fun canFormat(file: PsiFile): Boolean = file is ElclFile
    override fun getFeatures(): Set<FormattingService.Feature> = setOf(FormattingService.Feature.FORMAT_FRAGMENTS)

    override fun formatDocument(document: Document, formattingRanges: List<TextRange>, formattingContext: FormattingContext,
        canChangeWhiteSpaceOnly: Boolean, quickFormat: Boolean) {
        val settings = formattingContext.codeStyleSettings.getCustomSettings(ElclCodeStyleSettings::class.java)
        val result = try {
            val format = if (ElclSettings.getInstance().state.loadFormattingConfiguration) {
                val reader = ElclFormatConfig { path ->
                    val virtual = LocalFileSystem.getInstance().findFileByNioFile(path)
                    virtual?.let { FileDocumentManager.getInstance().getCachedDocument(it)?.text } ?: Files.readString(path)
                }
                val file = formattingContext.containingFile.originalFile.virtualFile
                val configuration = if (file != null && file.isInLocalFileSystem) reader.discover(Path.of(file.path)) else null
                if (configuration == null) settings.formatFor(document.text) else reader.read(configuration)
            } else settings.formatFor(document.text)
            ElclFormatter().format(document.text, format, formattingRanges, canChangeWhiteSpaceOnly)
        } catch (e: IllegalArgumentException) {
            FormattingNotificationService.getInstance(formattingContext.project).reportError(
                "ELCL Formatting", "Cannot format ELCL", e.message ?: "Invalid formatting configuration")
            return
        }
        // The formatting command owns the write action and undo group. Do not schedule a second command.
        result.edits.asReversed().forEach { document.replaceString(it.range.startOffset, it.range.endOffset, it.replacement) }
        if (result.signedSourceChanged) {
            NotificationGroupManager.getInstance().getNotificationGroup("ELCL Formatting")
                .createNotification("Formatting changed signed source; re-sign the document.", NotificationType.WARNING)
                .notify(formattingContext.project)
        }
    }
}

/** Optional document-local detection only affects IDE defaults, never a discovered Core configuration. */
internal fun ElclCodeStyleSettings.formatFor(text: String): ElclFormat {
    var style = ElclFormatStyle.deserialize(FORMAT_STYLE)
    if (!FORMAT_DETECT_SECTION_NAMES && !FORMAT_DETECT_VALUE_NAMES && !FORMAT_DETECT_IDENTIFIERS) return ElclFormat(style)
    val statements = ElclSyntaxSnapshot(text).statements.filterNot { it.damaged }
    fun detect(samples: List<String>, case: FormatProperty, separator: FormatProperty) {
        fun winner(values: List<String>): String? = if (values.size < 3) null else values.groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.takeIf { it.value * 3 > values.size * 2 }?.key
        val cases = samples.mapNotNull { sample ->
            val letters = sample.filter(Char::isLetter)
            when {
                letters.isEmpty() -> null
                letters.all(Char::isLowerCase) -> "lower"
                letters.all(Char::isUpperCase) -> "upper"
                sample.split(' ', '_').all { it.firstOrNull()?.isUpperCase() == true && it.drop(1).all { c -> !c.isLetter() || c.isLowerCase() } } -> "title"
                else -> null
            }
        }
        val separators = samples.mapNotNull { if ('_' in it && ' ' !in it) "underscore" else if (' ' in it && '_' !in it) "space" else null }
        val patch = mutableMapOf<FormatProperty, String>()
        winner(cases)?.let { patch[case] = it }
        winner(separators)?.let { patch[separator] = it }
        style = style.patched(patch)
    }
    if (FORMAT_DETECT_SECTION_NAMES) detect(statements.filter { it.kind == "section" }.flatMap { it.names }
        .filter { !it.text.startsWith('"') }.map { it.text }, FormatProperty.SECTION_CASE, FormatProperty.SECTION_SEPARATOR)
    if (FORMAT_DETECT_VALUE_NAMES) detect(statements.filter { it.kind == "assignment" && !it.meta }.flatMap { it.names }
        .filter { !it.text.startsWith('"') }.map { it.text }, FormatProperty.VALUE_CASE, FormatProperty.VALUE_SEPARATOR)
    if (FORMAT_DETECT_IDENTIFIERS) detect(statements.filter { !it.meta }.flatMap { it.values }
        .filter { it.type == dev.erbsland.elcl.psi.ElclTypes.TEXT }.map { dev.erbsland.elcl.syntax.decodeElclText(it.text.removeSurrounding("\"")) }
        .filter { it.matches(Regex("[A-Za-z][A-Za-z0-9]*([ _][A-Za-z0-9]+)*")) }, FormatProperty.TEXT_IDENTIFIER_CASE, FormatProperty.TEXT_IDENTIFIER_SEPARATOR)
    return ElclFormat(style)
}

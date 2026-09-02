package dev.erbsland.elcl.validation

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import dev.erbsland.elcl.ElclFile
import dev.erbsland.elcl.highlight.ElclTextAttributes
import java.nio.file.Path

class ElclAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val file = element as? ElclFile ?: return
        val diagnostics = CachedValuesManager.getCachedValue(file) {
            val root = file.project.basePath?.let(Path::of)?.toAbsolutePath()?.normalize()
            val source = file.virtualFile?.path?.let(Path::of)
            val result = ElclTextAnalyzer(root).analyze(file.text, file.isValidationRules, source)
            CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT)
        }
        diagnostics.forEach { diagnostic ->
            val severity = if (diagnostic.severity == ElclDiagnosticSeverity.ERROR) HighlightSeverity.ERROR else HighlightSeverity.WARNING
            holder.newAnnotation(severity, diagnostic.message)
                .range(diagnostic.range)
                .highlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                .create()
        }
        // Escape sequences are deliberately highlighted semantically: the parser keeps text values atomic.
        Regex("\\\\(?:u[0-9a-fA-F]{4}|U[0-9a-fA-F]{8}|x[0-9a-fA-F]{2}|[\\\\\"\$nrt])")
            .findAll(file.text)
            .forEach { match ->
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(TextRange(match.range.first, match.range.last + 1))
                    .textAttributes(ElclTextAttributes.ESCAPE)
                    .create()
            }
    }
}

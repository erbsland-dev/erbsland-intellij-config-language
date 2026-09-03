package dev.erbsland.elcl.validation

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import dev.erbsland.elcl.ElclFile
import java.nio.file.Path

/** Publishes recoverable text-analysis diagnostics as editor annotations. */
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
    }
}

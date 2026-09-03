package dev.erbsland.elcl.validation

import java.nio.file.Path

/**
 * Editor-facing façade for recoverable ELCL document analysis.
 *
 * Each call is independent. When [projectRoot] and a source path are supplied,
 * local includes below that root participate in namespace-conflict checks.
 */
class ElclTextAnalyzer(
    private val projectRoot: Path? = null,
) {
    /** Returns syntax and semantic diagnostics in source order. */
    fun analyze(text: String, validationRules: Boolean, source: Path? = null): List<ElclDiagnostic> =
        ElclDocumentAnalyzer(projectRoot).analyze(text, validationRules, source)
}

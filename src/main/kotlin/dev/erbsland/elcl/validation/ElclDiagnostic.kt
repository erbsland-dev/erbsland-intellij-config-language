package dev.erbsland.elcl.validation

import com.intellij.openapi.util.TextRange

/** Severity levels produced by the lightweight editor analyzer. */
enum class ElclDiagnosticSeverity { ERROR, WARNING }

/** One analyzer finding with a range relative to the analyzed source text. */
data class ElclDiagnostic(
    val range: TextRange,
    val message: String,
    val severity: ElclDiagnosticSeverity = ElclDiagnosticSeverity.ERROR,
)

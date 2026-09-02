package dev.erbsland.elcl.validation

import com.intellij.openapi.util.TextRange

enum class ElclDiagnosticSeverity { ERROR, WARNING }

data class ElclDiagnostic(
    val range: TextRange,
    val message: String,
    val severity: ElclDiagnosticSeverity = ElclDiagnosticSeverity.ERROR,
)

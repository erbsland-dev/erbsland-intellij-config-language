package dev.erbsland.elcl.validation

/** Shared normalization used for ELCL names and validation-rule identifiers. */
internal fun normalize(value: String): String = value.trim().trim('"').lowercase().replace(Regex("[ _]+"), "_")

/** True when [value] uses the validation-rules escape for a literal `vr_` name. */
internal fun isEscapedVrName(value: String): Boolean = normalize(value).startsWith("vr_vr_")

/** True when [value] is reserved by the validation-rules `vr_` namespace. */
internal fun isReservedVrName(value: String): Boolean {
    val normalized = normalize(value)
    return normalized.startsWith("vr_") && !normalized.startsWith("vr_vr_")
}

/** Maps an escaped validation-rules name to the regular name it describes. */
internal fun semanticVrName(value: String): String = normalize(value).let {
    if (it.startsWith("vr_vr_")) it.removePrefix("vr_") else it
}

/** Maps a rule-document path to the path visible in the validated document. */
internal fun semanticVrPath(path: List<String>): List<String> = path.map(::semanticVrName)

/** Returns the first scalar from an editor-oriented comma-separated value. */
internal fun firstValue(value: String): String? = valueList(value).firstOrNull()

/** Splits the simple scalar/list forms required by semantic validation. */
internal fun valueList(value: String): List<String> =
    value.split(',').map { it.trim().trim('"', '`') }.filter { it.isNotEmpty() }

internal fun firstNumber(value: String): Double? = valueList(value).firstOrNull()?.replace("'", "")?.toDoubleOrNull()

internal fun compareVersions(a: String, b: String): Int {
    val left = a.split('.').map { it.toIntOrNull() ?: 0 }
    val right = b.split('.').map { it.toIntOrNull() ?: 0 }
    return (0 until maxOf(left.size, right.size)).firstNotNullOfOrNull { index ->
        (left.getOrElse(index) { 0 } - right.getOrElse(index) { 0 }).takeIf { it != 0 }
    } ?: 0
}

internal val MULTILINE_OPENERS = listOf("\"\"\"", "```", "///", "<<<")
internal val RESERVED_NAMES = setOf("vr_template", "vr_any", "vr_name", "vr_entry", "vr_key", "vr_dependency")
internal val CANONICAL_TYPES = listOf(
    "integer", "boolean", "float", "text", "date", "time", "date_time", "bytes", "time_delta", "reg_ex",
    "value", "value_list", "value_matrix", "section", "section_list", "section_with_texts", "not_validated",
)
internal val TYPES = setOf(
    "integer", "boolean", "float", "text", "date", "time", "date_time", "datetime", "bytes", "time_delta",
    "timedelta", "regex", "reg_ex", "value", "value_list", "valuelist", "value_matrix", "valuematrix",
    "section", "section_list", "sectionlist", "section_with_texts", "sectionwithtexts", "not_validated", "notvalidated",
)
internal val STRUCTURAL_TYPES = setOf("section", "section_list", "sectionlist", "section_with_texts", "sectionwithtexts", "not_validated", "notvalidated")
internal val MULTIPLE_TYPES = setOf("integer", "float", "text", "bytes", "value_list", "valuelist", "value_matrix", "valuematrix", "section", "section_list", "sectionlist", "section_with_texts", "sectionwithtexts")
internal val IN_TYPES = setOf("integer", "float", "text", "bytes")
internal val MODES = setOf("if", "if_not", "or", "xor", "xnor", "and")
internal val BOOLEAN_FIELDS = setOf("is_optional", "is_secret", "case_sensitive")
internal val SEMANTIC_IDENTIFIER_FIELDS = setOf("type", "use_template", "mode", "name", "key", "source", "target")
internal val FEATURES = setOf(
    "core", "minimum", "standard", "advanced", "all", "float", "byte_count", "multi_line", "section_list",
    "value_list", "text_names", "date_time", "code", "byte_data", "include", "regex", "time_delta",
)
internal val COMMON_FIELDS = setOf(
    "type", "use_template", "default", "is_optional", "title", "description", "error", "is_secret", "case_sensitive",
    "name", "key", "keys", "index", "index_name", "mode", "source", "target",
)
internal val CONSTRAINTS = setOf(
    "minimum", "maximum", "equals", "in", "multiple", "matches", "starts", "ends", "contains", "chars", "allowed_chars",
    "key", "version", "minimum_version", "maximum_version",
)
internal val NEGATABLE = setOf("equals", "in", "multiple", "matches", "starts", "ends", "contains", "chars", "allowed_chars", "key", "version")

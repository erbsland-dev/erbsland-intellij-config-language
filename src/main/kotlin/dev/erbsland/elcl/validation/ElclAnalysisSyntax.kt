package dev.erbsland.elcl.validation

/** Shared normalization used for ELCL names and validation-rule identifiers. */
internal fun normalize(value: String): String = value.trim().trim('"').lowercase().replace(Regex("[ _]+"), "_")

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
internal val TYPES = setOf(
    "integer", "boolean", "float", "text", "date", "time", "date_time", "datetime", "bytes", "time_delta",
    "timedelta", "regex", "reg_ex", "value", "value_list", "valuelist", "value_matrix", "valuematrix",
    "section", "section_list", "sectionlist", "section_with_texts", "sectionwithtexts", "not_validated", "notvalidated",
)
internal val STRUCTURAL_TYPES = setOf("section", "section_list", "sectionlist", "section_with_texts", "sectionwithtexts", "not_validated", "notvalidated")
internal val MULTIPLE_TYPES = setOf("integer", "float", "text", "bytes", "value_list", "valuelist", "value_matrix", "valuematrix", "section", "section_list", "sectionlist", "section_with_texts", "sectionwithtexts")
internal val IN_TYPES = setOf("integer", "float", "text", "bytes")
internal val MODES = setOf("if", "if_not", "or", "xor", "xnor", "and")
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

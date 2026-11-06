package dev.erbsland.elcl.format

/** One schema shared by the formatter, configuration reader, settings UI, and preview. */
internal enum class FormatProperty(val path: String, val label: String, val default: String, val choices: List<String> = emptyList()) {
    WIDTH("maximum_line_length", "Maximum line length", "118"),
    WRAPPING("value_wrapping", "Value wrapping", "next_line_if_fits", listOf("keep", "same_line", "next_line", "next_line_if_fits", "when_too_long")),
    SECTION_CASE("names.section_case", "Section name case", "title", cases()),
    SECTION_SEPARATOR("names.section_separator", "Section word separator", "space", separators()),
    VALUE_CASE("names.value_case", "Value name case", "title", cases()),
    VALUE_SEPARATOR("names.value_separator", "Value word separator", "space", separators()),
    TEXT_NAME_CASE("names.text_name_case", "Quoted name case", "keep", cases()),
    TEXT_NAME_SEPARATOR("names.text_name_separator", "Quoted name word separator", "space", separators()),
    TEXT_IDENTIFIER_CASE("names.text_identifier_case", "Text identifier case", "keep", cases()),
    TEXT_IDENTIFIER_SEPARATOR("names.text_identifier_separator", "Text identifier word separator", "space", separators()),
    SECTION_INDENT("section.indent", "Opening bracket column (or infinite)", "4"),
    SECTION_SUFFIX("section.suffix", "Section suffix width (or infinite)", "infinite"),
    SECTION_STAR("section.star", "Closing section-list star", "true", booleans()),
    SPACE_AFTER_OPEN("section.space_after_open", "Space after opening bracket", "true", booleans()),
    SPACE_BEFORE_CLOSE("section.space_before_close", "Space before closing bracket", "true", booleans()),
    SPACE_AROUND_DOT("section.space_around_dot", "Spaces around path dots", "true", booleans()),
    VECTOR("vector.style", "Vector layout", "keep", listOf("keep", "compact", "multiline")),
    BULLET_SPACES("vector.spaces_after_star", "Spaces after list bullet", "1"),
    MATRIX("matrix.style", "Matrix layout", "keep", listOf("keep", "align")),
    SEPARATOR("separator.character", "Assignment separator", ":", listOf(":", "=")),
    SEPARATOR_MODE("separator.position_mode", "Separator position mode", "column", positions()),
    SEPARATOR_POSITION("separator.position", "Separator position", "29"),
    VALUE_MODE("separator.value_position_mode", "Value position mode", "spaces", positions()),
    VALUE_POSITION("separator.value_position", "Value position", "1"),
    INDENT_STYLE("indentation.style", "Continuation indentation", "spaces", listOf("spaces", "tabs")),
    INDENT_COUNT("indentation.count", "Continuation indentation count", "4");

    fun validate(value: String): String {
        val normalized = value.lowercase(java.util.Locale.ROOT).replace(' ', '_')
        if (choices.isNotEmpty()) require(normalized in choices) { "$label: expected ${choices.joinToString()}" }
        else if (this in listOf(SECTION_INDENT, SECTION_SUFFIX) && normalized == "infinite") return normalized
        else {
            val number = normalized.toIntOrNull()
            val minimum = if (this in listOf(WIDTH, BULLET_SPACES, INDENT_COUNT)) 1 else 0
            require(number != null && number >= minimum) { "$label: expected an integer of at least $minimum" }
        }
        return normalized
    }
}

private fun cases() = listOf("keep", "lower", "title", "upper")
private fun separators() = listOf("space", "underscore")
private fun positions() = listOf("spaces", "column")
private fun booleans() = listOf("true", "false")

internal data class ElclFormatStyle(val properties: Map<FormatProperty, String> = emptyMap()) {
    operator fun get(key: FormatProperty): String = properties[key] ?: key.default
    fun number(key: FormatProperty): Int = if (get(key) == "infinite") -1 else get(key).toInt()
    fun enabled(key: FormatProperty): Boolean = get(key) == "true"
    fun patched(patch: Map<FormatProperty, String>): ElclFormatStyle = copy(properties = properties + patch)
    fun validate() { FormatProperty.entries.forEach { it.validate(get(it)) } }
    fun serialize(): String = FormatProperty.entries.joinToString("\n") { "${it.path}=${get(it)}" }

    companion object {
        fun deserialize(text: String): ElclFormatStyle = ElclFormatStyle(text.lineSequence().filter { it.isNotBlank() }.associate { line ->
            val property = FormatProperty.entries.firstOrNull { it.path == line.substringBefore('=') }
                ?: error("Unknown formatting property: ${line.substringBefore('=')}")
            property to property.validate(line.substringAfter('='))
        })
    }
}

internal data class ElclFormatRule(val selector: ElclFormatSelector, val patch: Map<FormatProperty, String>)
internal data class ElclFormat(val base: ElclFormatStyle = ElclFormatStyle(), val rules: List<ElclFormatRule> = defaultRules()) {
    fun resolve(statement: ElclStatement): ElclFormatStyle {
        val matches = rules.mapIndexedNotNull { i, rule -> rule.selector.match(statement)?.let { Triple(it, i, rule) } }
            .sortedWith { a, b -> compareScore(a.first, b.first).takeIf { it != 0 } ?: a.second.compareTo(b.second) }
        return matches.fold(base) { style, match -> style.patched(match.third.patch) }
    }
}

private fun defaultRules() = listOf(
    ElclFormatRule(ElclFormatSelector(relativeSection = true), mapOf(FormatProperty.SECTION_INDENT to "6", FormatProperty.SECTION_SUFFIX to "4")),
    ElclFormatRule(ElclFormatSelector(metaValue = true), mapOf(
        FormatProperty.VALUE_CASE to "lower", FormatProperty.SEPARATOR_MODE to "spaces", FormatProperty.SEPARATOR_POSITION to "0",
    )),
)

internal fun compareScore(a: List<Int>, b: List<Int>): Int = a.indices.firstNotNullOfOrNull { i ->
    a[i].compareTo(b[i]).takeIf { it != 0 }
} ?: 0

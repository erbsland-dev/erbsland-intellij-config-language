package dev.erbsland.elcl.style

import com.intellij.application.options.CodeStyle
import dev.erbsland.elcl.ElclFile
import dev.erbsland.elcl.validation.SEMANTIC_IDENTIFIER_FIELDS
import dev.erbsland.elcl.validation.normalize
import java.util.Locale

/** The six regular-name forms offered by ELCL code-style settings. */
enum class ElclNameStyle(
    val letterCase: ElclNameCase,
    val separator: ElclWordSeparator,
    private val label: String,
) {
    LOWER_UNDERSCORE(ElclNameCase.LOWER, ElclWordSeparator.UNDERSCORE, "foo_bar"),
    TITLE_UNDERSCORE(ElclNameCase.TITLE, ElclWordSeparator.UNDERSCORE, "Foo_Bar"),
    UPPER_UNDERSCORE(ElclNameCase.UPPER, ElclWordSeparator.UNDERSCORE, "FOO_BAR"),
    LOWER_SPACE(ElclNameCase.LOWER, ElclWordSeparator.SPACE, "foo bar"),
    TITLE_SPACE(ElclNameCase.TITLE, ElclWordSeparator.SPACE, "Foo Bar"),
    UPPER_SPACE(ElclNameCase.UPPER, ElclWordSeparator.SPACE, "FOO BAR");

    override fun toString(): String = label

    companion object {
        fun fromStored(value: String): ElclNameStyle = entries.firstOrNull { it.name == value } ?: TITLE_SPACE

        fun fromParts(letterCase: ElclNameCase, separator: ElclWordSeparator): ElclNameStyle =
            entries.first { it.letterCase == letterCase && it.separator == separator }
    }
}

enum class ElclNameCase { LOWER, TITLE, UPPER }
enum class ElclWordSeparator(val text: String) { UNDERSCORE("_"), SPACE(" ") }
enum class ElclNameCategory { SECTION, VALUE, IDENTIFIER }

/** Effective naming preferences for one document. */
internal data class ElclDocumentNameStyles(
    val section: ElclNameStyle,
    val value: ElclNameStyle,
    val identifier: ElclNameStyle,
) {
    fun style(category: ElclNameCategory): ElclNameStyle = when (category) {
        ElclNameCategory.SECTION -> section
        ElclNameCategory.VALUE -> value
        ElclNameCategory.IDENTIFIER -> identifier
    }

    fun format(value: String, category: ElclNameCategory): String = formatElclName(value, style(category))

    fun formatPath(value: String): String = value.split('.').joinToString(".") { format(it, ElclNameCategory.IDENTIFIER) }
}

/** Formats a normalized logical identifier using [style]. */
internal fun formatElclName(value: String, style: ElclNameStyle): String {
    val words = normalize(value).split('_').filter(String::isNotEmpty)
    return words.joinToString(style.separator.text) { word ->
        when (style.letterCase) {
            ElclNameCase.LOWER -> word.lowercase(Locale.ROOT)
            ElclNameCase.UPPER -> word.uppercase(Locale.ROOT)
            ElclNameCase.TITLE -> if (word == "vr") "VR" else word.replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
    }
}

/** Resolves stored preferences and optional document-local style detection. */
internal object ElclNameStyleResolver {
    fun forFile(file: ElclFile): ElclDocumentNameStyles {
        val settings = CodeStyle.getCustomSettings(file, ElclCodeStyleSettings::class.java)
        return forText(file.text, settings)
    }

    fun forText(text: String, settings: ElclCodeStyleSettings): ElclDocumentNameStyles {
        val samples = collectSamples(text)
        return ElclDocumentNameStyles(
            resolve(samples.getValue(ElclNameCategory.SECTION), settings.sectionProfile()),
            resolve(samples.getValue(ElclNameCategory.VALUE), settings.valueProfile()),
            resolve(samples.getValue(ElclNameCategory.IDENTIFIER), settings.identifierProfile()),
        )
    }

    private fun resolve(samples: List<String>, profile: ElclNameStyleProfile): ElclNameStyle {
        val preferred = profile.style
        if (!profile.detectAutomatically) return preferred
        val detectedCase = confidentWinner(samples.mapNotNull(::detectCase), preferred.letterCase)
        val detectedSeparator = confidentWinner(samples.mapNotNull(::detectSeparator), preferred.separator)
        return ElclNameStyle.fromParts(detectedCase, detectedSeparator)
    }

    private fun <T> confidentWinner(samples: List<T>, fallback: T): T {
        if (samples.size < 3) return fallback
        val winner = samples.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return fallback
        return if (winner.value * 3 > samples.size * 2) winner.key else fallback
    }

    private fun detectCase(value: String): ElclNameCase? {
        val words = value.split(Regex("[ _]")).filter(String::isNotEmpty)
        val letters = value.filter(Char::isLetter)
        if (letters.isEmpty()) return null
        if (letters.all(Char::isLowerCase)) return ElclNameCase.LOWER
        if (letters.all(Char::isUpperCase)) return ElclNameCase.UPPER
        return ElclNameCase.TITLE.takeIf {
            words.all { word ->
                val cased = word.filter(Char::isLetter)
                cased.isEmpty() || cased.all(Char::isUpperCase) ||
                    (cased.first().isUpperCase() && cased.drop(1).all(Char::isLowerCase))
            }
        }
    }

    private fun detectSeparator(value: String): ElclWordSeparator? = when {
        '_' in value && ' ' !in value -> ElclWordSeparator.UNDERSCORE
        ' ' in value && '_' !in value -> ElclWordSeparator.SPACE
        else -> null
    }

    private fun collectSamples(text: String): Map<ElclNameCategory, List<String>> {
        val result = ElclNameCategory.entries.associateWith { mutableListOf<String>() }
        var sectionKind: String? = null
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.substringBefore('#').trim()
            parseSectionPath(line)?.let { path ->
                path.filterNot { it.startsWith('"') }.forEach(result.getValue(ElclNameCategory.SECTION)::add)
                sectionKind = path.lastOrNull()?.let(::normalize)
                return@forEach
            }
            val separator = findAssignmentSeparator(line)
            if (separator <= 0) return@forEach
            val rawName = line.substring(0, separator).trim()
            if (rawName.startsWith('@') || rawName.startsWith('"')) return@forEach
            result.getValue(ElclNameCategory.VALUE) += rawName
            val field = normalize(rawName)
            if (field !in SEMANTIC_IDENTIFIER_FIELDS) return@forEach
            if (field == "name" && sectionKind != "vr_key") return@forEach
            QUOTED_VALUE.findAll(line.substring(separator + 1)).forEach { match ->
                val identifier = match.groupValues[1]
                if (field in PATH_FIELDS) identifier.split('.').forEach(result.getValue(ElclNameCategory.IDENTIFIER)::add)
                else result.getValue(ElclNameCategory.IDENTIFIER) += identifier
            }
        }
        return result
    }

    private fun parseSectionPath(line: String): List<String>? {
        val undecorated = line.trim('-').trim()
        val start = when {
            undecorated.startsWith("*[") -> 2
            undecorated.startsWith('[') -> 1
            else -> return null
        }
        val close = if (undecorated.endsWith("]*")) 2 else if (undecorated.endsWith(']')) 1 else return null
        return undecorated.substring(start, undecorated.length - close).removePrefix(".").split('.').map(String::trim)
    }

    private fun findAssignmentSeparator(line: String): Int {
        var quoted = false
        line.forEachIndexed { index, char ->
            if (char == '"') quoted = !quoted
            if (!quoted && (char == ':' || char == '=')) return index
        }
        return -1
    }

    private val QUOTED_VALUE = Regex("\"([^\"]*)\"")
    private val PATH_FIELDS = setOf("key", "source", "target")
}

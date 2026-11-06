package dev.erbsland.elcl.format

import dev.erbsland.elcl.psi.ElclTypes
import dev.erbsland.elcl.syntax.decodeElclText
import java.nio.file.Files
import java.nio.file.Path

internal class ElclFormatConfigException(message: String) : IllegalArgumentException(message)

/** Reads Core's elcl_format.elcl schema without evaluating application data or placeholders. */
internal class ElclFormatConfig(private val readText: (Path) -> String = { Files.readString(it) }) {
    fun discover(source: Path): Path? {
        var directory: Path? = source.toAbsolutePath().normalize().parent
        while (directory != null) {
            val candidate = directory.resolve("elcl_format.elcl")
            if (Files.isRegularFile(candidate)) return candidate
            directory = directory.parent
        }
        return null
    }

    fun read(path: Path): ElclFormat {
        val base = linkedMapOf<FormatProperty, String>()
        val rules = mutableListOf<RuleBuilder>()
        val declared = mutableSetOf<String>()
        val stack = linkedSetOf<Path>()
        var rootSeen = false
        fun readFile(file: Path, depth: Int) {
            val canonical = file.toRealPath()
            require(depth <= 5) { "Configuration include nesting exceeds five documents" }
            require(stack.add(canonical)) { "Configuration include cycle at $file" }
            try {
                val snapshot = ElclSyntaxSnapshot(readText(file))
                var currentRule: RuleBuilder? = null
                for (statement in snapshot.statements) {
                    try {
                        require(!statement.damaged) { "Malformed configuration statement" }
                        if (statement.meta) {
                            when (statement.names.first().text.lowercase()) {
                                "@include" -> {
                                    val values = statement.values
                                    require(values.size == 1 && values.single().type == ElclTypes.TEXT) { "@include requires one text source" }
                                    val source = decodeElclText(values.single().text.removeSurrounding("\""))
                                    includeFiles(file, source).forEach { readFile(it, depth + 1) }
                                    currentRule = null
                                }
                                "@version" -> require(texts(statement) == listOf("1.0")) { "Unsupported ELCL version" }
                                "@features", "@signature" -> Unit
                                else -> error("Unknown configuration meta command")
                            }
                            continue
                        }
                        val parts = statement.path ?: error("Configuration statement has no section path")
                        require(parts.firstOrNull() == "elcl_format") { "Expected Elcl Format configuration" }
                        rootSeen = true
                        val relative = parts.drop(1)
                        if (statement.kind == "section") {
                            if (relative == listOf("rules") && statement.list) {
                                currentRule = RuleBuilder().also(rules::add)
                            } else {
                                require(!statement.list) { "Only Elcl Format.Rules is a section list" }
                                val isRule = relative.firstOrNull() == "rules"
                                val suffix = if (isRule) relative.drop(1) else relative
                                val valid = if (isRule) suffix == listOf("selector") || suffix.firstOrNull() == "style" && validStyleSection(suffix.drop(1))
                                    else suffix.isEmpty() || suffix.firstOrNull() == "base" && validStyleSection(suffix.drop(1))
                                require(valid) { "Unknown formatting section: ${parts.joinToString(".")}" }
                                val declarations = if (isRule) (currentRule ?: error("Rule sections require a preceding rule entry")).declared else declared
                                require(declarations.add(parts.joinToString("."))) { "Duplicate formatting section" }
                                if (isRule && suffix.firstOrNull() == "style") currentRule!!.hasStyle = true
                            }
                            continue
                        }
                        when (relative.firstOrNull()) {
                            "base" -> addStyle(base, relative.drop(1).joinToString("."), statement)
                            "rules" -> {
                                val rule = currentRule ?: error("Rule fields require a preceding rule entry")
                                when (relative.getOrNull(1)) {
                                    "style" -> { rule.hasStyle = true; addStyle(rule.style, relative.drop(2).joinToString("."), statement) }
                                    "selector" -> {
                                        require(relative.size == 3 && rule.selector.putIfAbsent(relative[2], statement) == null) { "Invalid or duplicate selector property" }
                                    }
                                    else -> error("Expected Selector or Style inside a rule")
                                }
                            }
                            else -> error("Unknown formatting property: ${parts.joinToString(".")}")
                        }
                    } catch (e: IllegalArgumentException) {
                        throw ElclFormatConfigException("$file:${snapshot.text.take(statement.range.startOffset).count { it == '\n' } + 1}: ${e.message}")
                    } catch (e: IllegalStateException) {
                        throw ElclFormatConfigException("$file:${snapshot.text.take(statement.range.startOffset).count { it == '\n' } + 1}: ${e.message}")
                    }
                }
            } finally { stack.remove(canonical) }
        }
        try {
            readFile(path, 0)
            require(rootSeen) { "Missing Elcl Format section" }
            val defaults = ElclFormat()
            return ElclFormat(ElclFormatStyle(base), defaults.rules + rules.map { it.build() })
        } catch (e: ElclFormatConfigException) { throw e }
        catch (e: java.io.IOException) { throw ElclFormatConfigException("$path: ${e.message}") }
        catch (e: IllegalArgumentException) { throw ElclFormatConfigException("$path: ${e.message}") }
    }

    private fun includeFiles(file: Path, source: String): List<Path> {
        require(!source.contains(':') || source.startsWith("file:") || Regex("^[A-Za-z]:[/\\\\].*").matches(source)) { "Only local configuration includes are supported" }
        val path = file.parent.resolve(source.removePrefix("file:")).normalize()
        if ('*' !in path.toString()) { require(Files.isRegularFile(path)) { "Included file does not exist: $path" }; return listOf(path) }
        val elements = path.map { it.toString() }
        require(elements.dropLast(1).none { '*' in it && it != "**" }) { "Include wildcards are allowed only in the filename or a ** directory" }
        var root = path.root
        for (element in elements.takeWhile { '*' !in it }) root = root.resolve(element)
        val relative = root.relativize(path).map { it.toString() }
        fun matches(parts: List<String>, i: Int = 0, j: Int = 0): Boolean = when {
            i == relative.size -> j == parts.size
            relative[i] == "**" -> matches(parts, i + 1, j) || j < parts.size && matches(parts, i, j + 1)
            j == parts.size -> false
            else -> globRegex(relative[i]).matches(parts[j]) && matches(parts, i + 1, j + 1)
        }
        val found = Files.walk(root).use { files -> files.filter { Files.isRegularFile(it) && matches(root.relativize(it).map(Path::toString)) }.sorted().toList() }
        require(found.isNotEmpty()) { "Include source matches no files: $source" }
        return found
    }
}

private fun validStyleSection(parts: List<String>): Boolean = parts.isEmpty() ||
    parts.size == 1 && parts.single() in setOf("names", "section", "vector", "matrix", "separator", "indentation")

private fun addStyle(target: MutableMap<FormatProperty, String>, path: String, statement: ElclStatement) {
    val property = FormatProperty.entries.firstOrNull { it.path == path } ?: error("Unknown formatting property: $path")
    require(property !in target) { "Duplicate formatting property: $path" }
    val value = statement.values.singleOrNull() ?: error("$path requires a scalar value")
    val text = when {
        property.choices == listOf("true", "false") -> boolean(statement).toString()
        property.choices.isNotEmpty() -> texts(statement).single()
        value.type == ElclTypes.TEXT && property in listOf(FormatProperty.SECTION_INDENT, FormatProperty.SECTION_SUFFIX) -> texts(statement).single()
        else -> integer(statement).toString()
    }
    target[property] = property.validate(text)
}

private class RuleBuilder {
    val declared = mutableSetOf<String>()
    var hasStyle = false
    val style = linkedMapOf<FormatProperty, String>()
    val selector = linkedMapOf<String, ElclStatement>()
    fun build(): ElclFormatRule {
        require(hasStyle) { "Every formatting rule requires a Style section" }
        require(selector.keys.all { it in setOf("kind", "list_section", "relative_section", "section_names_more_than", "meta_value", "paths", "value_types") }) { "Unknown selector property" }
        val kind = selector["kind"]?.let { texts(it).single().identifier() }
        require(kind == null || kind in listOf("section", "assignment")) { "Invalid selector kind" }
        val types = selector["value_types"]?.let(::texts)?.map(String::identifier).orEmpty()
        require(types.all { it in setOf("integer", "boolean", "float", "text", "date", "time", "date_time", "bytes", "time_delta", "regex", "value_list") }) { "Invalid selector value type" }
        return ElclFormatRule(ElclFormatSelector(kind,
            selector["list_section"]?.let(::boolean), selector["relative_section"]?.let(::boolean),
            selector["section_names_more_than"]?.let(::integer), selector["meta_value"]?.let(::boolean),
            selector["paths"]?.let(::texts)?.map(::ElclPathPattern).orEmpty(), types), style)
    }
}

private fun texts(statement: ElclStatement): List<String> {
    require(statement.multilineHeader == null && statement.values.isNotEmpty() && statement.values.all { it.type == ElclTypes.TEXT }) { "Expected text or a text list" }
    return statement.values.map { decodeElclText(it.text.removeSurrounding("\"")) }
}
private fun boolean(statement: ElclStatement): Boolean {
    val value = statement.values.singleOrNull()
    require(value?.type == ElclTypes.BOOLEAN) { "Expected a boolean" }
    return value!!.text.lowercase() in setOf("yes", "on", "enabled", "true")
}
private fun integer(statement: ElclStatement): Int {
    val value = statement.values.singleOrNull()
    require(value?.type == ElclTypes.INTEGER) { "Expected a nonnegative integer" }
    val text = value!!.text.replace("'", "").removePrefix("+")
    val number = when {
        text.startsWith("0x", true) -> text.drop(2).toLongOrNull(16)
        text.startsWith("0b", true) -> text.drop(2).toLongOrNull(2)
        else -> text.toLongOrNull()
    }
    require(number != null && number in 0..Int.MAX_VALUE.toLong()) { "Expected a nonnegative integer within the supported range" }
    return number.toInt()
}
private fun String.identifier(): String = lowercase(java.util.Locale.ROOT).replace(' ', '_')

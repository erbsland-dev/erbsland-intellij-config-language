package dev.erbsland.elcl.format

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class ElclFormatConfigTest {
    private fun directory(test: (Path) -> Unit) {
        val root = Files.createTempDirectory("elcl-format-test")
        try { test(root) } finally { root.toFile().deleteRecursively() }
    }
    private fun write(root: Path, name: String, text: String): Path = root.resolve(name).also { Files.createDirectories(it.parent); Files.writeString(it, text) }

    @Test fun `nearest configuration replaces ancestor and IDE defaults`() = directory { root ->
        write(root, "elcl_format.elcl", "[Elcl Format.Base]\nMaximum Line Length: 80\n")
        val nearest = write(root, "nested/elcl_format.elcl", "[Elcl Format.Base.Names]\nValue Case: \"upper\"\n")
        val input = write(root, "nested/deep/input.elcl", "[a]\nx:1\n")
        val reader = ElclFormatConfig()
        assertEquals(nearest, reader.discover(input))
        val format = reader.read(nearest)
        assertEquals("118", format.base[FormatProperty.WIDTH])
        assertEquals("upper", format.base[FormatProperty.VALUE_CASE])
    }
    @Test fun `Core schema accepts all style properties and rule selectors`() = directory { root ->
        val text = buildString {
            val groups = FormatProperty.entries.groupBy { it.path.substringBeforeLast('.', "") }
            for ((group, properties) in groups) {
                append("[Elcl Format.Base${if (group.isEmpty()) "" else ".$group"}]\n")
                for (property in properties) {
                    val value = property.default
                    val quote = value.toIntOrNull() == null && value !in listOf("true", "false")
                    append(property.path.substringAfterLast('.')).append(": ").append(if (quote) "\"$value\"" else value).append('\n')
                }
            }
            append("*[Elcl Format.Rules]\n[.Selector]\nKind: \"assignment\"\nMeta Value: no\nPaths: \"a.**.x\", \"a.y\"\nValue Types: \"integer\", \"float\"\n[.Style.Names]\nValue Case: \"upper\"\n")
        }
        val file = write(root, "elcl_format.elcl", text)
        val format = ElclFormatConfig().read(file)
        assertEquals(3, format.rules.size)
        assertEquals("upper", format.resolve(ElclSyntaxSnapshot("[a]\nx:1\n").statements.last())[FormatProperty.VALUE_CASE])
        for (property in FormatProperty.entries) assertEquals(property.default, format.base[property])
    }
    @Test fun `local includes retain independent section context and reload changes`() = directory { root ->
        val child = write(root, "names.elcl", "[Elcl Format.Base.Names]\nValue Case: \"upper\"\n")
        val file = write(root, "elcl_format.elcl", "@include: \"names.elcl\"\n[Elcl Format.Base]\nMaximum Line Length: 90\n")
        val reader = ElclFormatConfig()
        assertEquals("upper", reader.read(file).base[FormatProperty.VALUE_CASE])
        Files.writeString(child, "[Elcl Format.Base.Names]\nValue Case: \"lower\"\n")
        assertEquals("lower", reader.read(file).base[FormatProperty.VALUE_CASE])
        val unsaved = ElclFormatConfig { path -> if (path == child) "[Elcl Format.Base.Names]\nValue Case: \"keep\"\n" else Files.readString(path) }
        assertEquals("keep", unsaved.read(file).base[FormatProperty.VALUE_CASE])
    }
    @Test fun `recursive include glob matches zero or more directories`() = directory { root ->
        write(root, "styles/names.elcl", "[Elcl Format.Base.Names]\nValue Case: \"upper\"\n")
        write(root, "styles/deep/section.elcl", "[Elcl Format.Base.Section]\nSuffix: 2\n")
        val file = write(root, "elcl_format.elcl", "@include: \"styles/**/*.elcl\"\n")
        val format = ElclFormatConfig().read(file)
        assertEquals("upper", format.base[FormatProperty.VALUE_CASE]); assertEquals("2", format.base[FormatProperty.SECTION_SUFFIX])
    }
    @Test fun `invalid configs fail before producing any edits`() = directory { root ->
        for (text in listOf(
            "[Other]\nx:1\n", "[Elcl Format.Base]\nUnknown:1\n", "[Elcl Format.Base]\nMaximum Line Length:0\n",
            "[Elcl Format.Base.Section]\nIndent:\"forever\"\n", "[Elcl Format.Base]\nValue Wrapping:\"bad\"\n",
            "*[Elcl Format.Rules]\n[.Selector]\nPaths:\"a.***\"\n[.Style]\nMaximum Line Length:80\n",
            "*[Elcl Format.Rules]\n[.Selector]\nPaths:\"a\"\n", "[Elcl Format.Base]\nMaximum Line Length:80,90\n",
            "@include:\"missing.elcl\"\n", "[Elcl Format.Base]\nx:???\n",
            "[Elcl Format.Base]\nMaximum Line Length:80\nMaximum Line Length:90\n",
        )) {
            val file = write(root, "elcl_format.elcl", text)
            assertThrows(text, ElclFormatConfigException::class.java) { ElclFormatConfig().read(file) }
        }
        val file = write(root, "elcl_format.elcl", "@include:\"elcl_format.elcl\"\n")
        assertThrows(ElclFormatConfigException::class.java) { ElclFormatConfig().read(file) }
    }
}

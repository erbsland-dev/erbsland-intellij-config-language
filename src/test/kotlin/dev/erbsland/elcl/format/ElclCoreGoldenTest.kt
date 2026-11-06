package dev.erbsland.elcl.format

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Files
import java.nio.file.Path

/** Outputs recorded from Core itself, without a Core runtime dependency in tests. */
@RunWith(Parameterized::class)
class ElclCoreGoldenTest(private val case: String) {
    @Test fun matchesCoreOutput() {
        val root = Path.of(javaClass.getResource("/format-core/$case/input.elcl")!!.toURI()).parent
        val source = Files.readString(root.resolve("input.elcl"))
        val coreOutput = Files.readString(root.resolve("expected.elcl"))
        // Core currently adds one space before each multiline regex/bytes comment on every pass.
        // Preserve the input spacing instead: reproducing that bug would violate idempotence.
        // Keep the captured Core files intact so this deliberate difference remains reviewable.
        val expected = if (case.startsWith("multiline-")) coreOutput
            .replace("[a-z]+  # comment", "[a-z]+ # comment")
            .replace("01 02  # bytes", "01 02 # bytes") else coreOutput
        val style = ElclFormatConfig().read(root.resolve("elcl_format.elcl"))
        val actual = ElclFormatter().format(source, style).text
        assertEquals(case, expected, actual)
        assertEquals("$case idempotence", actual, ElclFormatter().format(actual, style).text)
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): List<String> = ElclCoreGoldenTest::class.java.getResource("/format-core/cases.txt")!!.readText().lineSequence().filter { it.isNotEmpty() }.toList()
    }
}

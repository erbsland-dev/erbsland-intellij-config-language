package dev.erbsland.elcl.style

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.util.Disposer
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import dev.erbsland.elcl.ElclFileType
import dev.erbsland.elcl.format.*
import dev.erbsland.elcl.highlight.ElclSyntaxHighlighter
import dev.erbsland.elcl.settings.ElclSettings
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/** One level of aspect tabs, each with a padded form and a matching ELCL editor preview. */
internal class ElclFormattingPanel : JPanel(BorderLayout()) {
    private data class Binding(val component: JComponent, val read: () -> String, val write: (String) -> Unit)
    private data class Choice(val value: String, val label: String) { override fun toString() = label }
    private data class Completion(val style: JComboBox<ElclNameStyle>, val detect: JCheckBox)
    private data class Preview(val editor: EditorTextField, val example: String?, val error: JLabel)

    private val lifetime = Disposer.newDisposable("ELCL style previews")
    private val controls = linkedMapOf<FormatProperty, Binding>()
    private val labels = mutableMapOf<FormatProperty, JLabel>()
    private val previews = mutableListOf<Preview>()
    private val detectSections = JBCheckBox("Detect section naming from the document")
    private val detectValues = JBCheckBox("Detect value naming from the document")
    private val detectIdentifiers = JBCheckBox("Detect text identifier naming from the document")
    private val loadConfiguration = JBCheckBox("Load style from elcl_format.elcl files (IDE-wide)")
    private val completionSections = completion()
    private val completionValues = completion()
    private val completionIdentifiers = completion()
    private val tabs = JBTabbedPane()
    private var resetting = false

    init {
        val header = JPanel(BorderLayout(0, JBUI.scale(4))).apply {
            border = JBUI.Borders.empty(12, 12, 16, 12)
            add(loadConfiguration, BorderLayout.NORTH)
            add(note("When enabled, the file's directory and all parent directories are scanned for elcl_format.elcl files.<br>" +
                "The nearest file overrides this scheme and may load local includes. Disabled by default."), BorderLayout.CENTER)
        }
        add(header, BorderLayout.NORTH)
        add(tabs, BorderLayout.CENTER)
        tab("Layout", ElclStyleExamples.layout, "Try wrapping and alignment. Whitespace markers show continuation indentation.") {
            group("Line width and wrapping")
            property(FormatProperty.WIDTH)
            property(FormatProperty.WRAPPING)
            hint("Values move as a whole; quoted text is never split.")
            group("Assignments")
            property(FormatProperty.SEPARATOR)
            property(FormatProperty.SEPARATOR_MODE, "Position separator by")
            property(FormatProperty.SEPARATOR_POSITION)
            property(FormatProperty.VALUE_MODE, "Position value by")
            property(FormatProperty.VALUE_POSITION)
            hint("Column positions start at zero.")
            group("Continuation indentation")
            property(FormatProperty.INDENT_STYLE, "Indent using")
            property(FormatProperty.INDENT_COUNT, "Indent size")
        }
        tab("Names", ElclStyleExamples.names, "Try a case and word separator. File names and paths keep their punctuation.") {
            group("Section names")
            property(FormatProperty.SECTION_CASE, "Case")
            property(FormatProperty.SECTION_SEPARATOR, "Between words")
            check(detectSections)
            group("Value names")
            property(FormatProperty.VALUE_CASE, "Case")
            property(FormatProperty.VALUE_SEPARATOR, "Between words")
            check(detectValues)
            group("Quoted names")
            property(FormatProperty.TEXT_NAME_CASE, "Case")
            property(FormatProperty.TEXT_NAME_SEPARATOR, "Between words")
            hint("Changing quoted names can change case-sensitive keys.")
            group("Identifiers inside text values")
            property(FormatProperty.TEXT_IDENTIFIER_CASE, "Case")
            property(FormatProperty.TEXT_IDENTIFIER_SEPARATOR, "Between words")
            check(detectIdentifiers)
            hint("Only whole identifiers such as \"primary_server\" change.<br>Automatic detection needs at least three matching examples.")
        }
        tab("Sections", ElclStyleExamples.sections, "Try bracket spacing, path dots, decorations and closing stars on section lists.") {
            group("Decoration")
            property(FormatProperty.SECTION_INDENT, "Opening bracket column")
            property(FormatProperty.SECTION_SUFFIX, "Suffix width")
            hint("Fill on the left right-aligns the header.<br>Fill on both sides centers it within the line width.")
            property(FormatProperty.SECTION_STAR)
            group("Spacing")
            property(FormatProperty.SPACE_AFTER_OPEN)
            property(FormatProperty.SPACE_BEFORE_CLOSE)
            property(FormatProperty.SPACE_AROUND_DOT)
            hint("Relative sections keep Core's default decoration:<br>bracket at column 6 and suffix width 4.")
        }
        tab("Lists", ElclStyleExamples.lists, "Try compact or one-item-per-line lists, then align the matrix columns.") {
            group("Lists")
            property(FormatProperty.VECTOR, "Layout")
            property(FormatProperty.BULLET_SPACES)
            group("Matrices")
            property(FormatProperty.MATRIX, "Column alignment")
            hint("Alignment uses the line width from Layout.<br>Rows with comments keep their structure.")
        }
        tab("Completion", null, "These are example completion suggestions using your preferred spelling.") {
            hint("Completion spelling is independent of Reformat Code.<br>Automatic detection uses existing names when enough agree;<br>the preview shows the fallback spelling.")
            group("Section names")
            row("Preferred spelling", completionSections.style)
            check(completionSections.detect)
            group("Value names")
            row("Preferred spelling", completionValues.style)
            check(completionValues.detect)
            group("Identifiers in text")
            row("Preferred spelling", completionIdentifiers.style)
            check(completionIdentifiers.detect)
        }
    }

    private fun tab(title: String, example: String?, description: String, build: Form.() -> Unit) {
        val form = Form().apply(build)
        form.finish()
        val editor = EditorTextField(EditorFactory.getInstance().createDocument(""), null, ElclFileType, true, false).apply {
            name = "elcl.preview.$title"
            setDisposedWith(lifetime)
            setFontInheritedFromLAF(false)
            addSettingsProvider { ex ->
                // Settings can be opened without a project; EditorTextField otherwise skips its highlighter.
                ex.highlighter = LexerEditorHighlighter(ElclSyntaxHighlighter(), ex.colorsScheme)
                ex.settings.isLineNumbersShown = true
                ex.settings.isWhitespacesShown = true
                ex.settings.isUseSoftWraps = false
                ex.settings.isRightMarginShown = true
                ex.settings.setRightMargin(runCatching { style().number(FormatProperty.WIDTH) }.getOrDefault(118))
                ex.setVerticalScrollbarVisible(true)
                ex.setHorizontalScrollbarVisible(true)
            }
        }
        val error = JBLabel().apply { isVisible = false }
        previews += Preview(editor, example, error)
        val preview = JPanel(BorderLayout(0, JBUI.scale(8))).apply {
            border = JBUI.Borders.empty(12)
            add(note(description), BorderLayout.NORTH)
            add(editor, BorderLayout.CENTER)
            add(error, BorderLayout.SOUTH)
        }
        val scroll = JBScrollPane(form).apply {
            border = JBUI.Borders.empty()
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBar.unitIncrement = JBUI.scale(16)
        }
        tabs.addTab(title, JBSplitter(false, 0.43f).apply {
            firstComponent = scroll
            secondComponent = preview
        })
    }

    private fun completion() = Completion(JComboBox(ElclNameStyle.entries.toTypedArray()).apply {
        addActionListener { updatePreview() }
    }, JBCheckBox("Detect automatically").apply { addActionListener { updatePreview() } })

    private fun binding(property: FormatProperty): Binding {
        val binding = when {
            property.choices == listOf("true", "false") -> {
                val box = JBCheckBox(property.label).apply { addActionListener { updatePreview() } }
                Binding(box, { box.isSelected.toString() }, { box.isSelected = it == "true" })
            }
            property.choices.isNotEmpty() -> {
                val combo = JComboBox(property.choices.map { Choice(it, choiceLabel(property, it)) }.toTypedArray()).apply {
                    addActionListener { updatePreview() }
                }
                Binding(combo, { (combo.selectedItem as Choice).value }, { value ->
                    combo.selectedItem = (0 until combo.itemCount).map(combo::getItemAt).first { it.value == value }
                })
            }
            else -> {
                val text = JTextField(property.default, 6).apply {
                    document.addDocumentListener(object : DocumentListener {
                        override fun insertUpdate(e: DocumentEvent) = updatePreview()
                        override fun removeUpdate(e: DocumentEvent) = updatePreview()
                        override fun changedUpdate(e: DocumentEvent) = updatePreview()
                    })
                }
                if (property in listOf(FormatProperty.SECTION_INDENT, FormatProperty.SECTION_SUFFIX)) {
                    val fill = JBCheckBox("Fill available width").apply {
                        addActionListener { text.isEnabled = !isSelected; updatePreview() }
                    }
                    val panel = JPanel(BorderLayout(JBUI.scale(8), 0)).apply { add(text, BorderLayout.WEST); add(fill, BorderLayout.CENTER) }
                    Binding(panel, { if (fill.isSelected) "infinite" else text.text.trim() }, { value ->
                        fill.isSelected = value == "infinite"
                        text.text = if (value == "infinite") "0" else value
                        text.isEnabled = !fill.isSelected
                    })
                } else Binding(text, { text.text.trim() }, { text.text = it })
            }
        }
        binding.component.name = property.path
        controls[property] = binding
        binding.write(property.default)
        return binding
    }

    private fun style() = ElclFormatStyle(controls.mapValues { (property, binding) -> property.validate(binding.read()) })

    fun isModified(settings: ElclCodeStyleSettings): Boolean = runCatching {
        style().serialize() != ElclFormatStyle.deserialize(settings.FORMAT_STYLE).serialize() ||
            detectSections.isSelected != settings.FORMAT_DETECT_SECTION_NAMES ||
            detectValues.isSelected != settings.FORMAT_DETECT_VALUE_NAMES ||
            detectIdentifiers.isSelected != settings.FORMAT_DETECT_IDENTIFIERS ||
            selected(completionSections) != settings.SECTION_NAME_STYLE || completionSections.detect.isSelected != settings.DETECT_SECTION_NAME_STYLE ||
            selected(completionValues) != settings.VALUE_NAME_STYLE || completionValues.detect.isSelected != settings.DETECT_VALUE_NAME_STYLE ||
            selected(completionIdentifiers) != settings.IDENTIFIER_STYLE || completionIdentifiers.detect.isSelected != settings.DETECT_IDENTIFIER_STYLE ||
            loadConfiguration.isSelected != ElclSettings.getInstance().state.loadFormattingConfiguration
    }.getOrDefault(true)

    fun apply(settings: ElclCodeStyleSettings) {
        settings.FORMAT_STYLE = try { style().serialize() } catch (e: IllegalArgumentException) {
            throw ConfigurationException(e.message ?: "Invalid ELCL style")
        }
        settings.FORMAT_DETECT_SECTION_NAMES = detectSections.isSelected
        settings.FORMAT_DETECT_VALUE_NAMES = detectValues.isSelected
        settings.FORMAT_DETECT_IDENTIFIERS = detectIdentifiers.isSelected
        settings.SECTION_NAME_STYLE = selected(completionSections)
        settings.DETECT_SECTION_NAME_STYLE = completionSections.detect.isSelected
        settings.VALUE_NAME_STYLE = selected(completionValues)
        settings.DETECT_VALUE_NAME_STYLE = completionValues.detect.isSelected
        settings.IDENTIFIER_STYLE = selected(completionIdentifiers)
        settings.DETECT_IDENTIFIER_STYLE = completionIdentifiers.detect.isSelected
        ElclSettings.getInstance().state.loadFormattingConfiguration = loadConfiguration.isSelected
    }

    fun reset(settings: ElclCodeStyleSettings) {
        resetting = true
        try {
            val style = ElclFormatStyle.deserialize(settings.FORMAT_STYLE)
            controls.forEach { (key, binding) -> binding.write(style[key]) }
            detectSections.isSelected = settings.FORMAT_DETECT_SECTION_NAMES
            detectValues.isSelected = settings.FORMAT_DETECT_VALUE_NAMES
            detectIdentifiers.isSelected = settings.FORMAT_DETECT_IDENTIFIERS
            resetCompletion(completionSections, settings.SECTION_NAME_STYLE, settings.DETECT_SECTION_NAME_STYLE)
            resetCompletion(completionValues, settings.VALUE_NAME_STYLE, settings.DETECT_VALUE_NAME_STYLE)
            resetCompletion(completionIdentifiers, settings.IDENTIFIER_STYLE, settings.DETECT_IDENTIFIER_STYLE)
            loadConfiguration.isSelected = ElclSettings.getInstance().state.loadFormattingConfiguration
        } finally { resetting = false }
        updatePreview()
    }

    private fun selected(completion: Completion) = (completion.style.selectedItem as ElclNameStyle).name
    private fun resetCompletion(completion: Completion, style: String, detect: Boolean) {
        completion.style.selectedItem = ElclNameStyle.fromStored(style)
        completion.detect.isSelected = detect
    }

    fun dispose() = Disposer.dispose(lifetime)

    private fun updatePreview() {
        if (resetting || controls.size != FormatProperty.entries.size) return
        val temporary = ElclCodeStyleSettings(CodeStyleSettingsManager.getInstance().createSettings())
        val result = runCatching { style() }
        val format = result.getOrNull()
        if (format != null) {
            temporary.FORMAT_STYLE = format.serialize()
            temporary.FORMAT_DETECT_SECTION_NAMES = detectSections.isSelected
            temporary.FORMAT_DETECT_VALUE_NAMES = detectValues.isSelected
            temporary.FORMAT_DETECT_IDENTIFIERS = detectIdentifiers.isSelected
            labels[FormatProperty.SEPARATOR_POSITION]?.text = if (format[FormatProperty.SEPARATOR_MODE] == "column") "Separator column:" else "Spaces after name:"
            labels[FormatProperty.VALUE_POSITION]?.text = if (format[FormatProperty.VALUE_MODE] == "column") "Value column:" else "Spaces after separator:"
            listOf(FormatProperty.TEXT_NAME_CASE to FormatProperty.TEXT_NAME_SEPARATOR,
                FormatProperty.TEXT_IDENTIFIER_CASE to FormatProperty.TEXT_IDENTIFIER_SEPARATOR).forEach { (case, separator) ->
                controls.getValue(separator).component.isEnabled = format[case] != "keep"
            }
        }
        previews.forEach { preview ->
            val rendered = if (preview.example == null) Result.success(completionPreview())
                else result.mapCatching { ElclFormatter().format(preview.example, temporary.formatFor(preview.example)).text }
            rendered.onSuccess { text ->
                if (preview.editor.text != text) preview.editor.text = text
                preview.editor.editor?.settings?.setRightMargin(format?.number(FormatProperty.WIDTH) ?: 118)
            }
            preview.error.text = rendered.exceptionOrNull()?.message.orEmpty()
            preview.error.isVisible = rendered.isFailure
        }
    }

    private fun completionPreview(): String {
        fun spell(name: String, completion: Completion) = formatElclName(name, completion.style.selectedItem as ElclNameStyle)
        return "# Section name suggestion\n[${spell("server_settings", completionSections)}]\n\n" +
            "# Value name suggestion\n${spell("connection_timeout", completionValues)}: 30s\n\n" +
            "# Identifier suggestion inside text\nType: \"${spell("secure_socket", completionIdentifiers)}\"\n"
    }

    private inner class Form : JPanel(GridBagLayout()) {
        private var row = 0
        init { border = JBUI.Borders.empty(12); minimumSize = Dimension(0, 0) }
        fun group(title: String) {
            val label = JBLabel(title).apply { font = font.deriveFont(Font.BOLD) }
            add(label, constraints(0, row++).apply { gridwidth = 2; insets = JBUI.insets(if (row <= 1) 0 else 18, 0, 8, 0) })
        }
        fun property(property: FormatProperty, title: String = property.label) {
            val binding = binding(property)
            if (binding.component is JCheckBox) check(binding.component)
            else labels[property] = row(title, binding.component)
        }
        fun row(title: String, component: JComponent): JLabel {
            val label = JBLabel("$title:").apply { labelFor = component }
            add(label, constraints(0, row))
            add(component, constraints(1, row++).apply { weightx = 1.0; fill = GridBagConstraints.HORIZONTAL })
            component.accessibleContext?.accessibleName = title
            return label
        }
        fun check(box: JCheckBox) {
            add(box, constraints(0, row++).apply { gridwidth = 2 })
            box.addActionListener { updatePreview() }
        }
        fun hint(text: String) { add(note(text), constraints(0, row++).apply { gridwidth = 2; fill = GridBagConstraints.HORIZONTAL }) }
        fun finish() { add(JPanel(), constraints(0, row).apply { gridwidth = 2; weighty = 1.0; fill = GridBagConstraints.BOTH }) }
    }

    private fun constraints(x: Int, y: Int) = GridBagConstraints().apply {
        gridx = x; gridy = y; anchor = GridBagConstraints.WEST; insets = JBUI.insets(4, 0, 4, if (x == 0) 12 else 0)
    }
    private fun note(text: String) = JBLabel("<html>$text</html>").apply { foreground = com.intellij.util.ui.UIUtil.getContextHelpForeground() }

    private fun choiceLabel(property: FormatProperty, value: String): String = when (property) {
        FormatProperty.WRAPPING -> when (value) {
            "keep" -> "Keep existing line breaks"
            "same_line" -> "Keep values on the assignment line"
            "next_line" -> "Always move values to the next line"
            "next_line_if_fits" -> "Wrap long lines if the value will fit"
            else -> "Wrap all long lines"
        }
        FormatProperty.SEPARATOR_MODE -> if (value == "column") "Fixed column" else "Spaces after name"
        FormatProperty.VALUE_MODE -> if (value == "column") "Fixed column" else "Spaces after separator"
        FormatProperty.VECTOR -> when (value) { "keep" -> "Keep existing layout"; "compact" -> "One line"; else -> "One item per line" }
        FormatProperty.MATRIX -> if (value == "keep") "Keep existing spacing" else "Align columns"
        FormatProperty.SEPARATOR -> if (value == ":") "Colon (:)" else "Equals (=)"
        else -> when (value) {
            "keep" -> "Keep original"
            "lower" -> "lower case"
            "title" -> "Title Case"
            "upper" -> "UPPER CASE"
            "space" -> "Spaces (two words)"
            "underscore" -> "Underscores (two_words)"
            "spaces" -> "Spaces"
            "tabs" -> "Tabs"
            else -> value
        }
    }
}

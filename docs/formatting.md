# ELCL formatting and placeholders

Use the IDE's **Reformat Code** action for a whole ELCL file or a selection. Selection formatting includes the complete statements touched by the selection. Formatting participates in normal undo/redo. It preserves comments, blank lines, literal spelling, multiline payload content, and the document's final-newline convention. Damaged statements remain unchanged while valid surrounding statements can be formatted.

## IDE preferences

Open **Editor | Code Style | Erbsland Configuration Language**. Each tab has padded controls on the left and a matching, syntax-highlighted example on the right. The examples update immediately as settings change; whitespace markers make indentation visible.

| Tab | What to try in the example |
| --- | --- |
| Layout | Change the assignment character or column, then choose **Always move values to the next line**. Compare short and long messages, and change continuation indentation. |
| Names | Choose **UPPER CASE** for value names or **Underscores (two_words)** for section names. Quoted names and text identifiers stay unchanged until their own case setting is enabled. |
| Sections | Toggle bracket and path-dot spacing, the closing section-list star, or **Fill available width**. Filling both sides centers absolute section headers. |
| Lists | Choose **One item per line** to turn the ports into bullet entries, or **One line** to compact the hosts. Choose **Align columns** to line up the matrix's different-width numbers. |
| Completion | Change the preferred spelling of section names, value names, and text identifiers. The example shows fallback completion suggestions, independently of formatting preferences. |

Column positions start at zero. Relative-section decorations retain Core's default rule; this is explained on the Sections tab. Invalid partial numeric input keeps the last valid preview visible and must be corrected before applying.

## Configuration files

Configuration-file loading is **disabled by default**, including when upgrading from 0.6.0. Without opting in, formatting uses the active IDE/project code-style scheme and does not scan directories or read formatting configurations or their includes.

Enable **Load style from elcl_format.elcl files (IDE-wide)** at the top of the Code Style page to allow discovery. The checkbox note explains that the file's directory and all parent directories are scanned for `elcl_format.elcl` files. Discovery stops at the nearest match. This permission is stored in IDE preferences, separately from project code-style schemes, and takes effect when you apply the settings.

When enabled, the nearest file takes precedence over IDE formatting preferences. Ancestor configurations are not merged. Unspecified properties use Core defaults, so the same file produces the same style regardless of each user's IDE settings. Files without a configuration use the active IDE/project code-style scheme. Settings previews always show the scheme being edited and never load configuration files.

Local `@include` commands are supported, including filename wildcards and whole-directory `**` patterns. Includes are processed in sorted order and limited to five nested documents. Placeholders are not expanded in formatting configurations. Unsaved configuration and included-file edits are used on the next format action. Invalid or unreadable configuration stops formatting and reports the problem without changing the document.

```elcl
[Elcl Format.Base]
Maximum Line Length: 118
Value Wrapping: "next_line_if_fits"

[.Names]
Section Case: "title"
Section Separator: "space"
Value Case: "title"
Value Separator: "space"
Text Name Case: "keep"
Text Identifier Case: "keep"

[.Section]
Indent: 4
Suffix: "infinite"
Star: Yes
Space After Open: Yes
Space Before Close: Yes
Space Around Dot: Yes

[.Separator]
Character: ":"
Position Mode: "column"
Position: 29
Value Position Mode: "spaces"
Value Position: 1

[.Indentation]
Style: "spaces"
Count: 4

[.Vector]
Style: "keep"
Spaces After Star: 1

[.Matrix]
Style: "keep"

*[Elcl Format.Rules]
[.Selector]
Paths: "schema.**.type"
[.Style.Names]
Text Identifier Case: "title"
Text Identifier Separator: "space"
```

The optional child sections beneath Base and each rule's Style accept these settings:

| Group | Properties and accepted values |
| --- | --- |
| Base / Style | `Maximum Line Length`: positive integer. `Value Wrapping`: `keep`, `same_line`, `next_line`, `next_line_if_fits`, `when_too_long`. |
| Names | `Section Case`, `Value Case`, `Text Name Case`, `Text Identifier Case`: `keep`, `lower`, `title`, `upper`. Each has a corresponding `... Separator`: `space` or `underscore`. |
| Section | `Indent`, `Suffix`: nonnegative integers or `"infinite"`. `Star`, `Space After Open`, `Space Before Close`, `Space Around Dot`: booleans. |
| Vector | `Style`: `keep`, `compact`, `multiline`. `Spaces After Star`: positive integer. |
| Matrix | `Style`: `keep`, `align`. |
| Separator | `Character`: `":"` or `"="`. `Position Mode`, `Value Position Mode`: `spaces` or `column`. `Position`, `Value Position`: nonnegative integers. |
| Indentation | `Style`: `spaces` or `tabs`. `Count`: positive integer. |

Line width is a soft limit measured in Unicode code points; a tab counts as one. Wrapping moves complete values and never splits literals. `next_line_if_fits` moves an overlong assignment only when its continuation, including its comment, fits; `when_too_long` moves it even if the continuation remains too long. Comments attached to a separator prevent joining that separator and its following value. Vector reflow retains commented rows and matrix structure. Matrix alignment falls back to minimal spacing if aligned rows exceed the limit.

Section Indent is the zero-based opening bracket column, filled with dashes. Infinite Indent right-aligns the header; infinite Suffix fills the remaining width. Setting both to infinite centers it. A finite suffix width includes the optional closing section-list star.

Text-name and text-identifier case changes are explicit opt-ins and affect only text that is entirely one regular identifier. They can change case-sensitive keys or application values. `keep` leaves quoted text unchanged, including its escapes.

## Selector rules

Ordered `*[Elcl Format.Rules]` entries have an optional Selector section and a required Style section. Style uses the same properties as Base; omitted properties inherit. Core's built-in rules for relative sections and meta-values remain active.

Selector supports `Kind` (`section` or `assignment`), `List Section`, `Relative Section`, `Section Names More Than`, `Meta Value`, `Paths`, and `Value Types`. Paths and value types accept one text value or a list. Value types are `integer`, `boolean`, `float`, `text`, `date`, `time`, `date_time`, `bytes`, `time_delta`, `regex`, and `value_list`.

Conditions combine with AND; path alternatives combine with OR. A section match does not select its assignments. Paths support component globs (`*`, `?`, character classes) and whole-component `**`. Quoted components match exact, case-sensitive text names, including literal dots and wildcard characters. Relative paths are resolved against the preceding absolute section; includes reset that context.

Each property takes its most specific matching rule. Exact paths outrank globs, and globs outrank rules without paths. Ties use literal component count, literal character count, fewer recursive/ordinary wildcards, condition count, and finally declaration order, with later rules winning. Selectors match original names. Opt-in quoted-name changes may therefore affect matching on later format actions.

## Placeholder highlighting

Open **Languages & Frameworks | Erbsland Configuration Language** and select **Placeholders: None** or **Erbsland Core**. The IDE-wide default is Erbsland Core; applying the preference updates open editors immediately.

```elcl
[Welcome]
Message: "Hello ${var:user|trim|fallback:Guest}"
```

Sources and filters have regular Core names. Parameters follow an optional colon and may be empty. A placeholder stays on one physical line, cannot nest, and supports up to 16 filters. Use `\$` to prevent a literal `${` from starting a placeholder. Unicode escapes such as `\u007c` and `\u007d` can represent pipes and closing braces inside parameters.

Highlighting applies to quoted text values, text list entries, and multiline text. It excludes quoted names, meta-values, code, and regex. Malformed expressions remain ordinary text, and no placeholder-specific diagnostics or runtime lookups are performed. Configure source/filter names, parameters, and punctuation separately under **Editor | Color Scheme | Erbsland Configuration Language | Placeholders**.

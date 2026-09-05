# Changelog

## Version 0.5.0

- Resolve validation-rule index paths relative to their `vr_key` scope, support canonical and compatibility entry paths, and honor scoped index visibility.
- Highlight all unescaped `vr_` reserved names while treating `vr_vr_` names as escaped regular identifiers.
- Add context-sensitive validation-rule completion for reserved names, fields, types, modes, templates, booleans, key paths, and named indexes, including unfinished text values.
- Add separate, automatically detectable code-style preferences for section names, value names, and semantic identifiers in all lower/title/upper and space/underscore forms.
- Expand the color preview with a clearly separated validation-rules example containing reserved names and identifiers.

## Version 0.4.1

- Fix the changelog displayed in the IDE's plugin update details.
- Build the Structure view from semantic section scopes, preserving declaration order, relative paths, repeated section-list entries, and their values.
- Add distinct section, section-list, value, and multiline-tag presentation, including platform Structure icons.
- Parse multiline opener/closer comments and regex/byte comments separately from value content.
- Inject tagged multiline code into languages available in the current IDE, with indentation-aware editable offset mapping.
- Group color settings by purpose and add separate colors for delimiters, name separators, text names, list separators, escapes, tags, and ignored multiline whitespace.
- Expand the color preview with text names, escaped text, inline and multiline code, and invalid syntax.
- Split analysis, Structure, highlighting, and injection responsibilities into focused documented components.
- Refresh the Structure view as PSI changes and index section-list entries from `[0]` in declaration order.
- Accept `hex:` in single-line byte data and report unsupported formats, illegal digits, and byte-splitting whitespace at their exact ranges in both byte forms.
- Support case-insensitive simple and Unicode text escapes, including `\u{...}`, with targeted errors for unknown sequences, null, surrogates, and out-of-range code points.

## Version 0.3.0 - Initial Public Version

- This is the initial public release of this plugin.

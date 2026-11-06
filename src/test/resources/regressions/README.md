# Parser regression fixtures

These synthetic documents preserve the syntax of reported regressions without retaining the original application configuration, URLs, or checksums.

- `files.elcl`: a quoted text name containing underscores and a filename extension.
- `doc-process.elcl`: inline and next-line regex values with named captures and escapes, inside decorated relative section-list entries.
- `vectors.elcl`: indented text values after assignment line breaks, followed by additional assignments and decorated sections.

The regression test checks all three documents for parser errors, semantic diagnostics, and editor error annotations.

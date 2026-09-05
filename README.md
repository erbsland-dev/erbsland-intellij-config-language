# Erbsland Configuration Language Support for JetBrains IDEs

Language support for [Erbsland Configuration Language](https://config-lang.erbsland.dev/) files in CLion,
PyCharm, IntelliJ IDEA, and other IntelliJ Platform IDEs.

## Features

- Syntax highlighting for `.elcl` files, including escapes, multiline tags, and visually distinct whitespace that ELCL discards from multiline values.
- ELCL syntax and semantic diagnostics.
- ELCL-VR highlighting, scoped diagnostics, and context-sensitive completion for `.vr.elcl` files.
- Local `@include` resolution inside the current project.
- Comment toggling, brace matching, folding, and a declaration-ordered semantic Structure view.
- Language injection for tagged multiline code when the requested language is available in the IDE.
- Logically grouped, configurable colors under **Editor | Color Scheme | Erbsland Configuration Language**.
- Formatter-ready naming preferences under **Editor | Code Style | Erbsland Configuration Language**, with independent automatic detection for section names, value names, and semantic identifiers.

The plugin validates `.vr.elcl` rule documents themselves. It does not yet apply validation rules to ordinary
configuration files.

## Build

Use a Java 17 or newer runtime to start Gradle. The configured Foojay resolver provisions the Java 21
compilation toolchain automatically:

```shell
./gradlew test buildPlugin
```

The installable archive is written to `build/distributions/`.

### Versioning

The build uses the nearest reachable Git tag as the plugin version. Release tags may be written as `1.2.3` or
`v1.2.3`; the optional `v` prefix is removed before the version is written into the plugin descriptor and archive
name. Tags must otherwise be valid semantic versions.

## Install

1. Build the plugin or download its ZIP artifact.
2. Open **Settings | Plugins** in the JetBrains IDE.
3. Choose **Install Plugin from Disk** from the gear menu.
4. Select the ZIP file and restart the IDE when prompted.

JetBrains IDE version 2025.2 or newer is required.

## Verification

```shell
./gradlew verifyPlugin
```

This verifies the plugin against IntelliJ IDEA Community releases from the 2025.2 and 2026.2 lines. The plugin only
uses the cross-product `com.intellij.modules.lang` API.

## Development

The restartable JFlex lexer and Grammar-Kit PSI parser are generated into `build/generated/`. Grammar-Kit mixin
support required by generated PSI lives in `src/main/generatedJava/`; all other generated output remains under
`build/generated/`. The plugin depends solely on `com.intellij.modules.lang`, so it can be installed in any
IntelliJ Platform product that provides the language API.

## Project Links

- [ELCL language reference](https://config-lang.erbsland.dev/)
- [Source code](https://github.com/erbsland-dev/erbsland-intellij-config-language)
- [Issue tracker](https://github.com/erbsland-dev/erbsland-intellij-config-language/issues)

## License

Copyright © 2026 Tobias Erbsland / Erbsland DEV. Licensed under the Apache License, Version 2.0.

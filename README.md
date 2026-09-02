# Erbsland Configuration Language Support for JetBrains IDEs

Language support for [Erbsland Configuration Language](https://config-lang.erbsland.dev/) files in CLion,
PyCharm, IntelliJ IDEA, and other IntelliJ Platform IDEs.

## Features

- Syntax highlighting for `.elcl` files, including every standard and advanced ELCL literal form.
- ELCL syntax and semantic diagnostics.
- ELCL-VR highlighting, completion, and rule-document diagnostics for `.vr.elcl` files.
- Local `@include` resolution inside the current project.
- Comment toggling, brace matching, folding, and structure view.
- Configurable colors under **Editor | Color Scheme | Erbsland Configuration Language**.

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

The restartable JFlex lexer and Grammar-Kit PSI parser are generated into `build/generated/`. Only the source
grammars in `src/main/grammar/` are versioned. The plugin depends solely on `com.intellij.modules.lang`, so it
can be installed in any IntelliJ Platform product that provides the language API.

## Project Links

- [ELCL language reference](https://config-lang.erbsland.dev/)
- [Source code](https://github.com/erbsland-dev/erbsland-intellij-config-language)
- [Issue tracker](https://github.com/erbsland-dev/erbsland-intellij-config-language/issues)

## License

Copyright © 2026 Tobias Erbsland / Erbsland DEV. Licensed under the Apache License, Version 2.0.

<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# phoenix-php Changelog

## [Unreleased]
### Added
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)
- PhpStorm 2026.2 target configuration, including the bundled PHP API dependency.
- Java 25 toolchain and Kotlin compiler update required by PhpStorm 2026.2.
- Rename test compatibility with PhpStorm 2026.2 VFS root-access rules.
- File and directory completion for the first argument of `projectDir()`.
- Ctrl/Cmd navigation from `projectDir()` paths to files and directories.
- Extensionless completion and navigation for Plates templates rendered by `view()`.
- `projectDir()` root calculation now respects the level argument of `dirname(__DIR__, N)`.
- `.env` key completion for the first argument of a project-defined `env()` helper.
- IDE-only `@phoenix-struct` annotation for Rider ORM result properties, including nullable rows, collections, and fluent queries.
- Project-root-relative `@phoenix-view` directories for template completion and navigation in annotated PHP functions and methods.
- Required `@phoenix-project-dir` directory values for project-relative file and folder completion and navigation, including paths without a leading slash.
- Documented standard `@var` PHPDoc for retaining struct hints on object-typed properties without changing PHP runtime types.

### Fixed
- Removed duplicate `env()` suggestions and preserve the exact `.env` key casing on insertion.
- Resolve view helpers by their actual declaration, preventing suggestions from unrelated same-named functions.
- Case-insensitive path and template completion with exact file and directory casing preserved on selection.

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

### Fixed
- Removed duplicate `env()` suggestions and preserve the exact `.env` key casing on insertion.

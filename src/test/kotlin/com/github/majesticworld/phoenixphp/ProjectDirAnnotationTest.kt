package com.github.majesticworld.phoenixphp

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class ProjectDirAnnotationTest : BasePlatformTestCase() {
    fun testSrcAnnotationCompletesAndNavigatesInsideSrcRatherThanTheProjectRoot() {
        val mail = myFixture.addFileToProject("src/Model/Mail.php", "<?php class Mail {}")
        myFixture.addFileToProject("src/Model/User.php", "<?php class User {}")
        val rootOnly = myFixture.addFileToProject("root-only.php", "<?php")
        myFixture.addFileToProject(
            "src/Core/helpers.php",
            """
            <?php
            /** @phoenix-project-dir src */
            function projectDir(string ${'$'}path): string {
                return dirname(__DIR__, 2) . ${'$'}path;
            }
            """.trimIndent(),
        )
        val root = complete("<?php projectDir('<caret>');")
        assertTrue("Missing src/Model directory in $root", "Model/" in root)
        assertFalse("Annotation must not expose the project root", rootOnly.name in root)
        assertFalse("The src directory is already the base", "src/" in root)

        val models = complete("<?php projectDir('Model/<caret>');")
        assertTrue("Mail.php" in models)
        assertTrue("User.php" in models)
        assertNavigates("<?php projectDir('Model/Ma<caret>il.php');", mail.virtualFile)
        assertNavigates("<?php projectDir('Mod<caret>el');", mail.virtualFile.parent)
    }

    fun testLeadingSlashRemainsRelativeToTheAnnotatedBase() {
        val mail = myFixture.addFileToProject("src/Model/Mail.php", "<?php")
        myFixture.addFileToProject("src/Model/User.php", "<?php")
        myFixture.addFileToProject(
            "helpers.php",
            """
            <?php
            /** @phoenix-project-dir src */
            function projectDir(string ${'$'}path): string { return ${'$'}path; }
            """.trimIndent(),
        )
        assertTrue("Mail.php" in complete("<?php projectDir('/Model/<caret>');"))
        assertNavigates("<?php projectDir('/Model/Ma<caret>il.php');", mail.virtualFile)
    }

    fun testMethodAnnotationSupportsWindowsDirectoriesAndNamedArguments() {
        val mail = myFixture.addFileToProject("src/Model/Mail.php", "<?php")
        myFixture.addFileToProject("src/Model/User.php", "<?php")
        myFixture.addFileToProject(
            "Paths.php",
            """
            <?php
            class Paths {
                /** @phoenix-project-dir src\Model */
                public static function source(string ${'$'}file, array ${'$'}options = []): string {
                    return ${'$'}file;
                }
            }
            """.trimIndent(),
        )
        val suggestions = complete("<?php Paths::source(options: [], file: '<caret>');")
        assertTrue("Mail.php" in suggestions)
        assertTrue("User.php" in suggestions)
        assertNavigates("<?php Paths::source(file: 'Ma<caret>il.php');", mail.virtualFile)
    }

    fun testEmptyMissingAndEscapingAnnotationValuesDoNotFallBackToTheHelperBody() {
        myFixture.addFileToProject("root-one.php", "<?php")
        myFixture.addFileToProject("root-two.php", "<?php")
        myFixture.addFileToProject(
            "support/helpers.php",
            """
            <?php
            namespace EmptyValue {
                /** @phoenix-project-dir */
                function projectDir(string ${'$'}path): string { return dirname(__DIR__) . ${'$'}path; }
            }
            namespace Missing {
                /** @phoenix-project-dir missing */
                function projectDir(string ${'$'}path): string { return dirname(__DIR__) . ${'$'}path; }
            }
            namespace Escaping {
                /** @phoenix-project-dir ../src */
                function projectDir(string ${'$'}path): string { return dirname(__DIR__) . ${'$'}path; }
            }
            """.trimIndent(),
        )
        for (namespace in listOf("EmptyValue", "Missing", "Escaping")) {
            assertFalse("root-one.php" in complete("<?php \\$namespace\\projectDir('<caret>');"))
        }
    }

    fun testOnlyTheFirstParameterOfTheResolvedAnnotatedHelperReceivesPaths() {
        myFixture.addFileToProject("src/Mail.php", "<?php")
        myFixture.addFileToProject("src/User.php", "<?php")
        myFixture.addFileToProject(
            "helpers.php",
            """
            <?php
            namespace App {
                /** @phoenix-project-dir src */
                function sourcePath(string ${'$'}path, string ${'$'}other = ''): string { return ${'$'}path; }
            }
            namespace Other {
                function sourcePath(string ${'$'}path): string { return ${'$'}path; }
            }
            """.trimIndent(),
        )
        assertTrue("Mail.php" in complete("<?php use function App\\sourcePath as source; source('<caret>');"))
        assertFalse("Mail.php" in complete("<?php \\Other\\sourcePath('<caret>');"))
        assertFalse("Mail.php" in complete("<?php \\App\\sourcePath('Mail.php', '<caret>');"))
    }

    fun testUnannotatedDirnameHelpersPreserveTheirDirectoryLevels() {
        myFixture.addFileToProject("resources/first.php", "<?php")
        myFixture.addFileToProject("resources/second.php", "<?php")
        myFixture.addFileToProject(
            "support/helpers.php",
            """
            <?php
            namespace One;
            function projectDir(string ${'$'}path): string { return dirname(__DIR__) . ${'$'}path; }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "src/Core/helpers.php",
            """
            <?php
            namespace Two;
            function projectDir(string ${'$'}path): string { return dirname(__DIR__, 2) . ${'$'}path; }
            """.trimIndent(),
        )
        for (namespace in listOf("One", "Two")) {
            assertTrue("first.php" in complete("<?php \\$namespace\\projectDir('/resources/<caret>');"))
        }
    }

    private fun complete(source: String): List<String> {
        myFixture.configureByText("consumer.php", source)
        myFixture.completeBasic()
        return myFixture.lookupElementStrings.orEmpty()
    }

    private fun assertNavigates(source: String, expected: VirtualFile) {
        myFixture.configureByText("consumer.php", source)
        val literal = PsiTreeUtil.getParentOfType(
            myFixture.file.findElementAt(myFixture.caretOffset),
            StringLiteralExpression::class.java,
        )!!
        assertTrue(
            "Path must resolve to ${expected.path}",
            literal.references.any { (it.resolve() as? PsiFileSystemItem)?.virtualFile == expected },
        )
    }
}

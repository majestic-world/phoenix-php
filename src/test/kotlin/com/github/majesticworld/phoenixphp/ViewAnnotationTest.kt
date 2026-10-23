package com.github.majesticworld.phoenixphp

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class ViewAnnotationTest : BasePlatformTestCase() {
    fun testAnnotatedHelperCompletesAndNavigatesFromTheProjectRoot() {
        val template = myFixture.addFileToProject("resources/views/admin/home.php", "<h1>Home</h1>")
        myFixture.addFileToProject("resources/views/admin/profile.php", "<h1>Profile</h1>")
        myFixture.addFileToProject(
            "src/Core/helpers.php",
            """
            <?php
            namespace App;
            use League\Plates\Engine;
            /** @phoenix-view resources\views */
            function view(string ${'$'}name, array ${'$'}data = []): void {
                static ${'$'}engine = null;
                ${'$'}engine ??= new Engine(projectDir('resources/views'));
                echo ${'$'}engine->render(${'$'}name, ${'$'}data);
            }
            """.trimIndent(),
        )
        myFixture.configureByText("consumer.php", "<?php use function App\\view; view('admin/<caret>');")
        myFixture.completeBasic()
        val suggestions = myFixture.lookupElementStrings.orEmpty()
        assertTrue("Missing extensionless view in $suggestions", "home" in suggestions)
        assertFalse("Template extensions must not be inserted", "home.php" in suggestions)

        myFixture.configureByText("consumer.php", "<?php use function App\\view; view('admin/ho<caret>me');")
        val literal = PsiTreeUtil.getParentOfType(
            myFixture.file.findElementAt(myFixture.caretOffset),
            StringLiteralExpression::class.java,
        )!!
        assertTrue(
            "View reference must resolve to the project-root template",
            literal.references.any { it.resolve()?.containingFile?.virtualFile == template.virtualFile },
        )
    }

    fun testInstanceAndStaticMethodsUseTheirOwnAnnotatedDirectories() {
        myFixture.addFileToProject("resources/views/home.php", "")
        myFixture.addFileToProject("resources/views/profile.php", "")
        myFixture.addFileToProject("reports/views/summary.php", "")
        myFixture.addFileToProject("reports/views/detail.php", "")
        myFixture.addFileToProject(
            "src/Renderer.php",
            """
            <?php
            namespace App;
            class Renderer {
                /** @phoenix-view resources/views */
                public function renderPage(string ${'$'}template, array ${'$'}data = []): void {}
                /** @phoenix-view reports/views */
                public static function report(string ${'$'}template, array ${'$'}data = []): void {}
            }
            """.trimIndent(),
        )
        val instance = complete("<?php (new \\App\\Renderer())->renderPage('<caret>');")
        assertTrue("home" in instance)
        assertFalse("summary" in instance)

        val static = complete("<?php \\App\\Renderer::report(data: [], template: '<caret>');")
        assertTrue("summary" in static)
        assertFalse("home" in static)
    }

    fun testImportedFunctionAliasUsesTheAnnotationRatherThanItsName() {
        myFixture.addFileToProject("resources/views/home.php", "")
        myFixture.addFileToProject("resources/views/profile.php", "")
        myFixture.addFileToProject(
            "src/helpers.php",
            """
            <?php
            namespace App;
            /** @phoenix-view resources/views */
            function renderTemplate(string ${'$'}template): void {}
            """.trimIndent(),
        )
        val suggestions = complete("<?php use function App\\renderTemplate as page; page('<caret>');")
        assertTrue("home" in suggestions)
        assertFalse("home.php" in suggestions)
    }

    fun testOnlyTheAnnotatedCallableAndItsTemplateArgumentReceiveViews() {
        myFixture.addFileToProject("resources/views/home.php", "")
        myFixture.addFileToProject("resources/views/profile.php", "")
        myFixture.addFileToProject(
            "src/helpers.php",
            """
            <?php
            namespace App {
                /** @phoenix-view resources/views */
                function view(string ${'$'}name, array ${'$'}data = []): void {}
            }
            namespace Other {
                function view(string ${'$'}name): void {}
            }
            """.trimIndent(),
        )
        assertFalse("home" in complete("<?php \\Other\\view('<caret>');"))
        assertFalse("home" in complete("<?php \\App\\view('home', ['title' => '<caret>']);"))
    }

    fun testAnnotationTakesPrecedenceAndMissingDirectoriesDoNotFallBack() {
        myFixture.addFileToProject("resources/views/home.php", "")
        myFixture.addFileToProject("resources/views/profile.php", "")
        myFixture.addFileToProject("legacy/views/old.php", "")
        myFixture.addFileToProject("legacy/views/older.php", "")
        myFixture.addFileToProject(
            "src/helpers.php",
            """
            <?php
            namespace App {
                /** @phoenix-view resources/views */
                function view(string ${'$'}name): void {
                    ${'$'}engine = new Engine(__DIR__ . '/../legacy/views');
                }
            }
            namespace Missing {
                /** @phoenix-view missing/views */
                function view(string ${'$'}name): void {
                    ${'$'}engine = new Engine(__DIR__ . '/../legacy/views');
                }
            }
            """.trimIndent(),
        )
        val suggestions = complete("<?php \\App\\view('<caret>');")
        assertTrue("home" in suggestions)
        assertFalse("old" in suggestions)
        assertFalse("old" in complete("<?php \\Missing\\view('<caret>');"))
    }

    fun testConventionalUnannotatedPlatesHelperStillCompletes() {
        myFixture.addFileToProject("resources/views/home.php", "")
        myFixture.addFileToProject("resources/views/profile.php", "")
        myFixture.addFileToProject(
            "src/helpers.php",
            """
            <?php
            function view(string ${'$'}name): void {
                ${'$'}engine = new Engine(__DIR__ . '/../resources/views');
            }
            """.trimIndent(),
        )
        assertTrue("home" in complete("<?php view('<caret>');"))
    }

    private fun complete(source: String): List<String> {
        myFixture.configureByText("consumer.php", source)
        myFixture.completeBasic()
        return myFixture.lookupElementStrings.orEmpty()
    }
}

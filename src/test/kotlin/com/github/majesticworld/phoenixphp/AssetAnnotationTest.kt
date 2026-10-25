package com.github.majesticworld.phoenixphp

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class AssetAnnotationTest : BasePlatformTestCase() {
    fun testAnnotatedAssetsHelperCompletesAndNavigatesFromTheConfiguredDirectory() {
        val app = myFixture.addFileToProject("public/assets/js/App.js", "console.log('app');")
        myFixture.addFileToProject("public/assets/js/Admin.js", "console.log('admin');")
        myFixture.addFileToProject("public/outside.js", "console.log('outside');")
        myFixture.addFileToProject(
            "src/Core/helpers.php",
            """
            <?php
            namespace App;
            /** @phoenix-asset public/assets */
            function assets(string ${'$'}name, bool ${'$'}id = true): string { return ${'$'}name; }
            """.trimIndent(),
        )

        val root = complete("<?php use function App\\assets; assets('<caret>');")
        assertTrue("Missing assets subdirectory in $root", "js/" in root)
        assertFalse("Annotation must not expose public outside assets", "outside.js" in root)

        val scripts = complete("<?php use function App\\assets; assets('js/<caret>');")
        assertTrue("App.js" in scripts)
        assertTrue("Admin.js" in scripts)
        assertNavigates("<?php use function App\\assets; assets('js/Ap<caret>p.js');", app.virtualFile)
    }

    fun testAnnotationSupportsAliasesNamedArgumentsAndOnlyTheFirstParameter() {
        myFixture.addFileToProject("public/assets/css/App.css", "body {}")
        myFixture.addFileToProject(
            "helpers.php",
            """
            <?php
            namespace App {
                /** @phoenix-asset public\\assets */
                function assetUrl(string ${'$'}name, bool ${'$'}id = true): string { return ${'$'}name; }
            }
            namespace Other {
                function assets(string ${'$'}name): string { return ${'$'}name; }
            }
            """.trimIndent(),
        )

        assertTrue("App.css" in complete("<?php use function App\\assetUrl as assets; assets(id: false, name: 'css/<caret>');"))
        assertFalse("App.css" in complete("<?php \\Other\\assets('css/<caret>');"))
        assertFalse("App.css" in complete("<?php \\App\\assetUrl('css/App.css', id: '<caret>');"))
    }

    fun testGlobalAssetsFunctionUsesItsAnnotation() {
        myFixture.addFileToProject("public/assets/app.js", "")
        myFixture.addFileToProject(
            "helpers.php",
            """
            <?php
            /** @phoenix-asset public/assets */
            function assets(string ${'$'}name, bool ${'$'}id = true): string { return ${'$'}name; }
            """.trimIndent(),
        )

        assertTrue("app.js" in complete("<?php assets('<caret>');"))
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
            "Asset reference must resolve to ${expected.path}",
            literal.references.any { (it.resolve() as? PsiFileSystemItem)?.virtualFile == expected },
        )
    }
}

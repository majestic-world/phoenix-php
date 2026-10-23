package com.github.majesticworld.phoenixphp

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PathCaseCompletionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("src/Command/CommandHandler.php", "<?php")
        myFixture.addFileToProject("src/Command/CommandQueue.php", "<?php")
        myFixture.addFileToProject("src/CommandBus/Dispatch.php", "<?php")
        myFixture.addFileToProject("resources/views/Admin/Command.php", "<h1>Command</h1>")
        myFixture.addFileToProject("resources/views/Admin/CommandList.php", "<h1>Commands</h1>")
        myFixture.addFileToProject("resources/views/Command/Run.php", "<h1>Run</h1>")
        myFixture.addFileToProject("resources/views/CommandBus/Dispatch.php", "<h1>Dispatch</h1>")
        myFixture.addFileToProject(
            "helpers.php",
            """
            <?php
            /** @phoenix-project-dir src */
            function projectDir(string ${'$'}path): string { return ${'$'}path; }
            /** @phoenix-view resources/views */
            function view(string ${'$'}name): void {}
            """.trimIndent(),
        )
    }

    fun testProjectDirectoryMatchingIgnoresCaseAndInsertsTheRealDirectoryName() {
        for (prefix in listOf("comma", "COMMA", "cOmMa")) {
            select(
                "<?php projectDir('$prefix<caret>');",
                "Command/",
                "<?php projectDir('Command/');",
            )
        }
    }

    fun testProjectFileSelectionPreservesItsCaseExtensionAndParentDirectory() {
        for (prefix in listOf("comma", "COMMA", "cOmMa")) {
            select(
                "<?php projectDir('Command/$prefix<caret>');",
                "CommandHandler.php",
                "<?php projectDir('Command/CommandHandler.php');",
            )
        }
    }

    fun testViewSelectionPreservesItsCaseAndOmitsTheTemplateExtension() {
        for (prefix in listOf("comma", "COMMA", "cOmMa")) {
            select(
                "<?php view('Admin/$prefix<caret>');",
                "Command",
                "<?php view('Admin/Command');",
            )
        }
    }

    fun testViewDirectorySelectionPreservesItsCaseAndTrailingSlash() {
        select("<?php view('comma<caret>');", "Command/", "<?php view('Command/');")
    }

    private fun select(source: String, name: String, expected: String) {
        myFixture.configureByText("consumer.php", source)
        myFixture.completeBasic()
        val elements = myFixture.lookupElements.orEmpty()
        val selected = elements.firstOrNull { it.lookupString == name }
        assertNotNull("Missing $name in ${elements.map { it.lookupString }}", selected)
        myFixture.lookup.currentItem = selected
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        myFixture.checkResult(expected)
    }
}

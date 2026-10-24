package com.github.majesticworld.phoenixphp

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.inspections.probablyBug.PhpUnhandledExceptionInspection
import com.jetbrains.php.lang.psi.elements.Function

class CompactThrowsIntentionTest : BasePlatformTestCase() {
    fun testCompactThrowsActionHasMatchingPreviewAndSupportsUndoAndRedo() {
        myFixture.addFileToProject(
            "exceptions.php",
            "<?php interface Throwable {} class Exception implements Throwable {}",
        )
        myFixture.enableInspections(PhpUnhandledExceptionInspection::class.java)
        val before = """
            <?php
            function sendMail(): void
            {
                th<caret>row new \Exception('No SMTP server configured');
            }
        """.trimIndent()
        myFixture.configureByText("native.php", before)
        val action = myFixture.findSingleIntention("Add compact PHPDoc @throws tags")
        myFixture.checkPreviewAndLaunchAction(action)
        assertCompactExceptionDoc()

        myFixture.performEditorAction(IdeActions.ACTION_UNDO)
        myFixture.checkResult(before.replace("<caret>", ""))
        myFixture.performEditorAction(IdeActions.ACTION_REDO)
        assertCompactExceptionDoc()
    }

    private fun assertCompactExceptionDoc() {
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val function = PsiTreeUtil.findChildOfType(myFixture.file, Function::class.java)!!
        val comment = function.docComment
        assertNotNull("The generated comment must remain a parsed PHPDoc", comment)
        assertTrue(
            "Expected one-line @throws PHPDoc, got ${comment!!.text}",
            Regex("""/\*\* @throws \\?Exception \*/""").matches(comment.text),
        )
        val exceptions = comment.exceptionClasses.flatMap { it.global(project).types }.toSet()
        assertEquals(setOf("\\Exception"), exceptions)
    }
}

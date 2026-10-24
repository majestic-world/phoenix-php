package com.github.majesticworld.phoenixphp

import com.github.majesticworld.phoenixphp.formatting.CompactThrowsPostFormatProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment
import com.jetbrains.php.lang.documentation.phpdoc.psi.tags.PhpDocThrowsTag

class CompactThrowsPostFormatProcessorTest : BasePlatformTestCase() {
    fun testReformatCompactsThrowsAndPreservesNativeExceptionMetadataAndImports() {
        myFixture.configureByText(
            "consumer.php",
            """
            <?php
            namespace App;
            use RuntimeException as Failure;
            /**
             * @throws Failure
             */
            function work(): void {}
            """.trimIndent(),
        )
        val types = exceptionTypes(comments().single())
        assertFalse("The fixture must have parsed exception metadata", types.isEmpty())

        reformat()

        assertThrows(comments().single(), "Failure", types)
        assertTrue(myFixture.file.text.contains("use RuntimeException as Failure;"))
        val once = myFixture.file.text
        reformat()
        assertEquals("Repeated formatting must be idempotent", once, myFixture.file.text)
        assertThrows(comments().single(), "Failure", types)
    }

    fun testQualifiedAndUnionExceptionSpellingSurvivesFormatting() {
        myFixture.configureByText(
            "consumer.php",
            """
            <?php
            /**
             * @throws \Vendor\Package\Failure|\RuntimeException
             */
            function work(): void {}
            """.trimIndent(),
        )
        val types = exceptionTypes(comments().single())
        reformat()
        assertThrows(comments().single(), "\\Vendor\\Package\\Failure|\\RuntimeException", types)
    }

    fun testDescriptionsAndAdditionalTagsStayMultiline() {
        val bodies = listOf(
            " * Explains why this operation fails.\n * @throws RuntimeException",
            " * @throws RuntimeException A useful explanation.",
            " * @throws RuntimeException\n * A useful explanation on another line.",
            " * @throws RuntimeException\n * @throws LogicException",
            " * @param string ${'$'}value\n * @throws RuntimeException",
            " * @throws RuntimeException\n * @return void",
            " * @throws RuntimeException *",
            " * @throws",
        )
        for (body in bodies) {
            myFixture.configureByText("consumer.php", "<?php\n/**\n$body\n */\nfunction work(string ${'$'}value): void {}")
            val before = comments().single()
            val tags = before.getTagElementsByName("@throws").map { it.tagValue }
            val types = exceptionTypes(before)
            reformat()
            val after = comments().single()
            assertTrue("Must retain multiline PHPDoc: $body", after.text.contains('\n'))
            assertEquals(tags, after.getTagElementsByName("@throws").map { it.tagValue })
            assertEquals(types, exceptionTypes(after))
            for (description in listOf("Explains why this operation fails.", "A useful explanation.", "A useful explanation on another line.")) {
                if (body.contains(description)) assertTrue(after.text.contains(description))
            }
        }
    }

    fun testReformatTextDoesNotCompactCommentsOutsideItsRange() {
        myFixture.configureByText(
            "consumer.php",
            """
            <?php
            /**
             * @throws FirstFailure
             */
            function first(): void {}

            /**
             * @throws SelectedFailure
             */
            function selected(): void {}

            /**
             * @throws LastFailure
             */
            function last(): void {}
            """.trimIndent(),
        )
        val before = comments()
        val first = before[0].text
        val last = before[2].text
        val selectedRange = before[1].textRange
        write {
            CodeStyleManager.getInstance(project).reformatText(
                myFixture.file,
                selectedRange.startOffset,
                selectedRange.endOffset,
            )
        }
        val after = comments()
        assertEquals(first, after[0].text)
        assertEquals("/** @throws SelectedFailure */", after[1].text)
        assertEquals(last, after[2].text)
    }

    fun testTabsAndIndentationOutsideCommentArePreserved() {
        myFixture.configureByText(
            "consumer.php",
            "<?php\nclass Worker {\n\t/**\n\t * @throws RuntimeException\n\t */\n\tpublic function work(): void {}\n}",
        )
        val manager = CodeStyleSettingsManager.getInstance(project)
        val previous = manager.temporarySettings
        val settings = CodeStyleSettingsManager.getSettings(project).clone()
        settings.getIndentOptions(myFixture.file.fileType).apply {
            USE_TAB_CHARACTER = true
            INDENT_SIZE = 4
            TAB_SIZE = 4
        }
        manager.setTemporarySettings(settings)
        try {
            reformat()
            assertTrue(myFixture.file.text.contains("\n\t/** @throws RuntimeException */"))
            assertEquals("/** @throws RuntimeException */", comments().single().text)
        } finally {
            if (previous == null) manager.dropTemporarySettings() else manager.setTemporarySettings(previous)
        }
    }

    fun testElementFormattingReturnsValidCompactPhpDoc() {
        myFixture.configureByText("consumer.php", "<?php\n/**\n * @throws RuntimeException\n */\nfunction work(): void {}")
        write {
            val formatted = CodeStyleManager.getInstance(project).reformat(comments().single())
            assertTrue(formatted.isValid)
            assertTrue(formatted is PhpDocComment)
            assertEquals("/** @throws RuntimeException */", formatted.text)
        }
    }

    fun testProcessorReturnsShrunkenRangeAndLeavesPartialCommentsAlone() {
        myFixture.configureByText("consumer.php", "<?php\n/**\n * @throws RuntimeException\n */\nfunction work(): void {}")
        val processor = CompactThrowsPostFormatProcessor()
        val settings = CodeStyleSettingsManager.getSettings(project)
        val original = myFixture.file.text
        val originalRange = comments().single().textRange
        write {
            val partial = TextRange(originalRange.startOffset + 3, originalRange.endOffset - 2)
            assertEquals(partial, processor.processText(myFixture.file, partial, settings))
            assertEquals(original, myFixture.file.text)
            val result = processor.processText(myFixture.file, originalRange, settings)
            assertEquals(comments().single().textRange, result)
            assertEquals(originalRange.startOffset, result.startOffset)
            assertEquals("/** @throws RuntimeException */", comments().single().text)
        }
    }

    fun testOrdinaryCommentsAndOtherFileTypesAreNotPhpDoc() {
        myFixture.configureByText("consumer.php", "<?php\n/***\n * @throws RuntimeException\n */\nfunction work(): void {}")
        reformat()
        assertTrue("Triple-star comments must not be converted into PHPDoc", myFixture.file.text.contains("/***"))
        assertFalse(myFixture.file.text.contains("/** @throws RuntimeException */"))

        val text = "/**\n * @throws RuntimeException\n */"
        myFixture.configureByText("notes.txt", text)
        reformat()
        assertEquals(text, myFixture.file.text)
    }

    private fun reformat() = write { CodeStyleManager.getInstance(project).reformat(myFixture.file) }

    private fun write(action: () -> Unit) {
        WriteCommandAction.runWriteCommandAction(project, Runnable {
            action()
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        })
    }

    private fun comments(): List<PhpDocComment> =
        PsiTreeUtil.findChildrenOfType(myFixture.file, PhpDocComment::class.java).sortedBy { it.textOffset }

    private fun exceptionTypes(comment: PhpDocComment): List<String> = comment.exceptionClasses.map { it.toString() }

    private fun assertThrows(comment: PhpDocComment, spelling: String, types: List<String>) {
        assertEquals("/** @throws $spelling */", comment.text)
        val tags = comment.getTagElementsByName("@throws")
        assertEquals(1, tags.size)
        assertTrue(tags.single() is PhpDocThrowsTag)
        assertEquals(types, exceptionTypes(comment))
    }
}

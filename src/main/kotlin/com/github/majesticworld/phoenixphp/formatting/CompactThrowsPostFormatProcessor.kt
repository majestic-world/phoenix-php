package com.github.majesticworld.phoenixphp.formatting

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment
import com.jetbrains.php.lang.documentation.phpdoc.psi.tags.PhpDocThrowsTag
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.PhpPsiElementFactory
import java.util.regex.Pattern

/** Keeps a description-free, single throws tag compact after PHP's native formatter. */
class CompactThrowsPostFormatProcessor : PostFormatProcessor {
    override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement =
        if (source is PhpFile || source.containingFile is PhpFile) compact(source, source.textRange) else source

    override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
        if (source !is PhpFile) return rangeToReformat
        val oldLength = source.textLength
        compact(source, rangeToReformat)
        return TextRange(rangeToReformat.startOffset, rangeToReformat.endOffset + source.textLength - oldLength)
    }

    private fun compact(element: PsiElement, range: TextRange): PsiElement {
        val elementRange = element.textRange
        if (!range.intersects(elementRange)) return element
        if (element is PhpDocComment) {
            // Partial selection must not rewrite the rest of a comment outside the formatting range.
            if (!range.contains(elementRange)) return element
            val tags = element.getTagElementsByName("@throws")
            if (tags.size != 1 || tags[0] !is PhpDocThrowsTag) return element
            val text = element.text
            if (text.startsWith("/***") || !text.endsWith("*/")) return element
            var exception: String? = null
            for (line in text.substring(3, text.length - 2).lineSequence()) {
                val content = line.trim().removePrefix("*").trim()
                if (content.isEmpty()) continue
                if (exception != null) return element
                val match = THROWS_TAG.matcher(content)
                if (!match.matches()) return element
                exception = match.group(1)
            }
            val replacementText = "/** @throws ${exception ?: return element} */"
            if (text == replacementText) return element
            val replacement = PhpPsiElementFactory.createFromText(
                element.project,
                PhpDocComment::class.java,
                replacementText,
            ) ?: return element
            return element.replace(replacement)
        }

        // Walk backwards: shrinking a later comment cannot change the offsets of an earlier sibling.
        // No saved PSI nodes or document edits survive a replacement, and no formatter is re-entered.
        var child = element.lastChild
        while (child != null) {
            val previous = child.prevSibling
            compact(child, range)
            child = previous
        }
        return element
    }

    private companion object {
        const val IDENTIFIER = "[\\p{L}_\\u0080-\\uFFFF][\\p{L}\\p{N}_\\u0080-\\uFFFF]*"
        const val CLASS_NAME = "\\\\?$IDENTIFIER(?:\\\\$IDENTIFIER)*"
        val THROWS_TAG: Pattern = Pattern.compile("@throws[ \\t]+($CLASS_NAME(?:[ \\t]*\\|[ \\t]*$CLASS_NAME)*)")
    }
}

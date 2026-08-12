package com.github.majesticworld.phoenixphp.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/** Completes view() names without exposing the template file extension. */
class ViewCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            com.intellij.patterns.PlatformPatterns.psiElement(),
            ViewCompletionProvider(),
        )
    }

    private class ViewCompletionProvider : CompletionProvider<CompletionParameters>() {

        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet,
        ) {
            val literal = PsiTreeUtil.getParentOfType(
                parameters.position,
                StringLiteralExpression::class.java,
                false,
            ) ?: return
            if (!ViewPathResolver.isViewArgument(literal)) return

            val requestedPath = RequestedPath.from(pathBeforeCaret(literal, parameters.offset)) ?: return
            val directory = ViewPathResolver.resolveDirectory(literal, requestedPath.directories) ?: return
            val matchedResult = result.withPrefixMatcher(requestedPath.fileNamePrefix)

            directory.children
                .asSequence()
                .sortedWith(compareByDescending<VirtualFile> { it.isDirectory }.thenBy { it.name.lowercase() })
                .forEach { child -> matchedResult.addElement(toLookupElement(child)) }
        }

        private fun pathBeforeCaret(literal: StringLiteralExpression, caretOffset: Int): String {
            val contentStart = literal.textRange.startOffset + literal.valueRange.startOffset
            val caretInContents = (caretOffset - contentStart).coerceIn(0, literal.contents.length)
            return literal.contents.substring(0, caretInContents)
        }

        private fun toLookupElement(file: VirtualFile): LookupElementBuilder {
            val lookupName = ViewPathResolver.viewName(file) + if (file.isDirectory) "/" else ""
            return LookupElementBuilder
                .create(lookupName)
                .withPresentableText(lookupName)
                .withTypeText(file.name, true)
                .withIcon(if (file.isDirectory) AllIcons.Nodes.Folder else AllIcons.FileTypes.Any_type)
        }
    }

    private data class RequestedPath(
        val directories: List<String>,
        val fileNamePrefix: String,
    ) {
        companion object {
            fun from(value: String): RequestedPath? {
                val path = value.replace('\\', '/')
                val segments = path.split('/')
                val hasTrailingSlash = path.endsWith('/')
                val directorySegments = if (hasTrailingSlash) segments else segments.dropLast(1)
                if (directorySegments.any { it == ".." }) return null

                return RequestedPath(
                    directories = directorySegments.filter { it.isNotBlank() && it != "." },
                    fileNamePrefix = if (hasTrailingSlash) "" else segments.last(),
                )
            }
        }
    }
}

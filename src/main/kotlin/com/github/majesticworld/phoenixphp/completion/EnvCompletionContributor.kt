package com.github.majesticworld.phoenixphp.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.intellij.openapi.vfs.VfsUtilCore
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/** Completes the first string argument of a project-defined env() helper from .env keys. */
class EnvCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            com.intellij.patterns.PlatformPatterns.psiElement(),
            EnvCompletionProvider(),
        )
    }

    private class EnvCompletionProvider : CompletionProvider<CompletionParameters>() {

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
            if (!isEnvArgument(literal)) return

            val matchedResult = result.withPrefixMatcher(pathBeforeCaret(literal, parameters.offset))
            environmentKeys(literal)
                .forEach { key ->
                    matchedResult.addElement(
                        LookupElementBuilder
                            .create(key)
                            .withTypeText(".env", true)
                            // The default PHP completion case-corrector may keep the
                            // typed lowercase text. Replace it explicitly so the value
                            // always matches the spelling from .env.
                            .withInsertHandler { insertionContext, _ ->
                                insertionContext.document.replaceString(
                                    insertionContext.startOffset,
                                    insertionContext.tailOffset,
                                    key,
                                )
                                insertionContext.tailOffset = insertionContext.startOffset + key.length
                            },
                    )
                }

            // env() owns this context. Do not append PhpStorm's built-in $_ENV
            // suggestions, which would duplicate the .env keys in this popup.
            result.stopHere()
        }

        private fun isEnvArgument(literal: StringLiteralExpression): Boolean {
            val functionCall = PsiTreeUtil.getParentOfType(
                literal,
                FunctionReference::class.java,
                false,
            ) ?: return false
            if (!functionCall.name.equals(FUNCTION_NAME, ignoreCase = true) ||
                functionCall.parameters.firstOrNull() != literal
            ) {
                return false
            }

            return functionCall.resolve() is Function ||
                PhpIndex.getInstance(functionCall.project)
                    .getFunctionsByName(FUNCTION_NAME)
                    .any { it.name.equals(FUNCTION_NAME, ignoreCase = true) }
        }

        private fun environmentKeys(literal: StringLiteralExpression): List<String> =
            FilenameIndex.getVirtualFilesByName(
                ENV_FILE_NAME,
                GlobalSearchScope.projectScope(literal.project),
            )
                .asSequence()
                .flatMap { file -> extractKeys(VfsUtilCore.loadText(file)).asSequence() }
                .distinct()
                .sorted()
                .toList()

        private fun extractKeys(content: String): List<String> = content
            .lineSequence()
            .mapNotNull { line -> ENV_KEY.find(line)?.groupValues?.get(1) }
            .toList()

        private fun pathBeforeCaret(literal: StringLiteralExpression, caretOffset: Int): String {
            val contentStart = literal.textRange.startOffset + literal.valueRange.startOffset
            val caretInContents = (caretOffset - contentStart).coerceIn(0, literal.contents.length)
            return literal.contents.substring(0, caretInContents)
        }
    }

    private companion object {
        const val FUNCTION_NAME = "env"
        const val ENV_FILE_NAME = ".env"
        val ENV_KEY = Regex("""^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=""")
    }
}

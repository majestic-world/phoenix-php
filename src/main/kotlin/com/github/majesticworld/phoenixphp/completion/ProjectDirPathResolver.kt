package com.github.majesticworld.phoenixphp.completion

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/** Resolves paths according to the conventional projectDir() helper implementation. */
internal object ProjectDirPathResolver {

    const val FUNCTION_NAME = "projectDir"

    fun isProjectDirArgument(literal: StringLiteralExpression): Boolean = functionCall(literal) != null

    fun resolveDirectory(literal: StringLiteralExpression, directories: List<String>): VirtualFile? {
        val root = helperRoot(literal) ?: return null
        return directories.fold(root) { current, segment ->
            current.findChild(segment)?.takeIf { it.isDirectory } ?: return null
        }
    }

    fun resolveTarget(literal: StringLiteralExpression): VirtualFile? {
        val path = literal.contents.replace('\\', '/')
        if (!path.startsWith('/')) return null

        val segments = path.split('/').filter { it.isNotBlank() && it != "." }
        if (segments.any { it == ".." }) return null

        val root = helperRoot(literal) ?: return null
        return segments.fold(root) { current, segment -> current.findChild(segment) ?: return null }
    }

    private fun helperRoot(literal: StringLiteralExpression): VirtualFile? {
        val functionCall = functionCall(literal) ?: return null
        val helper = functionCall.resolve() as? Function
            ?: PhpIndex.getInstance(functionCall.project)
                .getFunctionsByName(FUNCTION_NAME)
                .firstOrNull { it.name.equals(FUNCTION_NAME, ignoreCase = true) }
            ?: return null

        val helperDirectory = helper.containingFile.virtualFile?.parent ?: return null
        val levels = DIRNAME_LEVELS.find(helper.text)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?: 1

        // dirname(__DIR__, N) moves N levels above the directory containing
        // the helper. With no second argument, PHP uses N = 1.
        return generateSequence(helperDirectory) { it.parent }
            .drop(levels)
            .firstOrNull()
    }

    private fun functionCall(literal: StringLiteralExpression): FunctionReference? {
        val functionCall = PsiTreeUtil.getParentOfType(
            literal,
            FunctionReference::class.java,
            false,
        ) ?: return null

        return functionCall.takeIf {
            it.name.equals(FUNCTION_NAME, ignoreCase = true) &&
                it.parameters.firstOrNull() == literal
        }
    }

    private val DIRNAME_LEVELS = Regex(
        """dirname\s*\(\s*__DIR__\s*,\s*(\d+)\s*\)""",
        RegexOption.IGNORE_CASE,
    )
}

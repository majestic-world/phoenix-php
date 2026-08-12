package com.github.majesticworld.phoenixphp.completion

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/** Resolves view names using the directory passed to League\Plates\Engine in view(). */
internal object ViewPathResolver {

    const val FUNCTION_NAME = "view"

    fun isViewArgument(literal: StringLiteralExpression): Boolean = functionCall(literal) != null

    fun resolveDirectory(literal: StringLiteralExpression, directories: List<String>): VirtualFile? {
        val root = viewRoot(literal) ?: return null
        return directories.fold(root) { current, segment ->
            current.findChild(segment)?.takeIf { it.isDirectory } ?: return null
        }
    }

    fun resolveTarget(literal: StringLiteralExpression): VirtualFile? {
        val segments = literal.contents.replace('\\', '/')
            .split('/')
            .filter { it.isNotBlank() && it != "." }
        if (segments.any { it == ".." }) return null

        val root = viewRoot(literal) ?: return null
        return segments.foldIndexed(root) { index, current, segment ->
            val exactMatch = current.findChild(segment)
            when {
                exactMatch != null -> exactMatch
                index == segments.lastIndex -> current.children.firstOrNull {
                    !it.isDirectory && viewName(it) == segment
                } ?: return null
                else -> return null
            }
        }
    }

    fun viewName(file: VirtualFile): String =
        if (file.isDirectory) file.name else file.name.substringBeforeLast('.', missingDelimiterValue = file.name)

    private fun viewRoot(literal: StringLiteralExpression): VirtualFile? {
        val functionCall = functionCall(literal) ?: return null
        val helper = functionCall.resolve() as? Function
            ?: PhpIndex.getInstance(functionCall.project)
                .getFunctionsByName(FUNCTION_NAME)
                .firstOrNull { it.name.equals(FUNCTION_NAME, ignoreCase = true) }
            ?: return null
        val helperDirectory = helper.containingFile.virtualFile?.parent ?: return null
        val relativePath = ENGINE_DIRECTORY.find(helper.text)?.groupValues?.get(2) ?: return null

        return relativePath.replace('\\', '/')
            .split('/')
            .fold(helperDirectory) { current, segment ->
                when (segment) {
                    "", "." -> current
                    ".." -> current.parent ?: return null
                    else -> current.findChild(segment)?.takeIf { it.isDirectory } ?: return null
                }
            }
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

    private val ENGINE_DIRECTORY = Regex(
        """new\s+(?:\\?[\w\\]+\\)?Engine\s*\(\s*__DIR__\s*\.\s*(['\"])([^'\"]+)\1""",
    )
}

package com.github.majesticworld.phoenixphp.completion

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/** Resolves annotated view directories and conventional Plates view() helpers. */
internal object ViewPathResolver {

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
        val call = PsiTreeUtil.getParentOfType(literal, FunctionReference::class.java, false) ?: return null
        val helper = call.resolve() as? Function ?: return null
        val parameterName = helper.parameters.firstOrNull()?.name ?: return null
        if (call.getParameter(parameterName, 0) != literal) return null

        val annotation = helper.docComment?.getTagElementsByName("@phoenix-view")?.firstOrNull()
        if (annotation != null) return projectRelativeDirectory(literal, annotation.tagValue)

        if (!call.name.equals("view", ignoreCase = true)) return null
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

    private val ENGINE_DIRECTORY = Regex(
        """new\s+(?:\\?[\w\\]+\\)?Engine\s*\(\s*__DIR__\s*\.\s*(['\"])([^'\"]+)\1""",
    )
}

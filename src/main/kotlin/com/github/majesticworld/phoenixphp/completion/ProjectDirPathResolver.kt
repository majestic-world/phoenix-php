package com.github.majesticworld.phoenixphp.completion

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/** Resolves annotated project directories and conventional projectDir() helpers. */
internal object ProjectDirPathResolver {

    fun resolveDirectory(literal: StringLiteralExpression, directories: List<String>): VirtualFile? {
        val root = helperRoot(literal) ?: return null
        return directories.fold(root) { current, segment ->
            current.findChild(segment)?.takeIf { it.isDirectory } ?: return null
        }
    }

    fun resolveTarget(literal: StringLiteralExpression): VirtualFile? {
        val path = literal.contents.replace('\\', '/')
        val segments = path.split('/').filter { it.isNotBlank() && it != "." }
        if (segments.any { it == ".." }) return null

        val root = helperRoot(literal) ?: return null
        return segments.fold(root) { current, segment -> current.findChild(segment) ?: return null }
    }

    private fun helperRoot(literal: StringLiteralExpression): VirtualFile? {
        val call = PsiTreeUtil.getParentOfType(literal, FunctionReference::class.java, false) ?: return null
        val helper = call.resolve() as? Function ?: return null
        val parameterName = helper.parameters.firstOrNull()?.name ?: return null
        if (call.getParameter(parameterName, 0) != literal) return null

        val annotation = helper.docComment?.getTagElementsByName("@phoenix-project-dir")?.firstOrNull()
        if (annotation != null) return projectRelativeDirectory(literal, annotation.tagValue)

        if (!call.name.equals("projectDir", ignoreCase = true)) return null

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

    private val DIRNAME_LEVELS = Regex(
        """dirname\s*\(\s*__DIR__\s*,\s*(\d+)\s*\)""",
        RegexOption.IGNORE_CASE,
    )
}

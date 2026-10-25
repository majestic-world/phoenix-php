package com.github.majesticworld.phoenixphp.completion

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/** Resolves paths relative to directories configured by @phoenix-asset. */
internal object AssetPathResolver {

    fun resolveDirectory(literal: StringLiteralExpression, directories: List<String>): VirtualFile? {
        val root = assetRoot(literal) ?: return null
        return directories.fold(root) { current, segment ->
            current.findChild(segment)?.takeIf { it.isDirectory } ?: return null
        }
    }

    fun resolveTarget(literal: StringLiteralExpression): VirtualFile? {
        val segments = literal.contents.replace('\\', '/')
            .split('/')
            .filter { it.isNotBlank() && it != "." }
        if (segments.any { it == ".." }) return null

        val root = assetRoot(literal) ?: return null
        return segments.fold(root) { current, segment -> current.findChild(segment) ?: return null }
    }

    private fun assetRoot(literal: StringLiteralExpression): VirtualFile? {
        val call = PsiTreeUtil.getParentOfType(literal, FunctionReference::class.java, false) ?: return null
        val helper = call.resolve() as? Function ?: return null
        val parameterName = helper.parameters.firstOrNull()?.name ?: return null
        if (call.getParameter(parameterName, 0) != literal) return null

        val annotation = helper.docComment?.getTagElementsByName("@phoenix-asset")?.firstOrNull()
            ?: return null
        return projectRelativeDirectory(literal, annotation.tagValue)
    }
}

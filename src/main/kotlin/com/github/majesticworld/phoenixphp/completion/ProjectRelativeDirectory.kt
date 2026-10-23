package com.github.majesticworld.phoenixphp.completion

import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

internal fun projectRelativeDirectory(literal: StringLiteralExpression, value: String): VirtualFile? {
    val path = value.trim().replace('\\', '/')
    if (path.isBlank() || path.startsWith('/') || ':' in path) return null
    val segments = path.split('/').filter { it.isNotEmpty() && it != "." }
    if (".." in segments) return null
    val file = literal.containingFile.originalFile.virtualFile ?: return null
    val root = ProjectFileIndex.getInstance(literal.project).getContentRootForFile(file)
        ?: literal.project.basePath?.let { LocalFileSystem.getInstance().findFileByPath(it) }
        ?: return null
    return segments.fold(root) { current, segment ->
        current.findChild(segment)?.takeIf { it.isDirectory } ?: return null
    }
}

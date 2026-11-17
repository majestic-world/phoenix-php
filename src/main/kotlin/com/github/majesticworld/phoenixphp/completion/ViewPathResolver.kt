package com.github.majesticworld.phoenixphp.completion

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.documentation.phpdoc.psi.tags.PhpDocTag
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.Variable

/** Resolves annotated view directories, conventional Plates view() helpers and Plates template methods. */
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
        templateMethodRoot(call, literal)?.let { return it }
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

    /** `$this->insert()`, `$this->layout()` and `$this->fetch()` inside a template under an annotated view directory. */
    private fun templateMethodRoot(call: FunctionReference, literal: StringLiteralExpression): VirtualFile? {
        if (call !is MethodReference || call.name !in TEMPLATE_METHODS) return null
        if ((call.classReference as? Variable)?.name != "this") return null
        if (call.getParameter("name", 0) != literal) return null

        val templateFile = literal.containingFile.originalFile.virtualFile ?: return null
        return annotatedViewDirectories(literal.project)
            .mapNotNull { projectRelativeDirectory(literal, it) }
            .filter { VfsUtilCore.isAncestor(it, templateFile, true) }
            .maxByOrNull { it.path.length }
    }

    private fun annotatedViewDirectories(project: Project): Set<String> {
        if (DumbService.isDumb(project)) return emptySet()
        return CachedValuesManager.getManager(project).getCachedValue(project) {
            val psiManager = PsiManager.getInstance(project)
            val values = mutableSetOf<String>()
            PsiSearchHelper.getInstance(project).processCandidateFilesForText(
                GlobalSearchScope.projectScope(project),
                UsageSearchContext.IN_COMMENTS,
                true,
                "phoenix-view",
            ) { file ->
                psiManager.findFile(file)?.let { psiFile ->
                    PsiTreeUtil.findChildrenOfType(psiFile, PhpDocTag::class.java)
                        .filter { it.name == "@phoenix-view" }
                        .mapTo(values) { it.tagValue }
                }
                true
            }
            CachedValueProvider.Result.create(values, PsiModificationTracker.MODIFICATION_COUNT)
        }
    }

    private val TEMPLATE_METHODS = setOf("insert", "layout", "fetch")

    private val ENGINE_DIRECTORY = Regex(
        """new\s+(?:\\?[\w\\]+\\)?Engine\s*\(\s*__DIR__\s*\.\s*(['\"])([^'\"]+)\1""",
    )
}

package com.github.majesticworld.phoenixphp.completion

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/** Makes paths passed to projectDir() navigable with Ctrl/Cmd+B or Ctrl/Cmd+click. */
class ProjectDirReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(StringLiteralExpression::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext,
                ): Array<PsiReference> {
                    val literal = element as? StringLiteralExpression ?: return PsiReference.EMPTY_ARRAY
                    if (!ProjectDirPathResolver.isProjectDirArgument(literal) ||
                        ProjectDirPathResolver.resolveTarget(literal) == null
                    ) {
                        return PsiReference.EMPTY_ARRAY
                    }

                    return arrayOf(ProjectDirPathReference(literal))
                }
            },
        )
    }
}

private class ProjectDirPathReference(
    element: StringLiteralExpression,
) : PsiReferenceBase<StringLiteralExpression>(element, element.valueRange, false) {

    override fun resolve(): PsiElement? {
        val target = ProjectDirPathResolver.resolveTarget(element) ?: return null
        val psiManager = PsiManager.getInstance(element.project)

        return if (target.isDirectory) psiManager.findDirectory(target) else psiManager.findFile(target)
    }

    override fun getVariants(): Array<Any> = emptyArray()
}

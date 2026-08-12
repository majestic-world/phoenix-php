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

/** Makes view() names navigable without requiring their file extension. */
class ViewReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(StringLiteralExpression::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext,
                ): Array<PsiReference> {
                    val literal = element as? StringLiteralExpression ?: return PsiReference.EMPTY_ARRAY
                    if (!ViewPathResolver.isViewArgument(literal) ||
                        ViewPathResolver.resolveTarget(literal) == null
                    ) {
                        return PsiReference.EMPTY_ARRAY
                    }

                    return arrayOf(ViewPathReference(literal))
                }
            },
        )
    }
}

private class ViewPathReference(
    element: StringLiteralExpression,
) : PsiReferenceBase<StringLiteralExpression>(element, element.valueRange, false) {

    override fun resolve(): PsiElement? {
        val target = ViewPathResolver.resolveTarget(element) ?: return null
        val psiManager = PsiManager.getInstance(element.project)

        return if (target.isDirectory) psiManager.findDirectory(target) else psiManager.findFile(target)
    }

    override fun getVariants(): Array<Any> = emptyArray()
}

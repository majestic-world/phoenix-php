package com.github.majesticworld.phoenixphp.intentions

import com.github.majesticworld.phoenixphp.formatting.CompactThrowsPostFormatProcessor
import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.NewExpression
import com.jetbrains.php.lang.psi.elements.PhpThrowExpression

class AddCompactThrowsIntention : IntentionAction {
    override fun getText(): String = "Add compact PHPDoc @throws tags"

    override fun getFamilyName(): String = "Compact PHPDoc @throws"

    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
        target(project, editor, file) != null

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val target = target(project, editor, file) ?: return
        val pointer = SmartPointerManager.createPointer(target.function)
        target.fix.applyFix(project, target.descriptor)
        val comment = pointer.element?.docComment ?: return
        COMPACTOR.processElement(comment, CodeStyle.getSettings(file))
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        val original = IntentionPreviewUtils.getOriginalFile(file) ?: return IntentionPreviewInfo.EMPTY
        val target = target(project, editor, original) ?: return IntentionPreviewInfo.EMPTY
        val fix = target.fix.getFileModifierForPreview(file) as? LocalQuickFixOnPsiElement
            ?: return IntentionPreviewInfo.EMPTY
        val start = fix.startElement as? Function ?: return IntentionPreviewInfo.EMPTY
        val end = fix.endElement
        if (start.containingFile != file || (end != null && end.containingFile != file)) {
            return IntentionPreviewInfo.EMPTY
        }
        val pointer = SmartPointerManager.createPointer(start)
        fix.applyFix()
        val comment = pointer.element?.docComment ?: return IntentionPreviewInfo.EMPTY
        COMPACTOR.processElement(comment, CodeStyle.getSettings(file))
        return IntentionPreviewInfo.DIFF
    }

    private fun target(project: Project, editor: Editor, file: PsiFile): Target? {
        if (file !is PhpFile || !file.isPhysical || DumbService.isDumb(project)) return null
        var element = file.findElementAt(editor.caretModel.offset)
        var candidate: PsiElement? = null
        while (element != null && element !is Function) {
            if (element is PhpThrowExpression) {
                candidate = element
                break
            }
            if (candidate == null && (element is FunctionReference || element is NewExpression)) candidate = element
            element = element.parent
        }
        val expression = candidate ?: return null
        val function = PsiTreeUtil.getParentOfType(expression, Function::class.java) ?: return null
        if (function.docComment != null) return null
        val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
        val inspection = profile.getUnwrappedTool("PhpUnhandledExceptionInspection", expression) as? LocalInspectionTool
            ?: return null
        val holder = ProblemsHolder(InspectionManager.getInstance(project), file, true)
        expression.accept(inspection.buildVisitor(holder, true))
        for (descriptor in holder.results) {
            for (fix in descriptor.fixes.orEmpty()) {
                if (fix is LocalQuickFixOnPsiElement && fix.startElement == function) {
                    return Target(function, descriptor, fix)
                }
            }
        }
        return null
    }

    private data class Target(
        val function: Function,
        val descriptor: ProblemDescriptor,
        val fix: LocalQuickFixOnPsiElement,
    )

    private companion object {
        val COMPACTOR = CompactThrowsPostFormatProcessor()
    }
}

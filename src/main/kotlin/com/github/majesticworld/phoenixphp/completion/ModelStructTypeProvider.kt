package com.github.majesticworld.phoenixphp.completion

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.PhpLangUtil
import com.jetbrains.php.lang.psi.PhpPsiUtil
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.elements.PhpNamespace
import com.jetbrains.php.lang.psi.elements.PhpUse
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4
import java.util.HexFormat
import java.util.Locale

class ModelStructTypeProvider : PhpTypeProvider4 {
    override fun getKey(): Char = '\u03DE'

    override fun getType(element: PsiElement): PhpType? {
        val call = element as? MethodReference ?: return null
        val method = call.name?.lowercase(Locale.ROOT) ?: return null
        val mode = when (method) {
            "findbyid", "first", "random" -> "row"
            "findall", "wherein", "wherenotin", "search" -> "list"
            "findby", "between" -> {
                val argument = call.getParameter("all", if (method == "findby") 2 else 3)
                when {
                    argument == null -> if (method == "findby") "list" else "row"
                    PhpLangUtil.isTrue(PhpPsiUtil.unparenthesize(argument)) -> "list"
                    PhpLangUtil.isFalse(PhpPsiUtil.unparenthesize(argument)) -> "row"
                    else -> "both"
                }
            }
            else -> return null
        }
        val receiver = call.classReference ?: return null
        val result = PhpType()
        for (type in receiver.type.types) {
            val encoded = HEX.formatHex(type.toByteArray(Charsets.UTF_8))
            result.add("#${getKey()}$mode:$method:$encoded")
        }
        return result.takeUnless { it.isEmpty }
    }

    override fun complete(expression: String, project: Project): PhpType? {
        val dimensions = PhpType.getPluralDimension(expression)
        val parts = PhpType.unpluralize(expression, dimensions).substring(2).split(':', limit = 3)
        if (parts.size != 3) return null
        val receiver = String(HEX.parseHex(parts[2]), Charsets.UTF_8)
        val index = PhpIndex.getInstance(project)
        val result = PhpType()
        for (type in PhpType().add(receiver).global(project).types) {
            for (model in index.getClassesByFQN(type)) {
                val method = model.findMethodByName(parts[1]) ?: continue
                if (!method.containingClass?.fqn.equals(MODEL_CLASS, ignoreCase = true)) continue
                val struct = structClass(model, index, hashSetOf()) ?: continue
                val row = PhpType().add(struct.fqn)
                if (parts[0] != "list") result.add(row).add(PhpType.NULL)
                if (parts[0] != "row") result.add(row.pluralise())
            }
        }
        return result.takeUnless { it.isEmpty }?.pluralise(dimensions)
    }

    override fun getBySignature(
        expression: String,
        visited: MutableSet<String>,
        depth: Int,
        project: Project,
    ): Collection<PhpNamedElement> = emptyList()

    override fun emptyResultIsComplete(): Boolean = true

    private fun structClass(model: PhpClass, index: PhpIndex, visited: MutableSet<String>): PhpClass? {
        if (!visited.add(model.fqn)) return null
        val tag = model.docComment?.getTagElementsByName("@phoenix-struct")?.firstOrNull()
        if (tag != null) {
            val name = tag.tagValue.trim().takeWhile { !it.isWhitespace() }
            if (!CLASS_NAME.matches(name)) return null
            return index.getClassesByFQN(qualify(name, model)).singleOrNull()
        }
        for (parent in model.superClasses) {
            structClass(parent, index, visited)?.let { return it }
        }
        return null
    }

    private fun qualify(name: String, model: PhpClass): String {
        if (name.startsWith('\\')) return name
        val namespace = model.namespaceName.trim('\\')
        if (name.startsWith("namespace\\", ignoreCase = true)) {
            return "\\" + listOf(namespace, name.substringAfter('\\')).filter { it.isNotEmpty() }.joinToString("\\")
        }
        val scope = PsiTreeUtil.getParentOfType(model, PhpNamespace::class.java)
        val prefix = name.substringBefore('\\')
        for (use in PsiTreeUtil.findChildrenOfType(model.containingFile, PhpUse::class.java)) {
            if (use.isOfConst || use.isOfFunction || use.isTraitImport) continue
            if (PsiTreeUtil.getParentOfType(use, PhpNamespace::class.java) != scope) continue
            val alias = use.aliasName ?: use.name
            if (!alias.equals(prefix, ignoreCase = true)) continue
            val target = use.targetReference?.fqn ?: continue
            return target + name.removePrefix(prefix)
        }
        return "\\" + listOf(namespace, name).filter { it.isNotEmpty() }.joinToString("\\")
    }

    private companion object {
        const val MODEL_CLASS = "\\Rider\\System\\Database\\Model"
        val CLASS_NAME = Regex("""\\?[\p{L}_\x80-\xFF][\p{L}\p{N}_\x80-\xFF]*(?:\\[\p{L}_\x80-\xFF][\p{L}\p{N}_\x80-\xFF]*)*""")
        val HEX: HexFormat = HexFormat.of()
    }
}

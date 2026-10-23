package com.github.majesticworld.phoenixphp

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.Variable

class ModelStructTypeProviderTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "Rider/System/Database/Model.php",
            """
            <?php
            namespace Rider\System\Database;
            class Model {
                public function findById(int ${'$'}id): ?object {}
                public function first(string ${'$'}column, mixed ${'$'}value): ?object {}
                public function random(): ?object {}
                public function findAll(?int ${'$'}limit = null, int ${'$'}offset = 0): array {}
                public function whereIn(string ${'$'}column, array ${'$'}values): array {}
                public function whereNotIn(string ${'$'}column, array ${'$'}values): array {}
                public function search(string ${'$'}column, mixed ${'$'}value): array {}
                public function findBy(string ${'$'}column, mixed ${'$'}value, bool ${'$'}all = true, ?int ${'$'}limit = null, int ${'$'}offset = 0): array|object|null {}
                public function between(string ${'$'}column, mixed ${'$'}min, mixed ${'$'}max, bool ${'$'}all = false): array|object|null {}
                public function orderBy(string ${'$'}column, string ${'$'}direction): static { return ${'$'}this; }
                public function count(): int { return 0; }
                public function create(array ${'$'}values): int { return 1; }
                public function sum(string ${'$'}column): array { return []; }
                public function query(string ${'$'}sql, array ${'$'}params = []): array { return []; }
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "Seek/Horus/Struct/MailData.php",
            """
            <?php
            namespace Seek\Horus\Struct;
            class MailData {
                public int ${'$'}id;
                public ?string ${'$'}host;
                public ?int ${'$'}port;
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "Seek/Horus/Model/Mail.php",
            """
            <?php
            namespace Seek\Horus\Model;
            /** @phoenix-struct \Seek\Horus\Struct\MailData */
            class Mail extends \Rider\System\Database\Model {}
            """.trimIndent(),
        )
    }

    fun testSingleRowResultsKeepNullAndOfferStructFields() {
        for (expression in listOf("${'$'}model->findById(1)", "${'$'}model->first('id', 1)", "${'$'}model->random()")) {
            assertExpressionType(expression, ROW, NULL)
            assertRowCompletion(expression)
        }
        assertExpressionType("${'$'}model->findById(1)->id", "\\int")
        assertExpressionType("${'$'}model->findById(1)->host", "\\string", NULL)
        assertExpressionType("${'$'}model->findById(1)->port", "\\int", NULL)
    }

    fun testArrayResultsKeepTheirElementType() {
        for (expression in listOf(
            "${'$'}model->findAll()",
            "${'$'}model->whereIn('id', [1, 2])",
            "${'$'}model->whereNotIn('id', [1, 2])",
            "${'$'}model->search('host', 'mail')",
        )) {
            assertExpressionType(expression, ROW_ARRAY)
            assertExpressionType("$expression[0]", ROW)
            assertRowCompletion("$expression[0]")
        }
    }

    fun testForeachInfersStructFieldsWithoutAVarAnnotation() {
        configureConsumer(
            """
            foreach (${'$'}model->findAll() as ${'$'}row) {
                ${'$'}ro<caret>w;
            }
            """.trimIndent(),
        )
        assertCaretVariableType(ROW)
        configureConsumer(
            """
            foreach (${'$'}model->findAll() as ${'$'}row) {
                ${'$'}row-><caret>;
            }
            """.trimIndent(),
        )
        assertStructFieldsInCompletion()
    }

    fun testFluentOrderByRemainsAModelAndThenResolvesRows() {
        assertExpressionType("${'$'}model->orderBy('id', 'DESC')", "\\Seek\\Horus\\Model\\Mail")
        val row = "${'$'}model->orderBy('id', 'DESC')->findById(1)"
        assertExpressionType(row, ROW, NULL)
        assertRowCompletion(row)
        assertExpressionType("${'$'}model->orderBy('id', 'DESC')->findAll()", ROW_ARRAY)
    }

    fun testFindByDistinguishesDefaultTrueFalseAndUnknownBoolean() {
        assertExpressionType("${'$'}model->findBy('id', 1)", ROW_ARRAY)
        assertExpressionType("${'$'}model->findBy('id', 1, true)", ROW_ARRAY)
        assertExpressionType("${'$'}model->findBy('id', 1, false)", ROW, NULL)
        assertExpressionType("${'$'}model->findBy('id', 1, ${'$'}all)", ROW_ARRAY, ROW, NULL)
        assertRowCompletion("${'$'}model->findBy('id', 1, false)")
        assertRowCompletion("${'$'}model->findBy('id', 1, true)[0]")
    }

    fun testBetweenDistinguishesDefaultFalseTrueAndUnknownBoolean() {
        assertExpressionType("${'$'}model->between('id', 1, 10)", ROW, NULL)
        assertExpressionType("${'$'}model->between('id', 1, 10, false)", ROW, NULL)
        assertExpressionType("${'$'}model->between('id', 1, 10, true)", ROW_ARRAY)
        assertExpressionType("${'$'}model->between('id', 1, 10, ${'$'}all)", ROW_ARRAY, ROW, NULL)
        assertRowCompletion("${'$'}model->between('id', 1, 10)")
        assertRowCompletion("${'$'}model->between('id', 1, 10, true)[0]")
    }

    fun testNamedAllArgumentCanAppearBeforeOtherArguments() {
        assertExpressionType("${'$'}model->findBy(all: false, value: 1, column: 'id')", ROW, NULL)
        assertExpressionType("${'$'}model->findBy(value: 1, column: 'id', all: true)", ROW_ARRAY)
        assertExpressionType("${'$'}model->between(all: true, max: 10, column: 'id', min: 1)", ROW_ARRAY)
        assertRowCompletion("${'$'}model->findBy(all: false, value: 1, column: 'id')")
    }

    fun testStructAnnotationResolvesImportedNamesAndAliases() {
        myFixture.addFileToProject(
            "Seek/Horus/Model/ImportedMail.php",
            """
            <?php
            namespace Seek\Horus\Model;
            use Seek\Horus\Struct\MailData;
            /** @phoenix-struct MailData */
            class ImportedMail extends \Rider\System\Database\Model {}
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "Seek/Horus/Model/AliasedMail.php",
            """
            <?php
            namespace Seek\Horus\Model;
            use Seek\Horus\Struct\MailData as RowData;
            /** @phoenix-struct RowData */
            class AliasedMail extends \Rider\System\Database\Model {}
            """.trimIndent(),
        )
        for (model in listOf("ImportedMail", "AliasedMail")) {
            val expression = "(new \\Seek\\Horus\\Model\\$model())->findById(1)"
            assertExpressionType(expression, ROW, NULL)
            assertRowCompletion(expression)
        }
    }

    fun testStructAnnotationResolvesNamesRelativeToModelNamespace() {
        myFixture.addFileToProject(
            "Seek/Horus/Struct/LocalMail.php",
            """
            <?php
            namespace Seek\Horus\Struct;
            /** @phoenix-struct MailData */
            class LocalMail extends \Rider\System\Database\Model {}
            """.trimIndent(),
        )
        val expression = "(new \\Seek\\Horus\\Struct\\LocalMail())->findById(1)"
        assertExpressionType(expression, ROW, NULL)
        assertRowCompletion(expression)
    }

    fun testNonRowMethodsKeepNativeTypes() {
        assertExpressionType("${'$'}model->count()", "\\int")
        assertExpressionType("${'$'}model->create(['host' => 'mail'])", "\\int")
        assertExpressionType("${'$'}model->sum('port')", "\\array")
        assertExpressionType("${'$'}model->query('SELECT COUNT(*) AS total FROM mail')", "\\array")
    }

    fun testUnannotatedModelsAndUnrelatedClassesAreNotRetyped() {
        myFixture.addFileToProject(
            "OtherModels.php",
            """
            <?php
            namespace Other;
            class UnannotatedMail extends \Rider\System\Database\Model {}
            /** @phoenix-struct \Seek\Horus\Struct\MailData */
            class NotAModel {
                public function findById(int ${'$'}id): ?object {}
                public function findAll(): array {}
            }
            """.trimIndent(),
        )
        for (model in listOf("UnannotatedMail", "NotAModel")) {
            assertExpressionType("(new \\Other\\$model())->findById(1)", "\\object", NULL)
            assertExpressionType("(new \\Other\\$model())->findAll()", "\\array")
        }
    }

    fun testModelOverridesKeepTheirOwnReturnTypes() {
        myFixture.addFileToProject(
            "Seek/Horus/Model/CustomMail.php",
            """
            <?php
            namespace Seek\Horus\Model;
            class CustomResult {
                public string ${'$'}customValue;
            }
            /** @phoenix-struct \Seek\Horus\Struct\MailData */
            class CustomMail extends \Rider\System\Database\Model {
                public function findById(int ${'$'}id): ?CustomResult {}
                public function findAll(?int ${'$'}limit = null, int ${'$'}offset = 0): array {}
            }
            """.trimIndent(),
        )
        assertExpressionType(
            "(new \\Seek\\Horus\\Model\\CustomMail())->findById(1)",
            "\\Seek\\Horus\\Model\\CustomResult",
            NULL,
        )
        assertExpressionType("(new \\Seek\\Horus\\Model\\CustomMail())->findAll()", "\\array")
    }

    private fun assertExpressionType(expression: String, vararg expected: String) {
        configureConsumer("${'$'}result = $expression;\n${'$'}res<caret>ult;")
        assertCaretVariableType(*expected)
    }

    private fun assertCaretVariableType(vararg expected: String) {
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)
        val variable = PsiTreeUtil.getParentOfType(leaf, Variable::class.java, false)
        assertNotNull("Expected a PHP variable at the caret", variable)
        val actual = variable!!.type.global(project).filterMixed().filterUnknown().types.toSet()
        if (expected.any { it == ROW || it == ROW_ARRAY }) {
            assertEquals(
                "Struct shape of ${variable.text}",
                expected.filter { it != NULL }.toSet(),
                actual.filter { it.startsWith("\\Seek\\Horus\\Struct\\") }.toSet(),
            )
            if (NULL in expected) assertTrue("Missing nullable result in $actual", NULL in actual)
        } else {
            assertEquals("Resolved type of ${variable.text}", expected.toSet(), actual)
        }
    }

    private fun assertRowCompletion(expression: String) {
        configureConsumer("$expression-><caret>;")
        assertStructFieldsInCompletion()
    }

    private fun assertStructFieldsInCompletion() {
        myFixture.completeBasic()
        val suggestions = myFixture.lookupElementStrings.orEmpty().toSet()
        for (field in listOf("id", "host", "port")) {
            assertTrue("Missing $field in completion: $suggestions", field in suggestions)
        }
    }

    private fun configureConsumer(body: String) {
        myFixture.configureByText(
            "consumer.php",
            """
            <?php
            namespace Consumer;
            use Seek\Horus\Model\Mail;
            function inspectMail(bool ${'$'}all): void {
                ${'$'}model = new Mail();
                $body
            }
            """.trimIndent(),
        )
    }

    companion object {
        private const val ROW = "\\Seek\\Horus\\Struct\\MailData"
        private const val ROW_ARRAY = "\\Seek\\Horus\\Struct\\MailData[]"
        private const val NULL = "\\null"
    }
}

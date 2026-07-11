package com.nexarag.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模块依赖架构测试。
 */
class ModuleDependencyTest {

    private final JavaClasses classes = new ClassFileImporter().importPackages("com.nexarag");

    @Test
    void commonShouldNotDependOnBusinessModules() {
        noClasses()
                .that().resideInAPackage("com.nexarag.common..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.nexarag.document..",
                        "com.nexarag.chat..",
                        "com.nexarag.retrieval..",
                        "com.nexarag.workflow..",
                        "com.nexarag.model..",
                        "com.nexarag.auth.."
                )
                .check(classes);
    }

    @Test
    void infraShouldNotDependOnBusinessModules() {
        noClasses()
                .that().resideInAPackage("com.nexarag.infra..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.nexarag.document..",
                        "com.nexarag.chat..",
                        "com.nexarag.retrieval..",
                        "com.nexarag.workflow..",
                        "com.nexarag.auth.."
                )
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void businessModulesShouldNotDependOnWorkflow() {
        noClasses()
                .that().resideInAnyPackage(
                        "com.nexarag.document..",
                        "com.nexarag.chat..",
                        "com.nexarag.retrieval..",
                        "com.nexarag.model.."
                )
                .should().dependOnClassesThat().resideInAPackage("com.nexarag.workflow..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void controllersShouldNotDependOnMappers() {
        noClasses()
                .that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..mapper..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void legacyDocumentQueueAndLocalWorkerShouldBeRemoved() {
        // 1. 验证旧Redis文档队列包不再包含任何类
        assertThat(classes.stream()
                .filter(javaClass -> javaClass.getPackageName().startsWith("com.nexarag.infra.queue.document")))
                .isEmpty();

        // 2. 验证本地Worker和旧任务投递接口不再存在
        assertThat(classes.stream().map(javaClass -> javaClass.getSimpleName()))
                .doesNotContain("LocalDocumentPipelineWorker", "DocumentPipelineWorkerProperties",
                        "DocumentProcessTaskDispatcher", "DocumentPipelineQueue");
    }
}

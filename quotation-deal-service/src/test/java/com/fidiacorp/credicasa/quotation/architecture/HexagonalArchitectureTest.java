package com.fidiacorp.credicasa.quotation.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Driver ASR-MOD (QA-04) para MS-4 quotation-deal-service: Arquitectura Hexagonal + DDD verificada en cada build.
 * Además de las reglas de capas, el microservicio no puede depender del código de otro bounded context
 * (solo del shared kernel {@code com.fidiacorp.credicasa.shared}).
 */
@AnalyzeClasses(packages = "com.fidiacorp.credicasa.quotation", importOptions = {ImportOption.DoNotIncludeTests.class})
public class HexagonalArchitectureTest {

    @ArchTest
    public static final ArchRule domainMustNotDependOnInfrastructure =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                    .because("ASR-MOD: El dominio debe permanecer agnóstico de frameworks, JPA y detalles de infraestructura.");

    @ArchTest
    public static final ArchRule domainMustNotDependOnApplication =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("..application..")
                    .because("ASR-MOD: El núcleo de dominio no depende de la capa de orquestación.");

    @ArchTest
    public static final ArchRule domainMustNotDependOnInterfaces =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("..interfaces..")
                    .because("ASR-MOD: El dominio no conoce controladores REST ni OpenAPI.");

    @ArchTest
    public static final ArchRule domainMustNotDependOnSpringOrJpa =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..")
                    .because("ASR-MOD: El dominio es Java puro; Spring y JPA viven en los adaptadores.");

    @ArchTest
    public static final ArchRule applicationMustNotDependOnInfrastructure =
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                    .because("ASR-MOD: La aplicación usa infraestructura solo mediante puertos de salida.");

    @ArchTest
    public static final ArchRule noDependencyOnOtherBoundedContexts =
            noClasses().should().dependOnClassesThat().resideInAnyPackage("com.fidiacorp.credicasa.identity..", "com.fidiacorp.credicasa.catalog..", "com.fidiacorp.credicasa.pricing..", "com.fidiacorp.credicasa.communication..")
                    .because("ADR-001/PA-05: cada microservicio es desplegable de forma independiente; la colaboración es por API o eventos.");

    @ArchTest
    public static final ArchRule hexagonalLayersRespectDependencies =
            layeredArchitecture().consideringOnlyDependenciesInLayers()
                    .layer("Domain").definedBy("..domain..")
                    .layer("Application").definedBy("..application..")
                    .layer("Infrastructure").definedBy("..infrastructure..")
                    .layer("Interfaces").definedBy("..interfaces..")
                    .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Interfaces")
                    .whereLayer("Application").mayOnlyBeAccessedByLayers("Interfaces", "Infrastructure")
                    .because("Las dependencias apuntan hacia el centro (Inversión de Dependencias).");
}

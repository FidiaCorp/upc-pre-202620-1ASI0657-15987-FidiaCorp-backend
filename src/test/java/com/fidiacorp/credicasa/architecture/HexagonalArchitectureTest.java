package com.fidiacorp.credicasa.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Driver 3: Modificabilidad e Interoperabilidad (ASR-MOD)
 * Pruebas de Arquitectura Automatizadas con ArchUnit para salvaguardar
 * la Arquitectura Hexagonal y Domain-Driven Design (DDD).
 */
@AnalyzeClasses(packages = "com.fidiacorp.credicasa", importOptions = {ImportOption.DoNotIncludeTests.class})
public class HexagonalArchitectureTest {

    @ArchTest
    public static final ArchRule domainMustNotDependOnInfrastructure =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                    .because("ASR-MOD: El dominio debe permanecer completamente agnóstico de frameworks, JPA, y detalles de infraestructura.");

    @ArchTest
    public static final ArchRule domainMustNotDependOnApplication =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("..application..")
                    .because("ASR-MOD: El núcleo de dominio no debe depender de la capa de orquestación de aplicación.");

    @ArchTest
    public static final ArchRule domainMustNotDependOnInterfaces =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("..interfaces..")
                    .because("ASR-MOD: El dominio no debe tener conocimiento de controladores REST ni OpenAPI.");

    @ArchTest
    public static final ArchRule applicationMustNotDependOnInfrastructure =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                    .because("ASR-MOD: La capa de aplicación interactúa con infraestructura únicamente a través de puertos de salida (Ports & Adapters).");

    @ArchTest
    public static final ArchRule hexagonalLayersRespectDependencies =
            layeredArchitecture()
                    .consideringAllDependencies()
                    .layer("Domain").definedBy("..domain..")
                    .layer("Application").definedBy("..application..")
                    .layer("Infrastructure").definedBy("..infrastructure..")
                    .layer("Interfaces").definedBy("..interfaces..")
                    .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Interfaces")
                    .whereLayer("Application").mayOnlyBeAccessedByLayers("Interfaces", "Infrastructure")
                    .because("Las dependencias deben apuntar hacia el centro (Inversión de Dependencias en Arquitectura Hexagonal).");
}

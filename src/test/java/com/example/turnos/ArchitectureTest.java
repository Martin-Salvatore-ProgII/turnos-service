package com.example.turnos;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Verifica la regla de dependencias de la arquitectura hexagonal de la cátedra (skill
 * {@code /hexagonal}) sobre todas las features del servicio.
 */
@AnalyzeClasses(packages = "com.example.turnos", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	// allowEmptyShould: una capa todavía sin clases no hace fallar la regla.

	@ArchTest
	static final ArchRule domainDoesNotDependOnOtherLayers = noClasses()
			.that().resideInAPackage("..domain..")
			.should().dependOnClassesThat().resideInAnyPackage("..application..", "..infrastructure..")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule domainDoesNotDependOnFrameworks = noClasses()
			.that().resideInAPackage("..domain..")
			.should().dependOnClassesThat().resideInAnyPackage(
					"org.springframework..",
					"jakarta.persistence..",
					"jakarta.validation..",
					"com.fasterxml.jackson..",
					"tools.jackson..")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule applicationDoesNotDependOnInfrastructure = noClasses()
			.that().resideInAPackage("..application..")
			.should().dependOnClassesThat().resideInAPackage("..infrastructure..")
			.allowEmptyShould(true);

}

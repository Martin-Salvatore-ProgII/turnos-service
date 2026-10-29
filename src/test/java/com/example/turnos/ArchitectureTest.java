package com.example.turnos;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

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

	// Una feature solo usa de otra los puertos y modelos de su domain, nunca su application ni su
	// infrastructure (ADR-0062). shared no es una feature: se usa desde cualquier lado.
	@ArchTest
	static final ArchRule featuresOnlyUseTheDomainOfOtherFeatures = classes()
			.that().resideInAPackage("com.example.turnos.*..")
			.and().resideOutsideOfPackage("com.example.turnos.shared..")
			.should(notUseTheApplicationOrInfrastructureOfAnotherFeature());

	private static final String ROOT = "com.example.turnos.";

	private static ArchCondition<JavaClass> notUseTheApplicationOrInfrastructureOfAnotherFeature() {
		return new ArchCondition<>("not use the application or infrastructure of another feature") {
			@Override
			public void check(JavaClass origin, ConditionEvents events) {
				String originFeature = featureOf(origin.getPackageName());
				for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
					String targetPackage = dependency.getTargetClass().getPackageName();
					String targetFeature = featureOf(targetPackage);
					boolean anotherFeature = targetFeature != null && !targetFeature.equals("shared")
							&& !targetFeature.equals(originFeature);
					boolean notItsDomain = targetPackage.startsWith(ROOT + targetFeature + ".application")
							|| targetPackage.startsWith(ROOT + targetFeature + ".infrastructure");
					if (anotherFeature && notItsDomain) {
						events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
					}
				}
			}
		};
	}

	// El primer paquete debajo de com.example.turnos es la feature (o shared).
	private static String featureOf(String packageName) {
		if (!packageName.startsWith(ROOT)) {
			return null;
		}
		String rest = packageName.substring(ROOT.length());
		int dot = rest.indexOf('.');
		return dot < 0 ? rest : rest.substring(0, dot);
	}

}

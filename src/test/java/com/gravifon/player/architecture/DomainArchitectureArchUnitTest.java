package com.gravifon.player.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.jmolecules.archunit.JMoleculesArchitectureRules;
import org.jmolecules.archunit.JMoleculesDddRules;

@AnalyzeClasses(packages = "com.gravifon.player", importOptions = ImportOption.DoNotIncludeTests.class)
public class DomainArchitectureArchUnitTest {
    @ArchTest
    ArchRule dddRules = JMoleculesDddRules.all();
    @ArchTest
    ArchRule architectureRules = JMoleculesArchitectureRules.ensureOnionSimple();
    @ArchTest
    ArchRule domainModelShouldNotDependOnSpringOrJakarta = noClasses()
        .that()
        .resideInAPackage("com.gravifon.player..model..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springframework..", "jakarta.servlet..", "jakarta.persistence..");
}

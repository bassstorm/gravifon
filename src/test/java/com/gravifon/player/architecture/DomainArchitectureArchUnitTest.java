package com.gravifon.player.architecture;

import static com.tngtech.archunit.lang.conditions.ArchConditions.not;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.ArrayList;
import java.util.List;

@AnalyzeClasses(packages = "com.gravifon.player", importOptions = ImportOption.DoNotIncludeTests.class)
class DomainArchitectureArchUnitTest {
    private static final String PLAYER = "com.gravifon.player";
    @ArchTest
    static final ArchRule sessionSliceDoesNotDependOnOtherSlices = noDependencyFrom(
            PLAYER + "..session..",
            PLAYER + "..registry..",
            PLAYER + "..playlist..",
            PLAYER + "..playback..",
            PLAYER + "..streaming..",
            PLAYER + "..api..",
            PLAYER + "..observability.."
    );
    @ArchTest
    static final ArchRule registrySliceDoesNotDependOnOtherSlices = noDependencyFrom(
            PLAYER + "..registry..",
            PLAYER + "..session..",
            PLAYER + "..playlist..",
            PLAYER + "..playback..",
            PLAYER + "..streaming..",
            PLAYER + "..api..",
            PLAYER + "..observability.."
    );
    @ArchTest
    static final ArchRule playlistSliceDoesNotDependOnForbiddenPackages = noDependencyFrom(
            PLAYER + "..playlist..",
            PLAYER + "..playback..",
            PLAYER + "..streaming..",
            PLAYER + "..api..",
            PLAYER + "..observability..",
            PLAYER + "..session.service..",
            PLAYER + "..registry.service..",
            PLAYER + "..registry.scan..",
            PLAYER + "..registry.metadata..",
            PLAYER + "..registry.stream.."
    );
    @ArchTest
    static final ArchRule playbackSliceDoesNotDependOnForbiddenPackages = noDependencyFrom(
            PLAYER + "..playback..",
            PLAYER + "..playlist.service..",
            PLAYER + "..playlist.repository..",
            PLAYER + "..streaming..",
            PLAYER + "..api..",
            PLAYER + "..observability.."
    );
    @ArchTest
    static final ArchRule streamingSliceDoesNotDependOnForbiddenPackages = noDependencyFrom(
            PLAYER + "..streaming..",
            PLAYER + "..playlist..",
            PLAYER + "..persistence..",
            PLAYER + "..api..",
            PLAYER + "..observability.."
    );
    @ArchTest
    static final ArchRule persistenceDoesNotDependOnSlicesOrEdges = noDependencyFrom(
            PLAYER + "..persistence..",
            PLAYER + "..session..",
            PLAYER + "..registry..",
            PLAYER + "..playlist..",
            PLAYER + "..playback..",
            PLAYER + "..streaming..",
            PLAYER + "..api..",
            PLAYER + "..config..",
            PLAYER + "..observability.."
    );
    @ArchTest
    static final ArchRule apiDoesNotDependOnPersistence =
            noDependencyFrom(PLAYER + "..api..", PLAYER + "..persistence..");
    @ArchTest
    static final ArchRule domainModelsRemainPure = noClasses()
        .that()
        .resideInAnyPackage(PLAYER + "..model..", PLAYER + "..identity..")
        .and()
        .haveNameNotMatching(PLAYER + "\\.api\\.model\\..*")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
                "org.springframework..",
                "jakarta..",
                "org.jaudiotagger..",
                "lombok..",
                PLAYER + "..service..",
                PLAYER + "..repository..",
                PLAYER + "..persistence..",
                PLAYER + "..api..",
                PLAYER + "..config..",
                PLAYER + "..observability..",
                PLAYER + "..streaming..",
                PLAYER + "..scan..",
                PLAYER + "..metadata..",
                PLAYER + "..stream.."
        )
        .allowEmptyShould(true);
    @ArchTest
    static final ArchRule applicationPackagesDoNotDependOnPresentationOrPersistence = noClasses()
        .that()
        .resideInAnyPackage(PLAYER + "..service..", PLAYER + "..scan..", PLAYER + "..stream..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(PLAYER + "..api..", PLAYER + "..persistence..", "jakarta.servlet..")
        .allowEmptyShould(true);
    @ArchTest
    static final ArchRule repositoryPublicMethodsExposeOnlyDomainTypes = classes()
        .that()
        .resideInAPackage(PLAYER + "..repository..")
        .should(not(new HavePublicMethodsExposingPersistenceTypes()))
        .allowEmptyShould(true);

    private static ArchRule noDependencyFrom(String sourcePackage, String... forbiddenPackages) {
        return noClasses()
            .that()
            .resideInAPackage(sourcePackage)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(forbiddenPackages)
            .allowEmptyShould(true);
    }

    private static final class HavePublicMethodsExposingPersistenceTypes extends ArchCondition<JavaClass> {
        private HavePublicMethodsExposingPersistenceTypes() {
            super("have public methods that expose persistence or Spring Data types");
        }

        @Override
        public void check(JavaClass item, ConditionEvents events) {
            for (JavaMethod method : item.getMethods()) {
                if (!method.getModifiers().contains(JavaModifier.PUBLIC)) {
                    continue;
                }

                List<String> exposedTypes = new ArrayList<>();
                method
                    .getReturnType()
                    .getAllInvolvedRawTypes()
                    .stream()
                    .filter(DomainArchitectureArchUnitTest::isPersistenceType)
                    .map(JavaClass::getName)
                    .forEach(exposedTypes::add);
                method
                    .getParameters()
                    .stream()
                    .flatMap(parameter -> parameter.getType().getAllInvolvedRawTypes().stream())
                    .filter(DomainArchitectureArchUnitTest::isPersistenceType)
                    .map(JavaClass::getName)
                    .forEach(exposedTypes::add);

                if (!exposedTypes.isEmpty()) {
                    events.add(SimpleConditionEvent.violated(item, item.getName() + " exposes " + exposedTypes));
                }
            }
        }
    }

    private static boolean isPersistenceType(JavaClass type) {
        return type.getPackageName().startsWith(PLAYER + ".persistence")
                || type.getPackageName().startsWith("org.springframework.data");
    }
}

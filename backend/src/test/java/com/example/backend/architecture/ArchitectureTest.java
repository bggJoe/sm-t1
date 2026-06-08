package com.example.backend.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Phase 3 Layer 2: Architecture rules derived from ARCHITECTURE-LAYERS.md.
 *
 * Package conventions (per module, e.g. registration):
 *   Presentation  : com.example.backend.*.web
 *   Application   : com.example.backend.*.application
 *   Domain        : com.example.backend.*.domain
 *   Infrastructure: com.example.backend.*.infrastructure
 */
class ArchitectureTest {

    static JavaClasses classes;

    /** Test classes only — for ARCH-RULE-005 */
    static JavaClasses testClasses;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.example.backend");
        testClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages("com.example.backend");
    }

    /** ARCH-RULE-001: Presentation -> Infrastructure FORBIDDEN */
    @Test
    void presentationMustNotDependOnInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..web..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .because("Presentation must not bypass Application layer to reach Infrastructure")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    /** ARCH-RULE-002: Application -> Infrastructure impl FORBIDDEN */
    @Test
    void applicationMustNotDependOnInfrastructureImpl() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .because("Application layer must depend on Port interfaces, not Infrastructure implementations")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    /** ARCH-RULE-003: Domain -> anything FORBIDDEN */
    @Test
    void domainMustNotDependOnOtherLayers() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.example.backend..web..",
                        "com.example.backend..application..",
                        "com.example.backend..infrastructure.."
                )
                .because("Domain layer is the innermost layer and must not depend outward")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    /** ARCH-RULE-004: anything -> Presentation FORBIDDEN */
    @Test
    void noLayerShouldDependOnPresentation() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..application..", "..domain..", "..infrastructure..")
                .should().dependOnClassesThat().resideInAPackage("com.example.backend..web..")
                .because("No backend layer has a reason to depend on Presentation")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    /**
     * ARCH-RULE-005: Test classes in ..application.. must not depend on ..infrastructure..
     *
     * DO_NOT_INCLUDE_TESTS means ARCH-RULE-002 does not cover test code.
     * This rule closes the gap: unit tests in the Application layer must mock
     * Port interfaces, not wire in Infrastructure implementations directly.
     */
    @Test
    void applicationTestsMustNotDependOnInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .because("Application-layer tests must use mocked Port interfaces, not Infrastructure implementations")
                .allowEmptyShould(true);
        rule.check(testClasses);
    }

    /**
     * ARCH-RULE-006: SecurityFilterChain must only be referenced inside the security module.
     *
     * Prevents other modules from declaring their own SecurityFilterChain beans,
     * which would create conflicting filter chain configurations.
     * (See DEC-015: PUBLIC path declaration mechanism)
     */
    @Test
    void onlySecurityModuleCanDependOnSecurityFilterChain() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("..security..")
                .should().dependOnClassesThat()
                .resideInAPackage("org.springframework.security.web..")
                .because("Spring Security web classes (including SecurityFilterChain) must only be configured in the security module")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    /**
     * ARCH-RULE-007: admin module must not depend on registration domain or infrastructure.
     *
     * Enforces DEC-019 (admin = thin Portal): admin may only reference registration's
     * public application-layer UseCase interfaces, never its entities or JPA repositories.
     * Two sub-rules, both must pass.
     */
    @Test
    void adminMustNotDependOnRegistrationDomainOrInfrastructure() {
        ArchRule noDomain = noClasses()
                .that().resideInAPackage("com.example.backend.admin..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.example.backend.registration.domain..")
                .because("admin module must access registration only via UseCase interfaces (DEC-019), not domain entities")
                .allowEmptyShould(true);

        ArchRule noInfrastructure = noClasses()
                .that().resideInAPackage("com.example.backend.admin..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.example.backend.registration.infrastructure..")
                .because("admin module must access registration only via UseCase interfaces (DEC-019), not infrastructure")
                .allowEmptyShould(true);

        noDomain.check(classes);
        noInfrastructure.check(classes);
    }

    /**
     * ARCH-RULE-008: account module Application layer must not depend on Infrastructure.
     * Mirrors ARCH-RULE-002 scoped to account module.
     */
    @Test
    void accountApplicationMustNotDependOnInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.example.backend.account.application..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.example.backend.account.infrastructure..")
                .because("account Application layer must depend on AccountRepository port interface, not JPA implementations")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    /**
     * ARCH-RULE-009: account module Domain layer must not depend on other layers.
     * Mirrors ARCH-RULE-003 scoped to account module.
     */
    @Test
    void accountDomainMustNotDependOnOtherLayers() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.example.backend.account.domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.example.backend.account.application..",
                        "com.example.backend.account.infrastructure..",
                        "com.example.backend.account..web.."
                )
                .because("account Domain layer must be independent of all other layers")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    /**
     * ARCH-RULE-010: admin module must not depend on account domain or infrastructure.
     * Enforces DEC-021: admin accesses account only via CreateAccountUseCase interface.
     */
    @Test
    void adminMustNotDependOnAccountDomainOrInfrastructure() {
        ArchRule noDomain = noClasses()
                .that().resideInAPackage("com.example.backend.admin..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.example.backend.account.domain..")
                .because("admin module must access account only via CreateAccountUseCase interface (DEC-021), not domain entities")
                .allowEmptyShould(true);

        ArchRule noInfrastructure = noClasses()
                .that().resideInAPackage("com.example.backend.admin..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.example.backend.account.infrastructure..")
                .because("admin module must access account only via CreateAccountUseCase interface (DEC-021), not infrastructure")
                .allowEmptyShould(true);

        noDomain.check(classes);
        noInfrastructure.check(classes);
    }
}

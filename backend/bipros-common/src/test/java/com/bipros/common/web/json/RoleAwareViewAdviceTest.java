package com.bipros.common.web.json;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure-logic test for {@link RoleAwareViewAdvice#narrower(Class, Class)} — proves the FLS
 * "explicit @JsonView can never widen past the role-derived view" rule.
 */
@DisplayName("RoleAwareViewAdvice narrower(roleView, explicit)")
class RoleAwareViewAdviceTest {

    @Test
    @DisplayName("Identical views → return either")
    void identicalViewsReturnSame() {
        assertThat(RoleAwareViewAdvice.narrower(Views.Public.class, Views.Public.class))
                .isEqualTo(Views.Public.class);
    }

    @Test
    @DisplayName("Explicit Admin requested by Public-role caller → role wins (Public)")
    void wideExplicitDoesNotWidenNarrowRole() {
        // Caller has roleView=Public; controller annotated @JsonView(Admin). Without the fix
        // the advice would return Admin and leak admin-only fields. With the fix, returns Public.
        assertThat(RoleAwareViewAdvice.narrower(Views.Public.class, Views.Admin.class))
                .isEqualTo(Views.Public.class);
    }

    @Test
    @DisplayName("Explicit Internal requested by Admin-role caller → explicit wins (Internal)")
    void narrowerExplicitWinsOverWideRole() {
        // Caller is ADMIN (roleView=Admin); controller deliberately asked for Internal-only.
        // We honour the controller's narrower intent.
        assertThat(RoleAwareViewAdvice.narrower(Views.Admin.class, Views.Internal.class))
                .isEqualTo(Views.Internal.class);
    }

    @Test
    @DisplayName("Explicit FinanceConfidential requested by FINANCE-role caller → either (same)")
    void equalExplicitAndRole() {
        assertThat(RoleAwareViewAdvice.narrower(Views.FinanceConfidential.class, Views.FinanceConfidential.class))
                .isEqualTo(Views.FinanceConfidential.class);
    }

    @Test
    @DisplayName("Explicit Public on FINANCE-role response → Public wins (controller's narrower intent)")
    void publicExplicitOnFinanceRole() {
        assertThat(RoleAwareViewAdvice.narrower(Views.FinanceConfidential.class, Views.Public.class))
                .isEqualTo(Views.Public.class);
    }

    @Test
    @DisplayName("Unrelated marker class (not in Views hierarchy) → role view wins as safe ceiling")
    void unrelatedExplicitFallsBackToRole() {
        assertThat(RoleAwareViewAdvice.narrower(Views.Internal.class, UnrelatedView.class))
                .isEqualTo(Views.Internal.class);
    }

    /** Marker outside our {@link Views} hierarchy — neither extends nor is extended by the others. */
    interface UnrelatedView {}
}

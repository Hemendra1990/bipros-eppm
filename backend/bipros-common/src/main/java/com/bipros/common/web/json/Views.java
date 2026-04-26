package com.bipros.common.web.json;

/**
 * Marker classes for Jackson {@code @JsonView}-based field-level masking.
 *
 * <p>The hierarchy is least-privileged at the top:
 * <pre>
 *   Public  ⊂  Internal  ⊂  FinanceConfidential  ⊂  Admin
 * </pre>
 *
 * <p>A field annotated with {@code @JsonView(Views.FinanceConfidential.class)} is serialised
 * only when the active view is {@code FinanceConfidential} or {@code Admin}. The active view is
 * picked per-request by {@link RoleAwareViewAdvice} from the caller's roles.
 *
 * <p>Role → view mapping (see {@link RoleAwareViewAdvice}):
 * <ul>
 *   <li>{@code ROLE_ADMIN} → {@link Admin}</li>
 *   <li>{@code ROLE_FINANCE} → {@link FinanceConfidential}</li>
 *   <li>{@code ROLE_PMO}, {@code ROLE_EXECUTIVE}, {@code ROLE_PROJECT_MANAGER} → {@link Internal}</li>
 *   <li>everyone else (incl. {@code ROLE_TEAM_MEMBER}, {@code ROLE_CLIENT}, {@code ROLE_VIEWER}) → {@link Public}</li>
 * </ul>
 */
public final class Views {

    private Views() {}

    /** Default. Visible to every authenticated user. */
    public interface Public {}

    /** Internal project metadata (PM / PMO / Executive). */
    public interface Internal extends Public {}

    /** Money-impacting fields: budgets, costs, contract values, payment terms, rates. */
    public interface FinanceConfidential extends Internal {}

    /** Admin-only PII / system fields: email, mobile, contract end-date, etc. */
    public interface Admin extends FinanceConfidential {}
}

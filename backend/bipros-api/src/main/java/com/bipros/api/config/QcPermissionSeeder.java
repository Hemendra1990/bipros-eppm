package com.bipros.api.config;

import com.bipros.security.domain.model.Profile;
import com.bipros.security.domain.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Grants the NCR.* permission family — which gates the Quality Control module
 * (QC test sessions, test-type master, dashboard) plus NCRs — to the Sarooj
 * profiles that should hold it (QC round, 2026-08-19).
 *
 * <p><b>Self-healing, additive-only</b>: same contract as {@link StorePermissionSeeder} —
 * runs every boot, ADDS missing codes to the listed profiles, never removes
 * anything, so admin customisations survive.
 *
 * <p>Grants (owner decision 2026-08-19): Quality Engineer keeps the full family;
 * PM / Construction Manager / Project Control / QS can log and edit test
 * sessions; Engineer / Supervisor / Site Manager get read-only so they can see
 * FAIL results for their work sections and re-raise the RFIs (client ask).
 * Engineer / Supervisor additionally get RFI.CREATE so they can raise that RFI
 * themselves (Site Manager and the senior tier already qualify via
 * DOCUMENT.CREATE). All eight profiles already hold ACTIVITY.READ for the
 * form's activity picker.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class QcPermissionSeeder {

    private final ProfileRepository profileRepository;

    private static final Set<String> QC_WRITE = Set.of("NCR.READ", "NCR.CREATE", "NCR.UPDATE");
    private static final Set<String> QC_READ_RAISE_RFI = Set.of("NCR.READ", "RFI.CREATE");

    private static final Map<String, Set<String>> GRANTS = Map.of(
        "SAROOJ_QUALITY_ENGINEER", Set.of("NCR.READ", "NCR.CREATE", "NCR.UPDATE", "NCR.APPROVE"),
        "SAROOJ_PM", QC_WRITE,
        "SAROOJ_CONSTRUCTION_MANAGER", QC_WRITE,
        "SAROOJ_PROJECT_CONTROL", QC_WRITE,
        "SAROOJ_QS", QC_WRITE,
        "SAROOJ_ENGINEER", QC_READ_RAISE_RFI,
        "SAROOJ_SUPERVISOR", QC_READ_RAISE_RFI,
        "SAROOJ_SITE_MANAGER", Set.of("NCR.READ")
    );

    public void seed() {
        GRANTS.forEach((profileCode, codes) -> profileRepository.findByCode(profileCode)
            .ifPresent(profile -> addMissing(profile, codes)));
    }

    private void addMissing(Profile profile, Set<String> codes) {
        Set<String> missing = new java.util.HashSet<>(codes);
        missing.removeAll(profile.getPermissions());
        if (missing.isEmpty()) return;
        profile.getPermissions().addAll(missing);
        profileRepository.save(profile);
        log.info("Granted {} to profile {}", missing, profile.getCode());
    }
}

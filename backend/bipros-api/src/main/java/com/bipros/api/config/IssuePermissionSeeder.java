package com.bipros.api.config;

import com.bipros.security.domain.model.Profile;
import com.bipros.security.domain.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Fixes the ISSUE.* grant holes found in the issues parity round (owner decision
 * 2026-08-20): the PM profile could not log issues at all (had READ+UPDATE but no
 * CREATE), and the Quality Engineer could not progress or close the quality issues
 * they own (had CREATE+READ but no UPDATE).
 *
 * <p>Supervisor deliberately stays CREATE+READ — per the client's flow, supervisors
 * log issues; project control assigns the responsible person and closes after
 * satisfactory close-out (ISSUE.UPDATE).
 *
 * <p><b>Self-healing, additive-only</b>: same contract as {@link StorePermissionSeeder}
 * and {@link QcPermissionSeeder} — runs every boot, ADDS missing codes, never removes,
 * so admin profile customisations survive.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class IssuePermissionSeeder {

    private final ProfileRepository profileRepository;

    private static final Map<String, Set<String>> GRANTS = Map.of(
        "SAROOJ_PM", Set.of("ISSUE.CREATE"),
        "SAROOJ_QUALITY_ENGINEER", Set.of("ISSUE.UPDATE")
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

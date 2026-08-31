package com.bipros.api.config;

import com.bipros.security.domain.model.Profile;
import com.bipros.security.domain.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Grants MY_PROGRESS.READ — the per-user "My Progress" overview card (client ask,
 * 2026-08-20: day/week/month/cumulative quantities per supervised activity/BOQ) —
 * to the field profiles that supervise activities. Deliberately permission-based,
 * not role-hardcoded: any other profile can be granted the card via admin.
 *
 * <p><b>Self-healing, additive-only</b>: same contract as {@link StorePermissionSeeder},
 * {@link QcPermissionSeeder} and {@link IssuePermissionSeeder}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MyProgressPermissionSeeder {

    private final ProfileRepository profileRepository;

    private static final Map<String, Set<String>> GRANTS = Map.of(
        "SAROOJ_SUPERVISOR", Set.of("MY_PROGRESS.READ"),
        "SAROOJ_ENGINEER", Set.of("MY_PROGRESS.READ")
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

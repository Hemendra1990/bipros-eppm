package com.bipros.api.config;

import com.bipros.security.domain.model.Profile;
import com.bipros.security.domain.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Grants the STORE.* permission family (material store round, 2026-08-19) to the
 * profiles that should hold it. The store surfaces — Material Catalogue, GRNs,
 * Issue slips / returns, Stock Register and the consumption log — moved off
 * RESOURCE.* onto their own STORE.* codes so store entry can be storekeeper-only
 * without touching the RESOURCE.* grants that also drive equipment / labour
 * deployment logs and rate masters.
 *
 * <p><b>Self-healing, additive-only</b>: unlike {@link SaroojProfileSeeder} this
 * runs every boot and ADDS missing STORE.* codes to the listed profiles (existing
 * databases predate the codes), but it never removes anything — admin
 * customisations survive. Deployment story: boot once, then "assign the Store
 * Keeper profile + a Team-tab Store Keeper seat" is all an admin does.
 *
 * <p>Grants: storekeeper profiles get read + write; PM / Construction Manager
 * get read-only visibility of store data (owner decision 2026-08-19 — senior
 * tier views, storekeeper enters). Supervisors / engineers hold no STORE.* and
 * so lose the store surfaces while keeping their RESOURCE.* deployment logging.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class StorePermissionSeeder {

    private final ProfileRepository profileRepository;

    // ACTIVITY.READ for the storekeepers: the features guide strongly recommends
    // naming the Activity on an issue slip, and the activity pickers on the store
    // forms 403 without it (they'd render as "Unavailable").
    private static final Map<String, Set<String>> GRANTS = Map.of(
        "SAROOJ_STORE_KEEPER", Set.of("STORE.READ", "STORE.UPDATE", "ACTIVITY.READ"),
        "STORE_MANAGER", Set.of("STORE.READ", "STORE.UPDATE", "ACTIVITY.READ"),
        "SAROOJ_PM", Set.of("STORE.READ"),
        "SAROOJ_CONSTRUCTION_MANAGER", Set.of("STORE.READ")
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

package com.bipros.api.config.seeder;

import com.bipros.api.config.seeder.OmanDemoWorkbookReader.StaffMasterRow;
import com.bipros.security.domain.model.AuthMethod;
import com.bipros.security.domain.model.Department;
import com.bipros.security.domain.model.Role;
import com.bipros.security.domain.model.User;
import com.bipros.security.domain.model.UserRole;
import com.bipros.security.domain.repository.RoleRepository;
import com.bipros.security.domain.repository.UserRepository;
import com.bipros.security.domain.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Creates the OMAN-Demo staff directory (PM / CMs / engineers / supervisors) from the
 * customer-supplied workbooks and registers each resulting user in
 * {@link OmanDemoStaffDirectory} so the downstream seeders (project, WBS, daily data,
 * performance) can resolve display names → user ids deterministically.
 *
 * <p>Idempotent: existing usernames are skipped; the directory is rebuilt from
 * {@link UserRepository} on re-run so re-seeds still expose the full mapping to later
 * seeders.
 *
 * <p>Role mapping:
 * <ul>
 *   <li>{@code SUPERVISOR} → {@code SUPERVISOR}</li>
 *   <li>{@code ENGINEER} → {@code ENGINEER} (+ {@code QUALITY_ENGINEER} when designation contains "QA"/"QC")</li>
 *   <li>{@code CM} → {@code CM_MANAGER}</li>
 *   <li>{@code PM} → {@code PROJECT_MANAGER}</li>
 * </ul>
 *
 * <p>Profile-gated to {@code seed}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile("seed")
@Order(200)
public class OmanDemoStaffUserSeeder implements CommandLineRunner {

    static final String DEFAULT_PASSWORD = "OmanDemo@123";
    static final String EMAIL_DOMAIN = "@oman-demo.bipros.demo";
    static final String USERNAME_PREFIX = "oman-demo.";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final OmanDemoWorkbookReader reader;
    private final OmanDemoStaffDirectory directory;

    @Override
    public void run(String... args) {
        List<StaffMasterRow> staff = reader.readStaffMaster();
        if (staff.isEmpty()) {
            log.warn("[oman-demo staff] no staff rows resolved from workbooks; "
                    + "downstream seeders will run without a staff directory");
            return;
        }

        Map<String, Role> rolesByName = new HashMap<>();
        for (String r : List.of("SUPERVISOR", "ENGINEER", "QUALITY_ENGINEER",
                "CM_MANAGER", "PROJECT_MANAGER")) {
            roleRepository.findByName(r).ifPresent(role -> rolesByName.put(r, role));
        }
        if (!rolesByName.containsKey("SUPERVISOR")) {
            log.warn("[oman-demo staff] SUPERVISOR role missing — RBAC bootstrap may not have run; "
                    + "skipping user creation");
            return;
        }

        String hashedDefault = passwordEncoder.encode(DEFAULT_PASSWORD);
        int created = 0;
        int reused = 0;

        for (StaffMasterRow row : staff) {
            String username = USERNAME_PREFIX + toSlug(row.fullName());
            String email = username + EMAIL_DOMAIN;

            Optional<User> existing = userRepository.findByUsername(username);
            User user;
            if (existing.isPresent()) {
                user = existing.get();
                reused++;
            } else if (userRepository.existsByEmail(email)) {
                // Email collision without username match — skip rather than risk overwriting.
                log.warn("[oman-demo staff] email collision for '{}' on {} — skipping",
                        row.fullName(), email);
                continue;
            } else {
                user = new User(username, email, hashedDefault);
                String[] parts = splitName(row.fullName());
                user.setFirstName(parts[0]);
                user.setLastName(parts[1]);
                user.setDesignation(truncate(row.designation(), 120));
                user.setPrimaryIcpmsRole(row.roleCategory() + " — OMAN-Demo-Khasab");
                user.setDepartment(safeDepartment(row.department()));
                user.setAuthMethods(EnumSet.of(AuthMethod.USERNAME_PASSWORD));
                user.setEnabled(true);
                try {
                    user = userRepository.save(user);
                    created++;
                } catch (Exception e) {
                    log.warn("[oman-demo staff] failed to create '{}' ({}): {}",
                            row.fullName(), username, e.getMessage());
                    continue;
                }
            }

            attachRolesIfMissing(user, row, rolesByName);
            directory.register(row.fullName(), user.getId(), row.roleCategory());
        }

        log.info("[oman-demo staff] {} users registered ({} created, {} reused; "
                        + "supervisors={}, engineers={}, CMs={}, PMs={})",
                directory.size(), created, reused,
                directory.supervisorIds().size(), directory.engineerIds().size(),
                directory.cmIds().size(), directory.pmIds().size());
    }

    private void attachRolesIfMissing(User user, StaffMasterRow row, Map<String, Role> rolesByName) {
        switch (row.roleCategory()) {
            case "SUPERVISOR" -> attachRole(user, rolesByName.get("SUPERVISOR"));
            case "ENGINEER" -> {
                attachRole(user, rolesByName.get("ENGINEER"));
                String desig = row.designation() == null ? "" : row.designation().toUpperCase();
                if (desig.contains("QA") || desig.contains("QC") || desig.contains("QUALITY")) {
                    attachRole(user, rolesByName.get("QUALITY_ENGINEER"));
                }
            }
            case "CM" -> attachRole(user, rolesByName.get("CM_MANAGER"));
            case "PM" -> attachRole(user, rolesByName.get("PROJECT_MANAGER"));
            default -> { /* no role */ }
        }
    }

    private void attachRole(User user, Role role) {
        if (role == null) return;
        if (userRoleRepository.existsByUserIdAndRoleId(user.getId(), role.getId())) return;
        try {
            userRoleRepository.save(new UserRole(user.getId(), role.getId()));
        } catch (Exception e) {
            log.warn("[oman-demo staff] role attach failed for {}: {}",
                    user.getUsername(), e.getMessage());
        }
    }

    /** Stable, deterministic username slug derived from a display name. */
    static String toSlug(String displayName) {
        if (displayName == null) return "anon";
        return displayName.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    static String[] splitName(String fullName) {
        String trimmed = fullName.trim();
        int sp = trimmed.lastIndexOf(' ');
        if (sp <= 0) return new String[]{trimmed, ""};
        return new String[]{trimmed.substring(0, sp).trim(), trimmed.substring(sp + 1).trim()};
    }

    private static Department safeDepartment(String raw) {
        if (raw == null) return Department.CIVIL;
        try {
            return Department.fromString(raw);
        } catch (Exception e) {
            return Department.CIVIL;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}

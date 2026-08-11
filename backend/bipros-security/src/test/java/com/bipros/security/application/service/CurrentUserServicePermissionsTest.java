package com.bipros.security.application.service;

import com.bipros.security.domain.model.Profile;
import com.bipros.security.domain.model.Role;
import com.bipros.security.domain.model.RolePermissionMatrix;
import com.bipros.security.domain.model.User;
import com.bipros.security.domain.model.UserRole;
import com.bipros.security.domain.repository.ProfileRepository;
import com.bipros.security.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Pins the PROFILE-WINS rule (access-control round, 2026-08-11): when a user has an assigned
 * profile, the profile alone decides their permission set — the role matrix contributes NOTHING.
 * This is what lets a profile RESTRICT (the client's read-only Project Manager); under the old
 * additive union a profile could only widen.
 *
 * <p>Users with no profile keep the legacy role-matrix behaviour, so existing accounts are
 * unaffected until a profile is assigned. A dangling profileId (row deleted) falls back to the
 * legacy union rather than locking the user out.
 *
 * <p>ADMIN note: the admin bypass is ROLE-based ({@code isAdmin()} checks {@code ROLE_ADMIN}
 * in the security context) and short-circuits the evaluators before any permission-set lookup,
 * so this rule change cannot lock out an admin regardless of their profile's content.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CurrentUserService.permissionsFor — profile-wins rule")
class CurrentUserServicePermissionsTest {

    @Mock private UserRepository userRepository;
    @Mock private ProfileRepository profileRepository;
    @InjectMocks private CurrentUserService service;

    @Test
    @DisplayName("profile assigned -> profile permissions ONLY (role contributes nothing)")
    void profileWinsOverRoles() {
        UUID profileId = UUID.randomUUID();
        User user = fixture(profileId, "SUPERVISOR");   // matrix default incl. DPR.CREATE
        when(profileRepository.findById(profileId))
                .thenReturn(Optional.of(profileWith("DPR.READ")));

        assertThat(service.permissionsFor(user)).containsExactly("DPR.READ");
    }

    @Test
    @DisplayName("no profile -> legacy role union, unchanged")
    void noProfileKeepsLegacyRoleUnion() {
        User user = fixture(null, "SUPERVISOR");

        assertThat(service.permissionsFor(user))
                .isEqualTo(RolePermissionMatrix.permissionsForAll(List.of("SUPERVISOR")));
    }

    @Test
    @DisplayName("dangling profileId (row deleted) -> falls back to the role union, not lock-out")
    void danglingProfileFallsBackToRoles() {
        UUID missing = UUID.randomUUID();
        User user = fixture(missing, "SUPERVISOR");
        when(profileRepository.findById(missing)).thenReturn(Optional.empty());

        assertThat(service.permissionsFor(user)).contains("DPR.CREATE");
    }

    @Test
    @DisplayName("empty profile -> empty permissions (an all-unticked profile really denies)")
    void emptyProfileDeniesEverything() {
        UUID profileId = UUID.randomUUID();
        User user = fixture(profileId, "SUPERVISOR");
        when(profileRepository.findById(profileId)).thenReturn(Optional.of(profileWith()));

        assertThat(service.permissionsFor(user)).isEmpty();
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private static User fixture(UUID profileId, String... roleNames) {
        User user = new User("bob", "bob@example.com", "{noop}hashed");
        user.setEnabled(true);
        user.setProfileId(profileId);
        Set<UserRole> roles = new HashSet<>();
        for (String name : roleNames) {
            UserRole ur = new UserRole();
            ur.setRole(new Role(name));
            roles.add(ur);
        }
        user.setRoles(roles);
        return user;
    }

    private static Profile profileWith(String... codes) {
        return new Profile("TEST_PROFILE", "Test Profile", "fixture", "TESTER", false,
                new HashSet<>(Set.of(codes)));
    }
}

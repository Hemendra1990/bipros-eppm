package com.bipros.security.application.service;

import com.bipros.common.security.DataScope;
import com.bipros.common.security.ScopeKeys;
import com.bipros.security.domain.model.Profile;
import com.bipros.security.domain.model.Role;
import com.bipros.security.domain.model.User;
import com.bipros.security.domain.model.UserRole;
import com.bipros.security.domain.repository.ProfileRepository;
import com.bipros.security.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Pins gate-3 scope resolution (access-control round, 2026-08-11). */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScopeResolverService — gate-3 scope resolution")
class ScopeResolverServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private com.bipros.project.domain.repository.ProjectTeamRepository projectTeamRepository;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private ScopeResolverService service() {
        return new ScopeResolverService(
                new CurrentUserService(userRepository, profileRepository), profileRepository,
                projectTeamRepository, userRepository);
    }

    @Test
    @DisplayName("system context (no authentication) -> ALL")
    void systemContextIsAll() {
        SecurityContextHolder.clearContext();
        assertThat(service().resolveForCurrentUser()).isEqualTo(ScopeKeys.all());
    }

    @Test
    @DisplayName("ADMIN authority -> ALL regardless of profile")
    void adminIsAll() {
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                "root", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.setContext(new SecurityContextImpl(token));

        assertThat(service().resolveForCurrentUser().scope()).isEqualTo(DataScope.ALL);
    }

    @Test
    @DisplayName("profile with OWN -> OWN with user id + username and display-name aliases")
    void ownProfileCarriesAliases() {
        UUID profileId = UUID.randomUUID();
        User user = user("k.barman", "K.", "Barman", profileId);
        authenticate(user);
        Profile profile = new Profile("SAROOJ_SUPERVISOR", "Sup", "d", "SUPERVISOR", false, Set.of());
        profile.setDataScope("OWN");
        when(profileRepository.findById(profileId)).thenReturn(Optional.of(profile));

        ScopeKeys keys = service().resolveForCurrentUser();

        assertThat(keys.own()).isTrue();
        assertThat(keys.userId()).isEqualTo(user.getId());
        assertThat(keys.nameAliases()).containsExactlyInAnyOrder("k.barman", "K. Barman");
    }

    @Test
    @DisplayName("no profile -> PROJECT (legacy membership-wall behaviour)")
    void noProfileIsProject() {
        User user = user("subrat", "Subrat", "mohapatra", null);
        authenticate(user);

        ScopeKeys keys = service().resolveForCurrentUser();

        assertThat(keys.scope()).isEqualTo(DataScope.PROJECT);
        assertThat(keys.userId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("profile with null data_scope column -> PROJECT default")
    void nullScopeColumnDefaultsToProject() {
        UUID profileId = UUID.randomUUID();
        User user = user("qs", "Q", "S", profileId);
        authenticate(user);
        Profile profile = new Profile("SAROOJ_QS", "QS", "d", "FINANCE", false, Set.of());
        when(profileRepository.findById(profileId)).thenReturn(Optional.of(profile));

        assertThat(service().resolveForCurrentUser().scope()).isEqualTo(DataScope.PROJECT);
    }

    @Test
    @DisplayName("TEAM + project -> member set = self + transitive Team-tab downline, with aliases")
    void teamResolvesDownline() {
        UUID profileId = UUID.randomUUID();
        User engineer = user("EMP-210", "M.", "Pradeep", profileId);
        authenticate(engineer);
        Profile profile = new Profile("SAROOJ_ENGINEER", "Eng", "d", "SITE_ENGINEER", false, Set.of());
        profile.setDataScope("TEAM");
        when(profileRepository.findById(profileId)).thenReturn(Optional.of(profile));

        UUID projectId = UUID.randomUUID();
        User supervisor = user("k.barman", "K.", "Barman", null);
        User foreman = user("mohd.ismaila", "Mohd.", "Ismaila", null);
        User outsider = user("manzar", "Man", "Zar", null);
        // engineer <- supervisor <- foreman; outsider reports elsewhere.
        when(projectTeamRepository.findByProjectId(projectId)).thenReturn(List.of(
                teamRow(projectId, supervisor.getId(), engineer.getId()),
                teamRow(projectId, foreman.getId(), supervisor.getId()),
                teamRow(projectId, outsider.getId(), UUID.randomUUID())));
        when(userRepository.findAllById(
                Set.of(engineer.getId(), supervisor.getId(), foreman.getId())))
                .thenReturn(List.of(engineer, supervisor, foreman));

        ScopeKeys keys = service().resolveForProject(projectId);

        assertThat(keys.scope()).isEqualTo(DataScope.TEAM);
        assertThat(keys.personScoped()).isTrue();
        assertThat(keys.own()).isFalse();
        assertThat(keys.memberIds()).containsExactlyInAnyOrder(
                engineer.getId(), supervisor.getId(), foreman.getId());
        assertThat(keys.memberAliases()).contains(
                "EMP-210", "M. Pradeep", "k.barman", "K. Barman", "mohd.ismaila", "Mohd. Ismaila");
        assertThat(keys.nameAliases()).containsExactlyInAnyOrder("EMP-210", "M. Pradeep");
    }

    @Test
    @DisplayName("TEAM without a project id degrades to self-only (compat default)")
    void teamWithoutProjectIsSelfOnly() {
        UUID profileId = UUID.randomUUID();
        User engineer = user("EMP-210", "M.", "Pradeep", profileId);
        authenticate(engineer);
        Profile profile = new Profile("SAROOJ_ENGINEER", "Eng", "d", "SITE_ENGINEER", false, Set.of());
        profile.setDataScope("TEAM");
        when(profileRepository.findById(profileId)).thenReturn(Optional.of(profile));

        ScopeKeys keys = service().resolveForCurrentUser();

        assertThat(keys.scope()).isEqualTo(DataScope.TEAM);
        assertThat(keys.memberIds()).containsExactly(engineer.getId());
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private User user(String username, String first, String last, UUID profileId) {
        User user = new User(username, username + "@example.com", "{noop}x");
        user.setEnabled(true);
        user.setFirstName(first);
        user.setLastName(last);
        user.setProfileId(profileId);
        Set<UserRole> roles = new HashSet<>();
        UserRole ur = new UserRole();
        ur.setRole(new Role("SUPERVISOR"));
        roles.add(ur);
        user.setRoles(roles);
        try {
            var idField = com.bipros.common.model.BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return user;
    }

    private com.bipros.project.domain.model.ProjectTeamMember teamRow(
            UUID projectId, UUID userId, UUID reportsTo) {
        var row = new com.bipros.project.domain.model.ProjectTeamMember();
        row.setProjectId(projectId);
        row.setUserId(userId);
        row.setReportsToUserId(reportsTo);
        return row;
    }

    private void authenticate(User user) {
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                user.getUsername(), "n/a", List.of(new SimpleGrantedAuthority("ROLE_SUPERVISOR")));
        SecurityContextHolder.setContext(new SecurityContextImpl(token));
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
    }
}

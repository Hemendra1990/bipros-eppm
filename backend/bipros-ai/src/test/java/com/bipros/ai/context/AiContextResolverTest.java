package com.bipros.ai.context;

import com.bipros.common.security.ProjectAccessGuard;
import com.bipros.common.security.SecurityContextHelper;
import com.bipros.security.domain.model.User;
import com.bipros.security.domain.repository.ProfileRepository;
import com.bipros.security.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Guards the portfolio-mode change in {@link AiContextResolver}: when the
 * caller sends a null projectId, the resolver MUST leave it null even for
 * non-admin users with a single accessible project. The old auto-bind
 * (scoped.size() == 1 → bind to scoped[0]) made portfolio questions
 * impossible because a single-project user could never reach general mode.
 */
class AiContextResolverTest {

    private SecurityContextHelper sec;
    private ProjectAccessGuard guard;
    private UserRepository userRepo;
    private ProfileRepository profileRepo;
    private AiContextResolver resolver;

    @BeforeEach
    void setUp() {
        sec = Mockito.mock(SecurityContextHelper.class);
        guard = Mockito.mock(ProjectAccessGuard.class);
        userRepo = Mockito.mock(UserRepository.class);
        profileRepo = Mockito.mock(ProfileRepository.class);
        resolver = new AiContextResolver(guard, sec, userRepo, profileRepo);
    }

    @Test
    void nonAdminWithOneScopedProjectAndNullRequestStaysNull() {
        UUID userId = UUID.randomUUID();
        UUID p1 = UUID.randomUUID();
        User u = new User();
        u.setProfileId(null);

        when(sec.getCurrentUserId()).thenReturn(userId);
        when(sec.hasRole("ADMIN")).thenReturn(false);
        when(sec.hasRole("PROJECT_MANAGER")).thenReturn(false);
        when(userRepo.findById(userId)).thenReturn(Optional.of(u));
        when(guard.getAccessibleProjectIdsForCurrentUser()).thenReturn(Set.of(p1));

        AiContext ctx = resolver.resolve(null, "general");

        // Critical invariant: a null request projectId from a non-admin user with
        // a single accessible project must NOT auto-bind to that project.
        // The session is a portfolio session; scopedProjectIds carries breadth.
        assertNull(ctx.projectId(),
                "Auto-bind regression: portfolio request from single-project user got pinned");
        assertEquals(1, ctx.scopedProjectIds().size());
        assertTrue(ctx.scopedProjectIds().contains(p1));
    }

    @Test
    void explicitProjectIdIsRespected() {
        UUID userId = UUID.randomUUID();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        User u = new User();
        u.setProfileId(null);

        when(sec.getCurrentUserId()).thenReturn(userId);
        when(sec.hasRole("ADMIN")).thenReturn(false);
        when(sec.hasRole("PROJECT_MANAGER")).thenReturn(false);
        when(userRepo.findById(userId)).thenReturn(Optional.of(u));
        when(guard.getAccessibleProjectIdsForCurrentUser()).thenReturn(Set.of(p1, p2));

        AiContext ctx = resolver.resolve(p1, "general");

        assertEquals(p1, ctx.projectId(),
                "Explicit request projectId must be returned unchanged");
    }

    @Test
    void nonAdminWithEmptyScopeAndNullRequestStaysNull() {
        UUID userId = UUID.randomUUID();
        User u = new User();
        u.setProfileId(null);

        when(sec.getCurrentUserId()).thenReturn(userId);
        when(sec.hasRole("ADMIN")).thenReturn(false);
        when(sec.hasRole("PROJECT_MANAGER")).thenReturn(false);
        when(userRepo.findById(userId)).thenReturn(Optional.of(u));
        when(guard.getAccessibleProjectIdsForCurrentUser()).thenReturn(Set.of());

        AiContext ctx = resolver.resolve(null, "general");

        assertNull(ctx.projectId());
        assertTrue(ctx.scopedProjectIds().isEmpty());
    }
}

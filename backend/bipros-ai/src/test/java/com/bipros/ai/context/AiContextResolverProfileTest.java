package com.bipros.ai.context;

import com.bipros.common.security.ProjectAccessGuard;
import com.bipros.common.security.SecurityContextHelper;
import com.bipros.security.domain.model.Profile;
import com.bipros.security.domain.model.User;
import com.bipros.security.domain.repository.ProfileRepository;
import com.bipros.security.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class AiContextResolverProfileTest {

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
    void resolvesProfileFromUser() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User u = new User();
        u.setProfileId(profileId);
        Profile p = new Profile("SITE_MANAGER", "Site Manager", null, "SITE_MANAGER", true, java.util.Set.of());

        when(sec.getCurrentUserId()).thenReturn(userId);
        when(sec.hasRole("ADMIN")).thenReturn(false);
        when(sec.hasRole("PROJECT_MANAGER")).thenReturn(false);
        when(userRepo.findById(userId)).thenReturn(Optional.of(u));
        when(profileRepo.findById(profileId)).thenReturn(Optional.of(p));
        when(guard.getAccessibleProjectIdsForCurrentUser()).thenReturn(java.util.Set.of());

        AiContext ctx = resolver.resolve(null, "general");

        assertEquals("SITE_MANAGER", ctx.profile());
    }

    @Test
    void profileIsNullWhenUserHasNoProfile() {
        UUID userId = UUID.randomUUID();
        User u = new User();
        u.setProfileId(null);

        when(sec.getCurrentUserId()).thenReturn(userId);
        when(sec.hasRole("ADMIN")).thenReturn(false);
        when(sec.hasRole("PROJECT_MANAGER")).thenReturn(false);
        when(userRepo.findById(userId)).thenReturn(Optional.of(u));
        when(guard.getAccessibleProjectIdsForCurrentUser()).thenReturn(java.util.Set.of());

        AiContext ctx = resolver.resolve(null, "general");

        assertNull(ctx.profile());
    }

    @Test
    void usernamePrincipalFallsBackToUsernameLookup() {
        // The seeded admin authenticates with a username principal ("admin"), so
        // getCurrentUserId() throws (UUID.fromString fails). The resolver must
        // fall back to findByUsername so the profile — and tool visibility — still
        // resolves instead of silently becoming null.
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User u = new User();
        u.setId(userId);
        u.setProfileId(profileId);
        Profile p = new Profile("SYSTEM_ADMIN", "System Admin", null, "ADMIN", true, java.util.Set.of());

        when(sec.getCurrentUserId()).thenThrow(new IllegalArgumentException("Invalid UUID string: admin"));
        when(sec.getCurrentUsername()).thenReturn("admin");
        when(sec.hasRole("ADMIN")).thenReturn(true);
        when(userRepo.findByUsername("admin")).thenReturn(Optional.of(u));
        when(userRepo.findById(userId)).thenReturn(Optional.of(u));
        when(profileRepo.findById(profileId)).thenReturn(Optional.of(p));
        when(guard.getAccessibleProjectIdsForCurrentUser()).thenReturn(java.util.Set.of());

        AiContext ctx = resolver.resolve(null, "general");

        assertEquals(userId, ctx.userId());
        assertEquals("SYSTEM_ADMIN", ctx.profile());
    }

    @Test
    void adminWithSystemAdminProfileResolves() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User u = new User();
        u.setProfileId(profileId);
        Profile p = new Profile("SYSTEM_ADMIN", "System Admin", null, "ADMIN", true, java.util.Set.of());

        when(sec.getCurrentUserId()).thenReturn(userId);
        when(sec.hasRole("ADMIN")).thenReturn(true);
        when(userRepo.findById(userId)).thenReturn(Optional.of(u));
        when(profileRepo.findById(profileId)).thenReturn(Optional.of(p));
        when(guard.getAccessibleProjectIdsForCurrentUser()).thenReturn(java.util.Set.of());

        AiContext ctx = resolver.resolve(null, "general");

        assertEquals("ADMIN", ctx.role());
        assertEquals("SYSTEM_ADMIN", ctx.profile());
    }
}

package com.bipros.security.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.security.application.dto.CreateProfileRequest;
import com.bipros.security.application.dto.ProfileResponse;
import com.bipros.security.application.dto.UpdateProfileRequest;
import com.bipros.security.domain.model.PermissionCatalog;
import com.bipros.security.domain.model.Profile;
import com.bipros.security.domain.repository.ProfileRepository;
import com.bipros.security.domain.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final RoleRepository roleRepository;
    private final com.bipros.security.domain.repository.UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ProfileResponse> listProfiles() {
        return profileRepository.findAll().stream()
                .sorted(Comparator
                        .comparing(Profile::isSystemDefault).reversed()
                        .thenComparing(Profile::getName))
                .map(ProfileResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(UUID id) {
        Profile p = profileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", id));
        return ProfileResponse.from(p);
    }

    public ProfileResponse createProfile(CreateProfileRequest req) {
        if (profileRepository.existsByCode(req.code())) {
            throw new BusinessRuleException("DUPLICATE_PROFILE_CODE",
                    "A profile with code '" + req.code() + "' already exists");
        }
        validateRoleName(req.legacyRoleName());
        Set<String> perms = sanitizePermissions(req.permissions());

        Profile p = new Profile(req.code(), req.name(), req.description(), req.legacyRoleName(),
                false, perms);
        p.setDataScope(parseScope(req.dataScope()).name());
        Profile saved = profileRepository.save(p);
        log.info("Created profile {} ({}) with {} permissions", saved.getCode(), saved.getName(), perms.size());
        return ProfileResponse.from(saved);
    }

    public ProfileResponse updateProfile(UUID id, UpdateProfileRequest req) {
        Profile p = profileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", id));

        if (req.name() != null && !req.name().isBlank()) p.setName(req.name());
        if (req.description() != null) p.setDescription(req.description());
        if (req.legacyRoleName() != null && !req.legacyRoleName().isBlank()) {
            validateRoleName(req.legacyRoleName());
            p.setLegacyRoleName(req.legacyRoleName());
        }
        if (req.permissions() != null) {
            p.setPermissions(sanitizePermissions(req.permissions()));
        }
        // Data scope is config, not identity — editable on system defaults too (the owner's
        // "everything configurable" requirement).
        if (req.dataScope() != null && !req.dataScope().isBlank()) {
            p.setDataScope(parseScope(req.dataScope()).name());
        }

        Profile saved = profileRepository.save(p);
        return ProfileResponse.from(saved);
    }

    /** Strict parse for API input: unknown values are a client error, not a silent PROJECT. */
    private static com.bipros.common.security.DataScope parseScope(String raw) {
        if (raw == null || raw.isBlank()) return com.bipros.common.security.DataScope.PROJECT;
        try {
            return com.bipros.common.security.DataScope.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("UNKNOWN_DATA_SCOPE",
                    "dataScope must be one of OWN, PROJECT, ALL (got '" + raw + "')");
        }
    }

    public void deleteProfile(UUID id) {
        Profile p = profileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", id));
        if (p.isSystemDefault()) {
            throw new BusinessRuleException("PROFILE_SYSTEM_DEFAULT",
                    "System-default profiles cannot be deleted");
        }
        // Review round 2 (fail-open): users.profile_id is a soft FK — deleting an assigned
        // profile would silently drop those users back to the wider role-union permissions
        // and PROJECT scope. Reassign the users first, then delete.
        long assigned = userRepository.countByProfileId(id);
        if (assigned > 0) {
            throw new BusinessRuleException("PROFILE_IN_USE",
                    assigned + " user(s) still use this profile. Reassign them first.");
        }
        profileRepository.delete(p);
    }

    private void validateRoleName(String roleName) {
        if (roleRepository.findByName(roleName).isEmpty()) {
            throw new BusinessRuleException("UNKNOWN_ROLE",
                    "Role '" + roleName + "' does not exist");
        }
    }

    private Set<String> sanitizePermissions(Set<String> raw) {
        if (raw == null) return new HashSet<>();
        Set<String> invalid = raw.stream()
                .filter(c -> !PermissionCatalog.isValid(c))
                .collect(Collectors.toSet());
        if (!invalid.isEmpty()) {
            throw new BusinessRuleException("UNKNOWN_PERMISSION",
                    "Unknown permission codes: " + invalid);
        }
        return new HashSet<>(raw);
    }
}

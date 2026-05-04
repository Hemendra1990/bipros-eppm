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

        Profile saved = profileRepository.save(p);
        return ProfileResponse.from(saved);
    }

    public void deleteProfile(UUID id) {
        Profile p = profileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", id));
        if (p.isSystemDefault()) {
            throw new BusinessRuleException("PROFILE_SYSTEM_DEFAULT",
                    "System-default profiles cannot be deleted");
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

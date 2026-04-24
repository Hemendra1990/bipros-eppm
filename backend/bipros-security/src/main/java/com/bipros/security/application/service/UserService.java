package com.bipros.security.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.security.application.dto.UpdateUserProfileRequest;
import com.bipros.security.application.dto.UserResponse;
import com.bipros.security.domain.model.User;
import com.bipros.security.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class UserService {

    private static final Pattern EMP_CODE_PATTERN = Pattern.compile("^EMP-(\\d+)$");

    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));

        List<String> roles = extractRoles(user);
        return UserResponse.from(user, roles);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(Pageable pageable) {
        Page<User> users = userRepository.findAll(pageable);
        List<UserResponse> responses = users.getContent().stream()
                .map(user -> UserResponse.from(user, extractRoles(user)))
                .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, users.getTotalElements());
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        List<String> roles = extractRoles(user);
        return UserResponse.from(user, roles);
    }

    /**
     * Partial-update a user / personnel record. Changes the Screen 07 fields (mobile, department,
     * joining/contract dates, presence status) plus name/email/designation/organisation. Auth
     * details (username, password, roles) remain the responsibility of their dedicated endpoints.
     * Auto-generates {@code employeeCode} the first time the record is saved without one.
     */
    public UserResponse updateProfile(UUID id, UpdateUserProfileRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        if (request.firstName() != null) user.setFirstName(request.firstName());
        if (request.lastName() != null) user.setLastName(request.lastName());
        if (request.email() != null) {
            if (!request.email().equals(user.getEmail())
                && userRepository.findByEmail(request.email()).isPresent()) {
                throw new BusinessRuleException("DUPLICATE_EMAIL",
                    "A user with email '" + request.email() + "' already exists");
            }
            user.setEmail(request.email());
        }
        if (request.mobile() != null) user.setMobile(request.mobile());
        if (request.designation() != null) user.setDesignation(request.designation());
        if (request.department() != null) user.setDepartment(request.department());
        if (request.organisationId() != null) user.setOrganisationId(request.organisationId());
        if (request.joiningDate() != null) user.setJoiningDate(request.joiningDate());
        if (request.contractEndDate() != null) user.setContractEndDate(request.contractEndDate());
        if (request.presenceStatus() != null) user.setPresenceStatus(request.presenceStatus());
        if (request.enabled() != null) user.setEnabled(request.enabled());

        if (user.getEmployeeCode() == null || user.getEmployeeCode().isBlank()) {
            user.setEmployeeCode(generateEmployeeCode());
        }

        User saved = userRepository.save(user);
        auditService.logUpdate("User", id, "profile", null, UserResponse.from(saved, extractRoles(saved)));
        return UserResponse.from(saved, extractRoles(saved));
    }

    /**
     * Ensure the given user has a non-blank {@code employeeCode}. Useful during login / bootstrap
     * so legacy rows get an EMP-NNN on first touch without requiring a manual migration.
     */
    public void ensureEmployeeCode(User user) {
        if (user.getEmployeeCode() == null || user.getEmployeeCode().isBlank()) {
            user.setEmployeeCode(generateEmployeeCode());
            userRepository.save(user);
        }
    }

    private String generateEmployeeCode() {
        int next = 1;
        for (User u : userRepository.findAll()) {
            if (u.getEmployeeCode() == null) continue;
            Matcher m = EMP_CODE_PATTERN.matcher(u.getEmployeeCode());
            if (m.matches()) {
                int n = Integer.parseInt(m.group(1));
                if (n >= next) next = n + 1;
            }
        }
        return String.format("EMP-%03d", next);
    }

    private List<String> extractRoles(User user) {
        return user.getRoles().stream()
                .map(userRole -> userRole.getRole().getName())
                .collect(Collectors.toList());
    }
}

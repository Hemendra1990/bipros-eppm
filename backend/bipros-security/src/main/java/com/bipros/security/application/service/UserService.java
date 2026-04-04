package com.bipros.security.application.service;

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

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserResponse getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<String> roles = extractRoles(user);
        return UserResponse.from(user, roles);
    }

    public Page<UserResponse> listUsers(Pageable pageable) {
        Page<User> users = userRepository.findAll(pageable);
        List<UserResponse> responses = users.getContent().stream()
                .map(user -> UserResponse.from(user, extractRoles(user)))
                .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, users.getTotalElements());
    }

    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        List<String> roles = extractRoles(user);
        return UserResponse.from(user, roles);
    }

    private List<String> extractRoles(User user) {
        return user.getRoles().stream()
                .map(userRole -> userRole.getRole().getName())
                .collect(Collectors.toList());
    }
}

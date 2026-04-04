package com.bipros.security.application.service;

import com.bipros.security.application.dto.AuthResponse;
import com.bipros.security.application.dto.LoginRequest;
import com.bipros.security.application.dto.RegisterRequest;
import com.bipros.security.domain.model.Role;
import com.bipros.security.domain.model.User;
import com.bipros.security.domain.model.UserRole;
import com.bipros.security.domain.repository.RoleRepository;
import com.bipros.security.domain.repository.UserRepository;
import com.bipros.security.domain.repository.UserRoleRepository;
import com.bipros.security.infrastructure.jwt.JwtTokenProvider;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthResponse login(LoginRequest request) {
        log.debug("Attempting login for user: {}", request.username());

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.username(),
                            request.password()
                    )
            );

            User user = userRepository.findByUsername(request.username())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String accessToken = jwtTokenProvider.generateAccessToken(user);
            String refreshToken = jwtTokenProvider.generateRefreshToken(user);
            long expiresIn = jwtTokenProvider.getAccessTokenExpirationMs();

            log.info("User {} logged in successfully", request.username());
            return AuthResponse.of(accessToken, refreshToken, expiresIn);
        } catch (Exception e) {
            log.warn("Login failed for user: {}", request.username(), e);
            throw new RuntimeException("Invalid username or password");
        }
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.debug("Attempting registration for user: {}", request.username());

        if (userRepository.existsByUsername(request.username())) {
            throw new RuntimeException("Username is already taken");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new RuntimeException("Email is already registered");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEnabled(true);
        user.setAccountLocked(false);

        User savedUser = userRepository.save(user);

        Role viewerRole = roleRepository.findByName("VIEWER")
                .orElseGet(() -> {
                    Role newRole = new Role("VIEWER", "View-only access");
                    return roleRepository.save(newRole);
                });

        UserRole userRole = new UserRole(savedUser.getId(), viewerRole.getId());
        userRole.setRole(viewerRole);
        userRole.setUser(savedUser);
        userRoleRepository.save(userRole);
        savedUser.getRoles().add(userRole);

        String accessToken = jwtTokenProvider.generateAccessToken(savedUser);
        String refreshToken = jwtTokenProvider.generateRefreshToken(savedUser);
        long expiresIn = jwtTokenProvider.getAccessTokenExpirationMs();

        log.info("User {} registered successfully", request.username());
        return AuthResponse.of(accessToken, refreshToken, expiresIn);
    }

    public AuthResponse refreshToken(String refreshToken) {
        log.debug("Attempting to refresh token");

        try {
            if (!jwtTokenProvider.validateToken(refreshToken)) {
                throw new RuntimeException("Invalid or expired refresh token");
            }

            String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String newAccessToken = jwtTokenProvider.generateAccessToken(user);
            String newRefreshToken = jwtTokenProvider.generateRefreshToken(user);
            long expiresIn = jwtTokenProvider.getAccessTokenExpirationMs();

            log.info("Token refreshed for user: {}", username);
            return AuthResponse.of(newAccessToken, newRefreshToken, expiresIn);
        } catch (ExpiredJwtException | MalformedJwtException e) {
            log.warn("Token refresh failed due to invalid token", e);
            throw new RuntimeException("Invalid refresh token");
        }
    }
}

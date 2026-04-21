package com.bipros.security.api;

import com.bipros.common.dto.ApiResponse;
import com.bipros.common.dto.PagedResponse;
import com.bipros.security.application.dto.UserAccessResponse;
import com.bipros.security.application.dto.UserResponse;
import com.bipros.security.application.service.UserAccessService;
import com.bipros.security.application.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/users")
@PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
@Tag(name = "Users", description = "User management endpoints")
@Slf4j
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserAccessService userAccessService;

    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Retrieve the currently authenticated user details")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser() {
        try {
            UserResponse response = userService.getCurrentUser();
            return ResponseEntity.ok(ApiResponse.ok(response));
        } catch (Exception e) {
            log.error("Error retrieving current user", e);
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List users", description = "Retrieve a paginated list of all users (admin only)")
    public ResponseEntity<ApiResponse<PagedResponse<UserResponse>>> listUsers(Pageable pageable) {
        try {
            Page<UserResponse> page = userService.listUsers(pageable);
            PagedResponse<UserResponse> response = PagedResponse.of(
                    page.getContent(),
                    page.getTotalElements(),
                    page.getTotalPages(),
                    page.getNumber(),
                    page.getSize()
            );
            return ResponseEntity.ok(ApiResponse.ok(response));
        } catch (Exception e) {
            log.error("Error listing users", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("INTERNAL_SERVER_ERROR", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID", description = "Retrieve a specific user by their ID")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable UUID id) {
        try {
            UserResponse response = userService.getUserById(id);
            return ResponseEntity.ok(ApiResponse.ok(response));
        } catch (Exception e) {
            log.error("Error retrieving user: {}", id, e);
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        }
    }

    @GetMapping("/{id}/access")
    @Operation(summary = "Get IC-PMS module access & corridor scope for a user")
    public ResponseEntity<ApiResponse<UserAccessResponse>> getUserAccess(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(userAccessService.getAccess(id)));
        } catch (Exception e) {
            log.error("Error retrieving access for user: {}", id, e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("INTERNAL_SERVER_ERROR", e.getMessage()));
        }
    }
}

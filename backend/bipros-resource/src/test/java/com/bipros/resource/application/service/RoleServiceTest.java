package com.bipros.resource.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.AssignUserToRoleRequest;
import com.bipros.resource.application.dto.UserResourceRoleResponse;
import com.bipros.resource.domain.model.UserResourceRole;
import com.bipros.resource.domain.repository.ResourceRoleRepository;
import com.bipros.resource.domain.repository.ResourceTypeDefRepository;
import com.bipros.resource.domain.repository.UserResourceRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoleService Tests")
class RoleServiceTest {

  @Mock private ResourceRoleRepository roleRepository;
  @Mock private ResourceTypeDefRepository resourceTypeDefRepository;
  @Mock private UserResourceRoleRepository userResourceRoleRepository;
  @Mock private AuditService auditService;

  private RoleService service;

  @BeforeEach
  void setUp() {
    service = new RoleService(roleRepository, resourceTypeDefRepository, userResourceRoleRepository, auditService);
  }

  @Nested
  @DisplayName("assignUser")
  class AssignUserTests {

    @Test
    @DisplayName("primary flag atomically clears other primaries for the same user")
    void assignUser_primary_clearsOthers() {
      UUID roleId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      UUID otherMappingId = UUID.randomUUID();

      UserResourceRole otherPrimary = UserResourceRole.builder()
          .userId(userId).resourceRoleId(UUID.randomUUID())
          .primary(true).build();
      otherPrimary.setId(otherMappingId);

      when(roleRepository.existsById(roleId)).thenReturn(true);
      when(userResourceRoleRepository.findByUserIdAndResourceRoleId(userId, roleId)).thenReturn(Optional.empty());
      when(userResourceRoleRepository.findByUserId(userId)).thenReturn(List.of(otherPrimary));
      when(userResourceRoleRepository.save(any())).thenAnswer(inv -> {
        UserResourceRole u = inv.getArgument(0);
        u.setId(UUID.randomUUID());
        return u;
      });

      UserResourceRoleResponse response = service.assignUser(roleId,
          new AssignUserToRoleRequest(userId, true, null, null, null));

      assertThat(response.primary()).isTrue();
      assertThat(otherPrimary.isPrimary()).isFalse();
      verify(userResourceRoleRepository).save(otherPrimary);
    }
  }

  @Nested
  @DisplayName("setPrimaryRole")
  class SetPrimaryTests {

    @Test
    @DisplayName("sets primary and clears existing primary for same user")
    void setPrimaryRole_switchesPrimary() {
      UUID userId = UUID.randomUUID();
      UUID oldRoleId = UUID.randomUUID();
      UUID newRoleId = UUID.randomUUID();

      UserResourceRole oldPrimary = UserResourceRole.builder()
          .userId(userId).resourceRoleId(oldRoleId)
          .primary(true).build();
      oldPrimary.setId(UUID.randomUUID());

      UserResourceRole newPrimary = UserResourceRole.builder()
          .userId(userId).resourceRoleId(newRoleId)
          .primary(false).build();
      newPrimary.setId(UUID.randomUUID());

      when(userResourceRoleRepository.findByUserIdAndResourceRoleId(userId, newRoleId))
          .thenReturn(Optional.of(newPrimary));
      when(userResourceRoleRepository.findByUserId(userId)).thenReturn(List.of(oldPrimary, newPrimary));
      when(userResourceRoleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      UserResourceRoleResponse response = service.setPrimaryRole(userId, newRoleId);

      assertThat(response.primary()).isTrue();
      assertThat(oldPrimary.isPrimary()).isFalse();
      verify(userResourceRoleRepository).save(oldPrimary);
      verify(userResourceRoleRepository).save(newPrimary);
    }

    @Test
    @DisplayName("throws when user is not assigned to role")
    void setPrimaryRole_missingMapping_throws() {
      UUID userId = UUID.randomUUID();
      UUID roleId = UUID.randomUUID();

      when(userResourceRoleRepository.findByUserIdAndResourceRoleId(userId, roleId))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.setPrimaryRole(userId, roleId))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }
}

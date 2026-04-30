package com.bipros.resource.application.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.CreateResourceAssignmentRequest;
import com.bipros.resource.application.dto.ResourceAssignmentResponse;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.model.ResourceRate;
import com.bipros.resource.domain.model.Role;
import com.bipros.resource.domain.model.UserResourceRole;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.bipros.resource.domain.repository.ResourceRateRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import com.bipros.resource.domain.repository.ResourceRoleRepository;
import com.bipros.resource.domain.repository.UserResourceRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResourceAssignmentService — Role & Staffing Tests")
class ResourceAssignmentServiceRoleTest {

  @Mock private ResourceAssignmentRepository assignmentRepository;
  @Mock private ResourceRepository resourceRepository;
  @Mock private ResourceRateRepository rateRepository;
  @Mock private ResourceRoleRepository roleRepository;
  @Mock private UserResourceRoleRepository userResourceRoleRepository;
  @Mock private ActivityRepository activityRepository;
  @Mock private AuditService auditService;

  private ResourceAssignmentService service;

  @BeforeEach
  void setUp() {
    service = new ResourceAssignmentService(
        assignmentRepository, resourceRepository, rateRepository,
        roleRepository, userResourceRoleRepository, activityRepository, auditService);
  }

  @Nested
  @DisplayName("assignResource")
  class AssignTests {

    @Test
    @DisplayName("role-only assignment succeeds and cost uses Role.budgetedRate")
    void assignResource_roleOnly_succeeds_andCostFromRoleBudgetedRate() {
      UUID activityId = UUID.randomUUID();
      UUID roleId = UUID.randomUUID();
      UUID projectId = UUID.randomUUID();
      Role role = Role.builder().code("SUP").name("Supervisor")
          .budgetedRate(new BigDecimal("500.00")).build();
      role.setId(roleId);

      when(roleRepository.existsById(roleId)).thenReturn(true);
      when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
      when(assignmentRepository.findByActivityIdAndResourceIdIsNullAndRoleId(activityId, roleId))
          .thenReturn(Optional.empty());
      when(assignmentRepository.save(any())).thenAnswer(inv -> {
        ResourceAssignment a = inv.getArgument(0);
        a.setId(UUID.randomUUID());
        return a;
      });

      ResourceAssignmentResponse response = service.assignResource(
          new CreateResourceAssignmentRequest(
              activityId, null, roleId, projectId, 10.0, "STANDARD", null,
              LocalDate.now(), LocalDate.now().plusDays(5)));

      assertThat(response.roleId()).isEqualTo(roleId);
      assertThat(response.resourceId()).isNull();
      assertThat(response.staffed()).isFalse();
      assertThat(response.plannedCost()).isEqualByComparingTo(new BigDecimal("5000.00"));
    }

    @Test
    @DisplayName("both roleId and resourceId null throws validation error")
    void assignResource_bothNull_throwsValidation() {
      assertThatThrownBy(() -> service.assignResource(
          new CreateResourceAssignmentRequest(
              UUID.randomUUID(), null, null, UUID.randomUUID(), 1.0, "STANDARD", null, null, null)))
          .isInstanceOf(BusinessRuleException.class)
          .satisfies(ex -> assertThat(((BusinessRuleException) ex).getRuleCode()).isEqualTo("ASSIGNMENT_TARGET_REQUIRED"));
    }

    @Test
    @DisplayName("duplicate role-only slot is rejected")
    void assignResource_duplicateRoleOnlySlot_isRejected() {
      UUID activityId = UUID.randomUUID();
      UUID roleId = UUID.randomUUID();
      UUID projectId = UUID.randomUUID();
      ResourceAssignment existing = ResourceAssignment.builder()
          .activityId(activityId).roleId(roleId).resourceId(null).build();

      when(roleRepository.existsById(roleId)).thenReturn(true);
      when(assignmentRepository.findByActivityIdAndResourceIdIsNullAndRoleId(activityId, roleId))
          .thenReturn(Optional.of(existing));

      assertThatThrownBy(() -> service.assignResource(
          new CreateResourceAssignmentRequest(
              activityId, null, roleId, projectId, 1.0, "STANDARD", null, null, null)))
          .isInstanceOf(BusinessRuleException.class)
          .satisfies(ex -> assertThat(((BusinessRuleException) ex).getRuleCode()).isEqualTo("DUPLICATE_ROLE_ASSIGNMENT"));
    }
  }

  @Nested
  @DisplayName("staffAssignment")
  class StaffTests {

    @Test
    @DisplayName("staffing sets resource and recomputes cost from ResourceRate")
    void staffAssignment_setsResourceAndRecomputesCost() {
      UUID assignmentId = UUID.randomUUID();
      UUID resourceId = UUID.randomUUID();
      UUID roleId = UUID.randomUUID();
      UUID activityId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      ResourceAssignment assignment = ResourceAssignment.builder()
          .activityId(activityId).roleId(roleId)
          .resourceId(null).plannedUnits(8.0).rateType("STANDARD").build();
      assignment.setId(assignmentId);

      Resource resource = Resource.builder().name("R1").userId(userId).build();
      resource.setId(resourceId);
      ResourceRate rate = ResourceRate.builder()
          .resourceId(resourceId).rateType("STANDARD")
          .budgetedRate(new BigDecimal("300.00")).build();

      when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
      when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));
      when(userResourceRoleRepository.existsByUserIdAndResourceRoleId(userId, roleId)).thenReturn(true);
      when(assignmentRepository.findByActivityId(activityId)).thenReturn(List.of(assignment));
      when(rateRepository.findByResourceIdAndRateTypeOrderByEffectiveDateDesc(resourceId, "STANDARD"))
          .thenReturn(List.of(rate));
      when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      ResourceAssignmentResponse response = service.staffAssignment(assignmentId, resourceId, false);

      assertThat(response.resourceId()).isEqualTo(resourceId);
      assertThat(response.staffed()).isTrue();
      assertThat(response.plannedCost()).isEqualByComparingTo(new BigDecimal("2400.00"));
    }

    @Test
    @DisplayName("unqualified resource is rejected without override")
    void staffAssignment_resourceNotQualified_isRejected() {
      UUID assignmentId = UUID.randomUUID();
      UUID resourceId = UUID.randomUUID();
      UUID roleId = UUID.randomUUID();
      UUID activityId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      ResourceAssignment assignment = ResourceAssignment.builder()
          .activityId(activityId).roleId(roleId)
          .resourceId(null).plannedUnits(8.0).rateType("STANDARD").build();
      assignment.setId(assignmentId);

      Resource resource = Resource.builder().name("R1").userId(userId).build();
      resource.setId(resourceId);

      when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
      when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));
      when(userResourceRoleRepository.existsByUserIdAndResourceRoleId(userId, roleId)).thenReturn(false);

      assertThatThrownBy(() -> service.staffAssignment(assignmentId, resourceId, false))
          .isInstanceOf(BusinessRuleException.class)
          .satisfies(ex -> assertThat(((BusinessRuleException) ex).getRuleCode()).isEqualTo("RESOURCE_NOT_QUALIFIED"));
    }

    @Test
    @DisplayName("override allows unqualified resource when caller is admin")
    void staffAssignment_overrideAllowsUnqualified_whenAdmin() {
      UUID assignmentId = UUID.randomUUID();
      UUID resourceId = UUID.randomUUID();
      UUID roleId = UUID.randomUUID();
      UUID activityId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      ResourceAssignment assignment = ResourceAssignment.builder()
          .activityId(activityId).roleId(roleId)
          .resourceId(null).plannedUnits(8.0).rateType("STANDARD").build();
      assignment.setId(assignmentId);

      Resource resource = Resource.builder().name("R1").userId(userId).build();
      resource.setId(resourceId);

      when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
      when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));
      when(userResourceRoleRepository.existsByUserIdAndResourceRoleId(userId, roleId)).thenReturn(false);

      // Simulate admin authority
      var auth = new org.springframework.security.authentication.TestingAuthenticationToken(
          "admin", "pass", "ROLE_ADMIN");
      org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

      try {
        when(assignmentRepository.findByActivityId(activityId)).thenReturn(List.of(assignment));
        when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResourceAssignmentResponse response = service.staffAssignment(assignmentId, resourceId, true);
        assertThat(response.resourceId()).isEqualTo(resourceId);
      } finally {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
      }
    }
  }

  @Nested
  @DisplayName("swapResource")
  class SwapTests {

    @Test
    @DisplayName("swap replaces resource and recomputes cost")
    void swapResource_replacesResource_andRecomputesCost() {
      UUID assignmentId = UUID.randomUUID();
      UUID oldResourceId = UUID.randomUUID();
      UUID newResourceId = UUID.randomUUID();
      UUID roleId = UUID.randomUUID();
      UUID activityId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      ResourceAssignment assignment = ResourceAssignment.builder()
          .activityId(activityId).roleId(roleId)
          .resourceId(oldResourceId).plannedUnits(8.0).rateType("STANDARD").build();
      assignment.setId(assignmentId);

      Resource resource = Resource.builder().name("R2").userId(userId).build();
      resource.setId(newResourceId);
      ResourceRate rate = ResourceRate.builder()
          .resourceId(newResourceId).rateType("STANDARD")
          .budgetedRate(new BigDecimal("400.00")).build();

      when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
      when(resourceRepository.findById(newResourceId)).thenReturn(Optional.of(resource));
      when(userResourceRoleRepository.existsByUserIdAndResourceRoleId(userId, roleId)).thenReturn(true);
      when(assignmentRepository.findByActivityId(activityId)).thenReturn(List.of(assignment));
      when(rateRepository.findByResourceIdAndRateTypeOrderByEffectiveDateDesc(newResourceId, "STANDARD"))
          .thenReturn(List.of(rate));
      when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      ResourceAssignmentResponse response = service.swapResource(assignmentId, newResourceId, false);

      assertThat(response.resourceId()).isEqualTo(newResourceId);
      assertThat(response.plannedCost()).isEqualByComparingTo(new BigDecimal("3200.00"));
    }
  }
}

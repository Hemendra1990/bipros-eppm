package com.bipros.resource.application.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.CreateResourceAssignmentRequest;
import com.bipros.resource.application.dto.ResourceAssignmentResponse;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.model.ResourceRole;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.bipros.resource.domain.repository.ResourceRateRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import com.bipros.resource.domain.repository.ResourceRoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResourceAssignmentService.hydrate — effective role")
class ResourceAssignmentServiceTest {

  @Mock private ResourceAssignmentRepository assignmentRepository;
  @Mock private ResourceRepository resourceRepository;
  @Mock private ResourceRateRepository rateRepository;
  @Mock private ResourceRoleRepository roleRepository;
  @Mock private ActivityRepository activityRepository;
  @Mock private ProjectResourceService projectResourceService;
  @Mock private AuditService auditService;

  @InjectMocks private ResourceAssignmentService service;

  private final UUID projectId = UUID.randomUUID();
  private final UUID activityId = UUID.randomUUID();

  private Activity activity() {
    Activity a = new Activity();
    a.setId(activityId);
    a.setName("Excavation");
    return a;
  }

  private ResourceRole role(String code, String name) {
    ResourceRole r = ResourceRole.builder().code(code).name(name).build();
    r.setId(UUID.randomUUID());
    return r;
  }

  private Resource resource(String code, String name, ResourceRole role) {
    Resource r = Resource.builder().code(code).name(name).role(role).build();
    r.setId(UUID.randomUUID());
    return r;
  }

  private ResourceAssignment assignment(UUID resourceId, UUID roleId) {
    ResourceAssignment a = ResourceAssignment.builder()
        .activityId(activityId)
        .projectId(projectId)
        .resourceId(resourceId)
        .roleId(roleId)
        .build();
    a.setId(UUID.randomUUID());
    return a;
  }

  @Test
  @DisplayName("uses assignment.roleId when explicitly set")
  void usesAssignmentRoleId() {
    ResourceRole helperRole = role("RM-HLP", "Helper");
    Resource helper = resource("LAB-HLP-01", "Anil Das", helperRole);
    ResourceAssignment asg = assignment(helper.getId(), helperRole.getId());

    when(assignmentRepository.findByActivityId(activityId)).thenReturn(List.of(asg));
    when(resourceRepository.findAllById(anyIterable())).thenReturn(List.of(helper));
    when(roleRepository.findAllById(anyIterable())).thenReturn(List.of(helperRole));
    when(activityRepository.findAllById(anyIterable())).thenReturn(List.of(activity()));

    List<ResourceAssignmentResponse> out = service.getAssignmentsByActivity(activityId);

    assertThat(out).hasSize(1);
    ResourceAssignmentResponse r = out.get(0);
    assertThat(r.roleId()).isEqualTo(helperRole.getId());
    assertThat(r.roleName()).isEqualTo("Helper");
    assertThat(r.effectiveRoleId()).isEqualTo(helperRole.getId());
    assertThat(r.effectiveRoleName()).isEqualTo("Helper");
  }

  @Test
  @DisplayName("falls back to resource.role when assignment.roleId is null")
  void fallsBackToResourceRole() {
    ResourceRole helperRole = role("RM-HLP", "Helper / Unskilled Labour");
    Resource helper = resource("LAB-HLP-01", "Anil Das", helperRole);
    ResourceAssignment asg = assignment(helper.getId(), null);

    when(assignmentRepository.findByActivityId(activityId)).thenReturn(List.of(asg));
    when(resourceRepository.findAllById(anyIterable())).thenReturn(List.of(helper));
    when(roleRepository.findAllById(anyIterable())).thenReturn(List.of(helperRole));
    when(activityRepository.findAllById(anyIterable())).thenReturn(List.of(activity()));

    ResourceAssignmentResponse r = service.getAssignmentsByActivity(activityId).get(0);

    assertThat(r.roleId()).isNull();
    assertThat(r.roleName()).isNull();
    assertThat(r.effectiveRoleId()).isEqualTo(helperRole.getId());
    assertThat(r.effectiveRoleName()).isEqualTo("Helper / Unskilled Labour");
  }

  @Test
  @DisplayName("returns null effective role when assignment has neither role nor resource")
  void noRoleAndNoResource() {
    ResourceAssignment asg = assignment(null, null);

    when(assignmentRepository.findByActivityId(activityId)).thenReturn(List.of(asg));
    when(activityRepository.findAllById(anyIterable())).thenReturn(List.of(activity()));
    // resourceRepository / roleRepository are not invoked because their id sets are empty;
    // mark as lenient so strict-stubbing doesn't fail if Mockito's unused-stub check engages.
    lenient().when(resourceRepository.findAllById(anyIterable())).thenReturn(List.of());
    lenient().when(roleRepository.findAllById(anyIterable())).thenReturn(List.of());

    ResourceAssignmentResponse r = service.getAssignmentsByActivity(activityId).get(0);

    assertThat(r.effectiveRoleId()).isNull();
    assertThat(r.effectiveRoleName()).isNull();
  }

  @Test
  @DisplayName("assignResource initializes remainingUnits and remainingCost so role-grouped totals don't understate")
  void assignResourceSeedsRemaining() {
    ResourceRole helperRole = role("RM-HLP", "Helper");
    Resource helper = resource("LAB-HLP-01", "Anil Das", helperRole);

    when(resourceRepository.existsById(helper.getId())).thenReturn(true);
    when(projectResourceService.isInPool(projectId, helper.getId())).thenReturn(true);
    when(assignmentRepository.findByActivityId(activityId)).thenReturn(List.of());
    when(activityRepository.findById(activityId)).thenReturn(Optional.empty());
    // Rate sources empty -> plannedCost null. The helper must still seed null remainingCost
    // (no spurious zero) and remainingUnits = plannedUnits.
    lenient().when(projectResourceService.resolveRateOverride(any(), any())).thenReturn(null);

    ArgumentCaptor<ResourceAssignment> saved = ArgumentCaptor.forClass(ResourceAssignment.class);
    when(assignmentRepository.save(saved.capture())).thenAnswer(inv -> {
      ResourceAssignment a = inv.getArgument(0);
      a.setId(UUID.randomUUID());
      return a;
    });
    // hydrate(saved) — single-entity overload — calls these:
    when(resourceRepository.findById(helper.getId())).thenReturn(Optional.of(helper));
    when(activityRepository.findById(activityId)).thenReturn(Optional.empty());

    service.assignResource(new CreateResourceAssignmentRequest(
        activityId, helper.getId(), null, projectId, 7.0, null, null, null, null));

    ResourceAssignment persisted = saved.getValue();
    assertThat(persisted.getPlannedUnits()).isEqualTo(7.0);
    assertThat(persisted.getRemainingUnits())
        .as("remainingUnits should mirror plannedUnits at creation time")
        .isEqualTo(7.0);
    assertThat(persisted.getPlannedCost()).as("no rate sources -> plannedCost null").isNull();
    assertThat(persisted.getRemainingCost())
        .as("plannedCost null -> remainingCost null (no spurious zero)").isNull();
  }

  @Test
  @DisplayName("assignResource with resolvable rate seeds remainingCost = plannedCost")
  void assignResourceSeedsRemainingCostWhenRateAvailable() {
    ResourceRole helperRole = role("RM-HLP", "Helper");
    Resource helper = resource("LAB-HLP-01", "Anil Das", helperRole);

    when(resourceRepository.existsById(helper.getId())).thenReturn(true);
    when(projectResourceService.isInPool(projectId, helper.getId())).thenReturn(true);
    when(assignmentRepository.findByActivityId(activityId)).thenReturn(List.of());
    when(activityRepository.findById(activityId)).thenReturn(Optional.empty());
    when(projectResourceService.resolveRateOverride(projectId, helper.getId()))
        .thenReturn(new BigDecimal("500"));

    ArgumentCaptor<ResourceAssignment> saved = ArgumentCaptor.forClass(ResourceAssignment.class);
    when(assignmentRepository.save(saved.capture())).thenAnswer(inv -> {
      ResourceAssignment a = inv.getArgument(0);
      a.setId(UUID.randomUUID());
      return a;
    });
    when(resourceRepository.findById(helper.getId())).thenReturn(Optional.of(helper));

    service.assignResource(new CreateResourceAssignmentRequest(
        activityId, helper.getId(), null, projectId, 7.0, null, null, null, null));

    ResourceAssignment persisted = saved.getValue();
    assertThat(persisted.getPlannedCost()).isEqualByComparingTo("3500");
    assertThat(persisted.getRemainingCost())
        .as("remainingCost should mirror plannedCost at creation time")
        .isEqualByComparingTo("3500");
  }

  @Test
  @DisplayName("groups multiple assignments by effective role across role-explicit and resource-derived rows")
  void mixedRowsResolveSameRole() {
    ResourceRole helperRole = role("RM-HLP", "Helper");
    Resource resourceA = resource("LAB-HLP-01", "Anil Das", helperRole);
    Resource resourceB = resource("LAB-HLP-02", "Bablu Sahu", helperRole);
    ResourceAssignment explicit = assignment(resourceA.getId(), helperRole.getId());
    ResourceAssignment derived = assignment(resourceB.getId(), null);

    when(assignmentRepository.findByActivityId(activityId)).thenReturn(List.of(explicit, derived));
    when(resourceRepository.findAllById(anyIterable())).thenReturn(List.of(resourceA, resourceB));
    when(roleRepository.findAllById(anyIterable())).thenReturn(List.of(helperRole));
    when(activityRepository.findAllById(anyIterable())).thenReturn(List.of(activity()));

    List<ResourceAssignmentResponse> out = service.getAssignmentsByActivity(activityId);

    assertThat(out).hasSize(2);
    assertThat(out).allSatisfy(r -> {
      assertThat(r.effectiveRoleId()).isEqualTo(helperRole.getId());
      assertThat(r.effectiveRoleName()).isEqualTo("Helper");
    });
  }
}

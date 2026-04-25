package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.CreateResourceTypeDefRequest;
import com.bipros.resource.application.dto.ResourceTypeDefResponse;
import com.bipros.resource.application.dto.UpdateResourceTypeDefRequest;
import com.bipros.resource.domain.model.ResourceType;
import com.bipros.resource.domain.model.ResourceTypeDef;
import com.bipros.resource.domain.repository.ResourceRepository;
import com.bipros.resource.domain.repository.ResourceRoleRepository;
import com.bipros.resource.domain.repository.ResourceTypeDefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResourceTypeDefService Tests")
class ResourceTypeDefServiceTest {

  @Mock private ResourceTypeDefRepository repository;
  @Mock private ResourceRepository resourceRepository;
  @Mock private ResourceRoleRepository roleRepository;
  @Mock private AuditService auditService;

  private ResourceTypeDefService service;

  @BeforeEach
  void setUp() {
    service = new ResourceTypeDefService(repository, resourceRepository, roleRepository, auditService);
  }

  @Nested
  @DisplayName("create")
  class CreateTests {

    @Test
    @DisplayName("uppercases code and persists with systemDefault=false")
    void createNormalisesCodeAndDefaultsSystemFlag() {
      when(repository.findByCode("SUBCONTRACTOR")).thenReturn(Optional.empty());
      when(repository.save(any())).thenAnswer(inv -> {
        ResourceTypeDef d = inv.getArgument(0);
        d.setId(UUID.randomUUID());
        return d;
      });

      ResourceTypeDefResponse response = service.create(new CreateResourceTypeDefRequest(
          "subcontractor", "Sub-Contractor", ResourceType.LABOR, "SUB", 5, true));

      assertThat(response.code()).isEqualTo("SUBCONTRACTOR");
      assertThat(response.systemDefault()).isFalse();
      assertThat(response.codePrefix()).isEqualTo("SUB");
      assertThat(response.baseCategory()).isEqualTo(ResourceType.LABOR);
    }

    @Test
    @DisplayName("rejects duplicate code")
    void duplicateCodeThrows() {
      ResourceTypeDef existing = ResourceTypeDef.builder().code("MANPOWER").build();
      when(repository.findByCode("MANPOWER")).thenReturn(Optional.of(existing));

      assertThatThrownBy(() -> service.create(new CreateResourceTypeDefRequest(
          "MANPOWER", "Manpower", ResourceType.LABOR, null, null, true)))
          .isInstanceOf(BusinessRuleException.class)
          .hasMessageContaining("already exists");

      verify(repository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("update")
  class UpdateTests {

    @Test
    @DisplayName("rejects code change on system-default row")
    void systemDefaultCodeChangeRejected() {
      UUID id = UUID.randomUUID();
      ResourceTypeDef def = ResourceTypeDef.builder()
          .code("MANPOWER").name("Manpower").baseCategory(ResourceType.LABOR)
          .systemDefault(true).build();
      when(repository.findById(id)).thenReturn(Optional.of(def));

      assertThatThrownBy(() -> service.update(id, new UpdateResourceTypeDefRequest(
          "RENAMED", "Manpower", ResourceType.LABOR, "LAB", 10, true)))
          .isInstanceOf(BusinessRuleException.class)
          .hasMessageContaining("Cannot change the code");
    }

    @Test
    @DisplayName("rejects baseCategory change on system-default row")
    void systemDefaultBaseCategoryChangeRejected() {
      UUID id = UUID.randomUUID();
      ResourceTypeDef def = ResourceTypeDef.builder()
          .code("MANPOWER").name("Manpower").baseCategory(ResourceType.LABOR)
          .systemDefault(true).build();
      when(repository.findById(id)).thenReturn(Optional.of(def));

      assertThatThrownBy(() -> service.update(id, new UpdateResourceTypeDefRequest(
          "MANPOWER", "Manpower", ResourceType.MATERIAL, "LAB", 10, true)))
          .isInstanceOf(BusinessRuleException.class)
          .hasMessageContaining("base category");
    }

    @Test
    @DisplayName("accepts name + prefix change on system-default row")
    void systemDefaultDescriptiveFieldsAllowed() {
      UUID id = UUID.randomUUID();
      ResourceTypeDef def = ResourceTypeDef.builder()
          .code("MANPOWER").name("Manpower").baseCategory(ResourceType.LABOR)
          .codePrefix("LAB").sortOrder(10).active(true).systemDefault(true).build();
      when(repository.findById(id)).thenReturn(Optional.of(def));
      when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      ResourceTypeDefResponse response = service.update(id, new UpdateResourceTypeDefRequest(
          "MANPOWER", "Workforce", ResourceType.LABOR, "WF", 5, true));

      assertThat(response.name()).isEqualTo("Workforce");
      assertThat(response.codePrefix()).isEqualTo("WF");
      assertThat(response.sortOrder()).isEqualTo(5);
    }

    @Test
    @DisplayName("custom row: rejects duplicate-code rename")
    void customDuplicateCodeRejected() {
      UUID id = UUID.randomUUID();
      ResourceTypeDef def = ResourceTypeDef.builder()
          .code("TOOL").name("Tool").baseCategory(ResourceType.NONLABOR)
          .systemDefault(false).build();
      when(repository.findById(id)).thenReturn(Optional.of(def));
      when(repository.findByCode("MACHINE")).thenReturn(Optional.of(
          ResourceTypeDef.builder().code("MACHINE").build()));

      assertThatThrownBy(() -> service.update(id, new UpdateResourceTypeDefRequest(
          "machine", "Tool", ResourceType.NONLABOR, "EQ", 1, true)))
          .isInstanceOf(BusinessRuleException.class)
          .hasMessageContaining("already exists");
    }
  }

  @Nested
  @DisplayName("delete")
  class DeleteTests {

    @Test
    @DisplayName("rejects deletion of system-default row")
    void systemDefaultCannotBeDeleted() {
      UUID id = UUID.randomUUID();
      ResourceTypeDef def = ResourceTypeDef.builder()
          .code("MANPOWER").name("Manpower").baseCategory(ResourceType.LABOR)
          .systemDefault(true).build();
      when(repository.findById(id)).thenReturn(Optional.of(def));

      assertThatThrownBy(() -> service.delete(id))
          .isInstanceOf(BusinessRuleException.class)
          .hasMessageContaining("system-default");

      verify(repository, never()).delete(any());
    }

    @Test
    @DisplayName("rejects deletion when in use by Resources")
    void inUseByResourcesRejected() {
      UUID id = UUID.randomUUID();
      ResourceTypeDef def = ResourceTypeDef.builder()
          .code("TOOL").name("Tool").baseCategory(ResourceType.NONLABOR)
          .systemDefault(false).build();
      when(repository.findById(id)).thenReturn(Optional.of(def));
      when(resourceRepository.countByResourceTypeDefId(id)).thenReturn(3L);
      when(roleRepository.countByResourceTypeDefId(id)).thenReturn(0L);

      assertThatThrownBy(() -> service.delete(id))
          .isInstanceOf(BusinessRuleException.class)
          .hasMessageContaining("3 record");

      verify(repository, never()).delete(any());
    }

    @Test
    @DisplayName("rejects deletion when in use by Roles only")
    void inUseByRolesRejected() {
      UUID id = UUID.randomUUID();
      ResourceTypeDef def = ResourceTypeDef.builder()
          .code("TOOL").name("Tool").baseCategory(ResourceType.NONLABOR)
          .systemDefault(false).build();
      when(repository.findById(id)).thenReturn(Optional.of(def));
      when(resourceRepository.countByResourceTypeDefId(id)).thenReturn(0L);
      when(roleRepository.countByResourceTypeDefId(id)).thenReturn(2L);

      assertThatThrownBy(() -> service.delete(id))
          .isInstanceOf(BusinessRuleException.class)
          .hasMessageContaining("2 record");
    }

    @Test
    @DisplayName("deletes unused custom row")
    void unusedCustomDeletes() {
      UUID id = UUID.randomUUID();
      ResourceTypeDef def = ResourceTypeDef.builder()
          .code("TOOL").name("Tool").baseCategory(ResourceType.NONLABOR)
          .systemDefault(false).build();
      when(repository.findById(id)).thenReturn(Optional.of(def));
      when(resourceRepository.countByResourceTypeDefId(id)).thenReturn(0L);
      when(roleRepository.countByResourceTypeDefId(id)).thenReturn(0L);

      service.delete(id);

      verify(repository).delete(def);
    }

    @Test
    @DisplayName("missing row throws ResourceNotFoundException")
    void missingRowThrows() {
      UUID id = UUID.randomUUID();
      when(repository.findById(id)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.delete(id))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }
}

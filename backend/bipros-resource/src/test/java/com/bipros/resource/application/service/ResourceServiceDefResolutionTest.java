package com.bipros.resource.application.service;

import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.CreateResourceRequest;
import com.bipros.resource.application.dto.ResourceResponse;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceType;
import com.bipros.resource.domain.model.ResourceTypeDef;
import com.bipros.resource.domain.repository.ResourceRepository;
import com.bipros.resource.domain.repository.ResourceTypeDefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link ResourceService} resolves the {@link ResourceTypeDef} from the request,
 * copies its {@code baseCategory} onto the saved {@link Resource}, and uses the def's prefix for
 * auto-generated codes — falling back to the base-category default when no prefix is set.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ResourceService → ResourceTypeDef resolution")
class ResourceServiceDefResolutionTest {

  @Mock private ResourceRepository resourceRepository;
  @Mock private ResourceTypeDefRepository resourceTypeDefRepository;
  @Mock private AuditService auditService;

  private ResourceService service;

  @BeforeEach
  void setUp() {
    service = new ResourceService(resourceRepository, resourceTypeDefRepository, auditService);
  }

  @Test
  @DisplayName("resolves def by id, copies baseCategory onto Resource and uses def.codePrefix")
  void createWithExplicitDefIdUsesItsPrefixAndBaseCategory() {
    UUID defId = UUID.randomUUID();
    ResourceTypeDef customDef = ResourceTypeDef.builder()
        .code("SUBCONTRACTOR").name("Sub-Contractor")
        .baseCategory(ResourceType.LABOR).codePrefix("SUB")
        .systemDefault(false).build();
    customDef.setId(defId);

    when(resourceTypeDefRepository.findById(defId)).thenReturn(Optional.of(customDef));
    when(resourceRepository.findAll()).thenReturn(List.of());
    when(resourceRepository.findByCode(any())).thenReturn(Optional.empty());
    when(resourceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CreateResourceRequest request = new CreateResourceRequest(
        null, "ACME Civil", defId, null,
        null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null);

    ResourceResponse response = service.createResource(request);

    ArgumentCaptor<Resource> captor = ArgumentCaptor.forClass(Resource.class);
    org.mockito.Mockito.verify(resourceRepository).save(captor.capture());
    Resource saved = captor.getValue();

    assertThat(saved.getResourceType()).isEqualTo(ResourceType.LABOR);
    assertThat(saved.getResourceTypeDef()).isSameAs(customDef);
    assertThat(saved.getCode()).isEqualTo("SUB-001");
    assertThat(response.resourceTypeName()).isEqualTo("Sub-Contractor");
    assertThat(response.resourceTypeCode()).isEqualTo("SUBCONTRACTOR");
    assertThat(response.resourceTypeDefId()).isEqualTo(defId);
  }

  @Test
  @DisplayName("legacy path: enum-only request resolves to system-default def for that base category")
  void createWithLegacyEnumResolvesSystemDefault() {
    ResourceTypeDef seededMaterial = ResourceTypeDef.builder()
        .code("MATERIAL").name("Material")
        .baseCategory(ResourceType.MATERIAL).codePrefix("MAT")
        .systemDefault(true).build();
    seededMaterial.setId(UUID.randomUUID());

    when(resourceTypeDefRepository.findFirstByBaseCategoryAndSystemDefaultTrue(ResourceType.MATERIAL))
        .thenReturn(Optional.of(seededMaterial));
    when(resourceRepository.findAll()).thenReturn(List.of());
    when(resourceRepository.findByCode(any())).thenReturn(Optional.empty());
    when(resourceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CreateResourceRequest request = new CreateResourceRequest(
        null, "Cement OPC 53", null, ResourceType.MATERIAL,
        null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null);

    ResourceResponse response = service.createResource(request);

    assertThat(response.code()).isEqualTo("MAT-001");
    assertThat(response.resourceType()).isEqualTo(ResourceType.MATERIAL);
    assertThat(response.resourceTypeName()).isEqualTo("Material");
  }

  @Test
  @DisplayName("def with null prefix falls back to base-category default (NONLABOR → EQ)")
  void nullPrefixFallsBackToBaseCategoryDefault() {
    UUID defId = UUID.randomUUID();
    ResourceTypeDef tool = ResourceTypeDef.builder()
        .code("TOOL").name("Tool")
        .baseCategory(ResourceType.NONLABOR).codePrefix(null)
        .systemDefault(false).build();
    tool.setId(defId);

    when(resourceTypeDefRepository.findById(defId)).thenReturn(Optional.of(tool));
    when(resourceRepository.findAll()).thenReturn(List.of());
    when(resourceRepository.findByCode(any())).thenReturn(Optional.empty());
    when(resourceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CreateResourceRequest request = new CreateResourceRequest(
        null, "Vibrator", defId, null,
        null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null);

    ResourceResponse response = service.createResource(request);

    assertThat(response.code()).isEqualTo("EQ-001");
    assertThat(response.resourceType()).isEqualTo(ResourceType.NONLABOR);
  }
}

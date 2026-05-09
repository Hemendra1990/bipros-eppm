package com.bipros.resource.application.service;

import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.repository.ResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateMasterSyncService")
class RateMasterSyncServiceTest {

  @Mock private ResourceRepository resourceRepository;
  @Mock private ResourceAssignmentService resourceAssignmentService;

  private RateMasterSyncService service;

  @BeforeEach
  void setUp() {
    service = new RateMasterSyncService(resourceRepository, resourceAssignmentService);
  }

  @Test
  @DisplayName("updates unit and costPerUnit on every linked resource")
  void cascadesToAllLinkedResources() {
    UUID rateMasterId = UUID.randomUUID();
    Resource r1 = Resource.builder().unit("Day").costPerUnit(new BigDecimal("80")).build();
    r1.setId(UUID.randomUUID());
    Resource r2 = Resource.builder().unit("Day").costPerUnit(new BigDecimal("80")).build();
    r2.setId(UUID.randomUUID());
    when(resourceRepository.findByRateMasterId(rateMasterId)).thenReturn(List.of(r1, r2));

    int count = service.syncResourcesForRateMaster(rateMasterId, "Hour", new BigDecimal("90"));

    assertThat(count).isEqualTo(2);
    assertThat(r1.getUnit()).isEqualTo("Hour");
    assertThat(r1.getCostPerUnit()).isEqualByComparingTo("90");
    assertThat(r2.getUnit()).isEqualTo("Hour");
    assertThat(r2.getCostPerUnit()).isEqualByComparingTo("90");
  }

  @Test
  @DisplayName("returns 0 when no resources are linked")
  void zeroWhenNoLinks() {
    UUID rateMasterId = UUID.randomUUID();
    when(resourceRepository.findByRateMasterId(rateMasterId)).thenReturn(List.of());

    int count = service.syncResourcesForRateMaster(rateMasterId, "Day", BigDecimal.TEN);

    assertThat(count).isZero();
  }

  @Test
  @DisplayName("returns 0 and skips lookup when rateMasterId is null")
  void nullRateMasterIdNoOp() {
    int count = service.syncResourcesForRateMaster(null, "Day", BigDecimal.TEN);
    assertThat(count).isZero();
  }
}

package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.EquipmentRateMasterRequest;
import com.bipros.resource.application.dto.EquipmentRateMasterResponse;
import com.bipros.resource.domain.model.rate.EquipmentRateMaster;
import com.bipros.resource.domain.repository.EquipmentRateMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EquipmentRateMasterService")
class EquipmentRateMasterServiceTest {

  @Mock private EquipmentRateMasterRepository repository;
  @Mock private RateMasterSyncService rateMasterSyncService;
  @Mock private AuditService auditService;

  private EquipmentRateMasterService service;

  @BeforeEach
  void setUp() {
    service = new EquipmentRateMasterService(repository, rateMasterSyncService, auditService);
  }

  @Nested
  @DisplayName("create")
  class CreateTests {

    @Test
    @DisplayName("trims whitespace and persists name/make/model/unit/rate")
    void persistsValid() {
      when(repository.findByEquipmentNameAndMakeAndModel("Excavator", "Caterpillar", "B451"))
          .thenReturn(Optional.empty());
      when(repository.save(any())).thenAnswer(inv -> {
        EquipmentRateMaster e = inv.getArgument(0);
        e.setId(UUID.randomUUID());
        return e;
      });

      EquipmentRateMasterResponse r = service.create(new EquipmentRateMasterRequest(
          "  Excavator  ", " Caterpillar ", " B451 ", "Hour", new BigDecimal("67"), true));

      assertThat(r.equipmentName()).isEqualTo("Excavator");
      assertThat(r.make()).isEqualTo("Caterpillar");
      assertThat(r.model()).isEqualTo("B451");
      assertThat(r.unit()).isEqualTo("Hour");
      assertThat(r.rate()).isEqualByComparingTo("67");
    }

    @Test
    @DisplayName("rejects duplicate (name, make, model)")
    void duplicateKeyRejected() {
      EquipmentRateMaster existing = EquipmentRateMaster.builder()
          .equipmentName("Excavator").make("Caterpillar").model("B451")
          .unit("Hour").rate(BigDecimal.TEN).active(true).build();
      existing.setId(UUID.randomUUID());
      when(repository.findByEquipmentNameAndMakeAndModel("Excavator", "Caterpillar", "B451"))
          .thenReturn(Optional.of(existing));

      assertThatThrownBy(() -> service.create(new EquipmentRateMasterRequest(
          "Excavator", "Caterpillar", "B451", "Hour", new BigDecimal("99"), true)))
          .isInstanceOf(BusinessRuleException.class)
          .hasMessageContaining("already exists");
    }
  }
}

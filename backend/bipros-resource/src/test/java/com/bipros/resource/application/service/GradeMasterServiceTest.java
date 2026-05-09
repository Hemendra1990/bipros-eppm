package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.GradeMasterRequest;
import com.bipros.resource.application.dto.GradeMasterResponse;
import com.bipros.resource.domain.model.GradeMaster;
import com.bipros.resource.domain.repository.GradeMasterRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GradeMasterService")
class GradeMasterServiceTest {

  @Mock private GradeMasterRepository repository;
  @Mock private AuditService auditService;

  private GradeMasterService service;

  @BeforeEach
  void setUp() {
    service = new GradeMasterService(repository, auditService);
  }

  @Nested
  @DisplayName("create")
  class CreateTests {

    @Test
    @DisplayName("normalizes code to upper-case and persists")
    void normalizesCode() {
      when(repository.findByCode("A")).thenReturn(Optional.empty());
      when(repository.save(any())).thenAnswer(inv -> {
        GradeMaster g = inv.getArgument(0);
        g.setId(UUID.randomUUID());
        return g;
      });

      GradeMasterResponse r = service.create(new GradeMasterRequest("a", "Grade A", null, 10, true));

      assertThat(r.code()).isEqualTo("A");
      assertThat(r.name()).isEqualTo("Grade A");
      assertThat(r.sortOrder()).isEqualTo(10);
      assertThat(r.active()).isTrue();
    }

    @Test
    @DisplayName("rejects duplicate code")
    void rejectsDuplicate() {
      GradeMaster existing = GradeMaster.builder().code("A").name("Grade A").build();
      when(repository.findByCode("A")).thenReturn(Optional.of(existing));

      assertThatThrownBy(() -> service.create(new GradeMasterRequest("a", "x", null, null, true)))
          .isInstanceOf(BusinessRuleException.class)
          .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("defaults active to true and sortOrder to 0 when null")
    void defaultsApplied() {
      when(repository.findByCode("B")).thenReturn(Optional.empty());
      when(repository.save(any())).thenAnswer(inv -> {
        GradeMaster g = inv.getArgument(0);
        g.setId(UUID.randomUUID());
        return g;
      });

      GradeMasterResponse r = service.create(new GradeMasterRequest("B", "Grade B", null, null, null));

      assertThat(r.active()).isTrue();
      assertThat(r.sortOrder()).isEqualTo(0);
    }
  }

  @Nested
  @DisplayName("update")
  class UpdateTests {

    @Test
    @DisplayName("rejects code change to one that already exists")
    void rejectsDuplicateCodeOnUpdate() {
      UUID id = UUID.randomUUID();
      GradeMaster existing = GradeMaster.builder().code("A").name("Grade A").sortOrder(0).active(true).build();
      existing.setId(id);
      GradeMaster other = GradeMaster.builder().code("B").name("Grade B").build();

      when(repository.findById(id)).thenReturn(Optional.of(existing));
      when(repository.findByCode("B")).thenReturn(Optional.of(other));

      assertThatThrownBy(() -> service.update(id,
          new GradeMasterRequest("b", "Grade A renamed", null, null, null)))
          .isInstanceOf(BusinessRuleException.class)
          .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("throws when grade not found")
    void notFound() {
      UUID id = UUID.randomUUID();
      when(repository.findById(id)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.update(id,
          new GradeMasterRequest("X", "X", null, null, null)))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }
}

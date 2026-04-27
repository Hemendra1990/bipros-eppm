package com.bipros.risk.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.security.ProjectAccessGuard;
import com.bipros.common.util.AuditService;
import com.bipros.risk.application.dto.CreateRiskTemplateRequest;
import com.bipros.risk.application.dto.RiskSummary;
import com.bipros.risk.application.dto.RiskTemplateResponse;
import com.bipros.risk.application.dto.UpdateRiskTemplateRequest;
import com.bipros.risk.domain.model.Industry;
import com.bipros.risk.domain.model.Risk;
import com.bipros.risk.domain.model.RiskCategoryMaster;
import com.bipros.risk.domain.model.RiskTemplate;
import com.bipros.risk.domain.repository.RiskCategoryMasterRepository;
import com.bipros.risk.domain.repository.RiskRepository;
import com.bipros.risk.domain.repository.RiskTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RiskTemplateService")
class RiskTemplateServiceTest {

    @Mock private RiskTemplateRepository repository;
    @Mock private RiskRepository riskRepository;
    @Mock private RiskService riskService;
    @Mock private RiskScoringMatrixService matrixService;
    @Mock private AuditService auditService;
    @Mock private RiskCategoryMasterRepository categoryRepository;
    @Mock private ProjectAccessGuard projectAccess;

    private RiskTemplateService service;

    @BeforeEach
    void setUp() {
        service = new RiskTemplateService(repository, riskRepository, riskService, matrixService,
            auditService, categoryRepository, projectAccess);
    }

    private static RiskCategoryMaster categoryRef(String code) {
        RiskCategoryMaster cat = new RiskCategoryMaster();
        cat.setId(UUID.randomUUID());
        cat.setCode(code);
        cat.setName(code);
        return cat;
    }

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("uppercases code and persists with systemDefault=false")
        void createNormalisesCodeAndDefaultsSystemFlag() {
            when(repository.findByCode("ROAD-CUSTOM-1")).thenReturn(Optional.empty());
            when(repository.save(any())).thenAnswer(inv -> {
                RiskTemplate t = inv.getArgument(0);
                t.setId(UUID.randomUUID());
                return t;
            });

            UUID catId = UUID.randomUUID();
            when(categoryRepository.findById(catId)).thenReturn(Optional.of(categoryRef("LA-GENERIC")));
            RiskTemplateResponse response = service.create(new CreateRiskTemplateRequest(
                "road-custom-1", "Custom road risk", "desc", Industry.ROAD,
                Set.of("HIGHWAY"), catId,
                3, 4, 2, "guidance", false, 100, true));

            assertThat(response.code()).isEqualTo("ROAD-CUSTOM-1");
            assertThat(response.systemDefault()).isFalse();
            assertThat(response.industry()).isEqualTo(Industry.ROAD);
            assertThat(response.applicableProjectCategories()).containsExactly("HIGHWAY");
        }

        @Test
        @DisplayName("rejects duplicate code")
        void duplicateCodeThrows() {
            RiskTemplate existing = RiskTemplate.builder().code("ROAD-LAND-001").build();
            when(repository.findByCode("ROAD-LAND-001")).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> service.create(new CreateRiskTemplateRequest(
                "ROAD-LAND-001", "x", null, Industry.ROAD,
                null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");

            verify(repository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("update — system-default protection")
    class UpdateSystemDefault {

        @Test
        @DisplayName("rejects code change on system-default template")
        void systemDefaultCodeChangeRejected() {
            UUID id = UUID.randomUUID();
            RiskTemplate def = RiskTemplate.builder()
                .code("ROAD-LAND-001").title("Land").industry(Industry.ROAD)
                .systemDefault(true).build();
            when(repository.findById(id)).thenReturn(Optional.of(def));

            assertThatThrownBy(() -> service.update(id, new UpdateRiskTemplateRequest(
                "RENAMED", "Land", null, Industry.ROAD,
                null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot change the code");
        }

        @Test
        @DisplayName("rejects industry change on system-default template")
        void systemDefaultIndustryChangeRejected() {
            UUID id = UUID.randomUUID();
            RiskTemplate def = RiskTemplate.builder()
                .code("ROAD-LAND-001").title("Land").industry(Industry.ROAD)
                .systemDefault(true).build();
            when(repository.findById(id)).thenReturn(Optional.of(def));

            assertThatThrownBy(() -> service.update(id, new UpdateRiskTemplateRequest(
                "ROAD-LAND-001", "Land", null, Industry.GENERIC,
                null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("industry");
        }

        @Test
        @DisplayName("descriptive fields editable on system-default")
        void descriptiveFieldsEditable() {
            UUID id = UUID.randomUUID();
            RiskTemplate def = RiskTemplate.builder()
                .code("ROAD-LAND-001").title("Land").industry(Industry.ROAD)
                .systemDefault(true).active(true).build();
            when(repository.findById(id)).thenReturn(Optional.of(def));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UUID catId = UUID.randomUUID();
            when(categoryRepository.findById(catId)).thenReturn(Optional.of(categoryRef("LA-GENERIC")));
            RiskTemplateResponse response = service.update(id, new UpdateRiskTemplateRequest(
                "ROAD-LAND-001", "Land acquisition (revised)", "new description",
                Industry.ROAD, Set.of("HIGHWAY", "EXPRESSWAY"),
                catId, 4, 3, 5,
                "improved guidance", false, 10, true));

            assertThat(response.title()).isEqualTo("Land acquisition (revised)");
            assertThat(response.description()).isEqualTo("new description");
            assertThat(response.applicableProjectCategories())
                .containsExactlyInAnyOrder("HIGHWAY", "EXPRESSWAY");
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("rejects deletion of system-default template")
        void systemDefaultCannotBeDeleted() {
            UUID id = UUID.randomUUID();
            RiskTemplate def = RiskTemplate.builder()
                .code("ROAD-LAND-001").title("Land").industry(Industry.ROAD)
                .systemDefault(true).build();
            when(repository.findById(id)).thenReturn(Optional.of(def));

            assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("system-default");

            verify(repository, never()).delete(any());
        }

        @Test
        @DisplayName("deletes custom template")
        void customDeletes() {
            UUID id = UUID.randomUUID();
            RiskTemplate def = RiskTemplate.builder()
                .code("CUSTOM").title("x").industry(Industry.GENERIC)
                .systemDefault(false).build();
            when(repository.findById(id)).thenReturn(Optional.of(def));

            service.delete(id);

            verify(repository).delete(def);
        }
    }

    @Nested
    @DisplayName("copyToProject")
    class CopyToProject {

        @Test
        @DisplayName("creates one Risk per template, generates sequential codes, starts in IDENTIFIED with no owner")
        void copyCreatesRisks() {
            UUID projectId = UUID.randomUUID();
            UUID t1Id = UUID.randomUUID();
            UUID t2Id = UUID.randomUUID();
            RiskTemplate t1 = RiskTemplate.builder().code("ROAD-LAND-001")
                .title("Land acq").industry(Industry.ROAD).category(categoryRef("LA-GENERIC"))
                .defaultProbability(4).defaultImpactCost(3).defaultImpactSchedule(5).build();
            t1.setId(t1Id);
            RiskTemplate t2 = RiskTemplate.builder().code("ROAD-MONSOON-004")
                .title("Monsoon").industry(Industry.ROAD).category(categoryRef("MW-FLASH-FLOOD"))
                .defaultProbability(5).defaultImpactCost(2).defaultImpactSchedule(4).build();
            t2.setId(t2Id);

            when(repository.findAllById(List.of(t1Id, t2Id))).thenReturn(List.of(t1, t2));
            when(riskRepository.maxRiskCodeNumber(projectId)).thenReturn(0); // empty register
            when(riskRepository.save(any())).thenAnswer(inv -> {
                Risk r = inv.getArgument(0);
                r.setId(UUID.randomUUID());
                return r;
            });
            when(riskService.toSummary(any())).thenAnswer(inv -> {
                Risk r = inv.getArgument(0);
                return RiskSummary.builder().id(r.getId()).code(r.getCode()).title(r.getTitle()).build();
            });

            List<RiskSummary> created = service.copyToProject(projectId, List.of(t1Id, t2Id));

            assertThat(created).hasSize(2);
            assertThat(created.get(0).getCode()).isEqualTo("RISK-0001");
            assertThat(created.get(1).getCode()).isEqualTo("RISK-0002");
            assertThat(created.get(0).getTitle()).isEqualTo("Land acq");
            assertThat(created.get(1).getTitle()).isEqualTo("Monsoon");
        }

        @Test
        @DisplayName("continues sequence from existing project risks")
        void copyContinuesSequence() {
            UUID projectId = UUID.randomUUID();
            UUID tId = UUID.randomUUID();
            RiskTemplate t = RiskTemplate.builder().code("X").title("X")
                .industry(Industry.GENERIC).build();
            t.setId(tId);
            // Pretend the project already has 5 risks
            when(repository.findAllById(List.of(tId))).thenReturn(List.of(t));
            when(riskRepository.maxRiskCodeNumber(projectId)).thenReturn(5); // existing RISK-0001..RISK-0005
            when(riskRepository.save(any())).thenAnswer(inv -> {
                Risk r = inv.getArgument(0);
                r.setId(UUID.randomUUID());
                return r;
            });
            when(riskService.toSummary(any())).thenAnswer(inv -> {
                Risk r = inv.getArgument(0);
                return RiskSummary.builder().code(r.getCode()).build();
            });

            List<RiskSummary> created = service.copyToProject(projectId, List.of(tId));

            assertThat(created).hasSize(1);
            assertThat(created.get(0).getCode()).isEqualTo("RISK-0006");
        }
    }
}

package com.bipros.resource.application.service;

import com.bipros.resource.application.dto.LabourDesignationRequest;
import com.bipros.resource.application.dto.LabourDesignationResponse;
import com.bipros.resource.domain.model.LabourCategory;
import com.bipros.resource.domain.model.LabourDesignation;
import com.bipros.resource.domain.model.LabourGrade;
import com.bipros.resource.domain.model.LabourStatus;
import com.bipros.resource.domain.model.NationalityType;
import com.bipros.resource.domain.repository.LabourDesignationRepository;
import com.bipros.resource.domain.repository.ProjectLabourDeploymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LabourDesignationServiceTest {

    @Mock LabourDesignationRepository designationRepo;
    @Mock ProjectLabourDeploymentRepository deploymentRepo;

    @InjectMocks LabourDesignationService service;

    private LabourDesignationRequest baseRequest() {
        return new LabourDesignationRequest(
            "SM-001", "Project Manager",
            LabourCategory.SITE_MANAGEMENT, "Civil Engineering",
            LabourGrade.A, NationalityType.OMANI_OR_EXPAT,
            15, new BigDecimal("125.00"), "OMR",
            List.of("Project Planning", "FIDIC"),
            List.of("PMP", "B.Eng Civil"),
            null, null, 1);
    }

    @Test
    void create_persistsAndReturnsResponse() {
        when(designationRepo.existsByCode("SM-001")).thenReturn(false);
        when(designationRepo.save(any(LabourDesignation.class)))
            .thenAnswer(inv -> {
                LabourDesignation d = inv.getArgument(0);
                d.setId(UUID.randomUUID());
                return d;
            });

        LabourDesignationResponse out = service.create(baseRequest());

        assertThat(out.code()).isEqualTo("SM-001");
        assertThat(out.category()).isEqualTo(LabourCategory.SITE_MANAGEMENT);
        assertThat(out.codePrefix()).isEqualTo("SM");
        assertThat(out.skills()).containsExactly("Project Planning", "FIDIC");
    }

    @Test
    void create_rejectsCodePrefixCategoryMismatch() {
        LabourDesignationRequest bad = new LabourDesignationRequest(
            "PO-001", "Project Manager",
            LabourCategory.SITE_MANAGEMENT, "Civil Engineering",
            LabourGrade.A, NationalityType.OMANI_OR_EXPAT,
            15, new BigDecimal("125.00"), "OMR",
            List.of(), List.of(), null, null, 0);

        assertThatThrownBy(() -> service.create(bad))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("prefix");
    }

    @Test
    void create_rejectsDuplicateCode() {
        when(designationRepo.existsByCode("SM-001")).thenReturn(true);
        assertThatThrownBy(() -> service.create(baseRequest()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void delete_softDeletesByDefault() {
        UUID id = UUID.randomUUID();
        LabourDesignation existing = LabourDesignation.builder().code("SM-001")
            .designation("X").category(LabourCategory.SITE_MANAGEMENT).trade("T")
            .grade(LabourGrade.A).nationality(NationalityType.EXPAT)
            .experienceYearsMin(1).defaultDailyRate(BigDecimal.ONE)
            .currency("OMR").skills(List.of()).certifications(List.of())
            .status(LabourStatus.ACTIVE).sortOrder(0).build();
        existing.setId(id);

        when(designationRepo.findById(id)).thenReturn(Optional.of(existing));
        when(deploymentRepo.existsByDesignationId(id)).thenReturn(true);
        when(designationRepo.save(any(LabourDesignation.class))).thenAnswer(inv -> inv.getArgument(0));

        service.delete(id);

        assertThat(existing.getStatus()).isEqualTo(LabourStatus.INACTIVE);
    }
}

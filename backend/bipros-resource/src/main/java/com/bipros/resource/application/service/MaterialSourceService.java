package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.CreateMaterialSourceRequest;
import com.bipros.resource.application.dto.MaterialSourceResponse;
import com.bipros.resource.domain.model.LabTestStatus;
import com.bipros.resource.domain.model.MaterialSource;
import com.bipros.resource.domain.model.MaterialSourceLabTest;
import com.bipros.resource.domain.model.MaterialSourceType;
import com.bipros.resource.domain.repository.MaterialSourceLabTestRepository;
import com.bipros.resource.domain.repository.MaterialSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class MaterialSourceService {

    private final MaterialSourceRepository sourceRepository;
    private final MaterialSourceLabTestRepository labTestRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<MaterialSourceResponse> listByProject(UUID projectId, MaterialSourceType type) {
        List<MaterialSource> rows = type != null
            ? sourceRepository.findByProjectIdAndSourceType(projectId, type)
            : sourceRepository.findByProjectId(projectId);
        return rows.stream().map(this::hydrate).toList();
    }

    @Transactional(readOnly = true)
    public MaterialSourceResponse get(UUID id) {
        return hydrate(findOrThrow(id));
    }

    public MaterialSourceResponse create(UUID projectId, CreateMaterialSourceRequest request) {
        String code = request.sourceCode() != null && !request.sourceCode().isBlank()
            ? request.sourceCode() : generateCode(projectId, request.sourceType());
        if (sourceRepository.existsByProjectIdAndSourceCode(projectId, code)) {
            throw new BusinessRuleException("DUPLICATE_SOURCE_CODE",
                "Source code '" + code + "' already exists for this project");
        }

        MaterialSource source = MaterialSource.builder()
            .projectId(projectId)
            .sourceCode(code)
            .name(request.name())
            .sourceType(request.sourceType())
            .village(request.village())
            .taluk(request.taluk())
            .district(request.district())
            .state(request.state())
            .distanceKm(request.distanceKm())
            .approvedQuantity(request.approvedQuantity())
            .approvedQuantityUnit(request.approvedQuantityUnit())
            .approvalReference(request.approvalReference())
            .approvalAuthority(request.approvalAuthority())
            .cbrAveragePercent(request.cbrAveragePercent())
            .mddGcc(request.mddGcc())
            .labTestStatus(request.labTestStatus())
            .build();

        MaterialSource saved = sourceRepository.save(source);
        List<MaterialSourceLabTest> tests = saveLabTests(saved.getId(), request.labTests(), true);
        recomputeLabStatus(saved, tests);
        sourceRepository.save(saved);

        auditService.logCreate("MaterialSource", saved.getId(), hydrate(saved));
        return hydrate(saved);
    }

    public MaterialSourceResponse update(UUID id, CreateMaterialSourceRequest request) {
        MaterialSource source = findOrThrow(id);

        if (request.name() != null) source.setName(request.name());
        if (request.sourceType() != null) source.setSourceType(request.sourceType());
        if (request.village() != null) source.setVillage(request.village());
        if (request.taluk() != null) source.setTaluk(request.taluk());
        if (request.district() != null) source.setDistrict(request.district());
        if (request.state() != null) source.setState(request.state());
        if (request.distanceKm() != null) source.setDistanceKm(request.distanceKm());
        if (request.approvedQuantity() != null) source.setApprovedQuantity(request.approvedQuantity());
        if (request.approvedQuantityUnit() != null) source.setApprovedQuantityUnit(request.approvedQuantityUnit());
        if (request.approvalReference() != null) source.setApprovalReference(request.approvalReference());
        if (request.approvalAuthority() != null) source.setApprovalAuthority(request.approvalAuthority());
        if (request.cbrAveragePercent() != null) source.setCbrAveragePercent(request.cbrAveragePercent());
        if (request.mddGcc() != null) source.setMddGcc(request.mddGcc());
        if (request.labTestStatus() != null) source.setLabTestStatus(request.labTestStatus());

        List<MaterialSourceLabTest> tests = request.labTests() != null
            ? saveLabTests(id, request.labTests(), false)
            : labTestRepository.findBySourceId(id);
        if (request.labTestStatus() == null) {
            recomputeLabStatus(source, tests);
        }
        MaterialSource saved = sourceRepository.save(source);
        auditService.logUpdate("MaterialSource", id, "materialSource", null, hydrate(saved));
        return hydrate(saved);
    }

    public void delete(UUID id) {
        MaterialSource source = findOrThrow(id);
        labTestRepository.deleteBySourceId(id);
        sourceRepository.delete(source);
        auditService.logDelete("MaterialSource", id);
    }

    private List<MaterialSourceLabTest> saveLabTests(UUID sourceId,
            List<CreateMaterialSourceRequest.LabTestInput> inputs, boolean replace) {
        if (replace) {
            labTestRepository.deleteBySourceId(sourceId);
            labTestRepository.flush();
        }
        if (inputs == null) return labTestRepository.findBySourceId(sourceId);
        if (!replace) {
            labTestRepository.deleteBySourceId(sourceId);
            labTestRepository.flush();
        }
        inputs.forEach(in -> {
            MaterialSourceLabTest t = MaterialSourceLabTest.builder()
                .sourceId(sourceId)
                .testName(in.testName())
                .standardReference(in.standardReference())
                .resultValue(in.resultValue())
                .resultUnit(in.resultUnit())
                .passed(in.passed())
                .testDate(in.testDate())
                .remarks(in.remarks())
                .build();
            labTestRepository.save(t);
        });
        return labTestRepository.findBySourceId(sourceId);
    }

    /** Roll the individual lab-test outcomes up into the denormalised status on the source. */
    private void recomputeLabStatus(MaterialSource source, List<MaterialSourceLabTest> tests) {
        if (tests == null || tests.isEmpty()) {
            source.setLabTestStatus(LabTestStatus.TESTS_PENDING);
            return;
        }
        boolean anyPending = tests.stream().anyMatch(t -> t.getPassed() == null);
        boolean anyFailed = tests.stream().anyMatch(t -> Boolean.FALSE.equals(t.getPassed()));
        if (anyFailed) source.setLabTestStatus(LabTestStatus.ONE_OR_MORE_FAIL);
        else if (anyPending) source.setLabTestStatus(LabTestStatus.TESTS_PENDING);
        else source.setLabTestStatus(LabTestStatus.ALL_PASS);
    }

    private MaterialSourceResponse hydrate(MaterialSource source) {
        return MaterialSourceResponse.from(source, labTestRepository.findBySourceId(source.getId()));
    }

    private MaterialSource findOrThrow(UUID id) {
        return sourceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("MaterialSource", id));
    }

    private String generateCode(UUID projectId, MaterialSourceType type) {
        String prefix = type.prefix() + "-";
        int suffixStart = prefix.length() + 1;
        Integer max = sourceRepository.findMaxSuffix(projectId, prefix + "%", suffixStart);
        int next = max == null ? 1 : max + 1;
        return String.format("%s%03d", prefix, next);
    }
}

package com.bipros.risk.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.risk.application.dto.CreateRiskTemplateRequest;
import com.bipros.risk.application.dto.RiskSummary;
import com.bipros.risk.application.dto.RiskTemplateResponse;
import com.bipros.risk.application.dto.UpdateRiskTemplateRequest;
import com.bipros.risk.domain.model.Industry;
import com.bipros.risk.domain.model.Risk;
import com.bipros.risk.domain.model.RiskProbability;
import com.bipros.risk.domain.model.RiskStatus;
import com.bipros.risk.domain.model.RiskTemplate;
import com.bipros.risk.domain.repository.RiskRepository;
import com.bipros.risk.domain.repository.RiskTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class RiskTemplateService {

    private final RiskTemplateRepository repository;
    private final RiskRepository riskRepository;
    private final RiskService riskService;
    private final AuditService auditService;

    public RiskTemplateResponse create(CreateRiskTemplateRequest request) {
        log.info("Creating risk template: code={}, industry={}", request.code(), request.industry());
        String code = request.code().trim().toUpperCase();
        if (repository.findByCode(code).isPresent()) {
            throw new BusinessRuleException("DUPLICATE_RISK_TEMPLATE_CODE",
                "Risk template with code " + code + " already exists");
        }
        RiskTemplate template = RiskTemplate.builder()
            .code(code)
            .title(request.title())
            .description(request.description())
            .industry(request.industry())
            .applicableProjectCategories(normaliseCategories(request.applicableProjectCategories()))
            .category(request.category())
            .defaultProbability(request.defaultProbability())
            .defaultImpactCost(request.defaultImpactCost())
            .defaultImpactSchedule(request.defaultImpactSchedule())
            .mitigationGuidance(request.mitigationGuidance())
            .isOpportunity(request.isOpportunity() != null ? request.isOpportunity() : Boolean.FALSE)
            .sortOrder(request.sortOrder())
            .active(request.active() == null ? Boolean.TRUE : request.active())
            .systemDefault(Boolean.FALSE)
            .build();
        RiskTemplate saved = repository.save(template);
        auditService.logCreate("RiskTemplate", saved.getId(), RiskTemplateResponse.from(saved));
        return RiskTemplateResponse.from(saved);
    }

    public RiskTemplateResponse update(UUID id, UpdateRiskTemplateRequest request) {
        log.info("Updating risk template: id={}", id);
        RiskTemplate template = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("RiskTemplate", id));

        String requestedCode = request.code() == null ? null : request.code().trim().toUpperCase();

        if (Boolean.TRUE.equals(template.getSystemDefault())) {
            if (requestedCode != null && !requestedCode.equals(template.getCode())) {
                throw new BusinessRuleException("SYSTEM_DEFAULT_IMMUTABLE",
                    "Cannot change the code of a system-default risk template");
            }
            if (request.industry() != null && request.industry() != template.getIndustry()) {
                throw new BusinessRuleException("SYSTEM_DEFAULT_IMMUTABLE",
                    "Cannot change the industry of a system-default risk template");
            }
        } else {
            if (requestedCode != null && !requestedCode.equals(template.getCode())
                && repository.findByCode(requestedCode).isPresent()) {
                throw new BusinessRuleException("DUPLICATE_RISK_TEMPLATE_CODE",
                    "Risk template with code " + requestedCode + " already exists");
            }
            if (requestedCode != null) template.setCode(requestedCode);
            if (request.industry() != null) template.setIndustry(request.industry());
        }

        template.setTitle(request.title());
        template.setDescription(request.description());
        template.setApplicableProjectCategories(normaliseCategories(request.applicableProjectCategories()));
        template.setCategory(request.category());
        template.setDefaultProbability(request.defaultProbability());
        template.setDefaultImpactCost(request.defaultImpactCost());
        template.setDefaultImpactSchedule(request.defaultImpactSchedule());
        template.setMitigationGuidance(request.mitigationGuidance());
        if (request.isOpportunity() != null) template.setIsOpportunity(request.isOpportunity());
        template.setSortOrder(request.sortOrder());
        if (request.active() != null) template.setActive(request.active());

        RiskTemplate updated = repository.save(template);
        auditService.logUpdate("RiskTemplate", id, "riskTemplate", null, RiskTemplateResponse.from(updated));
        return RiskTemplateResponse.from(updated);
    }

    public void delete(UUID id) {
        log.info("Deleting risk template: id={}", id);
        RiskTemplate template = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("RiskTemplate", id));
        if (Boolean.TRUE.equals(template.getSystemDefault())) {
            throw new BusinessRuleException("SYSTEM_DEFAULT_PROTECTED",
                "Cannot delete the system-default risk template '" + template.getTitle() + "'");
        }
        repository.delete(template);
        auditService.logDelete("RiskTemplate", id);
    }

    @Transactional(readOnly = true)
    public RiskTemplateResponse get(UUID id) {
        RiskTemplate template = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("RiskTemplate", id));
        return RiskTemplateResponse.from(template);
    }

    @Transactional(readOnly = true)
    public List<RiskTemplateResponse> list(Industry industry, String projectCategory, Boolean activeOnly) {
        List<RiskTemplate> base = (industry != null)
            ? repository.findByIndustry(industry)
            : repository.findAll();
        String normalisedCategory =
            (projectCategory == null || projectCategory.isBlank())
                ? null : projectCategory.trim().toUpperCase();
        return base.stream()
            .filter(t -> activeOnly == null || !activeOnly || Boolean.TRUE.equals(t.getActive()))
            .filter(t -> {
                if (normalisedCategory == null) return true;
                Set<String> tags = t.getApplicableProjectCategories();
                // Templates with no category tags are industry-wide → always included.
                if (tags == null || tags.isEmpty()) return true;
                return tags.contains(normalisedCategory);
            })
            .sorted(displayOrder())
            .map(RiskTemplateResponse::from)
            .toList();
    }

    /**
     * Bulk-copy library templates into a project's risk register. Each copy starts in
     * {@link RiskStatus#IDENTIFIED} status with no owner — the PM is expected to assign
     * ownership and refine probability/impact before the risk counts as "well analysed"
     * (see {@code RiskQualityService}).
     *
     * <p>Risk codes are generated as {@code RISK-NNNN} continuing from the project's
     * existing risk count.
     */
    public List<RiskSummary> copyToProject(UUID projectId, List<UUID> templateIds) {
        log.info("Copying {} template(s) to project {}", templateIds.size(), projectId);
        List<RiskTemplate> templates = repository.findAllById(templateIds);
        if (templates.size() != new HashSet<>(templateIds).size()) {
            throw new ResourceNotFoundException("RiskTemplate",
                "one or more template IDs not found");
        }
        int existingCount = riskRepository.findByProjectId(projectId).size();
        List<RiskSummary> created = new ArrayList<>(templates.size());
        int seq = existingCount;
        for (RiskTemplate t : templates) {
            seq++;
            Risk risk = new Risk();
            risk.setProjectId(projectId);
            risk.setCode(String.format("RISK-%04d", seq));
            risk.setTitle(t.getTitle());
            risk.setDescription(t.getDescription());
            risk.setCategory(t.getCategory());
            risk.setStatus(RiskStatus.IDENTIFIED);
            risk.setProbability(toProbability(t.getDefaultProbability()));
            risk.setImpactCost(t.getDefaultImpactCost());
            risk.setImpactSchedule(t.getDefaultImpactSchedule());
            risk.setIsOpportunity(Boolean.TRUE.equals(t.getIsOpportunity()));
            risk.setIdentifiedDate(LocalDate.now());
            risk.calculateRiskScore();
            Risk saved = riskRepository.save(risk);
            auditService.logCreate("Risk", saved.getId(), saved);
            created.add(riskService.toSummary(saved));
        }
        return created;
    }

    private static RiskProbability toProbability(Integer level) {
        if (level == null) return null;
        return switch (level) {
            case 1 -> RiskProbability.VERY_LOW;
            case 2 -> RiskProbability.LOW;
            case 3 -> RiskProbability.MEDIUM;
            case 4 -> RiskProbability.HIGH;
            case 5 -> RiskProbability.VERY_HIGH;
            default -> null;
        };
    }

    private static Set<String> normaliseCategories(Set<String> raw) {
        if (raw == null || raw.isEmpty()) return new HashSet<>();
        Set<String> out = new HashSet<>(raw.size());
        for (String s : raw) {
            if (s == null || s.isBlank()) continue;
            out.add(s.trim().toUpperCase());
        }
        return out;
    }

    private static Comparator<RiskTemplate> displayOrder() {
        Comparator<RiskTemplate> bySort = Comparator.comparing(
            RiskTemplate::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()));
        return bySort.thenComparing(RiskTemplate::getTitle, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
    }
}

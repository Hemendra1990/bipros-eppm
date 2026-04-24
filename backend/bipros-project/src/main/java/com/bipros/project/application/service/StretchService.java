package com.bipros.project.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.project.application.dto.CreateStretchRequest;
import com.bipros.project.application.dto.StretchProgressResponse;
import com.bipros.project.application.dto.StretchResponse;
import com.bipros.project.application.dto.UpdateStretchRequest;
import com.bipros.project.domain.model.BoqItem;
import com.bipros.project.domain.model.Stretch;
import com.bipros.project.domain.model.StretchActivityLink;
import com.bipros.project.domain.model.StretchStatus;
import com.bipros.project.domain.repository.BoqItemRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.project.domain.repository.StretchActivityLinkRepository;
import com.bipros.project.domain.repository.StretchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class StretchService {

    private final StretchRepository stretchRepository;
    private final StretchActivityLinkRepository linkRepository;
    private final BoqItemRepository boqItemRepository;
    private final ProjectRepository projectRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<StretchResponse> listByProject(UUID projectId) {
        ensureProjectExists(projectId);
        return stretchRepository.findByProjectIdOrderByFromChainageM(projectId).stream()
            .map(this::hydrate)
            .toList();
    }

    @Transactional(readOnly = true)
    public StretchResponse get(UUID id) {
        return hydrate(findOrThrow(id));
    }

    /**
     * Cost-weighted physical progress over the linked BOQ items. Uses
     * {@code Σ(qtyExecuted × boqRate) / Σ(boqQty × boqRate)} so a half-complete expensive
     * item outweighs a completed cheap one.
     */
    @Transactional(readOnly = true)
    public StretchProgressResponse progress(UUID stretchId) {
        Stretch stretch = findOrThrow(stretchId);
        List<UUID> boqItemIds = linkRepository.findByStretchId(stretchId).stream()
            .map(StretchActivityLink::getBoqItemId)
            .toList();
        if (boqItemIds.isEmpty()) {
            return new StretchProgressResponse(
                stretch.getId(), stretch.getStretchCode(), stretch.getName(),
                0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        List<BoqItem> items = boqItemRepository.findAllById(boqItemIds);
        BigDecimal totalBoq = BigDecimal.ZERO;
        BigDecimal totalExecuted = BigDecimal.ZERO;
        for (BoqItem b : items) {
            BigDecimal qty = b.getBoqQty() != null ? b.getBoqQty() : BigDecimal.ZERO;
            BigDecimal rate = b.getBoqRate() != null ? b.getBoqRate() : BigDecimal.ZERO;
            BigDecimal executed = b.getQtyExecutedToDate() != null
                ? b.getQtyExecutedToDate() : BigDecimal.ZERO;
            totalBoq = totalBoq.add(qty.multiply(rate));
            totalExecuted = totalExecuted.add(executed.multiply(rate));
        }
        BigDecimal percent = totalBoq.signum() > 0
            ? totalExecuted.multiply(BigDecimal.valueOf(100))
                .divide(totalBoq, 4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        return new StretchProgressResponse(
            stretch.getId(), stretch.getStretchCode(), stretch.getName(),
            items.size(), totalBoq, totalExecuted, percent);
    }

    public StretchResponse create(UUID projectId, CreateStretchRequest request) {
        ensureProjectExists(projectId);
        validateChainage(request.fromChainageM(), request.toChainageM());

        String code = request.stretchCode() != null && !request.stretchCode().isBlank()
            ? request.stretchCode() : generateStretchCode(projectId);
        if (stretchRepository.existsByProjectIdAndStretchCode(projectId, code)) {
            throw new BusinessRuleException("DUPLICATE_STRETCH_CODE",
                "Stretch code '" + code + "' already exists for this project");
        }

        Stretch stretch = Stretch.builder()
            .projectId(projectId)
            .stretchCode(code)
            .name(request.name())
            .fromChainageM(request.fromChainageM())
            .toChainageM(request.toChainageM())
            .lengthM(request.toChainageM() - request.fromChainageM())
            .assignedSupervisorId(request.assignedSupervisorId())
            .packageCode(request.packageCode())
            .status(request.status() != null ? request.status() : StretchStatus.NOT_STARTED)
            .milestoneName(request.milestoneName())
            .targetDate(request.targetDate())
            .build();

        Stretch saved = stretchRepository.save(stretch);
        replaceBoqLinks(saved.getId(), request.boqItemIds());
        auditService.logCreate("Stretch", saved.getId(), StretchResponse.from(saved, request.boqItemIds()));
        return hydrate(saved);
    }

    public StretchResponse update(UUID id, UpdateStretchRequest request) {
        Stretch stretch = findOrThrow(id);

        if (request.name() != null) stretch.setName(request.name());
        if (request.fromChainageM() != null) stretch.setFromChainageM(request.fromChainageM());
        if (request.toChainageM() != null) stretch.setToChainageM(request.toChainageM());
        validateChainage(stretch.getFromChainageM(), stretch.getToChainageM());
        // Recompute derived length whenever either endpoint changes.
        if (request.fromChainageM() != null || request.toChainageM() != null) {
            stretch.setLengthM(stretch.getToChainageM() - stretch.getFromChainageM());
        }
        if (request.assignedSupervisorId() != null) stretch.setAssignedSupervisorId(request.assignedSupervisorId());
        if (request.packageCode() != null) stretch.setPackageCode(request.packageCode());
        if (request.status() != null) stretch.setStatus(request.status());
        if (request.milestoneName() != null) stretch.setMilestoneName(request.milestoneName());
        if (request.targetDate() != null) stretch.setTargetDate(request.targetDate());

        Stretch saved = stretchRepository.save(stretch);
        if (request.boqItemIds() != null) {
            replaceBoqLinks(saved.getId(), request.boqItemIds());
        }
        auditService.logUpdate("Stretch", id, "stretch", null, hydrate(saved));
        return hydrate(saved);
    }

    public void delete(UUID id) {
        Stretch stretch = findOrThrow(id);
        linkRepository.deleteByStretchId(id);
        stretchRepository.delete(stretch);
        auditService.logDelete("Stretch", id);
    }

    /** Replace the set of BOQ items linked to this stretch. Empty list clears all links. */
    public StretchResponse assignActivities(UUID stretchId, List<UUID> boqItemIds) {
        Stretch stretch = findOrThrow(stretchId);
        replaceBoqLinks(stretchId, boqItemIds);
        return hydrate(stretch);
    }

    private void replaceBoqLinks(UUID stretchId, List<UUID> boqItemIds) {
        if (boqItemIds == null) return;
        linkRepository.deleteByStretchId(stretchId);
        linkRepository.flush();
        for (UUID boqItemId : boqItemIds) {
            StretchActivityLink link = new StretchActivityLink();
            link.setStretchId(stretchId);
            link.setBoqItemId(boqItemId);
            linkRepository.save(link);
        }
    }

    private StretchResponse hydrate(Stretch stretch) {
        List<UUID> boqItemIds = linkRepository.findByStretchId(stretch.getId()).stream()
            .map(StretchActivityLink::getBoqItemId)
            .toList();
        return StretchResponse.from(stretch, boqItemIds);
    }

    private Stretch findOrThrow(UUID id) {
        return stretchRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Stretch", id));
    }

    private void validateChainage(Long from, Long to) {
        if (from == null || to == null) {
            throw new BusinessRuleException("INVALID_CHAINAGE",
                "fromChainageM and toChainageM are both required");
        }
        if (to <= from) {
            throw new BusinessRuleException("INVALID_CHAINAGE_RANGE",
                "toChainageM must be greater than fromChainageM");
        }
    }

    private void ensureProjectExists(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project", projectId);
        }
    }

    private String generateStretchCode(UUID projectId) {
        Integer max = stretchRepository.findMaxStretchSuffix(projectId);
        int next = max == null ? 1 : max + 1;
        return String.format("STR-%03d", next);
    }
}

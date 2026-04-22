package com.bipros.risk.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.risk.application.dto.ActivityCorrelationDto;
import com.bipros.risk.domain.model.ActivityCorrelation;
import com.bipros.risk.domain.repository.ActivityCorrelationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ActivityCorrelationService {

    private final ActivityCorrelationRepository repository;

    public List<ActivityCorrelationDto> listForProject(UUID projectId) {
        return repository.findByProjectId(projectId).stream()
            .map(ActivityCorrelationDto::from)
            .collect(Collectors.toList());
    }

    public ActivityCorrelationDto upsert(UUID projectId, ActivityCorrelationDto in) {
        if (in.getActivityAId().equals(in.getActivityBId())) {
            throw new BusinessRuleException("SELF_CORRELATION",
                "An activity cannot be correlated with itself.");
        }
        // Canonical order so (a,b) and (b,a) collapse to one row.
        UUID a = in.getActivityAId();
        UUID b = in.getActivityBId();
        if (a.compareTo(b) > 0) { UUID t = a; a = b; b = t; }

        ActivityCorrelation entity = repository
            .findByProjectIdAndActivityAIdAndActivityBId(projectId, a, b)
            .orElseGet(() -> ActivityCorrelation.builder()
                .projectId(projectId)
                .activityAId(in.getActivityAId()) // store as-supplied for traceability
                .activityBId(in.getActivityBId())
                .coefficient(in.getCoefficient())
                .build());
        // Normalise the stored order to the canonical pair so lookups are unique.
        entity.setActivityAId(a);
        entity.setActivityBId(b);
        entity.setCoefficient(in.getCoefficient());
        return ActivityCorrelationDto.from(repository.save(entity));
    }

    public void delete(UUID projectId, UUID activityAId, UUID activityBId) {
        UUID a = activityAId;
        UUID b = activityBId;
        if (a.compareTo(b) > 0) { UUID t = a; a = b; b = t; }
        long removed = repository.deleteByProjectIdAndActivityAIdAndActivityBId(projectId, a, b);
        if (removed == 0) {
            throw new ResourceNotFoundException("ActivityCorrelation", a + "-" + b);
        }
    }
}

package com.bipros.ai.agent.support;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.gis.application.port.WbsProgressProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Adapter for {@link WbsProgressProvider}: a WBS node's contractor-claimed % is the duration-weighted
 * mean of its activities' {@code percentComplete}, which {@code ActivityProgressFromBoqListener} keeps
 * in sync with APPROVED-DPR BOQ workdone. Lives in bipros-ai because it needs both bipros-gis (the
 * port) and bipros-activity (Activity progress) on the classpath, which bipros-gis itself cannot have.
 */
@Component
@RequiredArgsConstructor
public class WbsProgressProviderAdapter implements WbsProgressProvider {

    private final ActivityRepository activityRepository;

    @Override
    public Double claimedProgressForWbs(UUID wbsNodeId) {
        if (wbsNodeId == null) return null;
        List<Activity> activities = activityRepository.findByWbsNodeId(wbsNodeId);
        if (activities.isEmpty()) return null;
        double weightedNum = 0d;
        double weightedDen = 0d;
        double simpleSum = 0d;
        for (Activity a : activities) {
            double pct = a.getPercentComplete() == null ? 0d : a.getPercentComplete();
            double dur = a.getOriginalDuration() == null ? 0d : a.getOriginalDuration();
            weightedNum += dur * pct;
            weightedDen += dur;
            simpleSum += pct;
        }
        return weightedDen > 0 ? weightedNum / weightedDen : simpleSum / activities.size();
    }
}

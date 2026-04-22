package com.bipros.risk.simulation;

import com.bipros.activity.domain.repository.ActivityRelationshipRepository;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.baseline.infrastructure.repository.BaselineActivityRepository;
import com.bipros.baseline.infrastructure.repository.BaselineRepository;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.risk.application.simulation.MonteCarloEngine;
import com.bipros.risk.application.simulation.MonteCarloInput;
import com.bipros.risk.domain.repository.ActivityCorrelationRepository;
import com.bipros.risk.domain.repository.RiskRepository;
import com.bipros.scheduling.domain.algorithm.CalendarCalculator;
import com.bipros.scheduling.domain.repository.PertEstimateRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonteCarloEngineBaselineRequiredTest {

    @Test
    void failsFastWhenNoActiveBaseline() {
        BaselineRepository baselineRepo = Mockito.mock(BaselineRepository.class);
        BaselineActivityRepository baselineActivityRepo = Mockito.mock(BaselineActivityRepository.class);
        ActivityRepository activityRepo = Mockito.mock(ActivityRepository.class);
        ActivityRelationshipRepository relRepo = Mockito.mock(ActivityRelationshipRepository.class);
        PertEstimateRepository pertRepo = Mockito.mock(PertEstimateRepository.class);
        RiskRepository riskRepo = Mockito.mock(RiskRepository.class);
        ActivityCorrelationRepository corrRepo = Mockito.mock(ActivityCorrelationRepository.class);
        CalendarCalculator calendar = Mockito.mock(CalendarCalculator.class);

        UUID projectId = UUID.randomUUID();
        Mockito.when(baselineRepo.findByProjectIdAndIsActiveTrue(projectId)).thenReturn(List.of());

        MonteCarloEngine engine = new MonteCarloEngine(
            baselineRepo, baselineActivityRepo, activityRepo, relRepo, pertRepo, riskRepo, corrRepo, calendar);

        MonteCarloInput input = MonteCarloInput.defaultsFor(projectId, 1000);

        assertThatThrownBy(() -> engine.run(input))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("Create an active baseline");
    }
}

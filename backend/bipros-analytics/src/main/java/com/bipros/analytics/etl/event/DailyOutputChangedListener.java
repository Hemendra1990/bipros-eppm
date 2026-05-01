package com.bipros.analytics.etl.event;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.analytics.etl.AnalyticsEtlService;
import com.bipros.analytics.etl.DeadLetterHandler;
import com.bipros.common.event.DailyOutputChangedEvent;
import com.bipros.project.domain.model.DailyActivityResourceOutput;
import com.bipros.project.domain.repository.DailyActivityResourceOutputRepository;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyOutputChangedListener {

    private final AnalyticsEtlService etl;
    private final DeadLetterHandler deadLetter;
    private final DailyActivityResourceOutputRepository outputRepository;
    private final ActivityRepository activityRepository;
    private final ResourceRepository resourceRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDailyOutputChanged(DailyOutputChangedEvent event) {
        try {
            DailyActivityResourceOutput output = outputRepository
                    .findFirstByProjectIdAndOutputDateAndActivityIdAndResourceId(
                            event.projectId(), event.outputDate(), event.activityId(), event.resourceId())
                    .orElse(null);
            if (output == null) {
                log.warn("DailyActivityResourceOutput not found for event: {}", event);
                return;
            }

            Activity activity = activityRepository.findById(event.activityId()).orElse(null);
            Resource resource = resourceRepository.findById(event.resourceId()).orElse(null);

            // Insert activity progress
            etl.insertActivityProgressDaily(
                    event.projectId(), event.activityId(), event.outputDate(),
                    activity != null && activity.getPhysicalPercentComplete() != null
                            ? activity.getPhysicalPercentComplete().floatValue() : null,
                    activity != null && activity.getDurationPercentComplete() != null
                            ? activity.getDurationPercentComplete().floatValue() : null,
                    output.getQtyExecuted() != null ? output.getQtyExecuted().doubleValue() : null,
                    output.getQtyExecuted() != null ? output.getQtyExecuted().doubleValue() : null,
                    activity != null && activity.getChainageFromM() != null
                            ? activity.getChainageFromM().doubleValue() : null,
                    activity != null && activity.getChainageToM() != null
                            ? activity.getChainageToM().doubleValue() : null,
                    "computed");

            // Insert resource usage
            if (resource != null) {
                Float productivityActual = null;
                Float productivityNorm = null;
                if (output.getHoursWorked() != null && output.getHoursWorked() > 0
                        && output.getQtyExecuted() != null) {
                    productivityActual = output.getQtyExecuted().floatValue() / output.getHoursWorked().floatValue();
                }
                etl.insertResourceUsageDaily(
                        event.projectId(), event.activityId(), event.resourceId(),
                        resource.getResourceType() != null ? resource.getResourceType().getCode() : null,
                        event.outputDate(),
                        output.getHoursWorked() != null ? output.getHoursWorked().floatValue() : null,
                        output.getDaysWorked() != null ? output.getDaysWorked().floatValue() : null,
                        output.getQtyExecuted() != null ? output.getQtyExecuted().doubleValue() : null,
                        productivityActual, productivityNorm, null);
            }

            log.debug("ETL processed DailyOutputChangedEvent: project={} activity={} resource={}",
                    event.projectId(), event.activityId(), event.resourceId());
        } catch (Exception e) {
            log.error("ETL failed for DailyOutputChangedEvent: {}", event, e);
            deadLetter.record("project.daily_activity_resource_outputs",
                    "fact_activity_progress_daily", event, e);
        }
    }
}

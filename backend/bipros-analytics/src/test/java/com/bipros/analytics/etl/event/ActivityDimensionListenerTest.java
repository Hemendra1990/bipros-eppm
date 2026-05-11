package com.bipros.analytics.etl.event;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.analytics.etl.AnalyticsEtlService;
import com.bipros.analytics.etl.DeadLetterHandler;
import com.bipros.common.event.ActivitiesBulkUpdatedEvent;
import com.bipros.common.event.ActivityCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityDimensionListenerTest {

    private AnalyticsEtlService etl;
    private DeadLetterHandler deadLetter;
    private ActivityRepository activityRepository;
    private ActivityDimensionListener listener;

    @BeforeEach
    void setUp() {
        etl = mock(AnalyticsEtlService.class);
        deadLetter = mock(DeadLetterHandler.class);
        activityRepository = mock(ActivityRepository.class);
        listener = new ActivityDimensionListener(etl, deadLetter, activityRepository);
    }

    @Test
    void onActivityCreatedFetchesAndUpserts() {
        UUID activityId = UUID.randomUUID();
        Activity a = new Activity();
        a.setId(activityId);
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(a));

        listener.onActivityCreated(new ActivityCreatedEvent(UUID.randomUUID(), activityId, "A-1", "name"));

        verify(etl).upsertActivityDimension(a);
        verify(deadLetter, never()).record(anyString(), anyString(), any(), any());
    }

    @Test
    void bulkEventCallsBulkUpsert() {
        UUID projectId = UUID.randomUUID();
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        List<Activity> activities = ids.stream().map(id -> {
            Activity a = new Activity();
            a.setId(id);
            a.setProjectId(projectId);
            return a;
        }).toList();
        when(activityRepository.findAllById(ids)).thenReturn(activities);

        listener.onActivitiesBulkUpdated(new ActivitiesBulkUpdatedEvent(projectId, ids));

        verify(etl).upsertActivitiesBulkDimension(activities);
        verify(etl, never()).upsertActivityDimension(any());
    }

    @Test
    void bulkEventWithEmptyIdsIsNoOp() {
        UUID projectId = UUID.randomUUID();
        listener.onActivitiesBulkUpdated(new ActivitiesBulkUpdatedEvent(projectId, List.of()));

        verify(etl, never()).upsertActivitiesBulkDimension(anyList());
    }

    @Test
    void etlExceptionRoutesToDeadLetter() {
        UUID activityId = UUID.randomUUID();
        Activity a = new Activity();
        a.setId(activityId);
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(a));
        doThrow(new RuntimeException("ch down")).when(etl).upsertActivityDimension(a);

        ActivityCreatedEvent event = new ActivityCreatedEvent(UUID.randomUUID(), activityId, "A-1", "name");
        listener.onActivityCreated(event);

        verify(deadLetter).record(
                eq("activity.activities"),
                eq("dim_activity"),
                eq(event),
                any(Exception.class));
    }
}

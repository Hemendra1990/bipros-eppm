package com.bipros.resource.domain.algorithm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ResourceLeveler Tests")
class ResourceLevelerTest {

  private ResourceLeveler leveler;

  @BeforeEach
  void setUp() {
    leveler = new ResourceLeveler();
  }

  @Test
  @DisplayName("No overallocations when demand is within capacity")
  void levelResources_noOverallocations_returnsEmpty() {
    UUID activityId1 = UUID.randomUUID();
    UUID activityId2 = UUID.randomUUID();
    UUID resourceId = UUID.randomUUID();
    LocalDate start = LocalDate.of(2026, 1, 1);

    List<ResourceLeveler.ActivityInfo> activities = List.of(
        new ResourceLeveler.ActivityInfo(
            activityId1, start, start.plusDays(4), start, start.plusDays(4),
            0.0, 50, start, start.plusDays(4)),
        new ResourceLeveler.ActivityInfo(
            activityId2, start.plusDays(5), start.plusDays(9), start.plusDays(5),
            start.plusDays(9), 0.0, 50, start.plusDays(5), start.plusDays(9)));

    List<ResourceLeveler.AssignmentInfo> assignments = List.of(
        new ResourceLeveler.AssignmentInfo(activityId1, resourceId, 4.0),
        new ResourceLeveler.AssignmentInfo(activityId2, resourceId, 4.0));

    Map<UUID, Double> resourceMaxUnits = Map.of(resourceId, 8.0);

    ResourceLeveler.LevelingInput input = new ResourceLeveler.LevelingInput(
        activities, assignments, resourceMaxUnits);

    ResourceLeveler.LevelingOutput output = leveler.level(input);

    assertThat(output.delayedActivities()).isEmpty();
    assertThat(output.iterationsUsed()).isEqualTo(1);
  }

  @Test
  @DisplayName("Resolves overallocation by delaying lower-priority activity")
  void levelResources_withOverallocation_delaysActivity() {
    UUID activityId1 = UUID.randomUUID();
    UUID activityId2 = UUID.randomUUID();
    UUID resourceId = UUID.randomUUID();
    LocalDate start = LocalDate.of(2026, 1, 1);

    // Both activities overlap on same resource
    // Activity 1: 5 days, 8 units = 1.6 units/day
    // Activity 2: 5 days, 8 units = 1.6 units/day
    // Total: 3.2 units/day > 8 capacity? No, that's wrong too.
    // Let's make them 1-day activities: Activity 1: 1 day, 5 units = 5 units/day
    // Activity 2: 1 day, 5 units = 5 units/day. Total: 10 > 8. That works!
    List<ResourceLeveler.ActivityInfo> activities = List.of(
        new ResourceLeveler.ActivityInfo(
            activityId1, start, start, start, start,
            0.0, 50, start, start),
        new ResourceLeveler.ActivityInfo(
            activityId2, start, start, start, start,
            0.0, 100, start, start)); // Higher priority (100 > 50)

    List<ResourceLeveler.AssignmentInfo> assignments = List.of(
        new ResourceLeveler.AssignmentInfo(activityId1, resourceId, 5.0),
        new ResourceLeveler.AssignmentInfo(activityId2, resourceId, 5.0));

    Map<UUID, Double> resourceMaxUnits = Map.of(resourceId, 8.0);

    ResourceLeveler.LevelingInput input = new ResourceLeveler.LevelingInput(
        activities, assignments, resourceMaxUnits);

    ResourceLeveler.LevelingOutput output = leveler.level(input);

    // Activity 1 (lower priority) should be delayed
    assertThat(output.delayedActivities()).containsKey(activityId1);
    assertThat(output.delayedActivities().get(activityId1))
        .isEqualTo(start.plusDays(1)); // Should start the next day
  }

  @Test
  @DisplayName("Handles multiple resources correctly")
  void levelResources_multipleResources_resolvesPerResource() {
    UUID activityId1 = UUID.randomUUID();
    UUID activityId2 = UUID.randomUUID();
    UUID resourceId1 = UUID.randomUUID();
    UUID resourceId2 = UUID.randomUUID();
    LocalDate start = LocalDate.of(2026, 1, 1);

    List<ResourceLeveler.ActivityInfo> activities = List.of(
        new ResourceLeveler.ActivityInfo(
            activityId1, start, start.plusDays(4), start, start.plusDays(4),
            0.0, 50, start, start.plusDays(4)),
        new ResourceLeveler.ActivityInfo(
            activityId2, start, start.plusDays(4), start, start.plusDays(4),
            0.0, 50, start, start.plusDays(4)));

    List<ResourceLeveler.AssignmentInfo> assignments = List.of(
        new ResourceLeveler.AssignmentInfo(activityId1, resourceId1, 8.0),
        new ResourceLeveler.AssignmentInfo(activityId2, resourceId2, 8.0));

    Map<UUID, Double> resourceMaxUnits = Map.of(
        resourceId1, 8.0,
        resourceId2, 8.0);

    ResourceLeveler.LevelingInput input = new ResourceLeveler.LevelingInput(
        activities, assignments, resourceMaxUnits);

    ResourceLeveler.LevelingOutput output = leveler.level(input);

    // No overallocation - each resource has exactly one activity
    assertThat(output.delayedActivities()).isEmpty();
  }

  @Test
  @DisplayName("Prioritizes higher-float activities for delay")
  void levelResources_withFloat_delaysHigherFloatActivity() {
    UUID activityId1 = UUID.randomUUID();
    UUID activityId2 = UUID.randomUUID();
    UUID resourceId = UUID.randomUUID();
    LocalDate start = LocalDate.of(2026, 1, 1);

    // Activity 1 has high float, activity 2 has zero float (critical)
    // Both are 1-day activities with 5 units each = 10 units/day > 8 capacity
    List<ResourceLeveler.ActivityInfo> activities = List.of(
        new ResourceLeveler.ActivityInfo(
            activityId1, start, start, start, start.plusDays(5),
            5.0, 50, start, start), // High float
        new ResourceLeveler.ActivityInfo(
            activityId2, start, start, start, start,
            0.0, 50, start, start)); // Critical path

    List<ResourceLeveler.AssignmentInfo> assignments = List.of(
        new ResourceLeveler.AssignmentInfo(activityId1, resourceId, 5.0),
        new ResourceLeveler.AssignmentInfo(activityId2, resourceId, 5.0));

    Map<UUID, Double> resourceMaxUnits = Map.of(resourceId, 8.0);

    ResourceLeveler.LevelingInput input = new ResourceLeveler.LevelingInput(
        activities, assignments, resourceMaxUnits);

    ResourceLeveler.LevelingOutput output = leveler.level(input);

    // Activity 1 (higher float) should be delayed, not activity 2
    assertThat(output.delayedActivities()).containsKey(activityId1);
    assertThat(output.delayedActivities()).doesNotContainKey(activityId2);
  }

  @Test
  @DisplayName("Computes daily demand correctly for multiple days")
  void levelResources_multiDayActivities_computesDemandCorrectly() {
    UUID activityId = UUID.randomUUID();
    UUID resourceId = UUID.randomUUID();
    LocalDate start = LocalDate.of(2026, 1, 1);

    // 5-day activity with 10 units total = 2 units per day
    List<ResourceLeveler.ActivityInfo> activities = List.of(
        new ResourceLeveler.ActivityInfo(
            activityId, start, start.plusDays(4), start, start.plusDays(4),
            0.0, 50, start, start.plusDays(4)));

    List<ResourceLeveler.AssignmentInfo> assignments = List.of(
        new ResourceLeveler.AssignmentInfo(activityId, resourceId, 10.0));

    Map<UUID, Double> resourceMaxUnits = Map.of(resourceId, 8.0);

    ResourceLeveler.LevelingInput input = new ResourceLeveler.LevelingInput(
        activities, assignments, resourceMaxUnits);

    ResourceLeveler.LevelingOutput output = leveler.level(input);

    // 2 units/day is well within 8-unit capacity - no delay
    assertThat(output.delayedActivities()).isEmpty();
  }

  @Test
  @DisplayName("Returns empty result for empty input")
  void levelResources_emptyActivities_returnsEmpty() {
    ResourceLeveler.LevelingInput input = new ResourceLeveler.LevelingInput(
        List.of(), List.of(), Map.of());

    ResourceLeveler.LevelingOutput output = leveler.level(input);

    assertThat(output.delayedActivities()).isEmpty();
    assertThat(output.overallocationsResolved()).isEmpty();
  }

  @Test
  @DisplayName("Provides resolution messages for debugging")
  void levelResources_withOverallocation_providesResolutionMessages() {
    UUID activityId1 = UUID.randomUUID();
    UUID activityId2 = UUID.randomUUID();
    UUID resourceId = UUID.randomUUID();
    LocalDate start = LocalDate.of(2026, 1, 1);

    // 1-day activities with 5 units each = 10 units > 8 capacity
    List<ResourceLeveler.ActivityInfo> activities = List.of(
        new ResourceLeveler.ActivityInfo(
            activityId1, start, start, start, start,
            0.0, 50, start, start),
        new ResourceLeveler.ActivityInfo(
            activityId2, start, start, start, start,
            0.0, 100, start, start));

    List<ResourceLeveler.AssignmentInfo> assignments = List.of(
        new ResourceLeveler.AssignmentInfo(activityId1, resourceId, 5.0),
        new ResourceLeveler.AssignmentInfo(activityId2, resourceId, 5.0));

    Map<UUID, Double> resourceMaxUnits = Map.of(resourceId, 8.0);

    ResourceLeveler.LevelingInput input = new ResourceLeveler.LevelingInput(
        activities, assignments, resourceMaxUnits);

    ResourceLeveler.LevelingOutput output = leveler.level(input);

    assertThat(output.overallocationsResolved()).isNotEmpty();
    assertThat(output.overallocationsResolved().get(0))
        .contains("Delayed activity")
        .contains(activityId1.toString())
        .contains("resolve overallocation");
  }
}

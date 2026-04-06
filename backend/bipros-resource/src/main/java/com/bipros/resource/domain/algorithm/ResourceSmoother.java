package com.bipros.resource.domain.algorithm;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Resource smoothing algorithm: minimizes peak resource demand
 * by redistributing non-critical activities within their float.
 */
public class ResourceSmoother {

    private static final int MAX_ITERATIONS = 500;

    public record SmoothingResult(
            Map<UUID, LocalDate> shiftedActivities,
            double peakBefore,
            double peakAfter,
            int iterationsUsed) {}

    /**
     * Smooths resource usage by shifting activities within their available float
     * to minimize peak resource demand.
     */
    public SmoothingResult smooth(
            List<ResourceLeveler.ActivityInfo> activities,
            List<ResourceLeveler.AssignmentInfo> assignments,
            Map<UUID, Double> resourceMaxUnits) {

        Map<UUID, ResourceLeveler.ActivityInfo> activityMap = new LinkedHashMap<>();
        for (var a : activities) {
            activityMap.put(a.activityId(), a);
        }

        double peakBefore = calculatePeakDemand(activityMap, assignments, resourceMaxUnits);
        Map<UUID, LocalDate> shiftedActivities = new HashMap<>();

        int iteration = 0;
        boolean improved = true;

        while (improved && iteration < MAX_ITERATIONS) {
            iteration++;
            improved = false;

            // Find the date and resource with the highest utilization
            PeakInfo peak = findPeak(activityMap, assignments, resourceMaxUnits);
            if (peak == null) break;

            // Find activities contributing to the peak that have float
            List<UUID> candidateActivities = new ArrayList<>();
            for (var assignment : assignments) {
                if (!assignment.resourceId().equals(peak.resourceId)) continue;
                var activity = activityMap.get(assignment.activityId());
                if (activity == null) continue;

                // Activity is active on the peak date
                if (!activity.currentStart().isAfter(peak.date) &&
                        !activity.currentFinish().isBefore(peak.date)) {
                    // Has float available
                    double tf = activity.totalFloat() != null ? activity.totalFloat() : 0.0;
                    if (tf > 0) {
                        candidateActivities.add(assignment.activityId());
                    }
                }
            }

            // Try shifting each candidate by 1 day and check if peak reduces
            for (UUID candidateId : candidateActivities) {
                var original = activityMap.get(candidateId);
                double tf = original.totalFloat() != null ? original.totalFloat() : 0.0;

                // Try shifting forward by 1 day (within float)
                long maxShift = (long) tf;
                if (maxShift <= 0) continue;

                LocalDate newStart = original.currentStart().plusDays(1);
                LocalDate newFinish = original.currentFinish().plusDays(1);

                // Don't shift beyond late start
                if (original.lateStart() != null && newStart.isAfter(original.lateStart())) {
                    continue;
                }

                var shifted = new ResourceLeveler.ActivityInfo(
                        original.activityId(),
                        original.earlyStart(), original.earlyFinish(),
                        original.lateStart(), original.lateFinish(),
                        tf - 1, // reduce available float
                        original.projectPriority(),
                        newStart, newFinish);

                activityMap.put(candidateId, shifted);

                double newPeak = calculatePeakDemand(activityMap, assignments, resourceMaxUnits);

                if (newPeak < peakBefore - 0.001) {
                    // Keep the shift
                    shiftedActivities.put(candidateId, newStart);
                    peakBefore = newPeak;
                    improved = true;
                    break; // restart iteration with new state
                } else {
                    // Revert
                    activityMap.put(candidateId, original);
                }
            }
        }

        double peakAfter = calculatePeakDemand(activityMap, assignments, resourceMaxUnits);

        return new SmoothingResult(shiftedActivities,
                calculatePeakDemand(
                        activities.stream().collect(
                                LinkedHashMap::new,
                                (m, a) -> m.put(a.activityId(), a),
                                LinkedHashMap::putAll),
                        assignments, resourceMaxUnits),
                peakAfter, iteration);
    }

    private record PeakInfo(LocalDate date, UUID resourceId, double demand) {}

    private PeakInfo findPeak(
            Map<UUID, ResourceLeveler.ActivityInfo> activityMap,
            List<ResourceLeveler.AssignmentInfo> assignments,
            Map<UUID, Double> resourceMaxUnits) {

        LocalDate projectStart = activityMap.values().stream()
                .map(ResourceLeveler.ActivityInfo::currentStart)
                .min(LocalDate::compareTo).orElse(null);
        LocalDate projectEnd = activityMap.values().stream()
                .map(ResourceLeveler.ActivityInfo::currentFinish)
                .max(LocalDate::compareTo).orElse(null);

        if (projectStart == null || projectEnd == null) return null;

        PeakInfo maxPeak = null;

        for (LocalDate date = projectStart; !date.isAfter(projectEnd); date = date.plusDays(1)) {
            Map<UUID, Double> dailyDemand = new HashMap<>();

            for (var assignment : assignments) {
                var activity = activityMap.get(assignment.activityId());
                if (activity == null) continue;

                if (!activity.currentStart().isAfter(date) && !activity.currentFinish().isBefore(date)) {
                    long duration = activity.getDuration();
                    if (duration > 0) {
                        double unitsPerDay = assignment.plannedUnits() / duration;
                        dailyDemand.merge(assignment.resourceId(), unitsPerDay, Double::sum);
                    }
                }
            }

            for (var entry : dailyDemand.entrySet()) {
                double capacity = resourceMaxUnits.getOrDefault(entry.getKey(), 8.0);
                double utilization = entry.getValue() / capacity;
                if (maxPeak == null || utilization > maxPeak.demand / resourceMaxUnits.getOrDefault(maxPeak.resourceId, 8.0)) {
                    maxPeak = new PeakInfo(date, entry.getKey(), entry.getValue());
                }
            }
        }

        return maxPeak;
    }

    private double calculatePeakDemand(
            Map<UUID, ResourceLeveler.ActivityInfo> activityMap,
            List<ResourceLeveler.AssignmentInfo> assignments,
            Map<UUID, Double> resourceMaxUnits) {

        LocalDate projectStart = activityMap.values().stream()
                .map(ResourceLeveler.ActivityInfo::currentStart)
                .min(LocalDate::compareTo).orElse(null);
        LocalDate projectEnd = activityMap.values().stream()
                .map(ResourceLeveler.ActivityInfo::currentFinish)
                .max(LocalDate::compareTo).orElse(null);

        if (projectStart == null || projectEnd == null) return 0.0;

        double maxUtil = 0.0;

        for (LocalDate date = projectStart; !date.isAfter(projectEnd); date = date.plusDays(1)) {
            Map<UUID, Double> dailyDemand = new HashMap<>();

            for (var assignment : assignments) {
                var activity = activityMap.get(assignment.activityId());
                if (activity == null) continue;

                if (!activity.currentStart().isAfter(date) && !activity.currentFinish().isBefore(date)) {
                    long duration = activity.getDuration();
                    if (duration > 0) {
                        double unitsPerDay = assignment.plannedUnits() / duration;
                        dailyDemand.merge(assignment.resourceId(), unitsPerDay, Double::sum);
                    }
                }
            }

            for (var entry : dailyDemand.entrySet()) {
                double capacity = resourceMaxUnits.getOrDefault(entry.getKey(), 8.0);
                double utilization = entry.getValue() / capacity;
                maxUtil = Math.max(maxUtil, utilization);
            }
        }

        return maxUtil;
    }
}

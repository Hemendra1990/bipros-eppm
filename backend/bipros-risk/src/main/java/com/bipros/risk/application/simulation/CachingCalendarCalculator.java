package com.bipros.risk.application.simulation;

import com.bipros.scheduling.domain.algorithm.CalendarCalculator;

import java.time.LocalDate;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-simulation calendar calculator that pre-materialises the working-day bitmap
 * for each referenced calendar over the simulation horizon and answers every
 * {@link CalendarCalculator} call by in-memory lookup.
 * <p>
 * The underlying {@link com.bipros.scheduling.domain.algorithm.CalendarCalculator}
 * ({@code CalendarServiceAdapter → CalendarService}) issues two DB queries per day
 * via {@link #isWorkingDay}, and every arithmetic method walks day-by-day. A
 * 10-year project × 10 000 iterations produces tens of millions of DB round trips
 * unless the bitmap is cached up front. This implementation preloads the bitmap
 * once in {@link #primeHorizon} and keeps a parallel running-sum of working days
 * so {@link #countWorkingDays} is O(1).
 */
public final class CachingCalendarCalculator implements CalendarCalculator {

    private final CalendarCalculator delegate;
    private final LocalDate origin;
    private final int horizonDays;
    private final Map<UUID, CalendarWindow> windows = new HashMap<>();

    public CachingCalendarCalculator(CalendarCalculator delegate, LocalDate origin, int horizonDays) {
        if (horizonDays < 1) {
            throw new IllegalArgumentException("horizonDays must be positive: " + horizonDays);
        }
        this.delegate = delegate;
        this.origin = origin;
        this.horizonDays = horizonDays;
    }

    public void primeHorizon(UUID calendarId) {
        windows.computeIfAbsent(calendarId, id -> buildWindow(id));
    }

    private CalendarWindow buildWindow(UUID calendarId) {
        BitSet working = new BitSet(horizonDays + 1);
        // prefix[i] = count of working days in [origin, origin + i) (half-open, start inclusive, end exclusive)
        int[] prefix = new int[horizonDays + 2];
        int count = 0;
        for (int i = 0; i <= horizonDays; i++) {
            LocalDate d = origin.plusDays(i);
            boolean w = delegate.isWorkingDay(calendarId, d);
            if (w) {
                working.set(i);
            }
            prefix[i + 1] = count + (w ? 1 : 0);
            if (w) count++;
        }
        return new CalendarWindow(working, prefix);
    }

    private CalendarWindow window(UUID calendarId) {
        return windows.computeIfAbsent(calendarId, this::buildWindow);
    }

    private int indexOf(LocalDate date) {
        long days = date.toEpochDay() - origin.toEpochDay();
        if (days < 0) return -1;
        if (days > horizonDays) return horizonDays + 1; // out-of-range marker
        return (int) days;
    }

    @Override
    public boolean isWorkingDay(UUID calendarId, LocalDate date) {
        int idx = indexOf(date);
        if (idx < 0 || idx > horizonDays) return delegate.isWorkingDay(calendarId, date);
        return window(calendarId).working.get(idx);
    }

    @Override
    public double getWorkingHours(UUID calendarId, LocalDate date) {
        return delegate.getWorkingHours(calendarId, date);
    }

    @Override
    public LocalDate addWorkingDays(UUID calendarId, LocalDate start, double days) {
        int idx = indexOf(start);
        if (idx < 0 || idx > horizonDays) return delegate.addWorkingDays(calendarId, start, days);
        int target = (int) Math.round(days);
        if (target <= 0) return start;
        BitSet working = window(calendarId).working;
        int i = idx;
        int taken = 0;
        // Step past target working days; return the day AFTER the N-th one.
        while (i <= horizonDays) {
            if (working.get(i)) {
                taken++;
            }
            i++;
            if (taken >= target) return origin.plusDays(i);
        }
        int remainder = target - taken;
        LocalDate tail = origin.plusDays(horizonDays + 1);
        return delegate.addWorkingDays(calendarId, tail, remainder);
    }

    @Override
    public LocalDate subtractWorkingDays(UUID calendarId, LocalDate from, double days) {
        int idx = indexOf(from);
        if (idx < 0 || idx > horizonDays) return delegate.subtractWorkingDays(calendarId, from, days);
        int target = (int) Math.round(days);
        if (target <= 0) return from;
        BitSet working = window(calendarId).working;
        // Inverse of addWorkingDays: step backwards past target working days, land on the N-th.
        int i = idx - 1;
        int taken = 0;
        while (i >= 0) {
            if (working.get(i)) {
                taken++;
                if (taken >= target) return origin.plusDays(i);
            }
            i--;
        }
        int remainder = target - taken;
        return delegate.subtractWorkingDays(calendarId, origin, remainder);
    }

    @Override
    public double countWorkingDays(UUID calendarId, LocalDate start, LocalDate end) {
        int a = indexOf(start);
        int b = indexOf(end);
        if (a < 0 || b < 0 || a > horizonDays || b > horizonDays) {
            return delegate.countWorkingDays(calendarId, start, end);
        }
        if (b <= a) return 0;
        int[] prefix = window(calendarId).prefix;
        return prefix[b] - prefix[a]; // half-open [start, end)
    }

    private record CalendarWindow(BitSet working, int[] prefix) {}
}

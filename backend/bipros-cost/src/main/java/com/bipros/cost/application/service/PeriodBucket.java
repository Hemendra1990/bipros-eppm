package com.bipros.cost.application.service;

import java.time.LocalDate;

public record PeriodBucket(LocalDate start, LocalDate end, String name) {}

package com.bipros.portfolio.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record WhatIfRequest(
    UUID projectId,
    boolean addProject,
    BigDecimal budgetLimit
) {}

package com.bipros.portfolio.application.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdatePortfolioRequest(
    @NotBlank(message = "Portfolio name is required") String name,
    String description) {}

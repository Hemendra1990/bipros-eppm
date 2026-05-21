package com.bipros.hds.application.retrieval;

import java.util.List;

public record PlanResult(boolean isCompound, List<String> subQuestions, List<String> searchQueries) {}

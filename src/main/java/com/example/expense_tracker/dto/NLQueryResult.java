package com.example.expense_tracker.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record NLQueryResult(
        String originalQuery,
        String interpretedIntent,
        String generatedSafeSql,
        BigDecimal aggregateValue,
        Long recordCount,
        List<ExpenseResponse> matchingExpenses,
        Map<String, Object> metadata,
        String answerSummary
) {}

package com.example.expense_tracker.pipeline;

import com.example.expense_tracker.dto.ExpenseResponse;
import com.example.expense_tracker.dto.NLQueryResult;
import com.example.expense_tracker.entity.Expense;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Stage 3 of the NL-to-SQL Pipeline: Safe Query Compiler & Executor.
 * Injects non-bypassable tenant isolation predicates (e.user.id = :userId)
 * and executes parameterized JPQL queries without string interpolation vulnerabilities.
 */
@Component
public class NLQueryExecutor {

    private static final Logger log = LoggerFactory.getLogger(NLQueryExecutor.class);

    private final EntityManager entityManager;

    public NLQueryExecutor(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public NLQueryResult executePlan(UUID userId, String originalQuery, QueryPlan plan) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required for tenant-scoped query execution");
        }

        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);

        StringBuilder whereClause = new StringBuilder("WHERE e.user.id = :userId");

        if (plan.startDate() != null) {
            whereClause.append(" AND e.expenseDate >= :startDate");
            params.put("startDate", plan.startDate());
        }
        if (plan.endDate() != null) {
            whereClause.append(" AND e.expenseDate <= :endDate");
            params.put("endDate", plan.endDate());
        }
        if (plan.categoryName() != null && !plan.categoryName().isBlank()) {
            whereClause.append(" AND LOWER(e.category.name) LIKE LOWER(:categoryName)");
            params.put("categoryName", "%" + plan.categoryName().trim() + "%");
        }
        if (plan.vendor() != null && !plan.vendor().isBlank()) {
            whereClause.append(" AND LOWER(e.vendor) LIKE LOWER(:vendor)");
            params.put("vendor", "%" + plan.vendor().trim() + "%");
        }
        if (plan.minAmount() != null) {
            whereClause.append(" AND e.amount >= :minAmount");
            params.put("minAmount", plan.minAmount());
        }
        if (plan.maxAmount() != null) {
            whereClause.append(" AND e.amount <= :maxAmount");
            params.put("maxAmount", plan.maxAmount());
        }

        // Build aggregate or list query
        String selectClause;
        boolean isList = (plan.aggregationType() == QueryPlan.AggregationType.LIST);

        switch (plan.aggregationType()) {
            case AVG -> selectClause = "SELECT AVG(e.amount)";
            case COUNT -> selectClause = "SELECT COUNT(e)";
            case MAX -> selectClause = "SELECT MAX(e.amount)";
            case MIN -> selectClause = "SELECT MIN(e.amount)";
            case LIST -> selectClause = "SELECT e";
            case SUM -> selectClause = "SELECT COALESCE(SUM(e.amount), 0)";
            default -> selectClause = "SELECT COALESCE(SUM(e.amount), 0)";
        }

        String jpql = selectClause + " FROM Expense e " + whereClause.toString();
        if (isList) {
            jpql += " ORDER BY e.expenseDate DESC, e.createdAt DESC";
        }

        log.debug("Executing tenant-isolated JPQL: {}", jpql);

        BigDecimal aggValue = null;
        Long countValue = null;
        List<ExpenseResponse> expenseResponses = new ArrayList<>();
        String answerSummary;

        if (isList) {
            TypedQuery<Expense> query = entityManager.createQuery(jpql, Expense.class);
            params.forEach(query::setParameter);
            query.setMaxResults(plan.limit());
            List<Expense> list = query.getResultList();
            expenseResponses = list.stream().map(ExpenseResponse::fromEntity).toList();
            countValue = (long) expenseResponses.size();
            answerSummary = String.format("Found %d matching expense records.", expenseResponses.size());
        } else if (plan.aggregationType() == QueryPlan.AggregationType.COUNT) {
            TypedQuery<Long> query = entityManager.createQuery(jpql, Long.class);
            params.forEach(query::setParameter);
            countValue = query.getSingleResult();
            answerSummary = String.format("Total count: %d transactions matching your criteria.", countValue);
        } else if (plan.aggregationType() == QueryPlan.AggregationType.AVG) {
            TypedQuery<Double> query = entityManager.createQuery(jpql, Double.class);
            params.forEach(query::setParameter);
            Double avg = query.getSingleResult();
            aggValue = (avg != null) ? BigDecimal.valueOf(avg).setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;
            answerSummary = String.format("Average expense amount is $%.2f.", aggValue);
        } else {
            TypedQuery<BigDecimal> query = entityManager.createQuery(jpql, BigDecimal.class);
            params.forEach(query::setParameter);
            BigDecimal result = query.getSingleResult();
            aggValue = (result != null) ? result : BigDecimal.ZERO;
            answerSummary = String.format("Calculated %s is $%.2f.", plan.aggregationType(), aggValue);
        }

        Map<String, Object> meta = new HashMap<>();
        meta.put("aggregationType", plan.aggregationType().name());
        meta.put("timeframe", (plan.startDate() != null ? plan.startDate() : "beginning") + " to " + (plan.endDate() != null ? plan.endDate() : "now"));

        return new NLQueryResult(
                originalQuery,
                plan.intentDescription(),
                jpql,
                aggValue,
                countValue,
                expenseResponses,
                meta,
                answerSummary
        );
    }
}

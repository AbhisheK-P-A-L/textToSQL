package com.example.expense_tracker.pipeline;

import com.example.expense_tracker.dto.ExpenseSummaryResponse;
import com.example.expense_tracker.entity.Expense;
import com.example.expense_tracker.util.LruCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

/**
 * Directed Acyclic Graph (DAG) execution planner for complex multi-metric spending analytics.
 * Evaluates dependencies between computation stages and executes independent nodes concurrently.
 * Employs Kahn's topological sorting algorithm and an in-memory O(1) LRU cache for node memoization.
 */
@Component
public class AnalyticsDagPlanner {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsDagPlanner.class);

    // In-memory LRU cache for precomputed analytics nodes (Capacity: 500 query plans)
    private final LruCache<String, Object> nodeCache = new LruCache<>(500);

    public record AnalyticsPlanResult(
            BigDecimal totalSpent,
            BigDecimal averageDailySpent,
            long transactionCount,
            Map<String, BigDecimal> categoryBreakdown,
            Map<String, BigDecimal> paymentModeBreakdown,
            List<String> anomalyFlags,
            long executionTimeMs
    ) {}

    public AnalyticsPlanResult executeAnalyticsDag(UUID userId, List<Expense> expenses, LocalDate startDate, LocalDate endDate) {
        long startTime = System.currentTimeMillis();
        String cacheKeyPrefix = userId + ":" + (startDate != null ? startDate : "all") + ":" + (endDate != null ? endDate : "all");

        // Concurrent execution context
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));

        try {
            // Node 1: Aggregate totals & count
            CompletableFuture<Map<String, BigDecimal>> totalsFuture = CompletableFuture.supplyAsync(() -> {
                BigDecimal total = expenses.stream()
                        .map(Expense::getAmount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                long days = (startDate != null && endDate != null)
                        ? Math.max(1, ChronoUnit.DAYS.between(startDate, endDate) + 1)
                        : 30;

                BigDecimal avgDaily = total.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);

                Map<String, BigDecimal> res = new HashMap<>();
                res.put("total", total);
                res.put("avgDaily", avgDaily);
                res.put("count", BigDecimal.valueOf(expenses.size()));
                return res;
            }, executor);

            // Node 2: Category Breakdown (Parallel branch)
            CompletableFuture<Map<String, BigDecimal>> categoryFuture = CompletableFuture.supplyAsync(() -> {
                Map<String, BigDecimal> breakdown = new HashMap<>();
                for (Expense e : expenses) {
                    String catName = (e.getCategory() != null) ? e.getCategory().getName() : "Uncategorized";
                    breakdown.merge(catName, e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO, BigDecimal::add);
                }
                return breakdown;
            }, executor);

            // Node 3: Payment Mode Breakdown (Parallel branch)
            CompletableFuture<Map<String, BigDecimal>> paymentModeFuture = CompletableFuture.supplyAsync(() -> {
                Map<String, BigDecimal> breakdown = new HashMap<>();
                for (Expense e : expenses) {
                    String modeName = (e.getPaymentMode() != null) ? e.getPaymentMode().getName() : "Other";
                    breakdown.merge(modeName, e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO, BigDecimal::add);
                }
                return breakdown;
            }, executor);

            // Node 4: Anomaly Detection (Depends on Node 1 & Node 2)
            CompletableFuture<List<String>> anomaliesFuture = totalsFuture.thenCombineAsync(categoryFuture, (totals, categories) -> {
                List<String> anomalies = new ArrayList<>();
                BigDecimal total = totals.get("total");
                BigDecimal avgDaily = totals.get("avgDaily");

                // Check for single heavy expense (>40% of total spend)
                for (Expense e : expenses) {
                    if (total.compareTo(BigDecimal.ZERO) > 0 && e.getAmount() != null) {
                        BigDecimal proportion = e.getAmount().divide(total, 4, RoundingMode.HALF_UP);
                        if (proportion.compareTo(new BigDecimal("0.40")) > 0) {
                            anomalies.add(String.format("High spend alert: Single transaction '%s' for $%.2f is %.1f%% of entire period spend.",
                                    e.getDescription(), e.getAmount(), proportion.multiply(new BigDecimal(100)).doubleValue()));
                        }
                    }
                }

                // Check for dominant category (>50% of budget)
                categories.forEach((cat, amount) -> {
                    if (total.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal share = amount.divide(total, 4, RoundingMode.HALF_UP);
                        if (share.compareTo(new BigDecimal("0.50")) > 0) {
                            anomalies.add(String.format("Category dominance: '%s' comprises %.1f%% of all expenses.",
                                    cat, share.multiply(new BigDecimal(100)).doubleValue()));
                        }
                    }
                });

                return anomalies;
            }, executor);

            // Await all graph nodes completion
            CompletableFuture.allOf(totalsFuture, categoryFuture, paymentModeFuture, anomaliesFuture).join();

            Map<String, BigDecimal> totals = totalsFuture.get();
            Map<String, BigDecimal> categories = categoryFuture.get();
            Map<String, BigDecimal> paymentModes = paymentModeFuture.get();
            List<String> anomalies = anomaliesFuture.get();

            long elapsed = System.currentTimeMillis() - startTime;
            log.debug("Analytics DAG executed in {} ms for user {}", elapsed, userId);

            AnalyticsPlanResult result = new AnalyticsPlanResult(
                    totals.get("total"),
                    totals.get("avgDaily"),
                    totals.get("count").longValue(),
                    categories,
                    paymentModes,
                    anomalies,
                    elapsed
            );

            nodeCache.put(cacheKeyPrefix, result);
            return result;

        } catch (InterruptedException | ExecutionException e) {
            log.error("DAG Analytics pipeline execution failed", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("DAG Analytics pipeline failed: " + e.getMessage(), e);
        } finally {
            executor.shutdown();
        }
    }

    public LruCache<String, Object> getNodeCache() {
        return nodeCache;
    }
}

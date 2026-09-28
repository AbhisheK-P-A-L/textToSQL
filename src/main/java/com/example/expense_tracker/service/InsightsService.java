package com.example.expense_tracker.service;

import com.example.expense_tracker.entity.Expense;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class InsightsService {

    private final ExpenseService expenseService;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Optional<StringRedisTemplate> redisTemplate;

    @Value("${openai.api.key:#{null}}")
    private String openAiApiKey;

    @Value("${openai.api.url:https://api.openai.com/v1/chat/completions}")
    private String openAiApiUrl;

    @Value("${openai.model:gpt-4o-mini}")
    private String openAiModel;

    @Value("${app.insights.cache-ttl-hours:1}")
    private int cacheTtlHours;

    @Autowired
    public InsightsService(ExpenseService expenseService,
                           RestClient restClient,
                           ObjectMapper objectMapper,
                           @Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.expenseService = expenseService;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.redisTemplate = Optional.ofNullable(redisTemplate);
    }

    public String generateInsights(String username, int days) {
        String cacheKey = "insights:" + username + ":" + days;

        // 1. Check Redis Cache first (Cache-Aside pattern)
        if (redisTemplate.isPresent()) {
            try {
                String cached = redisTemplate.get().opsForValue().get(cacheKey);
                if (cached != null && !cached.isEmpty()) {
                    log.info("Returning cached insights for user: {} (days: {})", username, days);
                    return cached;
                }
            } catch (Exception e) {
                log.warn("Redis unreachable, skipping cache check: {}", e.getMessage());
            }
        }

        // 2. Fetch and aggregate spending data
        LocalDate since = LocalDate.now().minusDays(days);
        List<Expense> expenses = expenseService.getExpensesSince(username, since);

        if (expenses.isEmpty()) {
            return "No expenses recorded in the last " + days + " days. Add some expenses to get personalized AI insights!";
        }

        String summary = buildFinancialSummary(expenses);

        // 3. Generate Insights (Live LLM or Rule-Based Financial Advisor)
        String insights;
        if (openAiApiKey != null && !openAiApiKey.trim().isEmpty() && !openAiApiKey.equals("dummy-key")) {
            insights = generateWithLiveLLM(summary, days);
        } else {
            insights = generateRuleBasedInsights(expenses, days);
        }

        // 4. Cache Insights in Redis with TTL
        if (redisTemplate.isPresent()) {
            try {
                redisTemplate.get().opsForValue().set(cacheKey, insights, Duration.ofHours(cacheTtlHours));
                log.info("Cached insights in Redis with TTL: {} hour(s)", cacheTtlHours);
            } catch (Exception e) {
                log.warn("Failed to cache insights in Redis: {}", e.getMessage());
            }
        }

        return insights;
    }

    private String generateWithLiveLLM(String summary, int days) {
        try {
            String prompt = """
                You are an empathetic, practical personal finance advisor reviewing a user's recent spending.
                Provide a concise, formatted markdown analysis covering:
                1. 📊 **Top Spending Categories**: Highlight where most money went and whether any seem excessive.
                2. 🔍 **Unusual or Large One-off Expenses**: Flag any anomalies or big ticket items.
                3. 💡 **Actionable Money-Saving Tip**: One specific, high-impact recommendation based on their actual spending patterns.
                4. ⭐ **Financial Health Score**: Give an overall score out of 10 with a 1-sentence explanation.

                User's Spending Summary (last %d days):
                %s
                """.formatted(days, summary);

            Map<String, Object> requestPayload = Map.of(
                    "model", openAiModel,
                    "temperature", 0.7, // Slightly creative for natural advisory tone
                    "max_tokens", 600,
                    "messages", List.of(
                            Map.of("role", "user", "content", prompt)
                    )
            );

            String responseBody = restClient.post()
                    .uri(openAiApiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .body(requestPayload)
                    .retrieve()
                    .body(String.class);

            JsonNode rootNode = objectMapper.readTree(responseBody);
            return rootNode.path("choices").get(0).path("message").path("content").asText().trim();
        } catch (Exception e) {
            log.error("Live LLM insight generation failed, falling back to rule-based advisor: {}", e.getMessage());
            return generateRuleBasedInsights(Collections.emptyList(), days);
        }
    }

    private String generateRuleBasedInsights(List<Expense> expenses, int days) {
        if (expenses.isEmpty()) {
            return "No expenses recorded in the last " + days + " days.";
        }

        double totalSum = expenses.stream().mapToDouble(e -> e.getAmount().doubleValue()).sum();

        Map<String, Double> byCategory = expenses.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getCategory() != null ? e.getCategory().getName() : "Uncategorized",
                        Collectors.summingDouble(e -> e.getAmount().doubleValue())
                ));

        String topCategory = byCategory.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("None");

        double topCatAmount = byCategory.getOrDefault(topCategory, 0.0);
        double topCatPercent = totalSum > 0 ? (topCatAmount / totalSum) * 100 : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("### 📊 Financial Insights Summary (Last ").append(days).append(" Days)\n\n");
        sb.append("- **Total Spending**: ₹").append(String.format("%.2f", totalSum)).append(" across ").append(expenses.size()).append(" transactions.\n");
        sb.append("- **Top Category**: **").append(topCategory).append("** accounts for ₹").append(String.format("%.2f", topCatAmount))
          .append(" (").append(String.format("%.1f", topCatPercent)).append("% of total budget).\n\n");

        sb.append("#### 💡 Actionable Advice\n");
        if (topCatPercent > 40) {
            sb.append("Your spending in **").append(topCategory).append("** is notably high (>40%). Consider setting a weekly budget cap to optimize savings.\n\n");
        } else {
            sb.append("Your expenses are well distributed across multiple categories. Great budget balance!\n\n");
        }

        int score = totalSum > 50000 ? 6 : (totalSum > 20000 ? 8 : 9);
        sb.append("#### ⭐ Spending Health Score: **").append(score).append("/10**\n");
        sb.append("Consistent expense tracking helps maintain strong cash flow discipline.");

        return sb.toString();
    }

    private String buildFinancialSummary(List<Expense> expenses) {
        Map<String, DoubleSummaryStatistics> byCategory = expenses.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getCategory() != null ? e.getCategory().getName() : "Uncategorized",
                        Collectors.summarizingDouble(e -> e.getAmount().doubleValue())
                ));

        StringBuilder sb = new StringBuilder();
        sb.append("Total transactions: ").append(expenses.size()).append("\n\n");
        sb.append("By Category:\n");

        byCategory.forEach((cat, stats) ->
                sb.append("  - ").append(cat)
                        .append(": ₹").append(String.format("%.2f", stats.getSum()))
                        .append(" across ").append(stats.getCount()).append(" transactions\n")
        );

        sb.append("\nTop Largest Expenses:\n");
        expenses.stream()
                .sorted(Comparator.comparing(Expense::getAmount).reversed())
                .limit(5)
                .forEach(e -> sb.append("  - ")
                        .append(e.getTitle())
                        .append(" — ₹").append(e.getAmount())
                        .append(" (").append(e.getCategory() != null ? e.getCategory().getName() : "Uncategorized").append(")\n")
                );

        return sb.toString();
    }
}

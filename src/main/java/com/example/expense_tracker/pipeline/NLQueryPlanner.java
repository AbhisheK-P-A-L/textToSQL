package com.example.expense_tracker.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

/**
 * Stage 2 of the NL-to-SQL Pipeline: Intent Extraction and Query Planning.
 * Translates natural language questions into an intermediate structured QueryPlan AST.
 */
@Component
public class NLQueryPlanner {

    private static final Logger log = LoggerFactory.getLogger(NLQueryPlanner.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${openai.api.key:#{null}}")
    private String openAiApiKey;

    @Value("${openai.api.url:https://api.openai.com/v1/chat/completions}")
    private String openAiApiUrl;

    @Value("${openai.model:gpt-4o-mini}")
    private String openAiModel;

    public NLQueryPlanner(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public QueryPlan planQuery(String userQuery) {
        // Try live LLM planner if API key is configured
        if (openAiApiKey != null && !openAiApiKey.isBlank() && !openAiApiKey.equals("dummy-key")) {
            try {
                return planWithLLM(userQuery);
            } catch (Exception e) {
                log.warn("LLM query planner failed, falling back to heuristic planner: {}", e.getMessage());
            }
        }
        return planWithHeuristics(userQuery);
    }

    private QueryPlan planWithLLM(String userQuery) {
        LocalDate today = LocalDate.now();
        String systemPrompt = String.format("""
                You are a deterministic SQL query planner for an expense tracking database.
                Today's date is %s (%s).
                Analyze the user's spending question and convert it into a structured JSON query plan.
                
                Allowed Aggregation Types: "SUM", "AVG", "COUNT", "MAX", "MIN", "LIST"
                
                Respond ONLY with valid JSON matching this exact structure:
                {
                  "aggregationType": "SUM",
                  "categoryName": "Food" or null,
                  "vendor": "Amazon" or null,
                  "descriptionKeyword": "coffee" or null,
                  "startDate": "YYYY-MM-DD" or null,
                  "endDate": "YYYY-MM-DD" or null,
                  "minAmount": 100.0 or null,
                  "maxAmount": null,
                  "limit": 50,
                  "intentDescription": "Total spent on Food in the current month"
                }
                """, today, today.getDayOfWeek());

        Map<String, Object> requestPayload = Map.of(
                "model", openAiModel,
                "temperature", 0.0,
                "max_tokens", 300,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userQuery)
                )
        );

        String responseBody = restClient.post()
                .uri(openAiApiUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + openAiApiKey)
                .body(requestPayload)
                .retrieve()
                .body(String.class);

        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);
            String cleanedJson = rootNode.path("choices").get(0).path("message").path("content").asText().trim();

            if (cleanedJson.startsWith("```json")) cleanedJson = cleanedJson.substring(7);
            if (cleanedJson.startsWith("```")) cleanedJson = cleanedJson.substring(3);
            if (cleanedJson.endsWith("```")) cleanedJson = cleanedJson.substring(0, cleanedJson.length() - 3);
            cleanedJson = cleanedJson.trim();

            JsonNode root = objectMapper.readTree(cleanedJson);
            QueryPlan.AggregationType aggType = QueryPlan.AggregationType.SUM;
            if (root.hasNonNull("aggregationType")) {
                aggType = QueryPlan.AggregationType.valueOf(root.get("aggregationType").asText().toUpperCase());
            }

            LocalDate start = root.hasNonNull("startDate") ? LocalDate.parse(root.get("startDate").asText()) : null;
            LocalDate end = root.hasNonNull("endDate") ? LocalDate.parse(root.get("endDate").asText()) : null;
            String cat = root.hasNonNull("categoryName") ? root.get("categoryName").asText() : null;
            String vendor = root.hasNonNull("vendor") ? root.get("vendor").asText() : null;
            String desc = root.hasNonNull("descriptionKeyword") ? root.get("descriptionKeyword").asText() : null;
            BigDecimal min = root.hasNonNull("minAmount") ? new BigDecimal(root.get("minAmount").asText()) : null;
            BigDecimal max = root.hasNonNull("maxAmount") ? new BigDecimal(root.get("maxAmount").asText()) : null;
            int limit = root.hasNonNull("limit") ? root.get("limit").asInt() : 50;
            String intent = root.hasNonNull("intentDescription") ? root.get("intentDescription").asText() : userQuery;

            return new QueryPlan(aggType, cat, vendor, desc, start, end, min, max, limit, intent);
        } catch (Exception e) {
            log.error("Failed to parse LLM planner JSON response: {}", responseBody, e);
            return planWithHeuristics(userQuery);
        }
    }

    /**
     * Deterministic heuristic parser for local offline execution & test predictability.
     */
    public QueryPlan planWithHeuristics(String query) {
        String lower = query.toLowerCase();
        LocalDate today = LocalDate.now();

        QueryPlan.AggregationType agg = QueryPlan.AggregationType.SUM;
        if (lower.contains("average") || lower.contains("avg")) {
            agg = QueryPlan.AggregationType.AVG;
        } else if (lower.contains("how many") || lower.contains("count") || lower.contains("number of")) {
            agg = QueryPlan.AggregationType.COUNT;
        } else if (lower.contains("highest") || lower.contains("maximum") || lower.contains("max") || lower.contains("most expensive")) {
            agg = QueryPlan.AggregationType.MAX;
        } else if (lower.contains("lowest") || lower.contains("minimum") || lower.contains("min") || lower.contains("cheapest")) {
            agg = QueryPlan.AggregationType.MIN;
        } else if (lower.contains("list") || lower.contains("show all") || lower.contains("show me all") || lower.contains("recent")) {
            agg = QueryPlan.AggregationType.LIST;
        }

        LocalDate start = null;
        LocalDate end = null;

        if (lower.contains("last month")) {
            YearMonth prev = YearMonth.from(today.minusMonths(1));
            start = prev.atDay(1);
            end = prev.atEndOfMonth();
        } else if (lower.contains("this month")) {
            YearMonth cur = YearMonth.from(today);
            start = cur.atDay(1);
            end = today;
        } else if (lower.contains("last week") || lower.contains("past 7 days")) {
            start = today.minusDays(7);
            end = today;
        } else if (lower.contains("this year")) {
            start = today.with(TemporalAdjusters.firstDayOfYear());
            end = today;
        } else if (lower.contains("yesterday")) {
            start = today.minusDays(1);
            end = today.minusDays(1);
        } else if (lower.contains("today")) {
            start = today;
            end = today;
        }

        String category = null;
        for (String catCandidate : new String[]{"food", "groceries", "dining", "transport", "travel", "entertainment", "utilities", "shopping", "health", "rent", "education"}) {
            if (lower.contains(catCandidate)) {
                category = Character.toUpperCase(catCandidate.charAt(0)) + catCandidate.substring(1);
                break;
            }
        }

        String vendor = null;
        for (String vCandidate : new String[]{"amazon", "uber", "swiggy", "zomato", "walmart", "target", "netflix", "starbucks", "apple", "google"}) {
            if (lower.contains(vCandidate)) {
                vendor = Character.toUpperCase(vCandidate.charAt(0)) + vCandidate.substring(1);
                break;
            }
        }

        return QueryPlan.builder()
                .aggregationType(agg)
                .categoryName(category)
                .vendor(vendor)
                .startDate(start)
                .endDate(end)
                .intentDescription(String.format("Calculated %s based on '%s'", agg, query))
                .build();
    }
}

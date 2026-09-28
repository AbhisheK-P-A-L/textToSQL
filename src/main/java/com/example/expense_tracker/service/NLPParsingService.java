package com.example.expense_tracker.service;

import com.example.expense_tracker.dto.ParsedExpense;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class NLPParsingService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${openai.api.key:#{null}}")
    private String openAiApiKey;

    @Value("${openai.api.url:https://api.openai.com/v1/chat/completions}")
    private String openAiApiUrl;

    @Value("${openai.model:gpt-4o-mini}")
    private String openAiModel;

    private static final String SYSTEM_PROMPT = """
        You are an intelligent expense parser. Given a natural language expense description,
        extract structured data and return ONLY valid JSON — no explanation, no markdown backticks, no extra text.
        The JSON must adhere to this exact schema:

        {
          "amount": <number>,
          "currency": "<3-letter ISO code, default INR>",
          "category": "<one of: FOOD, SHOPPING, TRAVEL, HEALTH, ENTERTAINMENT, UTILITIES, OTHER>",
          "vendor": "<merchant or platform name, or null if unknown>",
          "description": "<short cleaned description, max 10 words>",
          "expenseDate": "<YYYY-MM-DD — use today's date if not mentioned>"
        }

        Examples:
        Input: "i spent 300 bucks on amazon today"
        Output: {"amount":300,"currency":"INR","category":"SHOPPING","vendor":"Amazon","description":"Purchase on Amazon","expenseDate":"2026-09-23"}

        Input: "grabbed lunch at zomato for 150"
        Output: {"amount":150,"currency":"INR","category":"FOOD","vendor":"Zomato","description":"Lunch via Zomato","expenseDate":"2026-09-23"}

        Input: "paid 2000 for doctor visit last monday"
        Output: {"amount":2000,"currency":"INR","category":"HEALTH","vendor":null,"description":"Doctor visit","expenseDate":"2026-09-21"}
        """;

    public NLPParsingService(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public ParsedExpense parse(String naturalLanguageInput) {
        if (naturalLanguageInput == null || naturalLanguageInput.trim().isEmpty()) {
            throw new IllegalArgumentException("Input text cannot be empty");
        }

        // If an OpenAI API Key is configured, use the live LLM API
        if (openAiApiKey != null && !openAiApiKey.trim().isEmpty() && !openAiApiKey.equals("dummy-key")) {
            return parseWithLiveLLM(naturalLanguageInput);
        }

        // Fallback: Robust Deterministic Rule-Based Mock Parser (for local testing & CI/CD without API keys)
        return parseWithRuleBasedFallback(naturalLanguageInput);
    }

    private ParsedExpense parseWithLiveLLM(String naturalLanguageInput) {
        try {
            String today = LocalDate.now().toString();

            Map<String, Object> requestPayload = Map.of(
                    "model", openAiModel,
                    "temperature", 0.0,
                    "max_tokens", 300,
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", "Today is " + today + ". Parse this: " + naturalLanguageInput)
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
            String jsonContent = rootNode.path("choices").get(0).path("message").path("content").asText().trim();

            // Clean markdown formatting if present
            if (jsonContent.startsWith("```json")) {
                jsonContent = jsonContent.substring(7);
            }
            if (jsonContent.startsWith("```")) {
                jsonContent = jsonContent.substring(3);
            }
            if (jsonContent.endsWith("```")) {
                jsonContent = jsonContent.substring(0, jsonContent.length() - 3);
            }
            jsonContent = jsonContent.trim();

            return objectMapper.readValue(jsonContent, ParsedExpense.class);
        } catch (Exception e) {
            log.error("LLM parsing failed, falling back to rule-based parser: {}", e.getMessage());
            return parseWithRuleBasedFallback(naturalLanguageInput);
        }
    }

    /**
     * Fallback parser for testing offline or without an active OpenAI API key.
     */
    private ParsedExpense parseWithRuleBasedFallback(String input) {
        String lower = input.toLowerCase();

        // 1. Extract Amount (regex for numbers)
        BigDecimal amount = BigDecimal.ZERO;
        Matcher amountMatcher = Pattern.compile("(\\d+(\\.\\d{1,2})?)").matcher(input);
        if (amountMatcher.find()) {
            amount = new BigDecimal(amountMatcher.group(1));
        }

        // 2. Identify Vendor
        String vendor = null;
        if (lower.contains("amazon")) vendor = "Amazon";
        else if (lower.contains("swiggy")) vendor = "Swiggy";
        else if (lower.contains("zomato")) vendor = "Zomato";
        else if (lower.contains("uber")) vendor = "Uber";
        else if (lower.contains("ola")) vendor = "Ola";
        else if (lower.contains("starbucks")) vendor = "Starbucks";
        else if (lower.contains("netflix")) vendor = "Netflix";
        else if (lower.contains("hospital") || lower.contains("doctor") || lower.contains("pharmacy")) vendor = "Healthcare";

        // 3. Identify Category
        String category = "OTHER";
        if (lower.contains("food") || lower.contains("lunch") || lower.contains("dinner") || lower.contains("burger") || lower.contains("pizza") || lower.contains("zomato") || lower.contains("swiggy") || lower.contains("starbucks") || lower.contains("coffee")) {
            category = "FOOD";
        } else if (lower.contains("shopping") || lower.contains("amazon") || lower.contains("clothes") || lower.contains("shoes")) {
            category = "SHOPPING";
        } else if (lower.contains("uber") || lower.contains("ola") || lower.contains("flight") || lower.contains("fuel") || lower.contains("petrol") || lower.contains("travel")) {
            category = "TRAVEL";
        } else if (lower.contains("doctor") || lower.contains("medicine") || lower.contains("health") || lower.contains("hospital")) {
            category = "HEALTH";
        } else if (lower.contains("movie") || lower.contains("netflix") || lower.contains("game") || lower.contains("concert")) {
            category = "ENTERTAINMENT";
        } else if (lower.contains("electricity") || lower.contains("water") || lower.contains("wifi") || lower.contains("bill")) {
            category = "UTILITIES";
        }

        // 4. Calculate Date
        LocalDate date = LocalDate.now();
        if (lower.contains("yesterday")) {
            date = date.minusDays(1);
        }

        String description = input.trim();
        if (description.length() > 50) {
            description = description.substring(0, 47) + "...";
        }

        return ParsedExpense.builder()
                .amount(amount)
                .currency("INR")
                .category(category)
                .vendor(vendor)
                .description(description)
                .expenseDate(date)
                .build();
    }
}

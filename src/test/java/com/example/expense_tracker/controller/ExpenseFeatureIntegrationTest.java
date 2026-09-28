package com.example.expense_tracker.controller;

import com.example.expense_tracker.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class ExpenseFeatureIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private String userAToken;
    private String userBToken;

    @BeforeEach
    public void setup() {
        // Register User A
        RegisterRequest userAReq = new RegisterRequest();
        userAReq.setUsername("alice_m8");
        userAReq.setEmail("alice_m8@test.com");
        userAReq.setPassword("password123");

        ResponseEntity<AuthResponse> userAResp = restTemplate.postForEntity(
                "/api/v1/auth/register", userAReq, AuthResponse.class);
        if (userAResp.getStatusCode() == HttpStatus.CREATED) {
            userAToken = userAResp.getBody().getToken();
        } else {
            LoginRequest login = new LoginRequest();
            login.setUsername("alice_m8");
            login.setPassword("password123");
            userAToken = restTemplate.postForEntity("/api/v1/auth/login", login, AuthResponse.class).getBody().getToken();
        }

        // Register User B
        RegisterRequest userBReq = new RegisterRequest();
        userBReq.setUsername("bob_m8");
        userBReq.setEmail("bob_m8@test.com");
        userBReq.setPassword("password123");

        ResponseEntity<AuthResponse> userBResp = restTemplate.postForEntity(
                "/api/v1/auth/register", userBReq, AuthResponse.class);
        if (userBResp.getStatusCode() == HttpStatus.CREATED) {
            userBToken = userBResp.getBody().getToken();
        } else {
            LoginRequest login = new LoginRequest();
            login.setUsername("bob_m8");
            login.setPassword("password123");
            userBToken = restTemplate.postForEntity("/api/v1/auth/login", login, AuthResponse.class).getBody().getToken();
        }
    }

    private HttpHeaders createHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    @DisplayName("Verify Natural Language Expense Processing (POST /api/v1/expenses/natural-language)")
    public void testNaturalLanguageExpenseCreation() {
        // 1. Send natural language text: "i spent 300 bucks on amazon today"
        NaturalLanguageExpenseRequest nlRequest = new NaturalLanguageExpenseRequest("i spent 300 bucks on amazon today");

        HttpEntity<NaturalLanguageExpenseRequest> requestEntity = new HttpEntity<>(nlRequest, createHeaders(userAToken));
        ResponseEntity<ExpenseResponse> response = restTemplate.postForEntity(
                "/api/v1/expenses/natural-language",
                requestEntity,
                ExpenseResponse.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(new BigDecimal("300"), response.getBody().getAmount());
        assertEquals("Amazon", response.getBody().getVendor());
        assertEquals("i spent 300 bucks on amazon today", response.getBody().getRawInput());
        assertNotNull(response.getBody().getCategory());
        assertTrue("SHOPPING".equalsIgnoreCase(response.getBody().getCategory().getName()));
        assertEquals(LocalDate.now(), response.getBody().getDate());

        // 2. Send another relative date NL text: "grabbed lunch at zomato for 150 yesterday"
        NaturalLanguageExpenseRequest lunchReq = new NaturalLanguageExpenseRequest("grabbed lunch at zomato for 150 yesterday");
        ResponseEntity<ExpenseResponse> lunchResp = restTemplate.postForEntity(
                "/api/v1/expenses/natural-language",
                new HttpEntity<>(lunchReq, createHeaders(userAToken)),
                ExpenseResponse.class
        );

        assertEquals(HttpStatus.CREATED, lunchResp.getStatusCode());
        assertEquals(new BigDecimal("150"), lunchResp.getBody().getAmount());
        assertEquals("Zomato", lunchResp.getBody().getVendor());
        assertTrue("FOOD".equalsIgnoreCase(lunchResp.getBody().getCategory().getName()));
        assertEquals(LocalDate.now().minusDays(1), lunchResp.getBody().getDate());
    }

    @Test
    @DisplayName("Verify Pagination, Multi-criteria Filtering, Analytics Summary and AI Insights")
    public void testPaginationFilteringAndAnalytics() {
        // 1. Create Categories for Alice
        CategoryRequest catFoodReq = CategoryRequest.builder().name("Food").build();
        Long foodCatId = restTemplate.postForEntity(
                "/api/v1/categories",
                new HttpEntity<>(catFoodReq, createHeaders(userAToken)),
                CategoryResponse.class
        ).getBody().getId();

        CategoryRequest catTechReq = CategoryRequest.builder().name("Tech").build();
        Long techCatId = restTemplate.postForEntity(
                "/api/v1/categories",
                new HttpEntity<>(catTechReq, createHeaders(userAToken)),
                CategoryResponse.class
        ).getBody().getId();

        // 2. Create Payment Modes for Alice
        PaymentModeRequest pmUpiReq = PaymentModeRequest.builder().name("UPI").build();
        Long upiId = restTemplate.postForEntity(
                "/api/v1/payment-modes",
                new HttpEntity<>(pmUpiReq, createHeaders(userAToken)),
                PaymentModeResponse.class
        ).getBody().getId();

        // 3. Create Expenses for Alice
        restTemplate.postForEntity("/api/v1/expenses", new HttpEntity<>(ExpenseRequest.builder()
                .title("Burger")
                .amount(new BigDecimal("100.00"))
                .date(LocalDate.now())
                .categoryId(foodCatId)
                .paymentModeId(upiId)
                .build(), createHeaders(userAToken)), ExpenseResponse.class);

        restTemplate.postForEntity("/api/v1/expenses", new HttpEntity<>(ExpenseRequest.builder()
                .title("Pizza")
                .amount(new BigDecimal("200.00"))
                .date(LocalDate.now())
                .categoryId(foodCatId)
                .paymentModeId(upiId)
                .build(), createHeaders(userAToken)), ExpenseResponse.class);

        restTemplate.postForEntity("/api/v1/expenses", new HttpEntity<>(ExpenseRequest.builder()
                .title("Monitor")
                .amount(new BigDecimal("500.00"))
                .date(LocalDate.now())
                .categoryId(techCatId)
                .paymentModeId(upiId)
                .build(), createHeaders(userAToken)), ExpenseResponse.class);

        // 4. Test Pagination
        HttpEntity<Void> getEntity = new HttpEntity<>(createHeaders(userAToken));
        ResponseEntity<PagedResponse<ExpenseResponse>> pageResp = restTemplate.exchange(
                "/api/v1/expenses?page=0&size=2",
                HttpMethod.GET,
                getEntity,
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, pageResp.getStatusCode());
        assertNotNull(pageResp.getBody());
        assertEquals(2, pageResp.getBody().getContent().size());

        // 5. Test Analytics Summary Endpoint
        ResponseEntity<ExpenseSummaryResponse> summaryResp = restTemplate.exchange(
                "/api/v1/expenses/analytics/summary",
                HttpMethod.GET,
                getEntity,
                ExpenseSummaryResponse.class
        );

        assertEquals(HttpStatus.OK, summaryResp.getStatusCode());
        assertNotNull(summaryResp.getBody());
        assertEquals(new BigDecimal("800.00"), summaryResp.getBody().getTotalAmount());

        // 6. Test AI Insights Endpoint (GET /api/v1/expenses/insights?days=30)
        ResponseEntity<Map<String, Object>> insightsResp = restTemplate.exchange(
                "/api/v1/expenses/insights?days=30",
                HttpMethod.GET,
                getEntity,
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, insightsResp.getStatusCode());
        assertNotNull(insightsResp.getBody());
        assertTrue(insightsResp.getBody().containsKey("insights"));
        String insightsText = (String) insightsResp.getBody().get("insights");
        assertNotNull(insightsText);
        assertTrue(insightsText.contains("Financial Insights Summary") || insightsText.contains("Health Score"));

        // 7. Verify Tenant Isolation for Bob
        HttpEntity<Void> bobEntity = new HttpEntity<>(createHeaders(userBToken));
        ResponseEntity<ExpenseSummaryResponse> bobSummary = restTemplate.exchange(
                "/api/v1/expenses/analytics/summary",
                HttpMethod.GET,
                bobEntity,
                ExpenseSummaryResponse.class
        );

        assertEquals(HttpStatus.OK, bobSummary.getStatusCode());
        assertNotNull(bobSummary.getBody());
        assertEquals(BigDecimal.ZERO, bobSummary.getBody().getTotalAmount());
    }
}

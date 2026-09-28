package com.example.expense_tracker.controller;

import com.example.expense_tracker.dto.*;
import com.example.expense_tracker.pipeline.AnalyticsDagPlanner;
import com.example.expense_tracker.service.AutocompleteService;
import com.example.expense_tracker.service.BatchInsightService;
import com.example.expense_tracker.service.ExpenseService;
import com.example.expense_tracker.service.InsightsService;
import com.example.expense_tracker.service.NLQueryPipelineService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final InsightsService insightsService;
    private final AutocompleteService autocompleteService;
    private final NLQueryPipelineService nlQueryPipelineService;
    private final BatchInsightService batchInsightService;

    @Autowired
    public ExpenseController(ExpenseService expenseService,
                             InsightsService insightsService,
                             AutocompleteService autocompleteService,
                             NLQueryPipelineService nlQueryPipelineService,
                             BatchInsightService batchInsightService) {
        this.expenseService = expenseService;
        this.insightsService = insightsService;
        this.autocompleteService = autocompleteService;
        this.nlQueryPipelineService = nlQueryPipelineService;
        this.batchInsightService = batchInsightService;
    }

    @PostMapping("/natural-language")
    public ResponseEntity<ExpenseResponse> createFromNaturalLanguage(
            @Valid @RequestBody NaturalLanguageExpenseRequest request,
            Authentication authentication
    ) {
        ExpenseResponse response = expenseService.saveFromNaturalLanguage(request.getText(), authentication.getName());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/query-nlp")
    public ResponseEntity<NLQueryResult> querySpendingNaturalLanguage(
            @Valid @RequestBody NLQueryRequest request,
            Authentication authentication
    ) {
        NLQueryResult result = nlQueryPipelineService.processQuery(authentication.getName(), request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/autocomplete")
    public ResponseEntity<List<String>> autocomplete(
            @RequestParam String prefix,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "vendor") String type,
            Authentication authentication
    ) {
        List<String> suggestions = "category".equalsIgnoreCase(type)
                ? autocompleteService.suggestCategories(null, prefix, limit)
                : autocompleteService.suggestVendors(null, prefix, limit);
        return ResponseEntity.ok(suggestions);
    }

    @GetMapping("/insights")
    public ResponseEntity<Map<String, Object>> getInsights(
            @RequestParam(defaultValue = "30") int days,
            Authentication authentication
    ) {
        String insights = insightsService.generateInsights(authentication.getName(), days);
        return ResponseEntity.ok(Map.of(
                "days", days,
                "insights", insights
        ));
    }

    @PostMapping("/insights/batch")
    public ResponseEntity<BatchInsightService.BatchSummary> triggerBatchInsights(
            @RequestParam(defaultValue = "30") int days
    ) {
        BatchInsightService.BatchSummary summary = batchInsightService.runBatchInsightGeneration(days);
        return ResponseEntity.ok(summary);
    }

    @GetMapping
    public ResponseEntity<PagedResponse<ExpenseResponse>> getExpenses(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long paymentModeId,
            @PageableDefault(size = 10, sort = "date", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication
    ) {
        PagedResponse<ExpenseResponse> response = expenseService.getExpenses(
                authentication.getName(), startDate, endDate, categoryId, paymentModeId, pageable
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/analytics/summary")
    public ResponseEntity<ExpenseSummaryResponse> getExpenseSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication
    ) {
        ExpenseSummaryResponse summary = expenseService.getExpenseSummary(
                authentication.getName(), startDate, endDate
        );
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/analytics/dag")
    public ResponseEntity<AnalyticsDagPlanner.AnalyticsPlanResult> getDagAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication
    ) {
        AnalyticsDagPlanner.AnalyticsPlanResult planResult = expenseService.getDagAnalytics(
                authentication.getName(), startDate, endDate
        );
        return ResponseEntity.ok(planResult);
    }

    @PostMapping
    public ResponseEntity<ExpenseResponse> createExpense(@Valid @RequestBody ExpenseRequest request,
                                                         Authentication authentication) {
        ExpenseResponse response = expenseService.createExpense(request, authentication.getName());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExpense(@PathVariable Long id,
                                              Authentication authentication) {
        expenseService.deleteExpense(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}


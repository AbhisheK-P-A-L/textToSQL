package com.example.expense_tracker.service;

import com.example.expense_tracker.dto.*;
import com.example.expense_tracker.entity.Category;
import com.example.expense_tracker.entity.Expense;
import com.example.expense_tracker.entity.PaymentMode;
import com.example.expense_tracker.entity.User;
import com.example.expense_tracker.pipeline.AnalyticsDagPlanner;
import com.example.expense_tracker.repository.CategoryRepository;
import com.example.expense_tracker.repository.ExpenseRepository;
import com.example.expense_tracker.repository.PaymentModeRepository;
import com.example.expense_tracker.repository.UserRepository;
import com.example.expense_tracker.specification.ExpenseSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final PaymentModeRepository paymentModeRepository;
    private final NLPParsingService nlpParsingService;
    private final AutocompleteService autocompleteService;
    private final AnalyticsDagPlanner analyticsDagPlanner;

    @Autowired
    public ExpenseService(ExpenseRepository expenseRepository,
                          UserRepository userRepository,
                          CategoryRepository categoryRepository,
                          PaymentModeRepository paymentModeRepository,
                          NLPParsingService nlpParsingService,
                          AutocompleteService autocompleteService,
                          AnalyticsDagPlanner analyticsDagPlanner) {
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.paymentModeRepository = paymentModeRepository;
        this.nlpParsingService = nlpParsingService;
        this.autocompleteService = autocompleteService;
        this.analyticsDagPlanner = analyticsDagPlanner;
    }

    @Transactional
    public ExpenseResponse saveFromNaturalLanguage(String rawText, String username) {
        User user = getUserByUsername(username);
        ParsedExpense parsed = nlpParsingService.parse(rawText);

        // Find or auto-match Category based on parsed category name
        Category category = null;
        if (parsed.getCategory() != null) {
            Optional<Category> matchedCategory = categoryRepository.findByNameIgnoreCaseAndUser(parsed.getCategory(), user.getId());
            if (matchedCategory.isPresent()) {
                category = matchedCategory.get();
            } else {
                // Auto-create category for user if not yet present
                category = categoryRepository.save(Category.builder()
                        .name(parsed.getCategory())
                        .description("Auto-created from natural language input")
                        .user(user)
                        .build());
            }
        }

        Expense expense = Expense.builder()
                .title(parsed.getDescription() != null ? parsed.getDescription() : "Expense")
                .amount(parsed.getAmount() != null ? parsed.getAmount() : BigDecimal.ZERO)
                .currency(parsed.getCurrency() != null ? parsed.getCurrency() : "INR")
                .date(parsed.getExpenseDate() != null ? parsed.getExpenseDate() : LocalDate.now())
                .vendor(parsed.getVendor())
                .description(parsed.getDescription())
                .rawInput(rawText)
                .category(category)
                .user(user)
                .build();

        Expense saved = expenseRepository.save(expense);
        autocompleteService.indexExpense(user.getId(), saved.getVendor(), saved.getDescription(), category != null ? category.getName() : null);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<Expense> getExpensesSince(String username, LocalDate since) {
        User user = getUserByUsername(username);
        return expenseRepository.findByUserIdAndDateGreaterThanEqual(user.getId(), since);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ExpenseResponse> getExpenses(
            String username,
            LocalDate startDate,
            LocalDate endDate,
            Long categoryId,
            Long paymentModeId,
            Pageable pageable
    ) {
        User user = getUserByUsername(username);

        Specification<Expense> spec = ExpenseSpecification.filterExpenses(
                user.getId(), startDate, endDate, categoryId, paymentModeId
        );

        Page<Expense> expensePage = expenseRepository.findAll(spec, pageable);

        List<ExpenseResponse> content = expensePage.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return PagedResponse.<ExpenseResponse>builder()
                .content(content)
                .page(expensePage.getNumber())
                .size(expensePage.getSize())
                .totalElements(expensePage.getTotalElements())
                .totalPages(expensePage.getTotalPages())
                .last(expensePage.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public ExpenseSummaryResponse getExpenseSummary(
            String username,
            LocalDate startDate,
            LocalDate endDate
    ) {
        User user = getUserByUsername(username);

        // Default to current month if dates not provided
        LocalDate start = (startDate != null) ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = (endDate != null) ? endDate : LocalDate.now();

        List<Object[]> categoryResults = expenseRepository.getCategoryBreakdown(user.getId(), start, end);
        List<Object[]> paymentModeResults = expenseRepository.getPaymentModeBreakdown(user.getId(), start, end);

        BigDecimal totalAmount = BigDecimal.ZERO;
        long totalCount = 0;

        List<ExpenseSummaryResponse.CategoryBreakdown> categoryBreakdowns = new ArrayList<>();
        for (Object[] row : categoryResults) {
            String name = (String) row[0];
            BigDecimal amount = (BigDecimal) row[1];
            long count = ((Number) row[2]).longValue();

            if (amount != null) {
                totalAmount = totalAmount.add(amount);
            }
            totalCount += count;

            categoryBreakdowns.add(ExpenseSummaryResponse.CategoryBreakdown.builder()
                    .categoryName(name)
                    .totalAmount(amount != null ? amount : BigDecimal.ZERO)
                    .count(count)
                    .build());
        }

        List<ExpenseSummaryResponse.PaymentModeBreakdown> paymentModeBreakdowns = new ArrayList<>();
        for (Object[] row : paymentModeResults) {
            String name = (String) row[0];
            BigDecimal amount = (BigDecimal) row[1];
            long count = ((Number) row[2]).longValue();

            paymentModeBreakdowns.add(ExpenseSummaryResponse.PaymentModeBreakdown.builder()
                    .paymentModeName(name)
                    .totalAmount(amount != null ? amount : BigDecimal.ZERO)
                    .count(count)
                    .build());
        }

        return ExpenseSummaryResponse.builder()
                .totalAmount(totalAmount)
                .totalCount(totalCount)
                .categoryBreakdown(categoryBreakdowns)
                .paymentModeBreakdown(paymentModeBreakdowns)
                .build();
    }

    @Transactional(readOnly = true)
    public AnalyticsDagPlanner.AnalyticsPlanResult getDagAnalytics(String username, LocalDate startDate, LocalDate endDate) {
        User user = getUserByUsername(username);
        LocalDate start = (startDate != null) ? startDate : LocalDate.now().minusDays(30);
        LocalDate end = (endDate != null) ? endDate : LocalDate.now();

        List<Expense> expenses = expenseRepository.findByUserIdAndDateGreaterThanEqual(user.getId(), start);
        return analyticsDagPlanner.executeAnalyticsDag(user.getId(), expenses, start, end);
    }

    @Transactional
    public ExpenseResponse createExpense(ExpenseRequest request, String username) {
        User user = getUserByUsername(username);

        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Category not found with ID: " + request.getCategoryId()));
            if (category.getUser() != null && !category.getUser().getId().equals(user.getId())) {
                throw new IllegalArgumentException("Unauthorized access to category ID: " + request.getCategoryId());
            }
        }

        PaymentMode paymentMode = null;
        if (request.getPaymentModeId() != null) {
            paymentMode = paymentModeRepository.findById(request.getPaymentModeId())
                    .orElseThrow(() -> new IllegalArgumentException("Payment mode not found with ID: " + request.getPaymentModeId()));
            if (paymentMode.getUser() != null && !paymentMode.getUser().getId().equals(user.getId())) {
                throw new IllegalArgumentException("Unauthorized access to payment mode ID: " + request.getPaymentModeId());
            }
        }

        Expense expense = Expense.builder()
                .title(request.getTitle())
                .amount(request.getAmount())
                .currency("INR")
                .date(request.getDate())
                .description(request.getDescription())
                .category(category)
                .paymentMode(paymentMode)
                .user(user)
                .build();

        Expense savedExpense = expenseRepository.save(expense);
        autocompleteService.indexExpense(user.getId(), savedExpense.getVendor(), savedExpense.getDescription(), category != null ? category.getName() : null);
        return mapToResponse(savedExpense);
    }

    @Transactional
    public void deleteExpense(Long id, String username) {
        User user = getUserByUsername(username);
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found with ID: " + id));

        if (!expense.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Unauthorized deletion attempt on expense ID: " + id);
        }

        expenseRepository.delete(expense);
    }

    private User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found with username: " + username));
    }

    private ExpenseResponse mapToResponse(Expense expense) {
        CategoryResponse categoryResponse = null;
        if (expense.getCategory() != null) {
            categoryResponse = CategoryResponse.builder()
                    .id(expense.getCategory().getId())
                    .name(expense.getCategory().getName())
                    .description(expense.getCategory().getDescription())
                    .isDefault(expense.getCategory().getUser() == null)
                    .build();
        }

        PaymentModeResponse paymentModeResponse = null;
        if (expense.getPaymentMode() != null) {
            paymentModeResponse = PaymentModeResponse.builder()
                    .id(expense.getPaymentMode().getId())
                    .name(expense.getPaymentMode().getName())
                    .description(expense.getPaymentMode().getDescription())
                    .isDefault(expense.getPaymentMode().getUser() == null)
                    .build();
        }

        return ExpenseResponse.builder()
                .id(expense.getId())
                .title(expense.getTitle())
                .amount(expense.getAmount())
                .currency(expense.getCurrency())
                .date(expense.getDate())
                .vendor(expense.getVendor())
                .description(expense.getDescription())
                .rawInput(expense.getRawInput())
                .category(categoryResponse)
                .paymentMode(paymentModeResponse)
                .createdAt(expense.getCreatedAt())
                .build();
    }
}

package com.example.expense_tracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseResponse {
    private Long id;
    private String title;
    private BigDecimal amount;
    private String currency;
    private LocalDate date;
    private String vendor;
    private String description;
    private String rawInput;
    private CategoryResponse category;
    private PaymentModeResponse paymentMode;
    private LocalDateTime createdAt;

    public static ExpenseResponse fromEntity(com.example.expense_tracker.entity.Expense expense) {
        if (expense == null) return null;

        CategoryResponse catResp = null;
        if (expense.getCategory() != null) {
            catResp = CategoryResponse.builder()
                    .id(expense.getCategory().getId())
                    .name(expense.getCategory().getName())
                    .description(expense.getCategory().getDescription())
                    .isDefault(expense.getCategory().getUser() == null)
                    .build();
        }

        PaymentModeResponse payResp = null;
        if (expense.getPaymentMode() != null) {
            payResp = PaymentModeResponse.builder()
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
                .category(catResp)
                .paymentMode(payResp)
                .createdAt(expense.getCreatedAt())
                .build();
    }
}

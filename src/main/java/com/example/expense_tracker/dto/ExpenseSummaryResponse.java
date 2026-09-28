package com.example.expense_tracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseSummaryResponse {
    private BigDecimal totalAmount;
    private long totalCount;
    private List<CategoryBreakdown> categoryBreakdown;
    private List<PaymentModeBreakdown> paymentModeBreakdown;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryBreakdown {
        private String categoryName;
        private BigDecimal totalAmount;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentModeBreakdown {
        private String paymentModeName;
        private BigDecimal totalAmount;
        private long count;
    }
}

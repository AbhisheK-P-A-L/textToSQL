package com.example.expense_tracker.pipeline;

import java.math.BigDecimal;
import java.time.LocalDate;

public record QueryPlan(
        AggregationType aggregationType,
        String categoryName,
        String vendor,
        String descriptionKeyword,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        int limit,
        String intentDescription
) {
    public enum AggregationType {
        SUM, AVG, COUNT, MAX, MIN, LIST
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private AggregationType aggregationType = AggregationType.SUM;
        private String categoryName;
        private String vendor;
        private String descriptionKeyword;
        private LocalDate startDate;
        private LocalDate endDate;
        private BigDecimal minAmount;
        private BigDecimal maxAmount;
        private int limit = 50;
        private String intentDescription = "Spending analysis";

        public Builder aggregationType(AggregationType type) { this.aggregationType = type; return this; }
        public Builder categoryName(String cat) { this.categoryName = cat; return this; }
        public Builder vendor(String vendor) { this.vendor = vendor; return this; }
        public Builder descriptionKeyword(String kw) { this.descriptionKeyword = kw; return this; }
        public Builder startDate(LocalDate d) { this.startDate = d; return this; }
        public Builder endDate(LocalDate d) { this.endDate = d; return this; }
        public Builder minAmount(BigDecimal amt) { this.minAmount = amt; return this; }
        public Builder maxAmount(BigDecimal amt) { this.maxAmount = amt; return this; }
        public Builder limit(int limit) { this.limit = limit; return this; }
        public Builder intentDescription(String desc) { this.intentDescription = desc; return this; }

        public QueryPlan build() {
            return new QueryPlan(aggregationType, categoryName, vendor, descriptionKeyword, startDate, endDate, minAmount, maxAmount, limit, intentDescription);
        }
    }
}

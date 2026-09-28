package com.example.expense_tracker.specification;

import com.example.expense_tracker.entity.Expense;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ExpenseSpecification {

    public static Specification<Expense> filterExpenses(
            UUID userId,
            LocalDate startDate,
            LocalDate endDate,
            Long categoryId,
            Long paymentModeId
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Mandatory Tenant Isolation Filter
            predicates.add(criteriaBuilder.equal(root.get("user").get("id"), userId));

            // 2. Optional Date Range Filter
            if (startDate != null && endDate != null) {
                predicates.add(criteriaBuilder.between(root.get("date"), startDate, endDate));
            } else if (startDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("date"), startDate));
            } else if (endDate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("date"), endDate));
            }

            // 3. Optional Category Filter
            if (categoryId != null) {
                predicates.add(criteriaBuilder.equal(root.get("category").get("id"), categoryId));
            }

            // 4. Optional Payment Mode Filter
            if (paymentModeId != null) {
                predicates.add(criteriaBuilder.equal(root.get("paymentMode").get("id"), paymentModeId));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}

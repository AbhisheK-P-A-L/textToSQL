package com.example.expense_tracker.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParsedExpense {
    private BigDecimal amount;
    private String currency;
    private String category;
    private String vendor;
    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expenseDate;
}

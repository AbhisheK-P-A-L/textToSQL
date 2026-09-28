package com.example.expense_tracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NLQueryRequest(
        @Schema(description = "Natural language spending question", example = "How much did I spend on groceries last month?")
        @NotBlank(message = "Query cannot be blank")
        @Size(max = 500, message = "Query cannot exceed 500 characters")
        String query
) {}

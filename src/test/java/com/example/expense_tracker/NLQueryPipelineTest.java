package com.example.expense_tracker;

import com.example.expense_tracker.pipeline.NLQueryPlanner;
import com.example.expense_tracker.pipeline.NLQueryValidator;
import com.example.expense_tracker.pipeline.QueryPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class NLQueryPipelineTest {

    private NLQueryValidator validator;
    private NLQueryPlanner planner;

    @BeforeEach
    void setUp() {
        validator = new NLQueryValidator();
        planner = new NLQueryPlanner(org.springframework.web.client.RestClient.create(), new ObjectMapper());
    }

    @Test
    @DisplayName("Validator successfully blocks forbidden SQL injection keywords and comments")
    void testValidatorBlocksAttacks() {
        // Comment delimiter attack
        assertThatThrownBy(() -> validator.validateInput("Show expenses -- drop table users"))
                .isInstanceOf(SecurityException.class);

        // Semicolon multi-query injection
        assertThatThrownBy(() -> validator.validateInput("SELECT * FROM expenses; DELETE FROM users;"))
                .isInstanceOf(SecurityException.class);

        // Forbidden DDL
        assertThatThrownBy(() -> validator.validateInput("DROP TABLE expenses"))
                .isInstanceOf(SecurityException.class);

        // Safe query passes
        validator.validateInput("How much did I spend on Food last month?");
    }

    @Test
    @DisplayName("Planner converts text to structured QueryPlan AST")
    void testPlannerHeuristics() {
        QueryPlan plan = planner.planWithHeuristics("How much did I spend on Food last month?");
        assertThat(plan.aggregationType()).isEqualTo(QueryPlan.AggregationType.SUM);
        assertThat(plan.categoryName()).isEqualTo("Food");
        assertThat(plan.startDate()).isNotNull();
        assertThat(plan.endDate()).isNotNull();

        QueryPlan countPlan = planner.planWithHeuristics("How many transactions at Amazon this month?");
        assertThat(countPlan.aggregationType()).isEqualTo(QueryPlan.AggregationType.COUNT);
        assertThat(countPlan.vendor()).isEqualTo("Amazon");
    }
}

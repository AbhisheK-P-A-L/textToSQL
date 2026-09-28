package com.example.expense_tracker.repository;

import com.example.expense_tracker.entity.Expense;
import com.example.expense_tracker.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
public class TenantIsolationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Test
    @DisplayName("Queries with explicit userId return ONLY rows belonging to that user")
    public void testTenantIsolationBetweenUsers() {
        // Seed User A
        User userA = User.builder()
                .username("user_a")
                .email("usera@example.com")
                .password("passwordA")
                .role("ROLE_USER")
                .build();
        userA = userRepository.save(userA);

        // Seed User B
        User userB = User.builder()
                .username("user_b")
                .email("userb@example.com")
                .password("passwordB")
                .role("ROLE_USER")
                .build();
        userB = userRepository.save(userB);

        // Save Expense for User A
        Expense expA = Expense.builder()
                .title("Coffee A")
                .amount(new BigDecimal("4.50"))
                .date(LocalDate.now())
                .user(userA)
                .build();
        expenseRepository.save(expA);

        // Save Expense for User B
        Expense expB = Expense.builder()
                .title("Groceries B")
                .amount(new BigDecimal("50.00"))
                .date(LocalDate.now())
                .user(userB)
                .build();
        expenseRepository.save(expB);

        // Verify User A query returns only User A's expense
        List<Expense> userAExpenses = expenseRepository.findByUserId(userA.getId());
        assertEquals(1, userAExpenses.size());
        assertEquals("Coffee A", userAExpenses.get(0).getTitle());

        // Verify User B query returns only User B's expense
        List<Expense> userBExpenses = expenseRepository.findByUserId(userB.getId());
        assertEquals(1, userBExpenses.size());
        assertEquals("Groceries B", userBExpenses.get(0).getTitle());
    }
}

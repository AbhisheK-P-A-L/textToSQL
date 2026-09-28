package com.example.expense_tracker.repository;

import com.example.expense_tracker.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    List<Expense> findByUserId(UUID userId);

    List<Expense> findByUserIdAndDateGreaterThanEqual(UUID userId, LocalDate since);

    @Query("SELECT coalesce(c.name, 'Uncategorized'), SUM(e.amount), COUNT(e) " +
           "FROM Expense e LEFT JOIN e.category c " +
           "WHERE e.user.id = :userId AND e.date BETWEEN :startDate AND :endDate " +
           "GROUP BY coalesce(c.name, 'Uncategorized')")
    List<Object[]> getCategoryBreakdown(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT coalesce(p.name, 'Unspecified'), SUM(e.amount), COUNT(e) " +
           "FROM Expense e LEFT JOIN e.paymentMode p " +
           "WHERE e.user.id = :userId AND e.date BETWEEN :startDate AND :endDate " +
           "GROUP BY coalesce(p.name, 'Unspecified')")
    List<Object[]> getPaymentModeBreakdown(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}

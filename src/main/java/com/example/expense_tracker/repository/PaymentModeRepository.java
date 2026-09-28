package com.example.expense_tracker.repository;

import com.example.expense_tracker.entity.PaymentMode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentModeRepository extends JpaRepository<PaymentMode, Long> {
    List<PaymentMode> findByUserIdOrUserIsNull(UUID userId);
    List<PaymentMode> findByUserId(UUID userId);
}

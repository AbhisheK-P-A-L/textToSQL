package com.example.expense_tracker.service;

import com.example.expense_tracker.dto.PaymentModeRequest;
import com.example.expense_tracker.dto.PaymentModeResponse;
import com.example.expense_tracker.entity.PaymentMode;
import com.example.expense_tracker.entity.User;
import com.example.expense_tracker.repository.PaymentModeRepository;
import com.example.expense_tracker.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PaymentModeService {

    private final PaymentModeRepository paymentModeRepository;
    private final UserRepository userRepository;

    @Autowired
    public PaymentModeService(PaymentModeRepository paymentModeRepository, UserRepository userRepository) {
        this.paymentModeRepository = paymentModeRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<PaymentModeResponse> getPaymentModesForUser(String username) {
        User user = getUserByUsername(username);
        List<PaymentMode> paymentModes = paymentModeRepository.findByUserIdOrUserIsNull(user.getId());
        return paymentModes.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public PaymentModeResponse createPaymentMode(PaymentModeRequest request, String username) {
        User user = getUserByUsername(username);

        PaymentMode paymentMode = PaymentMode.builder()
                .name(request.getName())
                .description(request.getDescription())
                .user(user)
                .build();

        PaymentMode savedPaymentMode = paymentModeRepository.save(paymentMode);
        return mapToResponse(savedPaymentMode);
    }

    private User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found with username: " + username));
    }

    private PaymentModeResponse mapToResponse(PaymentMode paymentMode) {
        return PaymentModeResponse.builder()
                .id(paymentMode.getId())
                .name(paymentMode.getName())
                .description(paymentMode.getDescription())
                .isDefault(paymentMode.getUser() == null)
                .build();
    }
}

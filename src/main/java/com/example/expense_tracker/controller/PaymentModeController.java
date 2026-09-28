package com.example.expense_tracker.controller;

import com.example.expense_tracker.dto.PaymentModeRequest;
import com.example.expense_tracker.dto.PaymentModeResponse;
import com.example.expense_tracker.service.PaymentModeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payment-modes")
public class PaymentModeController {

    private final PaymentModeService paymentModeService;

    @Autowired
    public PaymentModeController(PaymentModeService paymentModeService) {
        this.paymentModeService = paymentModeService;
    }

    @GetMapping
    public ResponseEntity<List<PaymentModeResponse>> getAllPaymentModes(Authentication authentication) {
        List<PaymentModeResponse> paymentModes = paymentModeService.getPaymentModesForUser(authentication.getName());
        return ResponseEntity.ok(paymentModes);
    }

    @PostMapping
    public ResponseEntity<PaymentModeResponse> createPaymentMode(@Valid @RequestBody PaymentModeRequest request,
                                                                 Authentication authentication) {
        PaymentModeResponse response = paymentModeService.createPaymentMode(request, authentication.getName());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}

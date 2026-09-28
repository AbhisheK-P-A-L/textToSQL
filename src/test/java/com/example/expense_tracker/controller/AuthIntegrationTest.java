package com.example.expense_tracker.controller;

import com.example.expense_tracker.dto.AuthResponse;
import com.example.expense_tracker.dto.LoginRequest;
import com.example.expense_tracker.dto.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class AuthIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Should successfully register a new user and login to get JWT token")
    public void testUserRegistrationAndLogin() {
        // 1. Register User
        RegisterRequest registerReq = new RegisterRequest();
        registerReq.setUsername("jwt_test_user");
        registerReq.setEmail("jwt_user@example.com");
        registerReq.setPassword("password123");

        ResponseEntity<AuthResponse> registerResp = restTemplate.postForEntity(
                "/api/v1/auth/register",
                registerReq,
                AuthResponse.class
        );

        assertEquals(HttpStatus.CREATED, registerResp.getStatusCode());
        assertNotNull(registerResp.getBody());
        assertNotNull(registerResp.getBody().getToken());
        assertEquals("jwt_test_user", registerResp.getBody().getUsername());

        // 2. Login User
        LoginRequest loginReq = new LoginRequest();
        loginReq.setUsername("jwt_test_user");
        loginReq.setPassword("password123");

        ResponseEntity<AuthResponse> loginResp = restTemplate.postForEntity(
                "/api/v1/auth/login",
                loginReq,
                AuthResponse.class
        );

        assertEquals(HttpStatus.OK, loginResp.getStatusCode());
        assertNotNull(loginResp.getBody());
        assertNotNull(loginResp.getBody().getToken());
        assertEquals("Bearer", loginResp.getBody().getTokenType());
    }
}

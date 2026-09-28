package com.example.expense_tracker.controller;

import com.example.expense_tracker.dto.CategoryRequest;
import com.example.expense_tracker.dto.CategoryResponse;
import com.example.expense_tracker.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    @Autowired
    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getAllCategories(Authentication authentication) {
        List<CategoryResponse> categories = categoryService.getCategoriesForUser(authentication.getName());
        return ResponseEntity.ok(categories);
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request,
                                                           Authentication authentication) {
        CategoryResponse response = categoryService.createCategory(request, authentication.getName());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}

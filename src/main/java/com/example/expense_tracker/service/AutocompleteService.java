package com.example.expense_tracker.service;

import com.example.expense_tracker.repository.CategoryRepository;
import com.example.expense_tracker.repository.ExpenseRepository;
import com.example.expense_tracker.util.Trie;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing Trie-based text autocomplete for vendors, categories, and expense descriptions.
 * Isolates Tries per tenant (userId) and maintains a shared global vendor base.
 */
@Service
public class AutocompleteService {

    private static final Logger log = LoggerFactory.getLogger(AutocompleteService.class);

    private final Trie globalVendorTrie = new Trie();
    private final Map<UUID, Trie> userVendorTries = new ConcurrentHashMap<>();
    private final Map<UUID, Trie> userCategoryTries = new ConcurrentHashMap<>();

    private final CategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;

    public AutocompleteService(CategoryRepository categoryRepository, ExpenseRepository expenseRepository) {
        this.categoryRepository = categoryRepository;
        this.expenseRepository = expenseRepository;
    }

    @PostConstruct
    public void initializeGlobalVendors() {
        // Seed default popular vendors & merchants
        List<String> commonVendors = List.of(
                "Amazon", "Amazon Prime", "Apple Store", "Apple Services", "Adobe",
                "Best Buy", "Costco", "CVS Pharmacy", "Coursera",
                "DoorDash", "Dominos", "Delta Airlines", "Dropbox",
                "Ebay", "Electricity Board", "Eventbrite",
                "Flipkart", "Freshworks",
                "Google Play", "Google Cloud", "GitHub", "Getir", "Grofers",
                "Home Depot", "HBO Max", "Hulu",
                "Instacart", "IKEA",
                "Jira", "JetBrains",
                "Kroger",
                "Lyft", "LinkedIn Premium",
                "McDonalds", "Microsoft Azure", "Medium", "Myntra",
                "Netflix", "Nike", "Notion", "NordVPN",
                "OpenAI", "Oracle Cloud",
                "PayPal", "PlayStation Store", "Pizza Hut",
                "QuickBooks",
                "Razorpay", "Redbus",
                "Spotify", "Starbucks", "Steam", "Subway", "Swiggy",
                "Target", "Trader Joe's", "Tesla Supercharger",
                "Uber", "Uber Eats", "Udemy", "Upwork",
                "Venmo", "Verizon",
                "Walmart", "Walgreens", "Whole Foods", "WordPress",
                "YouTube Premium", "Zomato", "Zara", "Zoom"
        );
        commonVendors.forEach(globalVendorTrie::insert);
        log.info("Initialized global vendor Trie with {} merchants", commonVendors.size());
    }

    public void indexExpense(UUID userId, String vendor, String description, String categoryName) {
        if (userId == null) return;

        if (vendor != null && !vendor.isBlank()) {
            userVendorTries.computeIfAbsent(userId, k -> new Trie()).insert(vendor);
            globalVendorTrie.insert(vendor);
        }
        if (description != null && !description.isBlank()) {
            userVendorTries.computeIfAbsent(userId, k -> new Trie()).insert(description);
        }
        if (categoryName != null && !categoryName.isBlank()) {
            userCategoryTries.computeIfAbsent(userId, k -> new Trie()).insert(categoryName);
        }
    }

    public List<String> suggestVendors(UUID userId, String prefix, int limit) {
        Set<String> uniqueSuggestions = new LinkedHashSet<>();

        // 1. User-specific frequent vendors first
        if (userId != null && userVendorTries.containsKey(userId)) {
            uniqueSuggestions.addAll(userVendorTries.get(userId).autocomplete(prefix, limit));
        }

        // 2. Global fallback suggestions to fill remaining slots
        int remaining = limit - uniqueSuggestions.size();
        if (remaining > 0) {
            uniqueSuggestions.addAll(globalVendorTrie.autocomplete(prefix, remaining));
        }

        return new ArrayList<>(uniqueSuggestions).subList(0, Math.min(limit, uniqueSuggestions.size()));
    }

    public List<String> suggestCategories(UUID userId, String prefix, int limit) {
        if (userId != null && userCategoryTries.containsKey(userId)) {
            return userCategoryTries.get(userId).autocomplete(prefix, limit);
        }
        // Fallback to database category match
        return categoryRepository.findByUserIdOrUserIsNull(userId).stream()
                .map(c -> c.getName())
                .filter(name -> name.toLowerCase().startsWith(prefix.toLowerCase().trim()))
                .limit(limit)
                .toList();
    }
}

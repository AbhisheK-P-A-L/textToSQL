package com.example.expense_tracker.service;

import com.example.expense_tracker.dto.NLQueryRequest;
import com.example.expense_tracker.dto.NLQueryResult;
import com.example.expense_tracker.entity.User;
import com.example.expense_tracker.pipeline.NLQueryExecutor;
import com.example.expense_tracker.pipeline.NLQueryPlanner;
import com.example.expense_tracker.pipeline.NLQueryValidator;
import com.example.expense_tracker.pipeline.QueryPlan;
import com.example.expense_tracker.repository.UserRepository;
import com.example.expense_tracker.util.LruCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrator service implementing the 3-stage Validator-Planner-Executor pipeline
 * for natural language spending queries with tenant isolation and O(1) in-memory query caching.
 */
@Service
public class NLQueryPipelineService {

    private static final Logger log = LoggerFactory.getLogger(NLQueryPipelineService.class);

    private final NLQueryValidator validator;
    private final NLQueryPlanner planner;
    private final NLQueryExecutor executor;
    private final UserRepository userRepository;

    // Local O(1) LRU query result cache (Capacity: 200 entries)
    private final LruCache<String, NLQueryResult> queryCache = new LruCache<>(200);

    public NLQueryPipelineService(NLQueryValidator validator,
                                  NLQueryPlanner planner,
                                  NLQueryExecutor executor,
                                  UserRepository userRepository) {
        this.validator = validator;
        this.planner = planner;
        this.executor = executor;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public NLQueryResult processQuery(String username, NLQueryRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        String rawQuery = request.query();
        String cacheKey = user.getId() + ":" + rawQuery.trim().toLowerCase();

        // 0. Check in-memory O(1) LRU cache
        NLQueryResult cached = queryCache.get(cacheKey);
        if (cached != null) {
            log.debug("Cache hit for NL query: {}", rawQuery);
            return cached;
        }

        // 1. Validator Stage: AST security & injection prevention
        validator.validateInput(rawQuery);

        // 2. Planner Stage: Convert text into structured QueryPlan AST
        QueryPlan plan = planner.planQuery(rawQuery);

        // 3. Executor Stage: Injects user tenant scope & executes safe parameterized JPQL
        NLQueryResult result = executor.executePlan(user.getId(), rawQuery, plan);

        // Save to cache
        queryCache.put(cacheKey, result);
        return result;
    }

    public void invalidateCache() {
        queryCache.clear();
    }
}

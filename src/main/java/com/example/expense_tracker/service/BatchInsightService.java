package com.example.expense_tracker.service;

import com.example.expense_tracker.entity.User;
import com.example.expense_tracker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service managing bounded scheduled batch insight generation.
 * Enforces:
 *  1. Chunked batch processing (fixed users per page/batch) to bound memory.
 *  2. Rate limiting (delays/token throttling) to prevent LLM API quota exhaustion.
 *  3. Graceful failure isolation so a single tenant failure does not abort the entire batch.
 */
@Service
public class BatchInsightService {

    private static final Logger log = LoggerFactory.getLogger(BatchInsightService.class);

    private static final int BATCH_SIZE = 20; // 20 users per batch chunk
    private static final long RATE_LIMIT_DELAY_MS = 250; // 250ms throttle between LLM API calls (4 req/sec max)

    private final UserRepository userRepository;
    private final InsightsService insightsService;

    public BatchInsightService(UserRepository userRepository, InsightsService insightsService) {
        this.userRepository = userRepository;
        this.insightsService = insightsService;
    }

    public BatchSummary runBatchInsightGeneration(int days) {
        long startTime = System.currentTimeMillis();
        AtomicInteger totalProcessed = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        int pageNumber = 0;
        Page<User> userPage;

        log.info("Starting bounded batch insight generation for {} days horizon (batch size: {})", days, BATCH_SIZE);

        do {
            userPage = userRepository.findAll(PageRequest.of(pageNumber, BATCH_SIZE));
            log.info("Processing batch page {} ({} users in chunk)", pageNumber, userPage.getNumberOfElements());

            for (User user : userPage.getContent()) {
                totalProcessed.incrementAndGet();
                try {
                    // Apply rate-limiting throttle to bound LLM API load
                    TimeUnit.MILLISECONDS.sleep(RATE_LIMIT_DELAY_MS);

                    // Generate or refresh user insights
                    insightsService.generateInsights(user.getUsername(), days);
                    successCount.incrementAndGet();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("Batch processing interrupted", ie);
                    break;
                } catch (Exception e) {
                    // Graceful failure handling: isolate error to individual user
                    failureCount.incrementAndGet();
                    log.warn("Failed to generate insight for user {} (User ID: {}): {}",
                            user.getUsername(), user.getId(), e.getMessage());
                }
            }

            pageNumber++;
        } while (userPage.hasNext());

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("Completed batch insight run in {} ms: total={}, success={}, failures={}",
                durationMs, totalProcessed.get(), successCount.get(), failureCount.get());

        return new BatchSummary(totalProcessed.get(), successCount.get(), failureCount.get(), durationMs);
    }

    public record BatchSummary(int totalProcessed, int successCount, int failureCount, long durationMs) {}
}

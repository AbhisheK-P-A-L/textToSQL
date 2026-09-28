package com.example.expense_tracker.scheduler;

import com.example.expense_tracker.service.BatchInsightService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BatchInsightScheduler {

    private static final Logger log = LoggerFactory.getLogger(BatchInsightScheduler.class);

    private final BatchInsightService batchInsightService;

    public BatchInsightScheduler(BatchInsightService batchInsightService) {
        this.batchInsightService = batchInsightService;
    }

    /**
     * Runs daily at 02:00 AM to pre-generate spending insights for all active users.
     */
    @Scheduled(cron = "${app.scheduler.insights.cron:0 0 2 * * *}")
    public void scheduleDailyInsights() {
        log.info("Executing scheduled batch insight generation job...");
        try {
            BatchInsightService.BatchSummary summary = batchInsightService.runBatchInsightGeneration(30);
            log.info("Scheduled batch completed successfully: {}/{} users processed, {} failed in {} ms",
                    summary.successCount(), summary.totalProcessed(), summary.failureCount(), summary.durationMs());
        } catch (Exception e) {
            log.error("Scheduled batch insight job encountered an unhandled error", e);
        }
    }
}

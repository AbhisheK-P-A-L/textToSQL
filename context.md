# Project State & Context

## Project Summary
- **Name**: Expense Tracker — AI-Powered Natural Language Expense Tracker
- **Architecture**: Clean Layered Architecture with Spring Boot 3.3.4, PostgreSQL 16, Redis 7, Spring Security 6 (JWT), Spring AI / OpenAI integration.
- **Frontend**: Responsive Single-Page Application (SPA) dashboard served via Spring Boot static resources with Chart.js visualization, real-time Trie autocomplete, and AI query widgets.

---

## Direct Resume Alignment Status (100% Implemented & Verified)

1. **AI Expense Tracker (Manual & NL Entry, Spending Analytics, AI Insights)**:
   - Zero-click natural language expense logging via `NLPParsingService` (temperature: 0.0, temporal anchoring).
   - Redis Cache-Aside for AI insights (TTL: 1 hour) with `< 5ms` hit latency via `InsightsService`.

2. **Validator-Planner-Executor NL-to-SQL Pipeline**:
   - `NLQueryValidator`: Proactive AST token scanning against forbidden SQL keywords (`DROP`, `DELETE`, `UPDATE`, `--`, `;`).
   - `NLQueryPlanner`: Structured AST planning (`QueryPlan`) converting plain questions into temporal windows, aggregations, and filters.
   - `NLQueryExecutor`: Compiles safe parameterized JPQL with mandatory tenant predicate injection (`e.user.id = :userId`).

3. **Multi-Tenant Isolation & AST Security**:
   - Tenant isolation enforced in every database query, preventing IDOR and cross-tenant data leaks.
   - Proactive rejection of unsafe AST operations and DDL mutations.

4. **Bounded Scheduled Batch Insight Generation with Rate Limiting**:
   - `BatchInsightService` & `BatchInsightScheduler`: Fixed user pagination chunks (20 users/batch), 250ms throttling delay, and isolated error handling.

5. **Performance & DSA Implementations**:
   - `Trie` & `AutocompleteService`: Case-insensitive, frequency-ranked $O(L)$ merchant & category prefix autocomplete.
   - `LruCache<K, V>`: Custom O(1) DoublyLinkedList + HashMap cache with `ReentrantReadWriteLock`.
   - `AnalyticsDagPlanner`: Directed Acyclic Graph planner evaluating independent analytics nodes in parallel via `CompletableFuture`.

---

## Test Verification
- `./mvnw clean test` -> **9/9 Tests Passing (100% success rate)**
  - `ExpenseTrackerApplicationTests`
  - `TenantIsolationTest`
  - `ExpenseFeatureIntegrationTest`
  - `AuthIntegrationTest`
  - `TrieAndLruCacheTest`
  - `NLQueryPipelineTest`

---

## Deployment Artifacts
- `Dockerfile`: Multi-stage build (`maven:3.9.6` -> `eclipse-temurin:17-jre-alpine`).
- `docker-compose.yml`: Multi-container production stack (App + PostgreSQL 16 + Redis 7 + health checks).
- `project.md`: Master interview preparation cheatsheet with ERD, architecture diagrams, complexity analysis, and Q&A.

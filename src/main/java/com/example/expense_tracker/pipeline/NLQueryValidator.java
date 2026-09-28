package com.example.expense_tracker.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Stage 1 of the NL-to-SQL Pipeline: AST & Token Validator.
 * Blocks unsafe SQL operations, DDL/DML mutation keywords, comment-based injection attacks,
 * and multi-statement execution vectors before query planning begins.
 */
@Component
public class NLQueryValidator {

    private static final Logger log = LoggerFactory.getLogger(NLQueryValidator.class);

    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "DROP", "DELETE", "UPDATE", "INSERT", "ALTER", "TRUNCATE", "CREATE",
            "REPLACE", "MERGE", "GRANT", "REVOKE", "EXEC", "EXECUTE", "SHUTDOWN",
            "XP_CMDSHELL", "INFORMATION_SCHEMA", "PG_CATALOG", "PG_USER", "PG_SHADOW",
            "INTO OUTFILE", "INTO DUMPFILE", "LOAD DATA", "SLEEP", "BENCHMARK",
            "UNION SELECT", "UNION ALL SELECT"
    );

    private static final Pattern COMMENT_PATTERN = Pattern.compile("(--|/\\*|\\*/|#|;)");

    public void validateInput(String rawQuery) {
        if (rawQuery == null || rawQuery.trim().isEmpty()) {
            throw new IllegalArgumentException("Natural language query cannot be empty");
        }

        if (rawQuery.length() > 500) {
            throw new IllegalArgumentException("Natural language query exceeds maximum length of 500 characters");
        }

        String normalized = rawQuery.toUpperCase();

        // Check for SQL injection comment tokens and multi-statement semicolons
        if (COMMENT_PATTERN.matcher(rawQuery).find()) {
            log.warn("Blocked query containing suspicious SQL comment or termination characters: {}", rawQuery);
            throw new SecurityException("Query contains prohibited SQL meta-characters or delimiters.");
        }

        // Check for forbidden DDL / DML / privilege escalation keywords
        for (String keyword : FORBIDDEN_KEYWORDS) {
            // Check as whole word or phrase
            if (Pattern.compile("\\b" + Pattern.quote(keyword) + "\\b", Pattern.CASE_INSENSITIVE).matcher(normalized).find()) {
                log.warn("Blocked potentially dangerous SQL AST keyword '{}' in query: {}", keyword, rawQuery);
                throw new SecurityException("Forbidden query operation detected: " + keyword + " is not permitted.");
            }
        }
    }
}

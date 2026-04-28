package com.bipros.analytics.infrastructure.clickhouse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * Runs at boot. Reads every {@code clickhouse-schema/*.sql} file in alphabetical order and
 * executes its statements against the writer DataSource. Every statement is expected to be
 * idempotent ({@code CREATE TABLE IF NOT EXISTS} / {@code CREATE OR REPLACE VIEW}) so re-runs
 * across restarts are safe.
 *
 * <p>Gated by {@code bipros.analytics.etl.enabled} — same flag as the orchestrator. Disabling
 * the flag suppresses both bootstrap and ETL on a given replica.
 */
@Component
@ConditionalOnProperty(name = "bipros.analytics.etl.enabled", havingValue = "true", matchIfMissing = true)
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@Slf4j
public class ClickHouseSchemaBootstrapper implements ApplicationRunner {

    private static final String LOCATION = "classpath:clickhouse-schema/*.sql";

    private final JdbcTemplate ch;

    public ClickHouseSchemaBootstrapper(@Qualifier("clickhouseJdbcTemplate") JdbcTemplate ch) {
        this.ch = ch;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources(LOCATION);
        Resource[] sorted = Arrays.stream(resources)
                .sorted(Comparator.comparing(r -> safeFilename(r)))
                .toArray(Resource[]::new);

        if (sorted.length == 0) {
            log.warn("ClickHouseSchemaBootstrapper found no DDL files at {}", LOCATION);
            return;
        }

        for (Resource r : sorted) {
            String filename = safeFilename(r);
            String content;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(r.getInputStream(), StandardCharsets.UTF_8))) {
                content = reader.lines().collect(Collectors.joining("\n"));
            }
            int executed = 0;
            for (String stmt : splitStatements(content)) {
                if (stmt.isBlank()) continue;
                try {
                    ch.execute(stmt);
                    executed++;
                } catch (RuntimeException ex) {
                    log.error("Failed executing DDL from {} (statement starting: {})",
                            filename, preview(stmt), ex);
                    throw ex;
                }
            }
            log.info("ClickHouseSchemaBootstrapper applied {} statements from {}", executed, filename);
        }
        log.info("ClickHouseSchemaBootstrapper applied {} files", sorted.length);
    }

    private static String safeFilename(Resource r) {
        String n = r.getFilename();
        return n == null ? "" : n;
    }

    /**
     * Naive splitter: splits on lines ending in {@code ;} (outside of quoted strings is not
     * detected — none of our DDL embeds semicolons inside strings, so this is fine). Comment
     * lines starting with {@code --} are stripped.
     */
    static java.util.List<String> splitStatements(String sql) {
        StringBuilder buf = new StringBuilder();
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String raw : sql.split("\n", -1)) {
            String line = raw;
            int comment = line.indexOf("--");
            if (comment >= 0) line = line.substring(0, comment);
            buf.append(line).append('\n');
            if (line.stripTrailing().endsWith(";")) {
                String s = buf.toString();
                int semi = s.lastIndexOf(';');
                out.add(s.substring(0, semi).trim());
                buf.setLength(0);
            }
        }
        String tail = buf.toString().trim();
        if (!tail.isEmpty()) out.add(tail);
        return out;
    }

    private static String preview(String s) {
        String oneLine = s.replace('\n', ' ').strip();
        return oneLine.length() <= 80 ? oneLine : oneLine.substring(0, 80) + "…";
    }
}

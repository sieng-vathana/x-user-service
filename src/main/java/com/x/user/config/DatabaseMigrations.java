package com.x.user.config;

import com.x.redis.cache.CacheNames;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Brings the permission data up to date on every start, so a new database needs no manual SQL.
 *
 * <p>Hibernate owns the table structure ({@code ddl-auto: update}), so the scripts in
 * {@code db/migration} only add rows. Spring Boot would run Flyway before Hibernate creates the
 * tables, so its auto-run is off ({@code spring.flyway.enabled: false}) and Flyway runs here,
 * after the entity manager factory is ready.
 */
@Component
@DependsOn("entityManagerFactory")
@RequiredArgsConstructor
public class DatabaseMigrations {

    /** Each service keeps its own history table because they all share one schema. */
    static final String HISTORY_TABLE = "flyway_schema_history_user";

    /** Keep business-owner permissions separate from platform-wide administration. */
    static final String REMOVE_PLATFORM_ADMIN_FROM_BUSINESS_OWNERS = """
            DELETE rp
            FROM role_permissions rp
            INNER JOIN roles r ON r.id = rp.role_id
            INNER JOIN permissions p ON p.id = rp.permission_id
            WHERE UPPER(r.role_code) = 'OWNER'
              AND LOWER(p.permission_code) = 'platform:admin'
            """;

    static final String GRANT_ROLE_PERMISSIONS = """
            INSERT INTO role_permissions (role_id, permission_id)
            SELECT r.id, p.id
            FROM roles r
            CROSS JOIN permissions p
            WHERE (UPPER(r.role_code) = 'PLATFORM_OWNER'
                   OR (UPPER(r.role_code) = 'OWNER'
                       AND LOWER(p.permission_code) <> 'platform:admin'))
              AND NOT EXISTS (
                  SELECT 1 FROM role_permissions rp
                  WHERE rp.role_id = r.id AND rp.permission_id = p.id)
            """;

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrations.class);

    private final DataSource dataSource;
    private final ObjectProvider<CacheManager> cacheManager;

    @PostConstruct
    void migrate() {
        MigrateResult result = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .table(HISTORY_TABLE)
                // Existing databases already hold tables, so their history starts at version 0.
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        int removedPlatformGrants = jdbc.update(REMOVE_PLATFORM_ADMIN_FROM_BUSINESS_OWNERS);
        int roleGrants = jdbc.update(GRANT_ROLE_PERMISSIONS);
        log.info("Database migrations: {} applied, {} owner permission grants added",
                result.migrationsExecuted, roleGrants);
        if (result.migrationsExecuted > 0 || removedPlatformGrants > 0 || roleGrants > 0) {
            clearCachedPermissions();
        }
    }

    /** Login lookups cache permission codes; dropping them lets new grants apply on the next sign-in or refresh. */
    private void clearCachedPermissions() {
        cacheManager.ifAvailable(manager -> {
            Cache cache = manager.getCache(CacheNames.USER_BY_USERNAME);
            if (cache == null) {
                return;
            }
            try {
                cache.clear();
            } catch (RuntimeException ex) {
                log.warn("Could not clear cached permissions; they expire on their own: {}", ex.getMessage());
            }
        });
    }
}

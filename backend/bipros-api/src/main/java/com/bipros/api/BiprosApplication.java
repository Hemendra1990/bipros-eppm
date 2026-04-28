package com.bipros.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * <h3>Why {@code RedisRepositoriesAutoConfiguration} is excluded</h3>
 * Phase 4 added {@code spring-boot-starter-data-redis} for the analytics
 * rate-limiter. We use Redis only as a key-value store via
 * {@code StringRedisTemplate} + a Lua script — no {@code @RedisHash} entities.
 *
 * <p>Without this exclusion, Spring Data detects two modules (JPA + Redis) on
 * the classpath and switches into "strict repository configuration mode". In
 * strict mode the JPA {@code entityManagerFactory} bean is not auto-wired,
 * which breaks {@code UserRepository} → {@code CustomUserDetailsService} →
 * {@code JwtAuthenticationFilter} at boot. Excluding the Redis-repositories
 * auto-config (the {@code RedisAutoConfiguration} that wires the Lettuce
 * client itself stays intact) keeps Spring Data in single-module mode.
 */
@SpringBootApplication(
        scanBasePackages = "com.bipros",
        exclude = { RedisRepositoriesAutoConfiguration.class }
)
@EntityScan(basePackages = "com.bipros")
@EnableJpaRepositories(basePackages = "com.bipros")
@EnableJpaAuditing
@EnableMethodSecurity
@EnableScheduling
@EnableAsync
public class BiprosApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(BiprosApplication.class);
        app.setAllowBeanDefinitionOverriding(true);
        app.run(args);
    }
}

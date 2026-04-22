package com.bipros.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootApplication(scanBasePackages = "com.bipros")
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

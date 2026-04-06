package com.bipros.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

// CORS is configured in SecurityConfig via Spring Security's CorsConfigurationSource.
// This duplicate bean has been removed to avoid conflicting CORS configurations.
// @Configuration
public class CorsConfig {
}

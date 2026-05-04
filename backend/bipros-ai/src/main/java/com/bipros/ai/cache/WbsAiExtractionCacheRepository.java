package com.bipros.ai.cache;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WbsAiExtractionCacheRepository
        extends JpaRepository<WbsAiExtractionCache, WbsAiExtractionCache.CacheKey> {
}

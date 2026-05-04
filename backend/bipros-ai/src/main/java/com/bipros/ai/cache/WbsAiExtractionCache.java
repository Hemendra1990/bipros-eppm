package com.bipros.ai.cache;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "wbs_ai_extraction_cache", schema = "ai")
@Getter
@Setter
public class WbsAiExtractionCache {

    @EmbeddedId
    private CacheKey id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", columnDefinition = "jsonb", nullable = false)
    private String resultJson;

    @Column(name = "hit_count", nullable = false)
    private int hitCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_hit_at")
    private Instant lastHitAt;

    @Embeddable
    @Getter
    @Setter
    public static class CacheKey implements Serializable {
        @Column(name = "file_sha256", length = 64, nullable = false)
        private String fileSha256;

        @Column(name = "prompt_version", nullable = false)
        private int promptVersion;

        @Column(name = "model_family", length = 50, nullable = false)
        private String modelFamily;

        public CacheKey() {}
        public CacheKey(String sha, int promptVersion, String modelFamily) {
            this.fileSha256 = sha; this.promptVersion = promptVersion; this.modelFamily = modelFamily;
        }
        @Override public int hashCode() {
            return java.util.Objects.hash(fileSha256, promptVersion, modelFamily);
        }
        @Override public boolean equals(Object o) {
            if (!(o instanceof CacheKey k)) return false;
            return java.util.Objects.equals(fileSha256, k.fileSha256)
                    && promptVersion == k.promptVersion
                    && java.util.Objects.equals(modelFamily, k.modelFamily);
        }
    }
}

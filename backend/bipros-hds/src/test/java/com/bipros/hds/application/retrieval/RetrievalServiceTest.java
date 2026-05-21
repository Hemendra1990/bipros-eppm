package com.bipros.hds.application.retrieval;

import com.bipros.hds.config.HdsProperties;
import com.bipros.hds.domain.HdsVersion;
import com.bipros.hds.domain.repo.HdsQueryLogRepository;
import com.bipros.hds.domain.repo.HdsVersionRepository;
import com.bipros.hds.infrastructure.embedding.EmbeddingClient;
import com.bipros.hds.infrastructure.reranker.NoopReranker;
import com.bipros.hds.infrastructure.retrieval.HybridSearchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetrievalServiceTest {

    @Test
    void safeFailsWhenNoChunksFound() {
        var props = new HdsProperties();
        props.getReranker().setTopK(10);
        props.getRetrieval().setMaxChunksPerQuery(20);
        props.getRetrieval().setMaxRounds(1);
        props.getRetrieval().setSimilarityFloor(0.3);
        props.getRetrieval().setVectorTopK(50);
        props.getRetrieval().setBm25TopK(50);
        props.getRetrieval().setCacheTtlSeconds(60);
        props.getVerifier().setMaxRetries(1);

        var versionRepo = mock(HdsVersionRepository.class);
        var logRepo = mock(HdsQueryLogRepository.class);
        var hybrid = mock(HybridSearchRepository.class);
        var embed = new EmbeddingClient() {
            @Override public List<float[]> embedBatch(List<String> inputs) {
                return inputs.stream().map(s -> new float[]{0f}).toList();
            }
            @Override public int dim() { return 1; }
        };
        var llm = new StubLlmGateway();
        var redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(mock(ValueOperations.class));
        when(redis.opsForSet()).thenReturn(mock(SetOperations.class));
        var cache = new QueryCache(redis);

        var version = new HdsVersion();
        UUID vid = UUID.randomUUID();
        version.setId(vid);
        version.setVersionLabel("Rev 1");
        when(versionRepo.findAllById(List.of(vid))).thenReturn(List.of(version));
        when(hybrid.searchByEmbedding(any(), any(), anyDouble(), anyInt())).thenReturn(List.of());
        when(hybrid.searchByKeyword(anyString(), any(), anyInt())).thenReturn(List.of());

        var svc = new RetrievalService(props, versionRepo, logRepo, hybrid, embed, new NoopReranker(), llm, cache);
        var ans = svc.answer("anything", List.of(vid), 1, UUID.randomUUID(), UUID.randomUUID(), null);
        assertThat(ans.answer()).contains("I don't see that");
        assertThat(ans.citations()).isEmpty();
    }
}

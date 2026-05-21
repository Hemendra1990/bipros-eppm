package com.bipros.hds.application.retrieval;

import com.bipros.hds.config.HdsProperties;
import com.bipros.hds.domain.HdsQueryLog;
import com.bipros.hds.domain.HdsVersion;
import com.bipros.hds.domain.repo.HdsQueryLogRepository;
import com.bipros.hds.domain.repo.HdsVersionRepository;
import com.bipros.hds.infrastructure.embedding.EmbeddingClient;
import com.bipros.hds.infrastructure.reranker.Reranker;
import com.bipros.hds.infrastructure.retrieval.HybridSearchRepository;
import com.bipros.hds.infrastructure.retrieval.ReciprocalRankFusion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class RetrievalService {

    private static final String SAFE_FAIL = "I don't see that in the selected HDS documents.";

    private final HdsProperties props;
    private final HdsVersionRepository versionRepo;
    private final HdsQueryLogRepository logRepo;
    private final HybridSearchRepository hybridRepo;
    private final EmbeddingClient embedClient;
    private final Reranker reranker;
    private final LlmGateway llm;
    private final QueryCache cache;
    private final ObjectMapper om = new ObjectMapper();

    public RetrievalAnswer answer(String question, List<UUID> selectedVersionIds,
                                  int maxRounds, UUID userId, UUID conversationId,
                                  LlmGateway.StreamCallback streamCb) {
        long started = System.currentTimeMillis();

        // Cache lookup
        var cached = cache.get(question, selectedVersionIds);
        if (cached != null) {
            log.info("Cache hit for query: '{}'", question);
            if (streamCb != null) streamCb.onToken(cached.answer());
            return cached;
        }

        // Resolve version labels for the prompt
        List<HdsVersion> versions = versionRepo.findAllById(selectedVersionIds);
        if (versions.isEmpty()) {
            return safeFail(question, selectedVersionIds, started);
        }

        // Phase 1: PLAN
        PlanResult plan = phasePlan(question, versions);

        // Phase 2 + 3 loop
        List<UUID> retrievedIds = new ArrayList<>();
        List<String> followUps = List.of();
        int roundsRun = 0;
        for (int round = 1; round <= maxRounds; round++) {
            List<String> queries = round == 1 ? plan.searchQueries() : followUps;
            var roundIds = phaseRetrieve(queries, selectedVersionIds);
            retrievedIds = dedupe(retrievedIds, roundIds);
            roundsRun = round;

            if (retrievedIds.isEmpty()) {
                return safeFail(question, selectedVersionIds, started);
            }

            var examine = phaseExamine(question, hybridRepo.fetchChunks(retrievedIds));
            if (examine.sufficient() || round == maxRounds) {
                break;
            }
            followUps = examine.followUpQueries();
        }

        // Phase 4: DRAFT
        var chunks = hybridRepo.fetchChunks(retrievedIds);
        if (chunks.size() > props.getRetrieval().getMaxChunksPerQuery()) {
            chunks = chunks.subList(0, props.getRetrieval().getMaxChunksPerQuery());
        }
        Map<String, HybridSearchRepository.ChunkRow> markerToChunk = new LinkedHashMap<>();
        StringBuilder chunkBlock = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            String marker = "c" + (i + 1);
            markerToChunk.put(marker, chunks.get(i));
            chunkBlock.append("[").append(marker).append("] ")
                .append(chunks.get(i).sectionPath()).append(" (p. ")
                .append(chunks.get(i).pageStart()).append(")\n")
                .append(chunks.get(i).content()).append("\n\n");
        }

        String draftUser = "Question: " + question + "\n\nChunks:\n" + chunkBlock;
        String draft = llm.completeStreaming(
            List.of(new LlmGateway.ChatMessage("system", Prompts.DRAFT_SYSTEM),
                    new LlmGateway.ChatMessage("user", draftUser)),
            streamCb);

        // Phase 5: VERIFY (up to maxRetries)
        VerifyResult verify = phaseVerify(draft, markerToChunk);
        int retries = 0;
        while (!verify.passed() && retries < props.getVerifier().getMaxRetries()) {
            String feedback = "Verifier rejected these claims:\n" +
                String.join("\n", verify.issues().stream().map(i -> "- " + i.claim() + " (" + i.explanation() + ")").toList()) +
                "\n\nRewrite the answer using only the chunks provided.";
            draft = llm.completeStreaming(
                List.of(new LlmGateway.ChatMessage("system", Prompts.DRAFT_SYSTEM),
                        new LlmGateway.ChatMessage("user", draftUser + "\n\n" + feedback)),
                streamCb);
            verify = phaseVerify(draft, markerToChunk);
            retries++;
        }

        if (!verify.passed()) {
            draft = SAFE_FAIL;
        }

        // Build citations from markers actually used in draft
        List<Citation> citations = buildCitations(draft, markerToChunk, versions);

        var meta = new LinkedHashMap<String, Object>();
        meta.put("duration_ms", (int) (System.currentTimeMillis() - started));
        meta.put("rounds", roundsRun);

        var answer = new RetrievalAnswer(draft, citations, verify, meta);
        cache.put(question, selectedVersionIds, answer, Duration.ofSeconds(props.getRetrieval().getCacheTtlSeconds()));
        logQuery(userId, conversationId, question, selectedVersionIds, retrievedIds, answer, started);
        return answer;
    }

    private PlanResult phasePlan(String question, List<HdsVersion> versions) {
        String userMsg = "Question: " + question + "\nSelected versions: " +
            versions.stream().map(v -> v.getVersionLabel()).toList();
        String json = llm.completeStructured(
            List.of(new LlmGateway.ChatMessage("system", Prompts.PLAN_SYSTEM),
                    new LlmGateway.ChatMessage("user", userMsg)),
            "plan");
        try {
            JsonNode n = om.readTree(json);
            return new PlanResult(
                n.path("is_compound").asBoolean(false),
                jsonArrayToList(n.path("sub_questions")),
                jsonArrayToList(n.path("search_queries")));
        } catch (Exception e) {
            return new PlanResult(false, List.of(), List.of(question));
        }
    }

    private List<UUID> phaseRetrieve(List<String> queries, List<UUID> selectedVersionIds) {
        if (queries == null || queries.isEmpty()) return List.of();
        List<UUID> dedup = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (String q : queries) {
            float[] qEmb = embedClient.embedBatch(List.of(q)).get(0);
            var dense = hybridRepo.searchByEmbedding(qEmb, selectedVersionIds,
                props.getRetrieval().getSimilarityFloor(),
                props.getRetrieval().getVectorTopK());
            var sparse = hybridRepo.searchByKeyword(q, selectedVersionIds,
                props.getRetrieval().getBm25TopK());
            var fused = ReciprocalRankFusion.fuse(List.of(dense, sparse), 60,
                props.getRetrieval().getVectorTopK());

            List<HybridSearchRepository.ChunkRow> rows = hybridRepo.fetchChunks(fused);
            List<String> texts = rows.stream().map(HybridSearchRepository.ChunkRow::content).toList();
            List<Integer> rerankedIdx = reranker.rerank(q, texts, props.getReranker().getTopK());
            for (int idx : rerankedIdx) {
                UUID id = rows.get(idx).id();
                if (seen.add(id)) dedup.add(id);
            }
        }
        return dedup;
    }

    private ExamineResult phaseExamine(String question, List<HybridSearchRepository.ChunkRow> chunks) {
        StringBuilder ctx = new StringBuilder();
        for (var c : chunks) ctx.append(c.sectionPath()).append("\n").append(c.content()).append("\n\n");
        String json = llm.completeStructured(
            List.of(new LlmGateway.ChatMessage("system", Prompts.EXAMINE_SYSTEM),
                    new LlmGateway.ChatMessage("user", "Question: " + question + "\n\nChunks:\n" + ctx)),
            "examine");
        try {
            JsonNode n = om.readTree(json);
            return new ExamineResult(
                n.path("sufficient").asBoolean(true),
                jsonArrayToList(n.path("follow_up_queries")));
        } catch (Exception e) {
            return new ExamineResult(true, List.of());
        }
    }

    private VerifyResult phaseVerify(String draft, Map<String, HybridSearchRepository.ChunkRow> markerToChunk) {
        StringBuilder ctx = new StringBuilder();
        markerToChunk.forEach((m, c) -> ctx.append("[").append(m).append("] ").append(c.content()).append("\n\n"));
        String json = llm.completeStructured(
            List.of(new LlmGateway.ChatMessage("system", Prompts.VERIFY_SYSTEM),
                    new LlmGateway.ChatMessage("user", "Draft answer:\n" + draft + "\n\nCited chunks:\n" + ctx)),
            "verify");
        try {
            JsonNode n = om.readTree(json);
            boolean passed = n.path("passed").asBoolean(false);
            List<VerifyResult.Issue> issues = new ArrayList<>();
            if (n.has("issues")) {
                for (JsonNode it : n.get("issues")) {
                    issues.add(new VerifyResult.Issue(
                        it.path("claim").asText(""),
                        it.path("citation").asText(""),
                        it.path("explanation").asText("")));
                }
            }
            return new VerifyResult(passed, issues);
        } catch (Exception e) {
            return new VerifyResult(false, List.of(new VerifyResult.Issue("", "", "verifier parse error")));
        }
    }

    private List<Citation> buildCitations(String draft, Map<String, HybridSearchRepository.ChunkRow> markerToChunk,
                                          List<HdsVersion> versions) {
        Map<UUID, HdsVersion> byVid = new HashMap<>();
        versions.forEach(v -> byVid.put(v.getId(), v));
        List<Citation> out = new ArrayList<>();
        Pattern p = Pattern.compile("\\[c(\\d+)\\]");
        Matcher m = p.matcher(draft);
        Set<String> seen = new LinkedHashSet<>();
        while (m.find()) {
            String marker = "c" + m.group(1);
            if (!seen.add(marker)) continue;
            var chunk = markerToChunk.get(marker);
            if (chunk == null) continue;
            var ver = byVid.get(chunk.hdsVersionId());
            String label = ver == null ? "Unknown" : ver.getVersionLabel();
            String excerpt = chunk.content().length() > 200
                ? chunk.content().substring(0, 200) + "…"
                : chunk.content();
            out.add(new Citation(marker, chunk.id(), chunk.hdsVersionId(), label,
                chunk.sectionPath(), chunk.pageStart(), chunk.pageEnd(), excerpt));
        }
        return out;
    }

    private RetrievalAnswer safeFail(String question, List<UUID> versionIds, long started) {
        var meta = Map.<String, Object>of("duration_ms", (int) (System.currentTimeMillis() - started), "rounds", 0);
        return new RetrievalAnswer(SAFE_FAIL, List.of(),
            new VerifyResult(true, List.of()), meta);
    }

    private void logQuery(UUID userId, UUID conversationId, String question, List<UUID> versionIds,
                          List<UUID> retrievedIds, RetrievalAnswer answer, long started) {
        try {
            var entry = new HdsQueryLog();
            entry.setUserId(userId);
            entry.setConversationId(conversationId);
            entry.setQueryText(question);
            entry.setSelectedVersionIds(versionIds.toArray(new UUID[0]));
            entry.setRetrievedChunkIds(retrievedIds.toArray(new UUID[0]));
            entry.setAnswerText(answer.answer());
            entry.setDurationMs((Integer) answer.metadata().get("duration_ms"));
            entry.setVerifierPassed(answer.verifier().passed());
            entry.setRounds((Integer) answer.metadata().getOrDefault("rounds", 0));
            logRepo.save(entry);
        } catch (Exception e) {
            log.warn("query log save failed", e);
        }
    }

    private static List<UUID> dedupe(List<UUID> existing, List<UUID> add) {
        var set = new LinkedHashSet<>(existing);
        set.addAll(add);
        return new ArrayList<>(set);
    }

    private static List<String> jsonArrayToList(JsonNode n) {
        if (n == null || !n.isArray()) return List.of();
        List<String> out = new ArrayList<>(n.size());
        n.forEach(e -> out.add(e.asText()));
        return out;
    }
}

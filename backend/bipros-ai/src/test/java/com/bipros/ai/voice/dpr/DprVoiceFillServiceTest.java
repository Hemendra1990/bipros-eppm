package com.bipros.ai.voice.dpr;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.ai.provider.LlmProvider;
import com.bipros.ai.provider.LlmProviderConfig;
import com.bipros.ai.provider.LlmProviderConfigRepository;
import com.bipros.ai.provider.ModelCapabilityRegistry;
import com.bipros.ai.provider.OpenAiCompatibleProvider;
import com.bipros.ai.voice.SpeechToTextService;
import com.bipros.project.application.service.BoqService;
import com.bipros.project.domain.model.BoqItem;
import com.bipros.project.domain.repository.BoqItemRepository;
import com.bipros.resource.application.dto.role.EquipmentRoleVariantResponse;
import com.bipros.resource.application.dto.role.ManpowerRoleRateResponse;
import com.bipros.resource.application.dto.role.MaterialRoleVariantResponse;
import com.bipros.resource.application.dto.role.RoleAssignmentResponse;
import com.bipros.resource.application.service.role.RoleAssignmentService;
import com.bipros.resource.application.service.role.RoleRateService;
import com.bipros.security.application.dto.UserResponse;
import com.bipros.security.application.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DprVoiceFillServiceTest {

    private SpeechToTextService speechToTextService;
    private LlmProviderConfigRepository providerConfigRepository;
    private OpenAiCompatibleProvider provider;
    private DprVoiceFillSchema schemaBuilder;
    private ObjectMapper objectMapper;
    private UserService userService;
    private ActivityRepository activityRepository;
    private BoqItemRepository boqItemRepository;
    private BoqService boqService;
    private RoleRateService roleRateService;
    private RoleAssignmentService roleAssignmentService;
    private ModelCapabilityRegistry capabilityRegistry;
    private DprVoiceFillService service;

    private final UUID projectId = UUID.randomUUID();
    private final UUID activityId = UUID.randomUUID();
    private final UUID manpowerRoleId = UUID.randomUUID();
    private final UUID manpowerVariantId = UUID.randomUUID();
    private final UUID equipmentRoleId = UUID.randomUUID();
    private final UUID equipmentVariantId = UUID.randomUUID();
    private final UUID materialRoleId = UUID.randomUUID();
    private final UUID materialVariantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        speechToTextService = mock(SpeechToTextService.class);
        providerConfigRepository = mock(LlmProviderConfigRepository.class);
        provider = mock(OpenAiCompatibleProvider.class);
        schemaBuilder = new DprVoiceFillSchema(new ObjectMapper());
        objectMapper = new ObjectMapper();
        userService = mock(UserService.class);
        activityRepository = mock(ActivityRepository.class);
        boqItemRepository = mock(BoqItemRepository.class);
        boqService = mock(BoqService.class);
        roleRateService = mock(RoleRateService.class);
        roleAssignmentService = mock(RoleAssignmentService.class);
        capabilityRegistry = new ModelCapabilityRegistry();

        service = new DprVoiceFillService(
            speechToTextService, providerConfigRepository, provider, schemaBuilder,
            objectMapper, userService,
            activityRepository, boqItemRepository, boqService,
            roleRateService, roleAssignmentService, capabilityRegistry);

        when(speechToTextService.transcribe(any(), any(), any(), any())).thenReturn("5 masons, 1 JCB");
        when(providerConfigRepository.findByIsDefaultTrueAndIsActiveTrue())
            .thenReturn(Optional.of(providerConfig("gpt-4o")));
        when(userService.listUsers(any(Pageable.class), anyString())).thenReturn(Page.empty());
        when(activityRepository.findByProjectId(any())).thenReturn(List.of());
        when(boqItemRepository.findByProjectIdOrderByItemNoAsc(any())).thenReturn(List.of());
        when(boqService.listForActivity(any(), any())).thenReturn(List.of());
    }

    private LlmProviderConfig providerConfig(String model) {
        LlmProviderConfig cfg = new LlmProviderConfig();
        cfg.setModel(model);
        cfg.setMaxTokens(4096);
        cfg.setTimeoutMs(60000);
        return cfg;
    }

    private DprVoiceFillRequest requestWithActivity() {
        ObjectNode state = objectMapper.createObjectNode();
        state.put("activityId", activityId.toString());
        return new DprVoiceFillRequest(state, List.of(), null);
    }

    @Test
    void loadsManpowerRolesFromRateBookAndPlannedAssignments() {
        RoleAssignmentResponse planned = new RoleAssignmentResponse(
            UUID.randomUUID(), activityId, "Excavation", projectId,
            manpowerRoleId, "Mason", "MANPOWER", manpowerVariantId, "Grade I",
            5, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal("500"), "DAY", "RATE", null, null, false);
        when(roleAssignmentService.listForActivity(activityId)).thenReturn(List.of(planned));

        ManpowerRoleRateResponse bookEntry = new ManpowerRoleRateResponse(
            UUID.randomUUID(), manpowerRoleId, "Mason", UUID.randomUUID(), "Skilled",
            UUID.randomUUID(), "Grade II", "DAY", new BigDecimal("500"), true);
        when(roleRateService.listAllManpower()).thenReturn(List.of(bookEntry));

        when(roleRateService.listAllEquipment()).thenReturn(List.of());
        when(roleRateService.listAllMaterial()).thenReturn(List.of());

        when(provider.chat(any(), any())).thenReturn(llmResponse("{}"));

        service.fill(projectId, emptyAudio(), requestWithActivity());

        ArgumentCaptor<LlmProvider.ChatRequest> captor = ArgumentCaptor.forClass(LlmProvider.ChatRequest.class);
        verify(provider).chat(any(), captor.capture());
        String prompt = captor.getValue().messages().get(1).content();
        assertThat(prompt).contains("Manpower roles");
        assertThat(prompt).contains(manpowerVariantId.toString());
        assertThat(prompt).contains(bookEntry.id().toString());
    }

    @Test
    void loadsEquipmentAndMaterialRolesFromRateBook() {
        when(roleAssignmentService.listForActivity(activityId)).thenReturn(List.of());

        EquipmentRoleVariantResponse eqBook = new EquipmentRoleVariantResponse(
            equipmentVariantId, equipmentRoleId, "JCB", "JCB", "3DX", "HOUR",
            new BigDecimal("800"), new BigDecimal("40"), true);
        when(roleRateService.listAllEquipment()).thenReturn(List.of(eqBook));

        MaterialRoleVariantResponse matBook = new MaterialRoleVariantResponse(
            materialVariantId, materialRoleId, "Aggregate", "20mm", "Cum",
            new BigDecimal("1200"), true);
        when(roleRateService.listAllMaterial()).thenReturn(List.of(matBook));

        when(roleRateService.listAllManpower()).thenReturn(List.of());
        when(provider.chat(any(), any())).thenReturn(llmResponse("{}"));

        service.fill(projectId, emptyAudio(), requestWithActivity());

        ArgumentCaptor<LlmProvider.ChatRequest> captor = ArgumentCaptor.forClass(LlmProvider.ChatRequest.class);
        verify(provider).chat(any(), captor.capture());
        String prompt = captor.getValue().messages().get(1).content();
        assertThat(prompt).contains("Equipment roles");
        assertThat(prompt).contains(equipmentVariantId.toString());
        assertThat(prompt).contains("Material roles");
        assertThat(prompt).contains(materialVariantId.toString());
    }

    @Test
    void loadsRateBookEvenWhenActivityIdIsNull() {
        ObjectNode state = objectMapper.createObjectNode();
        DprVoiceFillRequest req = new DprVoiceFillRequest(state, List.of(), null);

        ManpowerRoleRateResponse bookEntry = new ManpowerRoleRateResponse(
            manpowerVariantId, manpowerRoleId, "Mason", UUID.randomUUID(), "Skilled",
            UUID.randomUUID(), "Grade I", "DAY", new BigDecimal("500"), true);
        when(roleRateService.listAllManpower()).thenReturn(List.of(bookEntry));
        when(roleRateService.listAllEquipment()).thenReturn(List.of());
        when(roleRateService.listAllMaterial()).thenReturn(List.of());
        when(roleAssignmentService.listForActivity(any())).thenReturn(List.of());
        when(provider.chat(any(), any())).thenReturn(llmResponse("{}"));

        service.fill(projectId, emptyAudio(), req);

        verify(roleRateService).listAllManpower();
        ArgumentCaptor<LlmProvider.ChatRequest> captor = ArgumentCaptor.forClass(LlmProvider.ChatRequest.class);
        verify(provider).chat(any(), captor.capture());
        String prompt = captor.getValue().messages().get(1).content();
        assertThat(prompt).contains(manpowerVariantId.toString());
    }

    private org.springframework.web.multipart.MultipartFile emptyAudio() {
        org.springframework.web.multipart.MultipartFile audio =
            mock(org.springframework.web.multipart.MultipartFile.class);
        when(audio.isEmpty()).thenReturn(false);
        when(audio.getOriginalFilename()).thenReturn("audio.webm");
        when(audio.getContentType()).thenReturn("audio/webm");
        try {
            when(audio.getBytes()).thenReturn(new byte[]{0, 1, 2});
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        return audio;
    }

    private LlmProvider.ChatResponse llmResponse(String content) {
        return new LlmProvider.ChatResponse(content, List.of(), null, "gpt-4o");
    }

    // ─── deterministic dropdown resolution ────────────────────────────────────

    @Test
    void resolvesSupervisorByName() {
        UUID vijay = UUID.randomUUID();
        when(userService.listUsers(any(Pageable.class), anyString())).thenReturn(new PageImpl<>(List.of(
            user(vijay, "Vijay", "Kumar", "vijaykumar", "EMP-007", "SUPERVISOR"),
            user(UUID.randomUUID(), "Rahul", null, "rahul", "EMP-002", "SITE_MANAGER"))));
        stubLlmPatch("{\"supervisorUserId\":null,\"supervisorName\":\"Vijay Kumar\"}");

        JsonNode patch = service.fill(projectId, emptyAudio(), requestNoActivity()).patch();

        assertThat(patch.path("supervisorUserId").asText()).isEqualTo(vijay.toString());
        assertThat(patch.path("supervisorName").asText()).isEqualTo("Vijay Kumar");
    }

    @Test
    void resolvesSupervisorByEmployeeCode() {
        UUID vijay = UUID.randomUUID();
        when(userService.listUsers(any(Pageable.class), anyString())).thenReturn(new PageImpl<>(List.of(
            user(vijay, "Vijay", "Kumar", "vijaykumar", "EMP-007", "SUPERVISOR"),
            user(UUID.randomUUID(), "Rahul", null, "rahul", "EMP-002", "SITE_MANAGER"))));
        stubLlmPatch("{\"supervisorUserId\":null,\"supervisorName\":\"EMP-007\"}");

        JsonNode patch = service.fill(projectId, emptyAudio(), requestNoActivity()).patch();

        assertThat(patch.path("supervisorUserId").asText()).isEqualTo(vijay.toString());
    }

    @Test
    void ambiguousSupervisorDemotedToFollowUp() {
        when(userService.listUsers(any(Pageable.class), anyString())).thenReturn(new PageImpl<>(List.of(
            user(UUID.randomUUID(), "Anil", "Kumar", "anil", "E1", "SUPERVISOR"),
            user(UUID.randomUUID(), "Sunil", "Kumar", "sunil", "E2", "SUPERVISOR"))));
        stubLlmPatch("{\"supervisorUserId\":null,\"supervisorName\":\"Kumar\"}");

        DprVoiceFillResponse res = service.fill(projectId, emptyAudio(), requestNoActivity());

        assertThat(res.patch().path("supervisorUserId").isNull()).isTrue();
        assertThat(res.followUpQuestion()).isNotNull();
        assertThat(res.complete()).isFalse();
    }

    @Test
    void resolvesActivityByName() {
        UUID actId = UUID.randomUUID();
        // Build the mocks fully before stubbing the repository, else Mockito flags nested stubbing.
        List<Activity> acts = List.of(
            activity(actId, "2.3", "Mechanical Excavation"),
            activity(UUID.randomUUID(), "2.4", "Borrow Excavation"));
        when(activityRepository.findByProjectId(any())).thenReturn(acts);
        stubLlmPatch("{\"activityId\":null,\"activityName\":\"Mechanical Excavation\"}");

        JsonNode patch = service.fill(projectId, emptyAudio(), requestNoActivity()).patch();

        assertThat(patch.path("activityId").asText()).isEqualTo(actId.toString());
    }

    @Test
    void normalizesUnit() {
        stubLlmPatch("{\"unit\":\"cubic meter\"}");
        JsonNode patch = service.fill(projectId, emptyAudio(), requestNoActivity()).patch();
        assertThat(patch.path("unit").asText()).isEqualTo("Cum");
    }

    @Test
    void resolvesManpowerRowByTradeAndOmitsUnitRate() {
        stubManpowerMason();
        stubLlmPatch("{\"manpower\":[{\"manpowerRoleRateId\":null,\"roleId\":null,\"trade\":\"Mason\",\"nos\":10}]}");

        JsonNode row = service.fill(projectId, emptyAudio(), requestWithActivity()).patch().path("manpower").get(0);

        assertThat(row.path("manpowerRoleRateId").asText()).isEqualTo(manpowerVariantId.toString());
        assertThat(row.path("roleId").asText()).isEqualTo(manpowerRoleId.toString());
        assertThat(row.path("trade").asText()).isEqualTo("Mason");
        assertThat(row.has("unitRate")).as("cost is recomputed server-side, not emitted").isFalse();
    }

    @Test
    void rescuesHallucinatedManpowerVariantFromTrade() {
        stubManpowerMason();
        String bogus = UUID.randomUUID().toString();
        stubLlmPatch("{\"manpower\":[{\"manpowerRoleRateId\":\"" + bogus + "\",\"roleId\":\"" + bogus
            + "\",\"trade\":\"Mason\"}]}");

        JsonNode row = service.fill(projectId, emptyAudio(), requestWithActivity()).patch().path("manpower").get(0);

        assertThat(row.path("manpowerRoleRateId").asText()).isEqualTo(manpowerVariantId.toString());
        assertThat(row.path("roleId").asText()).isEqualTo(manpowerRoleId.toString());
    }

    @Test
    void unmatchedManpowerRowNulledAndDemoted() {
        stubManpowerMason();
        stubLlmPatch("{\"manpower\":[{\"manpowerRoleRateId\":null,\"roleId\":null,\"trade\":\"Welder\"}]}");

        DprVoiceFillResponse res = service.fill(projectId, emptyAudio(), requestWithActivity());
        JsonNode row = res.patch().path("manpower").get(0);

        assertThat(row.path("manpowerRoleRateId").isNull()).isTrue();
        assertThat(row.path("roleId").isNull()).isTrue();
        assertThat(res.followUpQuestion()).isNotNull();
    }

    // ─── helpers for resolution tests ─────────────────────────────────────────

    private void stubManpowerMason() {
        when(roleRateService.listAllManpower()).thenReturn(List.of(new ManpowerRoleRateResponse(
            manpowerVariantId, manpowerRoleId, "Mason", UUID.randomUUID(), "Skilled",
            UUID.randomUUID(), "Grade I", "Nos", new BigDecimal("500"), true)));
        when(roleRateService.listAllEquipment()).thenReturn(List.of());
        when(roleRateService.listAllMaterial()).thenReturn(List.of());
        when(roleAssignmentService.listForActivity(any())).thenReturn(List.of());
    }

    private void stubLlmPatch(String patchJson) {
        when(provider.chat(any(), any())).thenReturn(llmResponse(
            "{\"patch\":" + patchJson
                + ",\"photoCaptions\":[],\"followUpQuestion\":null,\"complete\":true,\"assistantMessage\":\"ok\"}"));
    }

    private DprVoiceFillRequest requestNoActivity() {
        return new DprVoiceFillRequest(objectMapper.createObjectNode(), List.of(), null);
    }

    private UserResponse user(UUID id, String first, String last, String username, String empCode, String role) {
        return new UserResponse(id, username, null, first, last, true, List.of(role),
            null, null, null, null, null, null, empCode, null, null, null, null, null, List.of(), List.of());
    }

    private Activity activity(UUID id, String code, String name) {
        Activity a = mock(Activity.class);
        when(a.getId()).thenReturn(id);
        when(a.getCode()).thenReturn(code);
        when(a.getName()).thenReturn(name);
        return a;
    }
}
